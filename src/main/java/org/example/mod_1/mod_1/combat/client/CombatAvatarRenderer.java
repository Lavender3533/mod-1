package org.example.mod_1.mod_1.combat.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class CombatAvatarRenderer
        extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, CombatPlayerModel> {

    public CombatAvatarRenderer(EntityRendererProvider.Context context) {
        super(context, new CombatPlayerModel(context.bakeLayer(CombatPlayerModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new PlayerItemInHandLayer<>(this));
        this.addLayer(new BackWeaponLayer(this));
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(AbstractClientPlayer player, AvatarRenderState state, float partialTick) {
        super.extractRenderState(player, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(player, state, partialTick, this.itemModelResolver);
        state.skin = player.getSkin();
        state.isSpectator = player.isSpectator();
        state.showHat = player.isModelPartShown(PlayerModelPart.HAT);
        state.showJacket = player.isModelPartShown(PlayerModelPart.JACKET);
        state.showLeftPants = player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        state.showRightPants = player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        state.showLeftSleeve = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        state.showRightSleeve = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        state.showCape = player.isModelPartShown(PlayerModelPart.CAPE);
        state.id = player.getId();
    }

    @Override
    protected void scale(AvatarRenderState state, PoseStack poseStack) {
        // Match vanilla AvatarRenderer: scale model to 15/16 (0.9375)
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.body().texturePath();
    }
}
