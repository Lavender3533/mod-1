package org.example.combatarts.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.example.combatarts.combat.client.render.mesh.*;

public class SkinnedMeshLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {

    public SkinnedMeshLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        Armature armature = MeshManager.getArmature();
        SkinnedMesh mesh = MeshManager.getMesh();
        if (armature == null || mesh == null) return;

        // Test with EF idle animation
        float gameTime = (float)(Minecraft.getInstance().level != null ?
                Minecraft.getInstance().level.getGameTime() : 0) +
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float animLength = MeshManager.getAnimLength("idle");
        float animTime = animLength > 0 ? (gameTime * 0.05f) % animLength : 0;

        Pose pose = MeshManager.getPoseAtTime("idle", animTime);
        armature.setPose(pose);

        Identifier skinTex = state.skin.body().texturePath();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(
                RenderTypes.entityCutoutNoCull(skinTex));

        poseStack.pushPose();

        // Undo MC's LivingEntityRenderer transforms:
        // MC applied: translate(0, 1.501, 0) -> scale(-1, -1, 1)
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        // TEST: Draw without skinning to verify mesh data
        mesh.draw(poseStack, buffer, Mesh.DrawingFunction.NEW_ENTITY,
                packedLight, 1.0f, 1.0f, 1.0f, 1.0f,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        bufferSource.endBatch();
    }
}
