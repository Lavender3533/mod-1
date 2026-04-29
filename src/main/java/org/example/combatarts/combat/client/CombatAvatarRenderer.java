package org.example.combatarts.combat.client;

import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import com.mojang.blaze3d.vertex.PoseStack;
import org.example.combatarts.combat.CombatState;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;
import org.example.combatarts.combat.client.render.mesh.MeshManager;
import org.example.combatarts.combat.client.render.mesh.Mesh;
import org.example.combatarts.combat.client.render.mesh.MeshManager;
import org.example.combatarts.combat.client.render.mesh.OpenMatrix4f;
import org.example.combatarts.combat.client.render.mesh.Pose;
import org.example.combatarts.combat.client.render.mesh.SkinnedMesh;

public class CombatAvatarRenderer
        extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, CombatPlayerModel> {

    private final CombatPlayerModel wideModel;
    private final CombatPlayerModel slimModel;

    public CombatAvatarRenderer(EntityRendererProvider.Context context) {
        super(context, new CombatPlayerModel(context.bakeLayer(CombatPlayerModel.LAYER_LOCATION), false), 0.5F);
        this.wideModel = this.getModel();
        this.slimModel = new CombatPlayerModel(context.bakeLayer(CombatPlayerModel.LAYER_LOCATION_SLIM), true);
        this.addLayer(new PlayerItemInHandLayer<>(this));
        this.addLayer(new GuardWeaponLayer(this, this.itemModelResolver));
        this.addLayer(new BackWeaponLayer(this, this.itemModelResolver));
        this.addLayer(new CombatCapeLayer(this, context));
        this.addLayer(new SkinnedMeshLayer(this));
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

        extractCapeState(player, state, partialTick);

        // Hide box model parts when skinned mesh is active
        if (MeshManager.getMesh() != null) {
            this.model.root.visible = false;
        }

        boolean weaponDrawn = false;
        boolean useCustomGuardWeaponLayer = false;
        var combatOpt = CombatCapabilityEvents.getCombat(player);
        if (combatOpt.isPresent()) {
            final boolean[] weaponDrawnRef = {false};
            final boolean[] customGuardRef = {false};
            combatOpt.ifPresent(cap -> {
                CombatAnimationController.updateAnimation(player, cap);
                weaponDrawnRef[0] = CombatCapabilityEvents.shouldRenderWeaponInHand(cap);
                CombatState combatState = cap.getState();
                // GuardWeaponLayer 对剑/矛都做自定义格挡渲染, 这里相应地把 vanilla item layer 抑制掉
                customGuardRef[0] = weaponDrawnRef[0]
                        && (cap.getWeaponType() == org.example.combatarts.combat.WeaponType.SWORD
                            || cap.getWeaponType() == org.example.combatarts.combat.WeaponType.SPEAR)
                        && (combatState == CombatState.BLOCK || combatState == CombatState.PARRY);
            });
            weaponDrawn = weaponDrawnRef[0];
            useCustomGuardWeaponLayer = customGuardRef[0];
        }

        // 未拔刀时抑制 PlayerItemInHandLayer 的手持渲染（武器改由 BackWeaponLayer 背上渲染）
        if (!weaponDrawn) {
            // BackWeaponLayer 自己通过 capability 拿到主手物品，不依赖这里
            // 所以我们可以把主手的 itemState 清空，vanilla 的 PlayerItemInHandLayer 就会跳过
            // 但 rightHandItemState 被子层读取，清空会影响 BackWeaponLayer
            // 改为只清空 mainArm 侧 — BackWeaponLayer 直接从 player 读取
            clearHandItem(state);
        } else if (useCustomGuardWeaponLayer) {
            // BLOCK/PARRY 改用真实 rightHand 挂点的自定义图层，避免 vanilla 仍按上臂挂点渲染。
            clearHandItem(state);
        }
    }

    private static void clearHandItem(AvatarRenderState state) {
        if (state.rightHandItemState != null) state.rightHandItemState.clear();
        if (state.leftHandItemState != null) state.leftHandItemState.clear();
    }

    // 复刻 vanilla AvatarRenderer.extractCapeState — vanilla 是 private,
    // 我们 extends LivingEntityRenderer 拿不到, 所以照抄计算逻辑给 capeFlap/Lean/Lean2 赋值。
    private static void extractCapeState(AbstractClientPlayer player, AvatarRenderState state, float partialTick) {
        ClientAvatarState s = ((ClientAvatarEntity) player).avatarState();
        double dx = s.getInterpolatedCloakX(partialTick)
                - Mth.lerp((double) partialTick, player.xo, player.getX());
        double dy = s.getInterpolatedCloakY(partialTick)
                - Mth.lerp((double) partialTick, player.yo, player.getY());
        double dz = s.getInterpolatedCloakZ(partialTick)
                - Mth.lerp((double) partialTick, player.zo, player.getZ());
        float yBody = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double sinY = Mth.sin((double) (yBody * 0.017453292F));
        double negCosY = -Mth.cos((double) (yBody * 0.017453292F));

        state.capeFlap = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
        state.capeLean = (float) (dx * sinY + dz * negCosY) * 100.0F;
        state.capeLean *= 1.0F - state.fallFlyingScale();
        state.capeLean = Mth.clamp(state.capeLean, 0.0F, 150.0F);
        state.capeLean2 = Mth.clamp((float) (dx * negCosY - dz * sinY) * 100.0F, -20.0F, 20.0F);

        float bob = s.getInterpolatedBob(partialTick);
        float walkDist = s.getInterpolatedWalkDistance(partialTick);
        state.capeFlap += Mth.sin(walkDist * 6.0F) * 32.0F * bob;
    }

    // TODO: Skinned mesh rendering will be integrated here once the rendering pipeline
    // is adapted from MultiBufferSource to SubmitNodeCollector (Forge 1.21.11 API change).
    // For now, the vanilla box model + layers continue to render via super.submit().

    @Override
    protected void scale(AvatarRenderState state, PoseStack poseStack) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.body().texturePath();
    }
}
