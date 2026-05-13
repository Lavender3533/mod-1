package org.example.combatarts.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.example.combatarts.combat.client.render.mesh.Armature;
import org.example.combatarts.combat.client.render.mesh.Joint;
import org.example.combatarts.combat.client.render.mesh.MathUtils;
import org.example.combatarts.combat.client.render.mesh.MeshManager;
import org.example.combatarts.combat.client.render.mesh.OpenMatrix4f;

public class CombatWingsLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {
    private final ElytraModel elytraModel;
    private final ElytraModel elytraBabyModel;
    private final EquipmentLayerRenderer equipmentRenderer;

    public CombatWingsLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent,
                            EntityRendererProvider.Context context) {
        super(parent);
        EntityModelSet modelSet = context.getModelSet();
        this.elytraModel = new ElytraModel(modelSet.bakeLayer(ModelLayers.ELYTRA));
        this.elytraBabyModel = new ElytraModel(modelSet.bakeLayer(ModelLayers.ELYTRA_BABY));
        this.equipmentRenderer = context.getEquipmentRenderer();
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !state.chestEquipment.is(Items.ELYTRA)) return;

        Equippable equippable = state.chestEquipment.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) return;

        Armature armature = MeshManager.getArmature();
        if (armature == null || !armature.hasJoint("Chest")) return;

        Joint chestJoint = armature.searchJointByName("Chest");
        OpenMatrix4f chestMatrix = armature.getPoseMatrices()[chestJoint.getId()];

        ElytraModel model = state.isBaby ? this.elytraBabyModel : this.elytraModel;
        model.setupAnim(state);

        poseStack.pushPose();
        // 与 skinned mesh / cape 一致: 先进入模型基准空间, 再乘 Chest 关节矩阵。
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0, -1.501, 0.0);
        MathUtils.mulStack(poseStack, chestMatrix);

        // 使用 CombatCapeLayer 调好的背部位置。ElytraModel 自身已经按 +Z 背部建模,
        // 所以不能套披风的 X180; 这里只翻 Elytra 的局部上下轴, 保持背部 Z 方向不变。
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180f));
        poseStack.translate(0.0F, -0.40F, 0.06F);
        this.equipmentRenderer.renderLayers(
                EquipmentClientInfo.LayerType.WINGS,
                equippable.assetId().get(),
                model,
                state,
                state.chestEquipment,
                poseStack,
                collector,
                packedLight,
                getPlayerElytraTexture(state),
                state.outlineColor,
                0
        );
        poseStack.popPose();
    }

    private static Identifier getPlayerElytraTexture(AvatarRenderState state) {
        if (state.skin != null) {
            ClientAsset.Texture elytraAsset = state.skin.elytra();
            if (elytraAsset != null) {
                return elytraAsset.texturePath();
            }
            ClientAsset.Texture capeAsset = state.skin.cape();
            if (state.showCape && capeAsset != null) {
                return capeAsset.texturePath();
            }
        }
        return null;
    }
}
