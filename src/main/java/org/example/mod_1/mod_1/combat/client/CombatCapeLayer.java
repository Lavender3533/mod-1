package org.example.mod_1.mod_1.combat.client;

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

// Cape 是单独 bake 的 ModelPart (root → hip → waist → chest → cape), 不在 body 模型树里 ——
// 主渲染 pass 只画 body 模型, 自然不会带着 cape 去用 body 贴图渲染。
// 这里 walk 自己内部 chain 到 chest 把变换叠到 PoseStack 上, 再用 cape 贴图 submit cape 部件.
public class CombatCapeLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {

    private final ModelPart root;
    private final ModelPart hip;
    private final ModelPart waist;
    private final ModelPart chest;
    private final ModelPart cape;

    public CombatCapeLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent,
                           EntityRendererProvider.Context context) {
        super(parent);
        ModelPart bakedRoot = context.bakeLayer(CombatPlayerModel.LAYER_LOCATION_CAPE);
        this.root = bakedRoot.getChild("root");
        this.hip = root.getChild("hip");
        this.waist = hip.getChild("waist");
        this.chest = waist.getChild("chest");
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

        // 把 body 模型当前的 chain 旋转/动画 复刻一份到我们独立 cape 链上 ——
        // 这样 cape 会跟着 chest 的战斗动画一起摆 (例如蓄力前倾时披风也跟着前倾)。
        CombatPlayerModel body = this.getParentModel();
        copyPose(this.root, body.root);
        copyPose(this.hip, body.hip);
        copyPose(this.waist, body.waist);
        copyPose(this.chest, body.chest);

        // 应用 capeFlap/Lean/Lean2 摆动 — 公式与 vanilla PlayerCapeModel.setupAnim 一致.
        cape.resetPose();
        final float DEG_TO_RAD = 0.017453292F;
        Quaternionf q = new Quaternionf()
                .rotateY(-(float) Math.PI)
                .rotateX((6.0F + state.capeLean / 2.0F + state.capeFlap) * DEG_TO_RAD)
                .rotateZ(state.capeLean2 / 2.0F * DEG_TO_RAD)
                .rotateY((180.0F - state.capeLean2 / 2.0F) * DEG_TO_RAD);
        cape.rotateBy(q);

        poseStack.pushPose();
        root.translateAndRotate(poseStack);
        hip.translateAndRotate(poseStack);
        waist.translateAndRotate(poseStack);
        chest.translateAndRotate(poseStack);
        // submitModelPart 内部会调用 cape.translateAndRotate, 然后渲染 cube
        collector.submitModelPart(cape, poseStack,
                RenderTypes.entitySolid(capeTex),
                packedLight, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
    }

    private static void copyPose(ModelPart dst, ModelPart src) {
        dst.x = src.x;       dst.y = src.y;       dst.z = src.z;
        dst.xRot = src.xRot; dst.yRot = src.yRot; dst.zRot = src.zRot;
        dst.xScale = src.xScale; dst.yScale = src.yScale; dst.zScale = src.zScale;
    }
}
