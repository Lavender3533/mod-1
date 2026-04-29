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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;
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

        Player player = resolvePlayer(state);
        String animName = resolveAnimation(player);

        float animTime = computeAnimTime(player, animName, state);

        Pose pose = MeshManager.getPoseAtTime(animName, animTime);
        armature.setPose(pose);

        Identifier skinTex = state.skin.body().texturePath();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(
                TriangulatedRenderType.entityTriangles(skinTex));

        poseStack.pushPose();
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        mesh.drawPosed(poseStack, buffer, Mesh.DrawingFunction.NEW_ENTITY,
                packedLight, 1.0f, 1.0f, 1.0f, 1.0f,
                OverlayTexture.NO_OVERLAY, armature, armature.getPoseMatrices());
        poseStack.popPose();

        bufferSource.endBatch();
    }

    private float computeAnimTime(Player player, String animName, AvatarRenderState state) {
        float animLength = MeshManager.getAnimLength(animName);
        if (animLength <= 0) return 0;

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

        if (animName.contains("walk") || animName.contains("run") || animName.equals("sneak")) {
            // Sync to player's walk animation position (like vanilla MC)
            float walkPos = state.walkAnimationPos;
            float speed = animName.contains("run") ? 0.05f : 0.08f;
            if (animName.equals("sneak")) speed = 0.06f;
            return (walkPos * speed) % animLength;
        }

        // Idle/hold animations: use game time
        float gameTime = (float)(Minecraft.getInstance().level != null ?
                Minecraft.getInstance().level.getGameTime() : 0) + partialTick;
        return (gameTime * 0.05f) % animLength;
    }

    private String resolveAnimation(Player player) {
        if (player == null) return "idle";

        boolean weaponDrawn = CombatCapabilityEvents.getCombat(player)
                .map(cap -> cap.isWeaponDrawn()).orElse(false);

        if (player.isCrouching()) {
            return "sneak";
        } else if (player.isSprinting()) {
            return weaponDrawn ? "run_longsword" : "run";
        } else {
            double dx = player.getX() - player.xOld;
            double dz = player.getZ() - player.zOld;
            double hSpeedSq = dx * dx + dz * dz;
            if (hSpeedSq > 0.0004) {
                return weaponDrawn ? "walk_longsword" : "walk";
            }
        }

        return weaponDrawn ? "hold_longsword" : "idle";
    }

    private static Player resolvePlayer(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(state.id);
        return entity instanceof Player p ? p : null;
    }
}
