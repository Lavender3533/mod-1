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
import org.example.combatarts.combat.WeaponDetector;
import org.example.combatarts.combat.WeaponType;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;
import org.example.combatarts.combat.client.render.mesh.*;

public class BackWeaponLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {

    private final ItemModelResolver itemModelResolver;

    public BackWeaponLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent,
                           ItemModelResolver itemModelResolver) {
        super(parent);
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        Player player = resolvePlayer(state);
        if (player == null) return;

        boolean drawn = CombatCapabilityEvents.getCombat(player)
                .map(CombatCapabilityEvents::shouldRenderWeaponInHand)
                .orElse(false);
        if (drawn) return;

        WeaponType weaponType = WeaponDetector.detect(player);
        if (weaponType == WeaponType.UNARMED) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        Armature armature = MeshManager.getArmature();
        boolean useArmature = armature != null && MeshManager.getMesh() != null && armature.hasJoint("Chest");

        ItemStackRenderState scratchState = new ItemStackRenderState();
        itemModelResolver.updateForLiving(scratchState, stack,
                useArmature ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.FIXED,
                player);
        if (scratchState.isEmpty()) return;

        poseStack.pushPose();

        if (useArmature) {
            Joint chest = armature.searchJointByName("Chest");
            OpenMatrix4f jointMatrix = armature.getPoseMatrices()[chest.getId()];
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0, -1.501, 0.0);
            MathUtils.mulStack(poseStack, jointMatrix);
            // EF Chest correction — positions item diagonally across the back
            OpenMatrix4f chestCorrection = new OpenMatrix4f(
                    3.3484866E-8F, -2.809714E-8F, -0.99999994F, 0.0F,
                    -0.6427876F, -0.7660444F, 0.0F, 0.0F,
                    -0.76604444F, 0.64278764F, -4.3711385E-8F, 0.0F,
                    0.25711504F, 0.30641776F, 0.14999999F, 1.0F
            );
            MathUtils.mulStack(poseStack, chestCorrection);
        } else {
            CombatPlayerModel model = this.getParentModel();
            model.root.translateAndRotate(poseStack);
            model.hip.translateAndRotate(poseStack);
            model.waist.translateAndRotate(poseStack);
            model.chest.translateAndRotate(poseStack);
            model.sheathBack.translateAndRotate(poseStack);

            // 烘焙的背挂武器位置偏移(用户 tweaker 调出的)
            poseStack.translate(0.0F, 0.0F, -0.05F);

            poseStack.mulPose(Axis.XP.rotationDegrees(BlockPoseTweaker.getBackRot(0)));
            poseStack.mulPose(Axis.YP.rotationDegrees(BlockPoseTweaker.getBackRot(1)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(BlockPoseTweaker.getBackRot(2)));
            poseStack.translate(
                    BlockPoseTweaker.getBackPos(0),
                    BlockPoseTweaker.getBackPos(1),
                    BlockPoseTweaker.getBackPos(2)
            );

            applyBackWeaponTransform(poseStack, weaponType);
        }

        scratchState.submit(poseStack, collector, packedLight, 0, 0);

        poseStack.popPose();
    }

    private static void applyBackWeaponTransform(PoseStack poseStack, WeaponType weaponType) {
        poseStack.mulPose(Axis.ZP.rotationDegrees(35.0f));
        poseStack.translate(0.0f, 0.35f, 0.0f);

        switch (weaponType) {
            case SPEAR -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(30.0f));
                poseStack.translate(-2.0f / 16.0f, 1.0f / 16.0f, 0);
                poseStack.scale(1.0f, 1.0f, 1.0f);
            }
            case SWORD -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(45.0f));
                poseStack.translate(-1.5f / 16.0f, 0, 0);
                poseStack.scale(0.9f, 0.9f, 0.9f);
            }
            default -> {
            }
        }
    }

    private static Player resolvePlayer(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(state.id);
        return entity instanceof Player p ? p : null;
    }
}
