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
    private boolean currentEntityFlying;

    public CombatAvatarRenderer(EntityRendererProvider.Context context) {
        super(context, new CombatPlayerModel(context.bakeLayer(CombatPlayerModel.LAYER_LOCATION), false), 0.5F);
        this.wideModel = this.getModel();
        this.slimModel = new CombatPlayerModel(context.bakeLayer(CombatPlayerModel.LAYER_LOCATION_SLIM), true);
        this.addLayer(new SkinnedMeshLayer(this));
        this.addLayer(new CombatWingsLayer(this, context));

        // 盔甲: vanilla EquipmentLayerRenderer + 桥接 HumanoidModel (跟随 mod 骨骼)。
        // 暂时禁用 — 待甲方确认这一版整体表现后再决定是否启用 + 调试坐标系。
        // BridgedHumanoidModel 文件保留, 启用时取消下面 addLayer 注释即可。
        /*
        net.minecraft.client.renderer.entity.ArmorModelSet<BridgedHumanoidModel> bridges =
                net.minecraft.client.model.geom.ModelLayers.PLAYER_ARMOR.map(
                        loc -> new BridgedHumanoidModel(context.bakeLayer(loc)));
        var equipmentRenderer = context.getEquipmentRenderer();
        var humanoidLayer = net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID;
        var leggingsLayer = net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS;

        this.addLayer(new net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer<>(
                this, equipmentRenderer, humanoidLayer,
                s -> getArmorStack(s, net.minecraft.world.entity.EquipmentSlot.HEAD),
                bridges.head(), bridges.head()));
        this.addLayer(new net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer<>(
                this, equipmentRenderer, humanoidLayer,
                s -> getArmorStack(s, net.minecraft.world.entity.EquipmentSlot.CHEST),
                bridges.chest(), bridges.chest()));
        this.addLayer(new net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer<>(
                this, equipmentRenderer, leggingsLayer,
                s -> getArmorStack(s, net.minecraft.world.entity.EquipmentSlot.LEGS),
                bridges.legs(), bridges.legs()));
        this.addLayer(new net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer<>(
                this, equipmentRenderer, humanoidLayer,
                s -> getArmorStack(s, net.minecraft.world.entity.EquipmentSlot.FEET),
                bridges.feet(), bridges.feet()));
        */

        this.addLayer(new PlayerItemInHandLayer<>(this));
        this.addLayer(new CombatItemInHandLayer(this, this.itemModelResolver));
        this.addLayer(new GuardWeaponLayer(this, this.itemModelResolver));
        this.addLayer(new BackWeaponLayer(this, this.itemModelResolver));
        this.addLayer(new CombatCapeLayer(this, context));
    }

    // 从 AvatarRenderState 取指定槽的盔甲 ItemStack。AvatarRenderState extends HumanoidRenderState,
    // HumanoidRenderState 字段 headEquipment/chestEquipment/legsEquipment/feetEquipment 提供装备的 stack。
    private static net.minecraft.world.item.ItemStack getArmorStack(AvatarRenderState state, net.minecraft.world.entity.EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> state.headEquipment;
            case CHEST -> state.chestEquipment;
            case LEGS -> state.legsEquipment;
            case FEET -> state.feetEquipment;
            default -> net.minecraft.world.item.ItemStack.EMPTY;
        };
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

        // AvatarRenderer 自动填的字段, 我们 extends LivingEntityRenderer 没继承, 手动填:
        // - fallFlyingTimeInTicks: setupRotations 用 fallFlyingScale() 算 entity pitch (鞘翅大字)
        state.fallFlyingTimeInTicks = (float) player.getFallFlyingTicks() + partialTick;

        var combatOpt = CombatCapabilityEvents.getCombat(player);

        // 飞行 → 显示 vanilla 盒子模型 + 跳过蒙皮网格 (各 layer 自行检查 FlyingDetector)
        boolean isFlying = FlyingDetector.isFlying(player);
        this.currentEntityFlying = isFlying;

        if (MeshManager.getMesh() != null) {
            this.wideModel.root.visible = true;
            this.slimModel.root.visible = true;
        }

        if (combatOpt.isPresent()) {
            combatOpt.ifPresent(cap -> {
                CombatAnimationController.updateAnimation(player, cap);
            });
        }

        if (!isFlying) {
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

    // 抄 vanilla AvatarRenderer.setupRotations 的飞行 pitch 逻辑 — 我们 extends LivingEntityRenderer
    // 默认 setupRotations 不处理飞行, 玩家会站着不会"水平大字"。
    // vanilla 用 fallFlyingScale (累积公式 t²/100) 渐进 pitch, 但这会让起飞前 0.5s 看不到效果。
    // 我们简化: 一进入 isFallFlying 就立即 90°, 不等累积。
    @Override
    protected void setupRotations(AvatarRenderState state, PoseStack poseStack, float bob, float yBodyRot) {
        super.setupRotations(state, poseStack, bob, yBodyRot);
        if (state.isFallFlying && !state.isAutoSpinAttack) {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F - state.xRot));
        }
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.body().texturePath();
    }
}
