package org.example.combatarts.combat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.network.chat.Component;

/**
 * 实时调试 BLOCK 动画姿势的工具。
 *
 * 可调对象:
 *   - 6 个骨骼旋转: rightUpperArm/rightLowerArm/rightHand + 左侧三个 (单位: 度，步进 5°)
 *   - sword_rot: GuardWeaponLayer 上叠加的剑旋转 (单位: 度，步进 5°)
 *   - sword_pos: GuardWeaponLayer 上叠加的剑平移 (单位: 模型单位，步进 0.05)
 *
 * 偏移叠加在 anim_block.json / GuardWeaponLayer 的现有值之上，按 / 打印当前值。
 */
public final class BlockPoseTweaker {

    private static final String[] BONE_NAMES = {
            "rightUpperArm", "rightLowerArm", "rightHand",
            "leftUpperArm",  "leftLowerArm",  "leftHand",
            "sword_rot",     "sword_pos",     "sword_blade_roll",
            "back_rot",      "back_pos",
            "held_rot",      "held_pos",
            "ef_shoulder",   "ef_arm"
    };
    private static final String[] AXIS_NAMES = {"X", "Y", "Z"};

    private static final int SWORD_ROT_INDEX = 6;
    private static final int SWORD_POS_INDEX = 7;
    private static final int SWORD_BLADE_INDEX = 8;
    private static final int BACK_ROT_INDEX = 9;
    private static final int BACK_POS_INDEX = 10;
    private static final int HELD_ROT_INDEX = 11;
    private static final int HELD_POS_INDEX = 12;
    private static final int EF_SHOULDER_INDEX = 13;
    private static final int EF_ARM_INDEX = 14;

    private static final float ROT_STEP_DEG = 5.0f;
    private static final float POS_STEP_UNIT = 0.05f;

    private static int currentBone = 0;
    private static int currentAxis = 0;

    // [bone][axis] delta. 旋转单位 = 度，平移单位 = 模型 unit
    private static final float[][] DELTAS = new float[BONE_NAMES.length][3];

    private BlockPoseTweaker() {}

    /**
     * 在 CombatAnimationController.applyTo17Bones 中，BLOCK 动画激活时对每个 bone 调用一次。
     * 把当前偏移叠加到 part 的旋转上（仅对真实骨骼生效）。
     */
    public static void applyDelta(ModelPart part, String boneName) {
        for (int i = 0; i < SWORD_ROT_INDEX; i++) {  // 仅前 6 个是真骨骼
            if (BONE_NAMES[i].equals(boneName)) {
                part.xRot += (float) Math.toRadians(DELTAS[i][0]);
                part.yRot += (float) Math.toRadians(DELTAS[i][1]);
                part.zRot += (float) Math.toRadians(DELTAS[i][2]);
                return;
            }
        }
    }

    /** GuardWeaponLayer 调用：剑旋转偏移（度）。 */
    public static float getSwordRot(int axis) {
        return DELTAS[SWORD_ROT_INDEX][axis];
    }

    /** GuardWeaponLayer 调用：剑平移偏移（模型 unit）。 */
    public static float getSwordPos(int axis) {
        return DELTAS[SWORD_POS_INDEX][axis];
    }

    /** GuardWeaponLayer 调用：沿剑身长轴的旋转（度）。X 轴值 = 旋转角度。 */
    public static float getSwordBladeRoll() {
        return DELTAS[SWORD_BLADE_INDEX][0];
    }

    /** BackWeaponLayer 调用：背挂武器的额外旋转（度）。 */
    public static float getBackRot(int axis) {
        return DELTAS[BACK_ROT_INDEX][axis];
    }

    /** BackWeaponLayer 调用：背挂武器的额外平移（unit）。 */
    public static float getBackPos(int axis) {
        return DELTAS[BACK_POS_INDEX][axis];
    }

    /** CombatItemInHandLayer 调用：手持武器的额外旋转（度）。 */
    public static float getHeldRot(int axis) {
        return DELTAS[HELD_ROT_INDEX][axis];
    }

    /** CombatItemInHandLayer 调用：手持武器的额外平移（unit）。 */
    public static float getHeldPos(int axis) {
        return DELTAS[HELD_POS_INDEX][axis];
    }

    /** SkinnedMeshLayer 调用：EF Shoulder_R 旋转（度）。 */
    public static float getEfShoulder(int axis) {
        return DELTAS[EF_SHOULDER_INDEX][axis];
    }

