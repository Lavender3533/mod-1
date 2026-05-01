package org.example.combatarts.combat.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.example.combatarts.CombatArts;
import org.example.combatarts.Config;
import org.example.combatarts.combat.CombatState;
import org.example.combatarts.combat.CombatStateMachine;
import org.example.combatarts.combat.WeaponDetector;
import org.example.combatarts.combat.WeaponType;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;
import org.example.combatarts.combat.capability.ICombatCapability;
import org.example.combatarts.combat.client.CombatAnimationController;
import org.example.combatarts.combat.client.BlockPoseTweaker;
import org.example.combatarts.combat.network.CombatNetworkChannel;
import org.example.combatarts.combat.network.CombatStatePacket;
import net.minecraftforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = CombatArts.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CombatInputHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SHEATH_CAMERA_GRACE_TICKS = 12; // 收刀完成后保持第三人称的宽限期(~0.6s)
    private static boolean forcedThirdPerson = false;
    private static boolean inspectCameraActive = false;
    private static CameraType cameraBeforeInspect = CameraType.FIRST_PERSON;
    private static int blockHoldTicks = 0;
    private static boolean rightMousePressed = false;
    private static boolean heavyKeyDown = false;
    private static int heavyChargeTicks = 0;
    private static boolean lastWeaponDrawn = false;
    private static int sheathCameraGrace = 0;

    /**
     * 拦截鼠标点击 — 拔刀后左右键改为战斗系统处理，不让 vanilla 处理。
     * 返回 true 表示取消 vanilla 的处理。
     */
    public static boolean onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return false;

        return CombatCapabilityEvents.getCombat(mc.player).map(cap -> {
            if (!cap.isWeaponDrawn()) return false;

            int button = event.getButton();
            int action = event.getAction();
            boolean press = action == GLFW.GLFW_PRESS;
            boolean release = action == GLFW.GLFW_RELEASE;

            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                if (press) {
                    // 左键按下：触发轻攻击（冲刺时变冲刺攻击）
                    if (shouldUseDashAttack(mc, cap)) {
                        cap.setComboCount(99);
                        requestWithPrediction(cap, CombatState.ATTACK_LIGHT);
                    } else {
                        requestWithPrediction(cap, CombatState.ATTACK_LIGHT);
                    }
                }
                return true; // 总是取消 vanilla 左键（无论按下/松开/重复）
            }

            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                if (press) {
                    rightMousePressed = true;
                    blockHoldTicks = 0;
                } else if (release && rightMousePressed) {
                    rightMousePressed = false;
                    if (cap.getState() == CombatState.BLOCK) {
                        requestWithPrediction(cap, CombatState.IDLE);
                    }
                    blockHoldTicks = 0;
                }
                return true;
            }

            return false;
        }).orElse(false);
    }

    @SubscribeEvent
    public static void onMovementInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.client.player.LocalPlayer lp)) return;

        CombatCapabilityEvents.getCombat(lp).ifPresent(cap -> {
            CombatState s = cap.getState();
            if (s == CombatState.ATTACK_LIGHT || s == CombatState.ATTACK_HEAVY) {
                // 攻击执行中:缩放移动输入,避免边走边砍滑步、姿势不协调
                event.getInput().moveVector = event.getInput().moveVector.scale(0.4f);
            }
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        handleKeyBindings(mc);

        CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
            CombatStateMachine.tick(cap, mc.player.level().getGameTime());

            // 检视打断：仅冲刺打断（慢走允许保持检视姿态），或动画播放完毕自动退出
            if (cap.getState() == CombatState.INSPECT) {
                if (mc.player.isSprinting() || CombatAnimationController.isCurrentAnimFinished(mc.player)) {
                    requestWithPrediction(cap, CombatState.IDLE);
                }
            }

            updateCamera(cap);

            handleHeavyChargeKey(cap);

            // 右键按住进入 BLOCK（达到 hold threshold 后），松开退出。重击改用 F 键蓄力。
            if (cap.isWeaponDrawn()) {
                if (rightMousePressed) {
                    blockHoldTicks++;
                    if (blockHoldTicks > Config.blockHoldThresholdTicks && cap.getState() != CombatState.BLOCK) {
                        requestWithPrediction(cap, CombatState.BLOCK);
                    }
                }
            } else {
                rightMousePressed = false;
                blockHoldTicks = 0;
                heavyKeyDown = false;
                heavyChargeTicks = 0;
            }

            // Update animation (after all state transitions)
            if (mc.player instanceof AbstractClientPlayer clientPlayer) {
                CombatAnimationController.updateAnimation(clientPlayer, cap);
            }
        });

        for (var player : mc.level.players()) {
            if (player == mc.player) continue;
            CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
                // 远端玩家不跑 state machine — state/stateTimer/combo 全由 CombatSyncPacket 写入。
                // 之前在这里跑 CombatStateMachine.tick 会让本地 timer 独立倒数, 跟服务端 sync 不同步:
                // 比如疯狂连击时, 本地 timer 提前到 0 把 state 过期成 IDLE, 紧接着 sync 包来了又拉回 ATTACK_LIGHT,
                // 动画就在攻击/待战之间抽搐一下。
                if (player instanceof AbstractClientPlayer clientPlayer) {
                    CombatAnimationController.updateAnimation(clientPlayer, cap);
                }
            });
        }
    }

    private static void handleKeyBindings(Minecraft mc) {
        while (CombatKeyBindings.COMBAT_TOGGLE.consumeClick()) {
            CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
                CombatState current = cap.getState();
                // Ignore R key during draw/sheath animation to prevent wasted inputs
                if (current == CombatState.DRAW_WEAPON || current == CombatState.SHEATH_WEAPON) return;

                if (cap.isWeaponDrawn()) {
                    // Sheath — always allowed
                    requestWithPrediction(cap, CombatState.SHEATH_WEAPON);
                } else {
                    // Draw — only if holding a weapon
                    WeaponType type = WeaponDetector.detect(mc.player);
                    if (type != WeaponType.UNARMED) {
                        cap.setWeaponType(type);
                        requestWithPrediction(cap, CombatState.DRAW_WEAPON);
                    }
                }
            });
        }

        while (CombatKeyBindings.DODGE.consumeClick()) {
            CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
                if (cap.isWeaponDrawn() && CombatStateMachine.canTransition(cap, CombatState.DODGE)) {
                    // 客户端预测：立即施力 + 状态切换
                    CombatStateMachine.requestTransition(cap, CombatState.DODGE);
                    float moveX = mc.player.xxa;
                    float moveZ = mc.player.zza;
                    CombatCapabilityEvents.applyDodgeImpulse(mc.player, moveX, moveZ);
                    // 把方向带给服务端，确保服务端施力方向一致
                    CombatNetworkChannel.CHANNEL.send(
                            new CombatStatePacket(CombatState.DODGE, 0, moveX, moveZ),
                            PacketDistributor.SERVER.noArg()
                    );
                }
            });
        }

        while (CombatKeyBindings.INSPECT.consumeClick()) {
            CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
                if (cap.isWeaponDrawn()) requestWithPrediction(cap, CombatState.INSPECT);
            });
        }

        while (CombatKeyBindings.RELOAD_ANIMATIONS.consumeClick()) {
            int count = CombatAnimationController.reloadAnimations();
            mc.gui.getChat().addMessage(
                    net.minecraft.network.chat.Component.literal("§a[Combat Arts] 动画已重载: " + count + " 个")
            );
        }

        // 姿势调试工具
        while (CombatKeyBindings.POSE_CYCLE_BONE.consumeClick()) BlockPoseTweaker.cycleBone();
        while (CombatKeyBindings.POSE_CYCLE_AXIS.consumeClick()) BlockPoseTweaker.cycleAxis();
        while (CombatKeyBindings.POSE_DECREASE.consumeClick())   BlockPoseTweaker.decrease();
        while (CombatKeyBindings.POSE_INCREASE.consumeClick())   BlockPoseTweaker.increase();
        while (CombatKeyBindings.POSE_PRINT.consumeClick())      BlockPoseTweaker.printAll();
        while (CombatKeyBindings.POSE_RESET_ALL.consumeClick())  BlockPoseTweaker.resetAll();
    }

    private static void requestWithPrediction(ICombatCapability cap, CombatState target) {
        requestWithPrediction(cap, target, 0);
    }

    private static void requestWithPrediction(ICombatCapability cap, CombatState target, int extra) {
        if (target == CombatState.ATTACK_HEAVY) {
            cap.setHeavyChargeMultiplier(CombatStateMachine.computeHeavyChargeMultiplier(extra));
        }
        // Client prediction: apply immediately for responsiveness
        CombatStateMachine.requestTransition(cap, target);
        // Send to server for authoritative validation
        CombatNetworkChannel.CHANNEL.send(
                new CombatStatePacket(target, extra),
                PacketDistributor.SERVER.noArg()
        );
    }

    private static boolean shouldUseDashAttack(Minecraft mc, ICombatCapability cap) {
        if (mc.player == null || cap.getWeaponType() != WeaponType.SWORD || !mc.player.isSprinting()) {
            return false;
        }

        CombatState state = cap.getState();
        if (state == CombatState.ATTACK_LIGHT || cap.hasQueuedLightAttack()) {
            return false;
        }

        if (cap.getComboCount() > 0 || state != CombatState.IDLE) return false;

        // 最近 2s 内攻击过 → 算"连击中",不再触发 dash,让玩家走 combo 1/2/3
        long lastAttack = cap.getLastAttackTime();
        if (lastAttack > 0) {
            long gameTime = mc.player.level().getGameTime();
            if (gameTime - lastAttack < 40) return false;
        }
        return true;
    }

    private static void handleHeavyChargeKey(ICombatCapability cap) {
        boolean isDown = cap.isWeaponDrawn() && CombatKeyBindings.HEAVY_ATTACK.isDown();

        if (isDown && !heavyKeyDown) {
            // Press edge → start charging
            if (CombatStateMachine.canTransition(cap, CombatState.ATTACK_HEAVY_CHARGING)) {
                requestWithPrediction(cap, CombatState.ATTACK_HEAVY_CHARGING);
                heavyChargeTicks = 0;
                cap.setChargeTicks(0); // 客户端镜像，让 HUD 进度条能读到
            }
        } else if (!isDown && heavyKeyDown) {
            // Release edge → fire heavy attack with charge multiplier
            if (cap.getState() == CombatState.ATTACK_HEAVY_CHARGING) {
                requestWithPrediction(cap, CombatState.ATTACK_HEAVY, heavyChargeTicks);
            }
            heavyChargeTicks = 0;
            cap.setChargeTicks(0);
        } else if (isDown && cap.getState() == CombatState.ATTACK_HEAVY_CHARGING) {
            heavyChargeTicks++;
            cap.setChargeTicks(heavyChargeTicks); // 客户端镜像
        }

        heavyKeyDown = isDown;
    }

    private static void updateCamera(ICombatCapability cap) {
        Minecraft mc = Minecraft.getInstance();
        CombatState state = cap.getState();

        if (state == CombatState.INSPECT && cap.isWeaponDrawn()) {
            if (!inspectCameraActive) {
                cameraBeforeInspect = mc.options.getCameraType();
                inspectCameraActive = true;
            }
            // 检视用第三人称（看完整角色 + 17 骨骼检视动画）
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            forcedThirdPerson = true;
            return;
        }

        if (inspectCameraActive) {
            if (mc.options.getCameraType() != cameraBeforeInspect) {
                mc.options.setCameraType(cameraBeforeInspect);
            }
            inspectCameraActive = false;
            forcedThirdPerson = cameraBeforeInspect != CameraType.FIRST_PERSON;
        }

        // 进入第三人称的条件：已拔刀 或 正在拔刀 或 正在收刀（让玩家看到收刀全程）
        // 收刀刚完成时再额外保持几 tick，让玩家看到收刀末尾 + idle 过渡完，不要立即跳回第一人称
        boolean drawn = cap.isWeaponDrawn();
        if (lastWeaponDrawn && !drawn) {
            sheathCameraGrace = SHEATH_CAMERA_GRACE_TICKS;
        }
        lastWeaponDrawn = drawn;
        if (sheathCameraGrace > 0) sheathCameraGrace--;

        boolean shouldBeThirdPerson = drawn
                || state == CombatState.DRAW_WEAPON
                || state == CombatState.SHEATH_WEAPON
                || sheathCameraGrace > 0;

        if (shouldBeThirdPerson && !forcedThirdPerson) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            forcedThirdPerson = true;
        } else if (!shouldBeThirdPerson && forcedThirdPerson) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            forcedThirdPerson = false;
        }
    }
}
