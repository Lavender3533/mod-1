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
        // 抵消 PartPose 自带的 PI Y rotation —— X 180° on PoseStack 会翻转局部 Z 方向
        // cape 几何体外面默认朝 -Z, PI Y 把它转到 +Z (vanilla 背后)
        // 但 X 180° 又把 +Z 翻回 -Z (变成正前方) => 从背后看到内面
        // 移除 PartPose 的 Y 旋转, 让外面保持朝 -Z, X 180° 翻转后正好朝 +Z = 背后
        cape.yRot = 0f;
        final float DEG_TO_RAD = 0.017453292F;

        // 披风物理摆动 (取反匹配 X180 翻转后的方向)
        Quaternionf q = new Quaternionf()
                .rotateX(-(6.0F + state.capeLean / 2.0F + state.capeFlap) * DEG_TO_RAD)
                .rotateZ(-state.capeLean2 / 2.0F * DEG_TO_RAD);
        cape.rotateBy(q);

        poseStack.pushPose();
        // 和 SkinnedMeshLayer 一样的基准变换
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0, -1.501, 0.0);
        // Chest 关节世界空间矩阵
        MathUtils.mulStack(poseStack, chestMatrix);
        // X 180° 让披风从顶部翻到背后垂下
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180f));
        // Z 偏移: -0.18 在身体里, -0.30 太远, 取 -0.27
        poseStack.translate(0.0, -0.15, -0.27);

        // 双面渲染: X180 翻转了法线方向, noCull 让两面都可见
        collector.submitModelPart(cape, poseStack,
                RenderTypes.entityCutoutNoCull(capeTex),
                packedLight, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
    }
}
