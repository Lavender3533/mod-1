package org.example.combatarts.combat.capability;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.example.combatarts.CombatArts;
import org.example.combatarts.combat.CombatState;
import org.example.combatarts.combat.CombatStateMachine;
import org.example.combatarts.combat.CombatSoundPlayer;
import org.example.combatarts.combat.WeaponDetector;
import org.example.combatarts.combat.WeaponType;
import org.example.combatarts.combat.network.CombatNetworkChannel;
import org.example.combatarts.combat.network.CombatSyncPacket;
import org.slf4j.Logger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Mod.EventBusSubscriber(modid = CombatArts.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CombatCapabilityEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Capability<ICombatCapability> COMBAT_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final Identifier CAP_KEY =
            Identifier.fromNamespaceAndPath(CombatArts.MODID, "combat");

    public static LazyOptional<ICombatCapability> getCombat(Player player) {
        return player.getCapability(COMBAT_CAPABILITY);
    }

    // 渲染层用: 决定剑该挂在手里还是背后. 拔刀第 4 tick (0.20s, 手到背后抓握时) 切到手,
    // 收刀第 6 tick (0.30s, 剑入鞘时) 切回背后. 否则直接读 isWeaponDrawn().
    public static boolean shouldRenderWeaponInHand(ICombatCapability cap) {
        CombatState state = cap.getState();
        int elapsed = state.getDurationTicks() - cap.getStateTimer();
        return switch (state) {
            case DRAW_WEAPON -> elapsed >= 4;
            case SHEATH_WEAPON -> elapsed < 6;
            default -> cap.isWeaponDrawn();
        };
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent.Entities event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(CAP_KEY, new CombatCapabilityProvider());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player newPlayer = event.getEntity();

        original.reviveCaps();
        getCombat(original).ifPresent(oldCap -> {
            getCombat(newPlayer).ifPresent(newCap -> {
                newCap.deserializeNBT(oldCap.serializeNBT());
                // 死亡复制 NBT 时强制清掉战斗状态 — 死时武器掉落, 重生 weaponDrawn=true 会让玩家
                // 一捡到武器就被强制第三人称(updateCamera 看到 drawn=true), 也会渲染错误的拔刀动画。
                if (event.isWasDeath()) {
                    newCap.setState(CombatState.IDLE);
                    newCap.setStateTimer(0);
                    newCap.setWeaponDrawn(false);
                    newCap.resetCombo();
                }
            });
        });
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.side() == LogicalSide.SERVER) {
            getCombat(event.player()).ifPresent(cap -> {
                CombatState prevState = cap.getState();
                boolean prevWeaponDrawn = cap.isWeaponDrawn();
                int prevComboCount = cap.getComboCount();
                int prevStateTimer = cap.getStateTimer();
                WeaponType prevWeaponType = cap.getWeaponType();

                // 拔刀状态下检测当前手持武器, 同步 weaponType.
                // 仅做"换武器类型"的同步, 不做"换到非武器就自动收刀" — 否则用户滚轮经过
                // 箭/红石/肉 等非武器槽位会被静默收刀, 滚回武器后还以为在战斗状态。
                // 不重置 combo: 攻击中 swap 时 combo 突然归 0 会让动画选择掉到 fallback (light_1),
                // 造成 3 段连击动画跳变。combo 由 comboWindowTicks 自然超时管理。
                if (cap.isWeaponDrawn() && isSafeForWeaponSwap(prevState)) {
                    WeaponType actual = WeaponDetector.detect(event.player());
                    if (actual != prevWeaponType) {
                        cap.setWeaponType(actual);
                    }
                }

                CombatStateMachine.tick(cap, event.player().level().getGameTime());

                if (cap.getState() == CombatState.DODGE) {
                    int elapsed = CombatState.DODGE.getDurationTicks() - cap.getStateTimer();
                    if (elapsed == 1) {
                        Player p = event.player();
                        if (p.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                            org.example.combatarts.combat.CombatParticles.spawnDodgeParticles(sl, p.position());
                        }
                        // 施力已在 packet handler 收到 DODGE 包时立即触发，这里只做粒子
                    }
                }

                // 蓄力中：周期 aura + 满蓄 ready 一次性反馈 + 移动减速(蹲下豁免)
                if (cap.getState() == CombatState.ATTACK_HEAVY_CHARGING) {
                    int prevCharge = cap.getChargeTicks();
                    int newCharge = prevCharge + 1;
                    cap.setChargeTicks(newCharge);
                    Player p = event.player();

                    // 移动减速:站立时 SLOWNESS II;蹲下不减速。短时长每 tick 续约,松开蓄力即自然失效
                    if (!p.isCrouching()) {
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.SLOWNESS,
                                5,      // 5 tick 续约,蓄力停了/蹲下了几 tick 内自动消
                                1,      // SLOWNESS II
                                true, false, false));
                    }

                    if (p.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        if (newCharge % 4 == 0) {
                            org.example.combatarts.combat.CombatParticles.spawnHeavyChargeAura(sl, p.position());
                        }
                        int maxTicks = org.example.combatarts.Config.heavyChargeMaxTicks;
                        if (prevCharge < maxTicks && newCharge >= maxTicks) {
                            org.example.combatarts.combat.CombatParticles.spawnHeavyChargeReady(sl, p.position());
                            sl.playSound(null, p.getX(), p.getY(), p.getZ(),
                                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(),
                                    net.minecraft.sounds.SoundSource.PLAYERS,
                                    0.5f, 1.6f);
                        }
                    }
                } else if (prevState == CombatState.ATTACK_HEAVY_CHARGING) {
                    cap.setChargeTicks(0); // exit charging → reset
                }

                if (cap.getState() == CombatState.ATTACK_LIGHT
                        && (prevState != CombatState.ATTACK_LIGHT || prevComboCount != cap.getComboCount())) {
                    CombatSoundPlayer.playStateSound(event.player(), cap.getState(), cap.getWeaponType(), cap.getComboCount());
                }

                // 攻击执行中减速 → 在客户端 CombatInputHandler.onMovementInput 里做(避免改 attribute 触发 FOV)

                if (prevState != cap.getState()
                        || prevWeaponDrawn != cap.isWeaponDrawn()
                        || prevComboCount != cap.getComboCount()
                        || prevWeaponType != cap.getWeaponType()
                        || (prevStateTimer > 0 && cap.getStateTimer() == 0)) {
                    broadcastCombatState(event.player(), cap);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer tracker)) return;
        Entity target = event.getTarget();
        if (!(target instanceof Player trackedPlayer)) return;

        getCombat(trackedPlayer).ifPresent(cap -> syncCombatStateToPlayer(trackedPlayer, cap, tracker));
    }

    // 拔刀中切换武器要选"安全时机" — 攻击/闪避/格挡反击/重击的命中帧期间切换会引发
    // 伤害结算混乱(如重击中途切矛, range/angle 立刻变), 所以只在静态状态下接受切换。
    private static boolean isSafeForWeaponSwap(CombatState state) {
        return switch (state) {
            case IDLE, DRAW_WEAPON, SHEATH_WEAPON, INSPECT, BLOCK -> true;
            default -> false;
        };
    }

    public static void broadcastCombatState(Player player, ICombatCapability cap) {
        CombatSyncPacket sync = new CombatSyncPacket(
                player.getId(),
                cap.getState(),
                cap.getWeaponType(),
                cap.isWeaponDrawn(),
                cap.getComboCount(),
                cap.getStateTimer()
        );
        CombatNetworkChannel.CHANNEL.send(sync, PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player));
    }

    public static void syncCombatStateToPlayer(Player player, ICombatCapability cap, ServerPlayer receiver) {
        CombatSyncPacket sync = new CombatSyncPacket(
                player.getId(),
                cap.getState(),
                cap.getWeaponType(),
                cap.isWeaponDrawn(),
                cap.getComboCount(),
                cap.getStateTimer()
        );
        CombatNetworkChannel.CHANNEL.send(sync, PacketDistributor.PLAYER.with(receiver));
    }

    public static void applyDodgeImpulse(Player player) {
        // 客户端调用入口：从本地 player 输入读取方向
        applyDodgeImpulse(player, player.xxa, player.zza);
    }

    public static void applyDodgeImpulse(Player player, float moveX, float moveZ) {
        Vec3 moveInput = new Vec3(moveX, 0, moveZ);
        Vec3 direction;
        if (moveInput.lengthSqr() > 0.001) {
            float yawRad = (float) Math.toRadians(player.getYRot());
            double sin = Math.sin(yawRad);
            double cos = Math.cos(yawRad);
            direction = new Vec3(
                    moveInput.x * cos - moveInput.z * sin,
                    0,
                    moveInput.x * sin + moveInput.z * cos
            ).normalize();
        } else {
            // 无方向输入 → 后撤
            float yawRad = (float) Math.toRadians(player.getYRot());
            direction = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
        }
        player.setDeltaMovement(direction.scale(0.8));
        player.hurtMarked = true;
    }
}
