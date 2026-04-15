package org.example.mod_1.mod_1.combat.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import com.mojang.blaze3d.vertex.PoseStack;

public class CombatAvatarRenderer
        extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, CombatPlayerModel> {

    private final CombatPlayerModel wideModel;
    private final CombatPlayerModel slimModel;

    public CombatAvatarRenderer(EntityRendererProvider.Context context) {
        super(context, new CombatPlayerModel(context.bakeLayer(CombatPlayerModel.LAYER_LOCATION), false), 0.5F);
        this.wideModel = this.getModel();
        this.slimModel = new CombatPlayerModel(context.bakeLayer(CombatPlayerModel.LAYER_LOCATION_SLIM), true);
        this.addLayer(new PlayerItemInHandLayer<>(this));
        this.addLayer(new BackWeaponLayer(this));
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(AbstractClientPlayer player, AvatarRenderState state, float partialTick) {
        // Switch model based on skin type BEFORE extracting state
        boolean isSlim = player.getSkin().model() == PlayerModelType.SLIM;
        this.model = isSlim ? this.slimModel : this.wideModel;

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
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.body().texturePath();
    }
}
