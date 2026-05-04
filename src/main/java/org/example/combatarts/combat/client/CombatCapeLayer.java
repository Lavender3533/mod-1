package org.example.combatarts.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.example.combatarts.combat.client.render.mesh.*;

public class CombatCapeLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {

    private final ModelPart cape;

    public CombatCapeLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent,
                           EntityRendererProvider.Context context) {
        super(parent);
        ModelPart bakedRoot = context.bakeLayer(CombatPlayerModel.LAYER_LOCATION_CAPE);
        ModelPart root = bakedRoot.getChild("root");
        ModelPart hip = root.getChild("hip");
        ModelPart waist = hip.getChild("waist");
        ModelPart chest = waist.getChild("chest");
        this.cape = chest.getChild("cape");
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !state.showCape) return;
        if (state.skin == null) return;
        ClientAsset.Texture capeAsset = state.skin.cape();
        if (capeAsset == null) return;
        Identifier capeTex = capeAsset.texturePath();
        if (capeTex == null) return;

        Armature armature = MeshManager.getArmature();
        if (armature == null || !armature.hasJoint("Chest")) return;

        Joint chestJoint = armature.searchJointByName("Chest");
        OpenMatrix4f chestMatrix = armature.getPoseMatrices()[chestJoint.getId()];

        cape.resetPose();
        final float DEG_TO_RAD = 0.017453292F;
        Quaternionf q = new Quaternionf()
                .rotateY(-(float) Math.PI)
                .rotateX((6.0F + state.capeLean / 2.0F + state.capeFlap) * DEG_TO_RAD)
                .rotateZ(state.capeLean2 / 2.0F * DEG_TO_RAD)
                .rotateY((180.0F - state.capeLean2 / 2.0F) * DEG_TO_RAD);
        cape.rotateBy(q);

        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0, -1.501, 0.0);
        MathUtils.mulStack(poseStack, chestMatrix);
        poseStack.translate(0.0, 0.25, 0.06);

        collector.submitModelPart(cape, poseStack,
                RenderTypes.entitySolid(capeTex),
                packedLight, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
    }
}
