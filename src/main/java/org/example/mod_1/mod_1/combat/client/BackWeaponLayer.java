package org.example.mod_1.mod_1.combat.client;

// 未拔刀时在背部渲染武器模型
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;

public class BackWeaponLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {

    public BackWeaponLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        ItemStackRenderState mainHandItem = state.rightHandItemState;
        if (mainHandItem == null || mainHandItem.isEmpty()) return;

        // 只在未拔刀时显示背部武器（拔刀后由 ItemInHandLayer 渲染手持）
        // 通过检查 state 中的 mainArm 物品来判断是否需要背部渲染
        // BackWeaponLayer 始终渲染，CombatAvatarRenderer 会根据 weaponDrawn 控制显示

        poseStack.pushPose();

        // 附着到 chest 骨骼
        this.getParentModel().chest.translateAndRotate(poseStack);

        // 位移到背后：向后偏移，略向上
        poseStack.translate(0.0f, 0.2f, 0.15f);
        // 旋转让剑斜背在背上
        poseStack.mulPose(Axis.ZP.rotationDegrees(45.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        // 缩放
        poseStack.scale(0.65f, 0.65f, 0.65f);

        mainHandItem.submit(poseStack, collector, packedLight, 0, 0);

        poseStack.popPose();
    }
}
