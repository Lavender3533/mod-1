package org.example.mod_1.mod_1.combat.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import org.example.mod_1.mod_1.Config;
import org.example.mod_1.mod_1.Mod_1;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.WeaponType;
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

    // === Combo pop animation state (client-only, single player view) ===
    private static int lastComboShown = 0;
    private static long lastComboChangeMs = 0L;
    private static final long COMBO_POP_MS = 200L;

    private static void render(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        CombatCapabilityEvents.getCombat(mc.player).ifPresent(cap -> {
            if (!cap.isWeaponDrawn() && cap.getState() == CombatState.IDLE) return;

            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();

            renderStateLine(gg, mc, cap, screenW, screenH);
            renderComboCount(gg, mc, cap, screenW, screenH);
            renderDodgeCooldown(gg, mc, cap, screenW, screenH);
            renderHeavyChargeBar(gg, mc, cap, screenW, screenH);
        });
    }

    // === 状态条: [武器] 状态 — 半透明黑底 + 状态色边框, 屏幕底部居中 ===
    private static void renderStateLine(GuiGraphics gg, Minecraft mc, ICombatCapability cap, int screenW, int screenH) {
        Component weaponLabel = weaponName(cap.getWeaponType());
        Component stateLabel = stateName(cap);
        Component combined = Component.empty()
                .append(Component.literal("[").append(weaponLabel).append("] ").withStyle(s -> s.withColor(0xC0C0C0)))
                .append(stateLabel.copy().withStyle(s -> s.withColor(0xFFFFFF)));

        int textW = mc.font.width(combined);
        int padX = 6;
        int padY = 3;
        int boxW = textW + padX * 2;
        int boxH = mc.font.lineHeight + padY * 2;
        int x = (screenW - boxW) / 2;
        int y = screenH - 64;

        int borderColor = stateAccentColor(cap);
        drawPill(gg, x, y, boxW, boxH, 0xC0000000, borderColor);

        // 拔刀指示: 在 box 左侧留个小色块 (像电源灯)
        if (cap.isWeaponDrawn()) {
            int dotSize = 3;
            int dotX = x - dotSize - 3;
            int dotY = y + (boxH - dotSize) / 2;
            gg.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, 0xFFFFAA00);
        }

        gg.drawString(mc.font, combined, x + padX, y + padY, 0xFFFFFFFF, true);
    }

    // === Combo 数字: 大号显示, 增量时弹出动画, 3 段附加 FINISHER! 标签 ===
    private static void renderComboCount(GuiGraphics gg, Minecraft mc, ICombatCapability cap, int screenW, int screenH) {
        int combo = cap.getComboCount();
        if (combo <= 0) return;

        // 触发 pop 动画: 仅在 combo 数变化时记录时间
        if (combo != lastComboShown) {
            lastComboShown = combo;
            lastComboChangeMs = System.currentTimeMillis();
        }

        // 冲刺攻击单独显示为 SPRINT 标签, 不画数字
        if (combo == 99) {
            Component sprint = Component.translatable("hud.mod_1.combo.sprint");
            int textW = mc.font.width(sprint);
            int x = screenW - textW - 16;
            int y = screenH / 2;
            drawShadowedText(gg, mc, sprint, x, y, 0xFFFF7700);
            return;
        }

        String comboText = combo + "x";
        int textW = mc.font.width(comboText);
        int anchorX = screenW - 32;
        int anchorY = screenH / 2;

        // pop 动画: 200ms 内 scale 从 2.5 平滑插值到 2.0
        float t = Math.min(1.0f, (System.currentTimeMillis() - lastComboChangeMs) / (float) COMBO_POP_MS);
        float scale = 2.5f - 0.5f * easeOutCubic(t);

        int color = switch (combo) {
            case 1 -> 0xFFFFFFFF;
            case 2 -> 0xFFFFCC33;
            default -> 0xFFFF3333;
        };

        gg.pose().pushMatrix();
        gg.pose().translate(anchorX, anchorY);
        gg.pose().scale(scale, scale);
        // 三层阴影 (left/right/down) 模拟简易光晕
        gg.drawString(mc.font, comboText, -1, 0, 0x80000000, false);
        gg.drawString(mc.font, comboText, 1, 0, 0x80000000, false);
        gg.drawString(mc.font, comboText, 0, 1, 0x80000000, false);
        gg.drawString(mc.font, comboText, 0, 0, color, true);
        gg.pose().popMatrix();

        // 第 3 段: FINISHER! 标签, 比 combo 数字小一号, 略偏下
        if (combo >= 3) {
            Component finisher = Component.translatable("hud.mod_1.combo.finisher");
            int finW = mc.font.width(finisher);
            int finX = anchorX + (textW - finW) / 2;
            int finY = anchorY + (int) (mc.font.lineHeight * scale) + 2;
            // 闪烁: 200ms 周期
            int phase = (int) (System.currentTimeMillis() / 200) % 2;
            int finColor = phase == 0 ? 0xFFFFD700 : 0xFFFFFFFF;
            drawShadowedText(gg, mc, finisher, finX, finY, finColor);
        }
    }

    // === 闪避 CD 条: 进度条 + 标签 ===
    private static void renderDodgeCooldown(GuiGraphics gg, Minecraft mc, ICombatCapability cap, int screenW, int screenH) {
        int cd = cap.getDodgeCooldown();
        if (cd <= 0) return;

        int max = Math.max(1, Config.dodgeCooldownTicks);
        float progress = 1.0f - ((float) cd / max);
        int barW = 60;
        int barH = 5;
        int x = (screenW - barW) / 2;
        int y = screenH - 78;

        Component label = Component.translatable("hud.mod_1.label.dodge");
        int labelW = mc.font.width(label);
        gg.drawString(mc.font, label, x - labelW - 4, y - 1, 0xFFAACCFF, true);

        drawProgressBar(gg, x, y, barW, barH, progress, 0xFF33CCFF, 0xFF1A4D66);
    }

    // === 蓄力条: 进度条 + 标签, 满蓄时显示 FULL! 闪烁 ===
    private static void renderHeavyChargeBar(GuiGraphics gg, Minecraft mc, ICombatCapability cap, int screenW, int screenH) {
        if (cap.getState() != CombatState.ATTACK_HEAVY_CHARGING) return;

        int held = cap.getChargeTicks();
        int max = Math.max(1, Config.heavyChargeMaxTicks);
        float progress = Math.min(1.0f, (float) held / max);
        boolean atFull = held >= max;

        int barW = 80;
        int barH = 6;
        int x = (screenW - barW) / 2;
        int y = screenH / 2 + 14;

        int fillColor;
        if (atFull) {
            int phase = (int) (System.currentTimeMillis() / 100) % 2;
            fillColor = phase == 0 ? 0xFF66FFFF : 0xFFAAFFFF;
        } else {
            int r = 0xFF;
            int g = (int) (0xCC * (1 - progress) + 0x33 * progress);
            int b = (int) (0x33 * (1 - progress));
            fillColor = 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        Component label = atFull
                ? Component.translatable("hud.mod_1.label.charge_full")
                : Component.translatable("hud.mod_1.label.charge");
        int labelW = mc.font.width(label);
        int labelColor = atFull ? 0xFF66FFFF : 0xFFFFCC66;
        gg.drawString(mc.font, label, x - labelW - 4, y - 1, labelColor, true);

        drawProgressBar(gg, x, y, barW, barH, progress, fillColor, 0xFF202020);
    }

    // === 通用 helper ===

    // "Pill": 矩形 bg + 1px 边框, 四角抠掉 1px 模拟圆角.
    private static void drawPill(GuiGraphics gg, int x, int y, int w, int h, int bgColor, int borderColor) {
        gg.fill(x, y, x + w, y + h, bgColor);
        // top + bottom border
        gg.fill(x + 1, y, x + w - 1, y + 1, borderColor);
        gg.fill(x + 1, y + h - 1, x + w - 1, y + h, borderColor);
        // left + right border
        gg.fill(x, y + 1, x + 1, y + h - 1, borderColor);
        gg.fill(x + w - 1, y + 1, x + w, y + h - 1, borderColor);
    }

    private static void drawProgressBar(GuiGraphics gg, int x, int y, int w, int h, float progress, int fillColor, int bgColor) {
        // 1px 黑色外框
        gg.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        // 背景
        gg.fill(x, y, x + w, y + h, bgColor);
        // 填充
        int fillW = (int) (w * progress);
        gg.fill(x, y, x + fillW, y + h, fillColor);
        // 顶部 1px 高光行 (画在已填的部分上, 显得有立体感)
        if (fillW > 0) {
            int hl = (fillColor & 0xFF000000) | ((fillColor & 0x00FCFCFC) >> 1) + 0x404040;
            gg.fill(x, y, x + fillW, y + 1, hl);
        }
    }

    private static void drawShadowedText(GuiGraphics gg, Minecraft mc, Component text, int x, int y, int color) {
        gg.drawString(mc.font, text, x + 1, y, 0x80000000, false);
        gg.drawString(mc.font, text, x, y + 1, 0x80000000, false);
        gg.drawString(mc.font, text, x, y, color, true);
    }

    private static Component weaponName(WeaponType type) {
        return switch (type) {
            case SWORD -> Component.translatable("hud.mod_1.weapon.sword");
            case SPEAR -> Component.translatable("hud.mod_1.weapon.spear");
            default -> Component.translatable("hud.mod_1.weapon.unarmed");
        };
    }

    private static Component stateName(ICombatCapability cap) {
        CombatState s = cap.getState();
        return switch (s) {
            case IDLE -> Component.translatable("hud.mod_1.state.idle");
            case DRAW_WEAPON -> Component.translatable("hud.mod_1.state.draw");
            case SHEATH_WEAPON -> Component.translatable("hud.mod_1.state.sheath");
            case ATTACK_LIGHT -> {
                int combo = cap.getComboCount();
                if (combo > 0 && combo != 99) {
                    yield Component.translatable("hud.mod_1.state.attack_light")
                            .append(Component.literal(" " + combo));
                } else {
                    yield Component.translatable("hud.mod_1.state.attack_light");
                }
            }
            case ATTACK_HEAVY -> Component.translatable("hud.mod_1.state.attack_heavy");
            case ATTACK_HEAVY_CHARGING -> Component.translatable("hud.mod_1.state.heavy_charging");
            case DODGE -> Component.translatable("hud.mod_1.state.dodge");
            case BLOCK -> Component.translatable("hud.mod_1.state.block");
            case PARRY -> Component.translatable("hud.mod_1.state.parry");
            case INSPECT -> Component.translatable("hud.mod_1.state.inspect");
            default -> Component.literal(s.name());
        };
    }

    // 状态色: 用于 pill 边框色, 给玩家"颜色 = 状态"的快速反馈.
    private static int stateAccentColor(ICombatCapability cap) {
        return switch (cap.getState()) {
            case PARRY -> 0xFF66FFFF;          // 青
            case BLOCK -> 0xFF3399FF;          // 蓝
            case ATTACK_LIGHT, ATTACK_HEAVY -> 0xFFFF5544;  // 红
            case ATTACK_HEAVY_CHARGING -> 0xFFFFAA00;       // 橙
            case DODGE -> 0xFFAACCFF;          // 浅蓝
            case INSPECT -> 0xFFCC99FF;        // 紫
            case DRAW_WEAPON, SHEATH_WEAPON -> 0xFFFFCC33;  // 黄
            default -> 0xFF666666;             // 灰
        };
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }
}
