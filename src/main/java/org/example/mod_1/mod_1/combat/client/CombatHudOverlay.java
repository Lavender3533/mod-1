package org.example.mod_1.mod_1.combat.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import org.example.mod_1.mod_1.Config;
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
            renderHeavyChargeBar(gg, cap, screenW, screenH);
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

    /**
     * 蓄力进度条 — 仅在 ATTACK_HEAVY_CHARGING 状态显示。
     * 进度从 0% 涨到 100%（满蓄）。颜色随进度过渡:橙黄 → 红;满蓄后整条闪烁青色提示。
     * 位置:屏幕中央偏下,准星正下方,比闪避 CD 条略低一点避免重叠。
     */
    private static void renderHeavyChargeBar(GuiGraphics gg, ICombatCapability cap, int screenW, int screenH) {
        if (cap.getState() != CombatState.ATTACK_HEAVY_CHARGING) return;

        int held = cap.getChargeTicks();
        int max = Math.max(1, Config.heavyChargeMaxTicks);
        float progress = Math.min(1.0f, (float) held / max);
        boolean atFull = held >= max;

        int barWidth = 80;
        int barHeight = 5;
        int x = (screenW - barWidth) / 2;
        int y = screenH / 2 + 14;  // 准星下方一点

        int bgColor = 0xFF202020;
        int frameColor = 0xFF000000;

        int fillColor;
        if (atFull) {
            // 满蓄:在两种青色之间快速闪烁
            int phase = (int) (System.currentTimeMillis() / 100) % 2;
            fillColor = phase == 0 ? 0xFF66FFFF : 0xFFAAFFFF;
        } else {
            // 渐变:橙黄(0%) → 红(100%)
            int r = 0xFF;
            int g = (int) (0xCC * (1 - progress) + 0x33 * progress);
            int b = (int) (0x33 * (1 - progress));
            fillColor = 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        gg.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, frameColor);
        gg.fill(x, y, x + barWidth, y + barHeight, bgColor);
        int fillW = (int) (barWidth * progress);
        gg.fill(x, y, x + fillW, y + barHeight, fillColor);
    }
}
