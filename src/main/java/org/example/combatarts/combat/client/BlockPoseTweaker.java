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
            "ef_shoulder_R", "ef_arm_R", "ef_hand_R",
            "ef_shoulder_L", "ef_arm_L", "ef_hand_L",
            "spin_axis",     "spin_pivot",    "spin_freeze"
    };
    private static final String[] AXIS_NAMES = {"X", "Y", "Z"};

    private static final int SWORD_ROT_INDEX = 6;
    private static final int SWORD_POS_INDEX = 7;
    private static final int SWORD_BLADE_INDEX = 8;
    private static final int BACK_ROT_INDEX = 9;
    private static final int BACK_POS_INDEX = 10;
    private static final int HELD_ROT_INDEX = 11;
    private static final int HELD_POS_INDEX = 12;
    private static final int EF_FIRST_INDEX = 13;       // ef_shoulder_R
    private static final int EF_LAST_INDEX = 18;        // ef_hand_L
    private static final int SPIN_AXIS_INDEX = 19;      // 转刀旋转轴向量 (vx,vy,vz)
    private static final int SPIN_PIVOT_INDEX = 20;     // 转刀绕点旋转中心 (unit)
    private static final int SPIN_FREEZE_INDEX = 21;    // X = 冻结 spinT (0..1)，配合 ; → draw_weapon 静态调试

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

    /** 转刀旋转轴向量分量 (vx,vy,vz)；CombatItemInHandLayer 归一化后用作转刀轴。全 0 时退化为 X 轴。 */
    public static float getSpinAxis(int axis) {
        return DELTAS[SPIN_AXIS_INDEX][axis];
    }

    /** 转刀绕点旋转的中心偏移 (unit)；相对于 Tool_R 锚点。pivot=0 时绕剑模型原点(剑柄末端)转。 */
    public static float getSpinPivot(int axis) {
        return DELTAS[SPIN_PIVOT_INDEX][axis];
    }

    /** 调试冻结模式下使用的 spinT (0..1)。配合 ; → draw_weapon 把转刀停在固定角度静态调参。 */
    public static float getSpinFreeze() {
        return DELTAS[SPIN_FREEZE_INDEX][0];
    }

    /** SkinnedMeshLayer 调用:6 个 EF 手臂关节的旋转偏移(度)。jointName 形如 "Shoulder_R"/"Arm_L"/"Hand_R" 等。 */
    public static float getEfDelta(String jointName, int axis) {
        int idx = switch (jointName) {
            case "Shoulder_R" -> EF_FIRST_INDEX;       // 13
            case "Arm_R"      -> EF_FIRST_INDEX + 1;   // 14
            case "Hand_R"     -> EF_FIRST_INDEX + 2;   // 15
            case "Shoulder_L" -> EF_FIRST_INDEX + 3;   // 16
            case "Arm_L"      -> EF_FIRST_INDEX + 4;   // 17
            case "Hand_L"     -> EF_FIRST_INDEX + 5;   // 18
            default -> -1;
        };
        return idx < 0 ? 0f : DELTAS[idx][axis];
    }

    /** EF 手臂关节名数组(供 SkinnedMeshLayer 遍历应用)。 */
    public static final String[] EF_BLOCK_JOINTS = {
            "Shoulder_R", "Arm_R", "Hand_R",
            "Shoulder_L", "Arm_L", "Hand_L"
    };

    // ==== 调试冻结目标 ====
    // OFF=正常游戏(tweaker EF 通道仅在 BLOCK 状态生效),其余=冻结到对应 anim 的 frame 0,
    // EF tweaker 偏移叠加上去。按 ; 切换。这样调任何状态的姿势都不会污染正常游戏。
    private static final String[] DEBUG_TARGETS = {
            null,                    // 0: OFF
            "hold_longsword",        // 1: 持刀站立
            "sword_heavy_charge",    // 2: 重击蓄力
            "draw_weapon",           // 3: 拔刀(frame 0)
            "sheath_weapon",         // 4: 收刀(frame 0)
            "block",                 // 5: 格挡
            "inspect",               // 6: 检视
    };
    private static final String[] DEBUG_TARGET_NAMES = {
            "OFF (正常游戏)",
            "hold_longsword (持刀站立)",
            "sword_heavy_charge (重击蓄力)",
            "draw_weapon (拔刀)",
            "sheath_weapon (收刀)",
            "block (格挡)",
            "inspect (检视)",
    };
    private static int currentDebugTarget = 0;

    /** 切换到下一个调试目标(off→hold→charge→draw→sheath→block→off)。 */
    public static void cycleDebugTarget() {
        currentDebugTarget = (currentDebugTarget + 1) % DEBUG_TARGETS.length;
        chat(ChatFormatting.YELLOW + "[Tweak] 调试目标: "
                + ChatFormatting.AQUA + DEBUG_TARGET_NAMES[currentDebugTarget]);
    }

    /** SkinnedMeshLayer 调用:返回当前冻结目标的 anim 名。null = 不冻结(正常游戏)。 */
    public static String getDebugTargetAnim() {
        return DEBUG_TARGETS[currentDebugTarget];
    }

    public static void cycleBone() {
        currentBone = (currentBone + 1) % BONE_NAMES.length;
        chatStatus();
    }

    /** 鼠标中键调用:只在 EF 通道 (13..18) 内循环切骨骼,跳过旧通道。 */
    public static void cycleEfBoneOnly() {
        if (currentBone < EF_FIRST_INDEX || currentBone > EF_LAST_INDEX) {
            currentBone = EF_FIRST_INDEX;
        } else {
            currentBone = currentBone == EF_LAST_INDEX ? EF_FIRST_INDEX : currentBone + 1;
        }
        chatStatus();
    }

    public static void cycleAxis() {
        currentAxis = (currentAxis + 1) % AXIS_NAMES.length;
        chatStatus();
    }

    /** 鼠标滚轮调用:正向滚动切下一个轴,反向切上一个轴。 */
    public static void cycleAxisFromScroll(double scrollDelta) {
        if (scrollDelta > 0) {
            currentAxis = (currentAxis + 1) % AXIS_NAMES.length;
        } else if (scrollDelta < 0) {
            currentAxis = (currentAxis + AXIS_NAMES.length - 1) % AXIS_NAMES.length;
        }
        chatStatus();
    }

    /** 鼠标拖动调用:dxPixels 像素 → 累加到当前 [bone][axis],中等灵敏度 1px = 0.5°。 */
    public static void mouseAdjust(double dxPixels) {
        DELTAS[currentBone][currentAxis] += (float)(dxPixels * 0.5);
    }

    /** PoseTweakerScreen 调用:返回 HUD 显示用的状态文字(无颜色码)。 */
    public static String getStatusText() {
        String target = DEBUG_TARGETS[currentDebugTarget] == null
                ? "OFF" : DEBUG_TARGET_NAMES[currentDebugTarget];
        return String.format("[%s]  %s.%s = %s%s",
                target,
                BONE_NAMES[currentBone],
                AXIS_NAMES[currentAxis],
                fmt(DELTAS[currentBone][currentAxis], currentBone),
                unitFor(currentBone));
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
        if (boneIdx == SPIN_PIVOT_INDEX) return POS_STEP_UNIT;          // 0.05 unit
        if (boneIdx == SPIN_AXIS_INDEX) return 0.1f;                    // 0.1 (向量分量)
        if (boneIdx == SPIN_FREEZE_INDEX) return 0.05f;                 // 0.05 (spinT 0..1)
        if (boneIdx >= EF_FIRST_INDEX && boneIdx <= EF_LAST_INDEX) return 10.0f;
        return ROT_STEP_DEG;
    }

    private static String unitFor(int boneIdx) {
        if (boneIdx == SWORD_POS_INDEX || boneIdx == BACK_POS_INDEX || boneIdx == HELD_POS_INDEX) return " (unit)";
        if (boneIdx == SPIN_PIVOT_INDEX) return " (unit)";
        if (boneIdx == SPIN_AXIS_INDEX) return " (vec)";
        if (boneIdx == SPIN_FREEZE_INDEX) return " (spinT)";
        return "°";
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
        if (boneIdx == SWORD_POS_INDEX || boneIdx == BACK_POS_INDEX || boneIdx == HELD_POS_INDEX) return "0.05";
        if (boneIdx == SPIN_PIVOT_INDEX) return "0.05";
        if (boneIdx == SPIN_AXIS_INDEX) return "0.1";
        if (boneIdx == SPIN_FREEZE_INDEX) return "0.05";
        return "5";
    }

    private static String fmt(float v, int boneIdx) {
        if (boneIdx == SWORD_POS_INDEX || boneIdx == BACK_POS_INDEX || boneIdx == HELD_POS_INDEX) return String.format("%.2f", v);
        if (boneIdx == SPIN_PIVOT_INDEX || boneIdx == SPIN_FREEZE_INDEX) return String.format("%.2f", v);
        if (boneIdx == SPIN_AXIS_INDEX) return String.format("%.1f", v);
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
