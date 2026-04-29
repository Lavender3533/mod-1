package org.example.combatarts.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.example.combatarts.combat.client.render.mesh.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkinnedMeshLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {
    private static final Logger LOGGER = LoggerFactory.getLogger("CombatArts");
    private static boolean logged = false;

    public SkinnedMeshLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        Armature armature = MeshManager.getArmature();
        SkinnedMesh mesh = MeshManager.getMesh();
        if (armature == null || mesh == null) return;

        float gameTime = (float)(Minecraft.getInstance().level != null ?
                Minecraft.getInstance().level.getGameTime() : 0) +
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float animLength = MeshManager.getAnimLength("idle");
        float animTime = animLength > 0 ? (gameTime * 0.05f) % animLength : 0;

        Pose pose = MeshManager.getPoseAtTime("idle", animTime);
        armature.setPose(pose);

        Identifier skinTex = state.skin.body().texturePath();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        // Debug: check render type mode
        var baseRT = RenderTypes.entityCutoutNoCull(skinTex);
        var triRT = TriangulatedRenderType.entityTriangles(skinTex);
        if (!logged) {
            LOGGER.info("[SkinnedMesh] Base RenderType mode: {}, Triangulated mode: {}",
                       baseRT.mode(), triRT.mode());
            LOGGER.info("[SkinnedMesh] Format: {}", triRT.format());
            // Log first few UV values from mesh
            float[] uvs = mesh.uvs();
            LOGGER.info("[SkinnedMesh] First 10 UV values: [{}, {}, {}, {}, {}, {}, {}, {}, {}, {}]",
                       uvs[0], uvs[1], uvs[2], uvs[3], uvs[4], uvs[5], uvs[6], uvs[7], uvs[8], uvs[9]);
            float[] pos = mesh.positions();
            LOGGER.info("[SkinnedMesh] First 9 pos values: [{}, {}, {}, {}, {}, {}, {}, {}, {}]",
                       pos[0], pos[1], pos[2], pos[3], pos[4], pos[5], pos[6], pos[7], pos[8]);
            logged = true;
        }

        VertexConsumer buffer = bufferSource.getBuffer(triRT);

        poseStack.pushPose();
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        mesh.drawPosed(poseStack, buffer, Mesh.DrawingFunction.NEW_ENTITY,
                packedLight, 1.0f, 1.0f, 1.0f, 1.0f,
                OverlayTexture.NO_OVERLAY, armature, armature.getPoseMatrices());
        poseStack.popPose();

        bufferSource.endBatch();
    }
}
