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
import org.example.combatarts.combat.client.render.mesh.*;

public class CombatItemInHandLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {

    private final ItemModelResolver itemModelResolver;
    private final ItemStackRenderState scratchState = new ItemStackRenderState();

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

            // 检视时剑旋转: 跟 inspect 动画同步，两段不同角度
            // A(0.4-1.4): held_rot (25, 0, 35), B(2.0-3.0): held_rot (35, 10, 15)
            if (combatState == CombatState.INSPECT) {
                float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
                float gameTime = (float)(Minecraft.getInstance().level != null ?
                        Minecraft.getInstance().level.getGameTime() : 0) + partial;
                float t = (gameTime * 0.05f) % 4.0f;

                float rx, ry, rz;
                if (t < 0.4f) {
                    float a = t / 0.4f;
                    rx = 25f * a; ry = 0f; rz = 35f * a;
                } else if (t < 1.4f) {
                    rx = 25f; ry = 0f; rz = 35f;
                } else if (t < 2.0f) {
                    float a = (t - 1.4f) / 0.6f;
                    rx = 25f + (35f - 25f) * a;
                    ry = 10f * a;
                    rz = 35f + (15f - 35f) * a;
                } else if (t < 3.0f) {
                    rx = 35f; ry = 10f; rz = 15f;
                } else if (t < 3.7f) {
                    float a = (t - 3.0f) / 0.7f;
                    rx = 35f * (1f - a); ry = 10f * (1f - a); rz = 15f * (1f - a);
                } else {
                    rx = 0f; ry = 0f; rz = 0f;
                }
                poseStack.mulPose(Axis.XP.rotationDegrees(rx));
                poseStack.mulPose(Axis.YP.rotationDegrees(ry));
                poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
            }

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
