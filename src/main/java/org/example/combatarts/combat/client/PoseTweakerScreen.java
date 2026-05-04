package org.example.combatarts.combat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 鼠标驱动的姿势调参界面。打开后:
 *  - 鼠标移动:差分调当前 [骨骼][轴] 的偏移(中等灵敏度 1px = 0.5°)
 *  - 滚轮:切轴 (X→Y→Z)
 *  - 鼠标中键:切骨骼(只在 EF 通道里循环)
 *  - 左/右键:不响应(避免误操作)
 *  - ESC:退出
 *
 * Screen 不暂停游戏(isPauseScreen=false),所以模型继续按 BlockPoseTweaker 的冻结目标显示,
 * 调参时偏移实时生效。光标到屏幕边缘会停 — 抬起鼠标移到中间继续即可。
 */
public class PoseTweakerScreen extends Screen {

    private double lastX = Double.NaN;

    public PoseTweakerScreen() {
        super(Component.literal("Pose Tweaker"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (Double.isNaN(lastX)) {
            lastX = x;
            return;
        }
        double dx = x - lastX;
        lastX = x;
        if (Math.abs(dx) >= 0.5) {
            BlockPoseTweaker.mouseAdjust(dx);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        BlockPoseTweaker.cycleAxisFromScroll(scrollY);
        return true;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);

        // 屏幕中央上方画 HUD
        String text = BlockPoseTweaker.getStatusText();
        int w = this.font.width(text);
        int x = this.width / 2 - w / 2;
        int y = 8;
        gui.fill(x - 4, y - 2, x + w + 4, y + this.font.lineHeight + 2, 0xCC000000);
        gui.drawString(this.font, text, x, y, 0xFFFFFF, false);

        // 第二行操作提示
        String hint = "move=adjust  wheel=axis  [/]=bone  ESC=exit";
        int hw = this.font.width(hint);
        int hx = this.width / 2 - hw / 2;
        int hy = y + this.font.lineHeight + 6;
        gui.fill(hx - 4, hy - 2, hx + hw + 4, hy + this.font.lineHeight + 2, 0x99000000);
        gui.drawString(this.font, hint, hx, hy, 0xAAAAAA, false);
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // 透明:不画背景,玩家能看到游戏世界 + 模型
    }
}
