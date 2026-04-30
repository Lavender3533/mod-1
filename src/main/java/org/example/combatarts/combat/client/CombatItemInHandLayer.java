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
}
