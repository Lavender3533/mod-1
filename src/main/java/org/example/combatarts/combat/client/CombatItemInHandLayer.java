package org.example.combatarts.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.example.combatarts.combat.CombatState;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;
import org.example.combatarts.combat.capability.ICombatCapability;
import org.example.combatarts.combat.client.render.mesh.*;

public class CombatItemInHandLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {

    private final ItemModelResolver itemModelResolver;
    private static final float SPEAR_ITEM_RECOVERY_SECONDS = 0.18f;
    private static final java.util.Map<Integer, ItemAnimState> itemAnimStates = new java.util.HashMap<>();
    private static final float[][] SPEAR_ATTACK_ROT_KEYS = {
            {0.00f, -40,   0,   5},
            {0.14f, -34,  -5,   0},
            {0.32f, -52,   1,   8},
            {0.48f, -65,   5,  15},
            {0.66f, -63,   4,  12},
            {0.86f, -48,   0,   7},
            {1.00f, -40,   0,   5},
    };
    private static final float[][] SPEAR_ATTACK_POS_KEYS = {
            {0.00f, 0.00f, -0.15f, -0.15f},
            {0.14f, 0.00f, -0.18f, -0.10f},
            {0.32f, 0.00f, -0.13f, -0.17f},
            {0.48f, 0.00f, -0.10f, -0.20f},
            {0.66f, 0.00f, -0.11f, -0.18f},
            {0.86f, 0.00f, -0.14f, -0.14f},
            {1.00f, 0.00f, -0.15f, -0.15f},
    };

    private static final class ItemAnimState {
        CombatState state = CombatState.IDLE;
        int combo = 0;
        int lastTimer = -1;
        long startNano = 0L;
        boolean wasSpearAttack = false;
        long recoveryStartNano = 0L;
    }

