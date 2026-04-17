package org.example.mod_1.mod_1.combat.client;

// 未拔刀时在背部渲染武器模型
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;

public class BackWeaponLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {

    private final ItemModelResolver itemModelResolver;
    private final ItemStackRenderState scratchState = new ItemStackRenderState();

    public BackWeaponLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent,
                           ItemModelResolver itemModelResolver) {
        super(parent);
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        Player player = resolvePlayer(state);
        if (player == null) return;

        // 只在未拔刀时显示背后武器
        boolean drawn = CombatCapabilityEvents.getCombat(player)
                .map(cap -> cap.isWeaponDrawn())
                .orElse(false);
        if (drawn) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        itemModelResolver.updateForLiving(scratchState, stack, ItemDisplayContext.FIXED, player);
        if (scratchState.isEmpty()) return;

        poseStack.pushPose();

        // 附着到 chest 骨骼
        this.getParentModel().chest.translateAndRotate(poseStack);

        // chest 骨骼空间：+X 玩家左, +Y 向下（朝脚）, +Z 朝前（朝脸）
        // 要贴在背后 → Z 应为负
        // 剑柄靠近右肩 → X 稍负（右侧）, Y 靠近顶部（-Y 方向）
        poseStack.translate(-2.0f / 16.0f, 2.0f / 16.0f, -2.8f / 16.0f);

        // 姿势：剑身贴背，剑柄右上，剑尖左下
        // 先绕 X 轴转 180° 让剑头部朝外（否则会朝前穿身体）
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
        // 再绕 Y 轴转 -90° 让剑躺平（不是直立）
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
        // 最后绕 Z 轴让剑倾斜 45° 呈斜背姿态
        poseStack.mulPose(Axis.ZP.rotationDegrees(45.0f));

        poseStack.scale(0.85f, 0.85f, 0.85f);

        scratchState.submit(poseStack, collector, packedLight, 0, 0);

        poseStack.popPose();
    }

    private static Player resolvePlayer(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(state.id);
        return entity instanceof Player p ? p : null;
    }
}
