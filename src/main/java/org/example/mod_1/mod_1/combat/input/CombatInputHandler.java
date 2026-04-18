package org.example.mod_1.mod_1.combat.input;

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
import org.example.mod_1.mod_1.Mod_1;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.CombatStateMachine;
import org.example.mod_1.mod_1.combat.WeaponDetector;
import org.example.mod_1.mod_1.combat.WeaponType;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;
import org.example.mod_1.mod_1.combat.capability.ICombatCapability;
import org.example.mod_1.mod_1.combat.client.CombatAnimationController;
import org.example.mod_1.mod_1.combat.network.CombatNetworkChannel;
import org.example.mod_1.mod_1.combat.network.CombatStatePacket;
import net.minecraftforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Mod_1.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CombatInputHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BLOCK_HOLD_THRESHOLD = 3;
    private static boolean forcedThirdPerson = false;
    private static boolean inspectCameraActive = false;
    private static CameraType cameraBeforeInspect = CameraType.FIRST_PERSON;
    private static int blockHoldTicks = 0;
    private static boolean rightMousePressed = false;

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
                    if (mc.player.isSprinting() && cap.getWeaponType() == WeaponType.SWORD) {
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
                    if (blockHoldTicks <= BLOCK_HOLD_THRESHOLD) {
                        requestWithPrediction(cap, CombatState.ATTACK_HEAVY);
                    } else if (cap.getState() == CombatState.BLOCK) {
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
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        handleKeyBindings(mc);

        CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
            CombatStateMachine.tick(cap, mc.player.level().getGameTime());

            // 检视打断：移动/攻击输入时退出 INSPECT，或动画播放完毕自动退出
            if (cap.getState() == CombatState.INSPECT) {
                double dx = mc.player.getX() - mc.player.xOld;
                double dz = mc.player.getZ() - mc.player.zOld;
                if (dx * dx + dz * dz > 0.001 || CombatAnimationController.isCurrentAnimFinished(mc.player)) {
                    requestWithPrediction(cap, CombatState.IDLE);
                }
            }

            updateCamera(cap);

            // 右键长按计时：用于区分 ATTACK_HEAVY（短按）和 BLOCK（长按）
            // 左键由 onMouseButton 直接处理，不经过 tick
            if (cap.isWeaponDrawn()) {
                if (rightMousePressed) {
                    blockHoldTicks++;
                    if (blockHoldTicks > BLOCK_HOLD_THRESHOLD && cap.getState() != CombatState.BLOCK) {
                        requestWithPrediction(cap, CombatState.BLOCK);
                    }
                }
            } else {
                rightMousePressed = false;
                blockHoldTicks = 0;
            }

            // Update animation (after all state transitions)
            if (mc.player instanceof AbstractClientPlayer clientPlayer) {
                CombatAnimationController.updateAnimation(clientPlayer, cap);
            }
        });

        for (var player : mc.level.players()) {
            if (player == mc.player) continue;
            CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
                CombatStateMachine.tick(cap, mc.level.getGameTime());
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
                    requestWithPrediction(cap, CombatState.DODGE);
                    // Apply dodge impulse immediately on client for responsiveness
                    CombatCapabilityEvents.applyDodgeImpulse(mc.player);
                }
            });
        }

        while (CombatKeyBindings.INSPECT.consumeClick()) {
            CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
                if (cap.isWeaponDrawn()) requestWithPrediction(cap, CombatState.INSPECT);
            });
        }
    }

    private static void requestWithPrediction(ICombatCapability cap, CombatState target) {
        // Client prediction: apply immediately for responsiveness
        CombatStateMachine.requestTransition(cap, target);
        // Send to server for authoritative validation
        CombatNetworkChannel.CHANNEL.send(
                new CombatStatePacket(target),
                PacketDistributor.SERVER.noArg()
        );
    }

    private static void updateCamera(ICombatCapability cap) {
        Minecraft mc = Minecraft.getInstance();
        CombatState state = cap.getState();

        if (state == CombatState.INSPECT && cap.isWeaponDrawn()) {
            if (!inspectCameraActive) {
                cameraBeforeInspect = mc.options.getCameraType();
                inspectCameraActive = true;
            }
            if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            forcedThirdPerson = false;
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
        boolean shouldBeThirdPerson = cap.isWeaponDrawn()
                || state == CombatState.DRAW_WEAPON
                || state == CombatState.SHEATH_WEAPON;

        if (shouldBeThirdPerson && !forcedThirdPerson) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            forcedThirdPerson = true;
        } else if (!shouldBeThirdPerson && forcedThirdPerson) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            forcedThirdPerson = false;
        }
    }
}