    public CombatItemInHandLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent,
                                 ItemModelResolver itemModelResolver) {
        super(parent);
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        Player player = resolvePlayer(state);
        if (player == null) return;

        var combatOpt = CombatCapabilityEvents.getCombat(player);
        if (!combatOpt.isPresent()) return;

        combatOpt.ifPresent(cap -> {
            if (!CombatCapabilityEvents.shouldRenderWeaponInHand(cap)) return;

            CombatState combatState = cap.getState();
            if (combatState == CombatState.BLOCK || combatState == CombatState.PARRY) return;

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) return;

            ItemStackRenderState scratchState = new ItemStackRenderState();
            itemModelResolver.updateForLiving(scratchState, stack,
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player);
            if (scratchState.isEmpty()) return;

            poseStack.pushPose();

            Armature armature = MeshManager.getArmature();
            if (armature != null && MeshManager.getMesh() != null && armature.hasJoint("Tool_R")) {
                renderViaArmature(poseStack, armature);
            } else {
                renderViaModelParts(poseStack);
            }

            // 拔刀转刀: 关键帧驱动的 Euler XYZ 旋转。X 是主旋转轴 (0 → -325°)，Y/Z 在
            // 特定 X 角度处插值，让旋转过程中剑动态偏向身体外侧避开砍身体。终点 Y/Z 回归 0
            // 防止转刀结束后剑停在歪状态。spin_freeze 用于按 ; → draw_weapon 后静态停帧调试。
            String debugAnim = BlockPoseTweaker.getDebugTargetAnim();
            boolean inDraw = combatState == CombatState.DRAW_WEAPON;
            boolean drawDebug = "draw_weapon".equals(debugAnim);
            if (inDraw || drawDebug) {
                float spinT = -1f;
                if (drawDebug) {
                    spinT = Math.max(0f, Math.min(1f, BlockPoseTweaker.getSpinFreeze()));
                } else {
                    int dur = CombatState.DRAW_WEAPON.getDurationTicks();
                    int remaining = cap.getStateTimer();
                    float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
                    float progress = (dur - remaining + partial) / (float) dur;
                    float spinStart = 0.30f, spinEnd = 0.95f;
                    if (progress >= spinStart && progress <= spinEnd) {
                        spinT = (progress - spinStart) / (spinEnd - spinStart);
                    }
                }
                if (spinT >= 0f) {
                    float xDeg = -325f * spinT;
                    float yDeg = spinY(xDeg);
                    float zDeg = spinZ(xDeg);
                    org.joml.Quaternionf q = new org.joml.Quaternionf().rotationXYZ(
                            (float) Math.toRadians(xDeg),
                            (float) Math.toRadians(yDeg),
                            (float) Math.toRadians(zDeg)
                    );
                    poseStack.mulPose(q);
                }
            }

            // 检视转刀: 旋转由手臂关节驱动，CombatItemInHandLayer 不加额外旋转
            if (combatState == CombatState.INSPECT) {
            }

            // 矛攻击: EF 动画已通过 Tool_R 控制矛的位置，不加额外变换

            // Live tweaker overlay
            poseStack.mulPose(Axis.XP.rotationDegrees(BlockPoseTweaker.getHeldRot(0)));
            poseStack.mulPose(Axis.YP.rotationDegrees(BlockPoseTweaker.getHeldRot(1)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(BlockPoseTweaker.getHeldRot(2)));
            poseStack.translate(
                    BlockPoseTweaker.getHeldPos(0),
                    BlockPoseTweaker.getHeldPos(1),
                    BlockPoseTweaker.getHeldPos(2)
            );

            scratchState.submit(poseStack, collector, packedLight, 0, 0);

            poseStack.popPose();
        });
    }

    private void renderViaArmature(PoseStack poseStack, Armature armature) {
        Joint toolR = armature.searchJointByName("Tool_R");
        OpenMatrix4f jointMatrix = armature.getPoseMatrices()[toolR.getId()];

        // Same base transform as SkinnedMeshLayer
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0, -1.501, 0.0);

        // Apply Tool_R world-space pose
        MathUtils.mulStack(poseStack, jointMatrix);

        // EF-style correction for Tool_R: translate(0, 0, -0.13) + rotate(-90, X)
        OpenMatrix4f correction = new OpenMatrix4f()
                .translate(0F, 0F, -0.13F)
                .rotateDeg(-90.0F, Vec3f.X_AXIS);
        MathUtils.mulStack(poseStack, correction);
    }

    private void renderViaModelParts(PoseStack poseStack) {
        CombatPlayerModel model = this.getParentModel();
        model.root.translateAndRotate(poseStack);
        model.hip.translateAndRotate(poseStack);
        model.waist.translateAndRotate(poseStack);
        model.chest.translateAndRotate(poseStack);
        model.rightUpperArm.translateAndRotate(poseStack);
        model.rightLowerArm.translateAndRotate(poseStack);
        model.rightHand.translateAndRotate(poseStack);
        model.weaponMount.translateAndRotate(poseStack);

        // Baseline correction for box model mode
        poseStack.mulPose(Axis.YP.rotationDegrees(-15.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-13.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-30.0F));
        poseStack.translate(0.04F, 0.11F, 0.01F);
        poseStack.mulPose(new org.joml.Quaternionf().rotateAxis(
                (float) Math.toRadians(-310.0F), 0.0F, 0.574F, -0.819F));
    }

    private static Player resolvePlayer(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(state.id);
        return entity instanceof Player p ? p : null;
    }

    private static float computeSpearItemProgress(Player player, ICombatCapability cap) {
        long now = System.nanoTime();
        ItemAnimState st = itemAnimStates.computeIfAbsent(player.getId(), id -> new ItemAnimState());
        int timer = cap.getStateTimer();
        int combo = cap.getComboCount();
        boolean restart = st.state != cap.getState()
                || st.combo != combo
                || timer > st.lastTimer
                || st.startNano == 0L;

        int durationTicks = CombatState.ATTACK_LIGHT.getDurationTicks();
        if (restart) {
            int remaining = Math.max(0, Math.min(durationTicks, timer));
            double elapsedSeconds = (durationTicks - remaining) / 20.0;
            st.startNano = now - (long) (elapsedSeconds * 1_000_000_000L);
            st.state = cap.getState();
            st.combo = combo;
        }
        st.lastTimer = timer;

        float elapsed = (now - st.startNano) / 1_000_000_000f;
        float length = durationTicks / 20.0f;
        return Math.max(0f, Math.min(1f, elapsed / length));
    }

    private static void updateSpearItemExitState(ItemAnimState itemState, boolean spearAttack) {
        long now = System.nanoTime();
        if (spearAttack) {
            itemState.wasSpearAttack = true;
            itemState.recoveryStartNano = 0L;
        } else if (itemState.wasSpearAttack) {
            itemState.wasSpearAttack = false;
            itemState.recoveryStartNano = now;
            itemState.state = CombatState.IDLE;
            itemState.lastTimer = -1;
        }
    }

    private static void applySpearItemRecovery(PoseStack poseStack, ItemAnimState itemState) {
        if (itemState.recoveryStartNano == 0L) return;

        float elapsed = (System.nanoTime() - itemState.recoveryStartNano) / 1_000_000_000f;
        float t = Math.max(0f, Math.min(1f, elapsed / SPEAR_ITEM_RECOVERY_SECONDS));
        float keepEndPose = 1f - smoothStep01(t);
        if (keepEndPose <= 0f) {
            itemState.recoveryStartNano = 0L;
            return;
        }

        float[] endRot = sampleKeyedVec3(SPEAR_ATTACK_ROT_KEYS, 1.0f);
        float[] endPos = sampleKeyedVec3(SPEAR_ATTACK_POS_KEYS, 1.0f);
        applySpearItemTransform(poseStack, scaleVec3(endRot, keepEndPose), scaleVec3(endPos, keepEndPose));
    }

    private static void applySpearItemTransform(PoseStack poseStack, float[] rot, float[] pos) {
        poseStack.mulPose(Axis.XP.rotationDegrees(rot[0]));
        poseStack.mulPose(Axis.YP.rotationDegrees(rot[1]));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rot[2]));
        poseStack.translate(pos[0], pos[1], pos[2]);
    }

    private static float[] scaleVec3(float[] vec, float scale) {
        return new float[] {vec[0] * scale, vec[1] * scale, vec[2] * scale};
    }

    private static float[] sampleKeyedVec3(float[][] keys, float progress) {
        if (progress <= keys[0][0]) {
            return new float[] {keys[0][1], keys[0][2], keys[0][3]};
        }
        int last = keys.length - 1;
        if (progress >= keys[last][0]) {
            return new float[] {keys[last][1], keys[last][2], keys[last][3]};
        }

        for (int i = 0; i < last; i++) {
            float[] a = keys[i];
            float[] b = keys[i + 1];
            if (progress >= a[0] && progress <= b[0]) {
                float span = b[0] - a[0];
                float t = span > 0f ? (progress - a[0]) / span : 0f;
                t = smoothStep01(t);
                return new float[] {
                        a[1] + (b[1] - a[1]) * t,
                        a[2] + (b[2] - a[2]) * t,
                        a[3] + (b[3] - a[3]) * t
                };
            }
        }

        return new float[] {0f, 0f, 0f};
    }

    private static float smoothStep01(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    // Y 偏移曲线: 0 → -15 → -10 → 0 (sin/cos 三段，天然平滑无尖角)
    //   [0,-100]: quarter-sin 爬升到 -15
    //   [-100,-210]: quarter-sin 回收到 -10
    //   [-210,-325]: quarter-cos 衰减到 0
    private static float spinY(float xDeg) {
        float ax = -xDeg;
        if (ax <= 100f) {
            return -15f * (float) Math.sin(ax / 100f * Math.PI * 0.5);
        } else if (ax <= 210f) {
            float t = (ax - 100f) / 110f;
            return -15f + 5f * (float) Math.sin(t * Math.PI * 0.5);
        } else {
            float t = (ax - 210f) / 115f;
            return -10f * (float) Math.cos(t * Math.PI * 0.5);
        }
    }

    // Z 偏移曲线: 0 → -5 → -20 → 0 (同结构)
    //   [0,-100]: quarter-sin 到 -5
    //   [-100,-210]: quarter-sin 到 -20
    //   [-210,-325]: quarter-cos 衰减到 0
    private static float spinZ(float xDeg) {
        float ax = -xDeg;
        if (ax <= 100f) {
            return -5f * (float) Math.sin(ax / 100f * Math.PI * 0.5);
        } else if (ax <= 210f) {
            float t = (ax - 100f) / 110f;
            return -5f - 15f * (float) Math.sin(t * Math.PI * 0.5);
        } else {
            float t = (ax - 210f) / 115f;
            return -20f * (float) Math.cos(t * Math.PI * 0.5);
        }
    }
}
