package org.example.mod_1.mod_1.combat.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import org.example.mod_1.mod_1.Mod_1;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;

public class CombatHudOverlay {

    private static final Identifier LAYER_ID =
            Identifier.fromNamespaceAndPath(Mod_1.MODID, "combat_hud");

    public static void register(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
                ForgeLayeredDraw.PRE_SLEEP_STACK,
                LAYER_ID,
                CombatHudOverlay::render
        );
    }

    private static void render(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
            if (!cap.isWeaponDrawn() && cap.getState() == CombatState.IDLE) return;

            CombatState state = cap.getState();
            String stateText = switch (state) {
                case IDLE -> "IDLE";
                case DRAW_WEAPON -> "DRAW";
                case SHEATH_WEAPON -> "SHEATH";
                case ATTACK_LIGHT -> "ATTACK L" + cap.getComboCount();
                case ATTACK_HEAVY -> "ATTACK H";
                case DODGE -> "DODGE";
                case BLOCK -> "BLOCK";
                case PARRY -> "PARRY!";
                case INSPECT -> "INSPECT";
                default -> state.name();
            };

            String info = "[" + cap.getWeaponType().name() + "] " + stateText;
            if (cap.isWeaponDrawn()) info = "* " + info;

            int width = mc.getWindow().getGuiScaledWidth();
            int textWidth = mc.font.width(info);
            int x = (width - textWidth) / 2;
            int y = mc.getWindow().getGuiScaledHeight() - 60;

            gg.drawString(mc.font, info, x, y, 0xFFFFFF00, true);
        });
    }
}