    /** SkinnedMeshLayer 调用：EF Arm_R 旋转（度）。 */
    public static float getEfArm(int axis) {
        return DELTAS[EF_ARM_INDEX][axis];
    }

    /** 当前是否在调 EF 关节通道。 */
    public static boolean isEfTweakActive() {
        return currentBone == EF_SHOULDER_INDEX || currentBone == EF_ARM_INDEX;
    }

    public static void cycleBone() {
        currentBone = (currentBone + 1) % BONE_NAMES.length;
        chatStatus();
    }

    public static void cycleAxis() {
        currentAxis = (currentAxis + 1) % AXIS_NAMES.length;
        chatStatus();
    }

    public static void increase() {
        DELTAS[currentBone][currentAxis] += stepFor(currentBone);
        chatStatus();
    }

    public static void decrease() {
        DELTAS[currentBone][currentAxis] -= stepFor(currentBone);
        chatStatus();
    }

    public static void resetCurrentAxis() {
        DELTAS[currentBone][currentAxis] = 0.0f;
        chatStatus();
    }

    public static void resetAll() {
        for (int i = 0; i < BONE_NAMES.length; i++) {
            for (int j = 0; j < 3; j++) DELTAS[i][j] = 0.0f;
        }
        chat(ChatFormatting.YELLOW + "[Tweak] 所有偏移已重置");
    }

    public static void printAll() {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatFormatting.GREEN).append("[Tweak] 当前偏移:\n");
        boolean any = false;
        for (int i = 0; i < BONE_NAMES.length; i++) {
            float dx = DELTAS[i][0], dy = DELTAS[i][1], dz = DELTAS[i][2];
            if (dx == 0 && dy == 0 && dz == 0) continue;
            any = true;
            sb.append(ChatFormatting.AQUA).append("  ").append(BONE_NAMES[i])
              .append(ChatFormatting.WHITE).append(": [")
              .append(fmt(dx, i)).append(", ")
              .append(fmt(dy, i)).append(", ")
              .append(fmt(dz, i)).append("]");
            sb.append(ChatFormatting.GRAY).append(unitFor(i)).append("\n");
        }
        if (!any) sb.append(ChatFormatting.GRAY).append("  （没有非零偏移）");
        chat(sb.toString());
    }

    private static float stepFor(int boneIdx) {
        if (boneIdx == SWORD_POS_INDEX || boneIdx == BACK_POS_INDEX || boneIdx == HELD_POS_INDEX) return POS_STEP_UNIT;
        if (boneIdx == EF_SHOULDER_INDEX || boneIdx == EF_ARM_INDEX) return 10.0f;
        return ROT_STEP_DEG;
    }

    private static String unitFor(int boneIdx) {
        return (boneIdx == SWORD_POS_INDEX || boneIdx == BACK_POS_INDEX || boneIdx == HELD_POS_INDEX) ? " (unit)" : "°";
    }

    private static void chatStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatFormatting.YELLOW).append("[Tweak] ");
        sb.append(ChatFormatting.AQUA).append(BONE_NAMES[currentBone]);
        sb.append(ChatFormatting.WHITE).append(".");
        sb.append(ChatFormatting.GOLD).append(AXIS_NAMES[currentAxis]);
        sb.append(ChatFormatting.WHITE).append(" = ");
        sb.append(ChatFormatting.GREEN).append(fmt(DELTAS[currentBone][currentAxis], currentBone));
        sb.append(ChatFormatting.GRAY).append(unitFor(currentBone));
        sb.append("  (步进 ").append(stepStr(currentBone)).append(unitFor(currentBone)).append(")");
        chat(sb.toString());
    }

    private static String stepStr(int boneIdx) {
        return (boneIdx == SWORD_POS_INDEX || boneIdx == BACK_POS_INDEX || boneIdx == HELD_POS_INDEX) ? "0.05" : "5";
    }

    private static String fmt(float v, int boneIdx) {
        if (boneIdx == SWORD_POS_INDEX || boneIdx == BACK_POS_INDEX || boneIdx == HELD_POS_INDEX) return String.format("%.2f", v);
        if (v == (int) v) return Integer.toString((int) v);
        return String.format("%.1f", v);
    }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(Component.literal(msg));
        }
    }
}
