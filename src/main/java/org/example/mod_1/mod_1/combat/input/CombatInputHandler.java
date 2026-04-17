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
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Mod_1.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CombatInputHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean forcedThirdPerson = false;
    private static int blockHoldTicks = 0;
    private static boolean rightMouseHeld = false;
    private static boolean leftMouseDownPrev = false;
    private static boolean rightMouseDownPrev = false;

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
            return button == InputConstants.MOUSE_BUTTON_LEFT
                    || button == InputConstants.MOUSE_BUTTON_RIGHT;
        }).orElse(false);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        handleKeyBindings(mc);

        CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
            CombatStateMachine.tick(cap, mc.player.level().getGameTime());

            // 检视打断：移动/攻击输入时退出 INSPECT，或动画播放完毕自动退出
            if (cap.getState() == CombatState.INSPECT) {
                double dx = mc.player.getX() - mc.player.xOld;
                double dz = mc.player.getZ() - mc.player.zOld;
                if (dx * dx + dz * dz > 0.001 || CombatAnimationController.isCurrentAnimFinished()) {
                    requestWithPrediction(cap, CombatState.IDLE);
                }
            }

            updateCamera(cap);

            // 先处理攻击/右键输入，保证 state 转换在本tick内被动画看到
            if (cap.isWeaponDrawn()) {
                // 左键：边沿检测（从未按→按下 = 一次点击）
                boolean leftDown = mc.options.keyAttack.isDown();
                if (leftDown && !leftMouseDownPrev) {
                    if (mc.player.isSprinting() && cap.getWeaponType() == WeaponType.SWORD) {
                        cap.setComboCount(99);
                        requestWithPrediction(cap, CombatState.ATTACK_LIGHT);
                    } else {
                        requestWithPrediction(cap, CombatState.ATTACK_LIGHT);
                    }
                }
                leftMouseDownPrev = leftDown;

                handleRightClick(mc, cap);
            } else {
                leftMouseDownPrev = false;
                rightMouseDownPrev = false;
                rightMouseHeld = false;
                blockHoldTicks = 0;
            }

            // Update animation (after all state transitions)
            if (mc.player instanceof AbstractClientPlayer clientPlayer) {
                CombatAnimationController.updateAnimation(clientPlayer, cap);
            }
        });
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

    private static void handleRightClick(Minecraft mc, ICombatCapability cap) {
        boolean pressed = mc.options.keyUse.isDown();

        if (pressed) {
            if (!rightMouseHeld) {
                rightMouseHeld = true;
                blockHoldTicks = 0;
            }
            blockHoldTicks++;
            if (blockHoldTicks > 3 && cap.getState() != CombatState.BLOCK) {
                requestWithPrediction(cap, CombatState.BLOCK);
            }
        } else if (rightMouseHeld) {
            rightMouseHeld = false;
            if (blockHoldTicks <= 3) {
                requestWithPrediction(cap, CombatState.ATTACK_HEAVY);
            } else if (cap.getState() == CombatState.BLOCK) {
                requestWithPrediction(cap, CombatState.IDLE);
            }
            blockHoldTicks = 0;
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
