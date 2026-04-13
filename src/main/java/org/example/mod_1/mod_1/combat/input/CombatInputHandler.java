package org.example.mod_1.mod_1.combat.input;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.api.distmarker.Dist;
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
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Mod_1.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CombatInputHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean forcedThirdPerson = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        handleKeyBindings(mc);

        CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
            CombatStateMachine.tick(cap, mc.player.level().getGameTime());
            updateCamera(cap);

            // Update animation
            if (mc.player instanceof AbstractClientPlayer clientPlayer) {
                CombatAnimationController.updateAnimation(clientPlayer, cap);
            }

            if (cap.isWeaponDrawn()) {
                while (mc.options.keyAttack.consumeClick()) {
                    requestWithPrediction(cap, CombatState.ATTACK_LIGHT);
                }
                while (mc.options.keyUse.consumeClick()) {
                    requestWithPrediction(cap, CombatState.ATTACK_HEAVY);
                }
            }
        });
    }

    private static void handleKeyBindings(Minecraft mc) {
        while (CombatKeyBindings.COMBAT_TOGGLE.consumeClick()) {
            CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
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
                if (cap.isWeaponDrawn()) requestWithPrediction(cap, CombatState.DODGE);
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
        if (cap.isWeaponDrawn() && !forcedThirdPerson) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            forcedThirdPerson = true;
        } else if (!cap.isWeaponDrawn() && forcedThirdPerson) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            forcedThirdPerson = false;
        }
    }
}
