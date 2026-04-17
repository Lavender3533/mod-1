package org.example.mod_1.mod_1.combat.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import org.example.mod_1.mod_1.Mod_1;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;
import org.example.mod_1.mod_1.combat.capability.ICombatCapability;

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

    private static final int DODGE_COOLDOWN_MAX = 20;

    private static void render(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
            if (!cap.isWeaponDrawn() && cap.getState() == CombatState.IDLE) return;

            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();

            renderStateLine(gg, mc, cap, screenW, screenH);
            renderComboCount(gg, mc, cap, screenW, screenH);
            renderDodgeCooldown(gg, cap, screenW, screenH);
        });
    }

    private static void renderStateLine(GuiGraphics gg, Minecraft mc, org.example.mod_1.mod_1.combat.capability.ICombatCapability cap, int screenW, int screenH) {
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

        int textWidth = mc.font.width(info);
        int x = (screenW - textWidth) / 2;
        int y = screenH - 60;
        gg.drawString(mc.font, info, x, y, 0xFFFFFF00, true);
    }

    private static void renderComboCount(GuiGraphics gg, Minecraft mc, org.example.mod_1.mod_1.combat.capability.ICombatCapability cap, int screenW, int screenH) {
        int combo = cap.getComboCount();
        if (combo <= 0 || combo == 99) return;

        String comboText = combo + "x";
        // Large combo display on right side of screen, mid-height
        int x = screenW - 40;
        int y = screenH / 2;

        gg.pose().pushMatrix();
        gg.pose().translate(x, y);
        gg.pose().scale(2.0f, 2.0f);
        int color = switch (combo) {
            case 1 -> 0xFFFFFFFF;
            case 2 -> 0xFFFFCC33;
            default -> 0xFFFF3333; // combo 3+
        };
        gg.drawString(mc.font, comboText, 0, 0, color, true);
        gg.pose().popMatrix();
    }

    private static void renderDodgeCooldown(GuiGraphics gg, org.example.mod_1.mod_1.combat.capability.ICombatCapability cap, int screenW, int screenH) {
        int cd = cap.getDodgeCooldown();
        if (cd <= 0) return;

        float progress = 1.0f - ((float) cd / DODGE_COOLDOWN_MAX);
        int barWidth = 60;
        int barHeight = 4;
        int x = (screenW - barWidth) / 2;
        int y = screenH - 72;

        // Background
        gg.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xFF000000);
        gg.fill(x, y, x + barWidth, y + barHeight, 0xFF333333);
        // Fill
        int fillW = (int) (barWidth * progress);
        gg.fill(x, y, x + fillW, y + barHeight, 0xFF33CCFF);
    }
}
