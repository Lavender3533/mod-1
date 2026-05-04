package org.example.combatarts.combat.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import org.example.combatarts.Config;
import org.example.combatarts.combat.CombatState;
import org.example.combatarts.combat.CombatStateMachine;
import org.example.combatarts.combat.CombatSoundPlayer;
import org.example.combatarts.combat.WeaponDetector;
import org.example.combatarts.combat.WeaponType;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;
import org.slf4j.Logger;

public class CombatStatePacket {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(CombatStatePacket::encode, CombatStatePacket::decode);

    private final byte stateOrdinal;
    private final int extra; // chargeTicks when target is ATTACK_HEAVY; 0 otherwise
    private final float moveX; // for DODGE: client's xxa input at dodge moment
    private final float moveZ; // for DODGE: client's zza input at dodge moment

    public CombatStatePacket(CombatState state) {
        this(state, 0, 0f, 0f);
    }

    public CombatStatePacket(CombatState state, int extra) {
        this(state, extra, 0f, 0f);
    }

    public CombatStatePacket(CombatState state, int extra, float moveX, float moveZ) {
        this.stateOrdinal = (byte) state.ordinal();
        this.extra = extra;
        this.moveX = moveX;
        this.moveZ = moveZ;
    }

    private CombatStatePacket(byte stateOrdinal, int extra, float moveX, float moveZ) {
        this.stateOrdinal = stateOrdinal;
        this.extra = extra;
        this.moveX = moveX;
        this.moveZ = moveZ;
    }

    public static void encode(CombatStatePacket msg, RegistryFriendlyByteBuf buf) {
        buf.writeByte(msg.stateOrdinal);
        buf.writeVarInt(msg.extra);
        buf.writeFloat(msg.moveX);
        buf.writeFloat(msg.moveZ);
    }

    public static CombatStatePacket decode(RegistryFriendlyByteBuf buf) {
        return new CombatStatePacket(buf.readByte(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(CombatStatePacket msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        CombatState requested = CombatState.fromOrdinal(msg.stateOrdinal);

        CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
            // 任何动作包到达时,先按当前主手物刷新 weaponType, 避免:
            //  切换热栏 → 同 tick 内点击 → 攻击包先到, tick 检测还没跑 → 用旧 weaponType.
            // 持非武器点击攻击/格挡 → 静默拒绝 (不收刀, 不影响后续滚回武器后的攻击)。
            // SHEATH_WEAPON 例外: 收刀过程允许已经是非武器, 让动画播放完。
            // 注意: 只在"安全"状态下切 weaponType, 否则 ATTACK_LIGHT 中途切会让动画跳变 + combo 错位。
            if (requested != CombatState.SHEATH_WEAPON) {
                WeaponType actual = WeaponDetector.detect(player);

                // 安全状态 (IDLE/DRAW/SHEATH/INSPECT/BLOCK) 才同步 weaponType, 不重置 combo
                // (combo 由 comboWindowTicks 自然超时管, 不需要在 swap 时强制清, 否则 3 段动画会被打断)
                CombatState curState = cap.getState();
                boolean safeToSwap = curState == CombatState.IDLE
                        || curState == CombatState.DRAW_WEAPON
                        || curState == CombatState.SHEATH_WEAPON
                        || curState == CombatState.INSPECT
                        || curState == CombatState.BLOCK;
                if (safeToSwap && actual != cap.getWeaponType()) {
                    cap.setWeaponType(actual);
                }

                // 非武器时只允许 IDLE / SHEATH_WEAPON; 攻击/格挡/拔刀/检视等通通拒绝, 但保留 drawn 状态。
                if (actual == WeaponType.UNARMED && requested != CombatState.IDLE) {
                    return;
                }
            }

            // Refresh weapon type from actual held item on server side
            if (requested == CombatState.DRAW_WEAPON) {
                WeaponType detected = WeaponDetector.detect(player);
                if (detected != WeaponType.UNARMED) {
                    cap.setWeaponType(detected);
                }
            }

            // Heavy attack: server computes charge multiplier from reported hold ticks
            if (requested == CombatState.ATTACK_HEAVY) {
                cap.setHeavyChargeMultiplier(CombatStateMachine.computeHeavyChargeMultiplier(msg.extra));
            }

            // Dash attack: 服务端独立检测 sprint 状态(客户端检测后无法把 combo=99 传到服务端)
            // 只在玩家最近 2s 没攻击过的情况下触发,否则继续走正常 combo 1/2/3
            if (requested == CombatState.ATTACK_LIGHT
                    && cap.getWeaponType() == WeaponType.SWORD
                    && player.isSprinting()
                    && cap.getComboCount() <= 0
                    && cap.getState() == CombatState.IDLE) {
                long lastAttack = cap.getLastAttackTime();
                long gameTime = player.level().getGameTime();
                boolean recentlyAttacked = lastAttack > 0 && (gameTime - lastAttack) < 40;
                if (!recentlyAttacked) {
                    cap.setComboCount(99);
                }
            }

            CombatState prevState = cap.getState();
            CombatStateMachine.requestTransition(cap, requested);

            // DODGE: 立即按客户端报告的方向施力（避免依赖服务端不可靠的 player.xxa）
            if (cap.getState() == CombatState.DODGE && prevState != CombatState.DODGE) {
                CombatCapabilityEvents.applyDodgeImpulse(player, msg.moveX, msg.moveZ);
            }

            // Play sound on actual state transitions only.
            // 不要在 ATTACK_LIGHT 同状态点击时重复触发: 那种点击多半被 requestTransition 当作
            // queuedLightAttack 排队, 没有真正推进 combo, 但旧实现会按"每次点击响一声"播放, 表现为
            // 连点左键音效翻倍。combo 推进时的音效由 server tick handler 监测 prevComboCount 变化触发。
            if (cap.getState() != prevState) {
                CombatSoundPlayer.playStateSound(player, cap.getState(), cap.getWeaponType(), cap.getComboCount());
            }

            CombatCapabilityEvents.broadcastCombatState(player, cap);
        });
    }
}
