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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.example.mod_1.mod_1.combat.WeaponDetector;
import org.example.mod_1.mod_1.combat.WeaponType;
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

        WeaponType weaponType = WeaponDetector.detect(player);
        if (weaponType == WeaponType.UNARMED) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        itemModelResolver.updateForLiving(scratchState, stack, ItemDisplayContext.FIXED, player);
        if (scratchState.isEmpty()) return;

        poseStack.pushPose();

        CombatPlayerModel model = this.getParentModel();
        model.root.translateAndRotate(poseStack);
        model.hip.translateAndRotate(poseStack);
        model.waist.translateAndRotate(poseStack);
        model.chest.translateAndRotate(poseStack);
        model.sheathBack.translateAndRotate(poseStack);

        applyBackWeaponTransform(poseStack, weaponType);

        scratchState.submit(poseStack, collector, packedLight, 0, 0);

        poseStack.popPose();
    }

    private static void applyBackWeaponTransform(PoseStack poseStack, WeaponType weaponType) {
        // sheathBack 现在挂在 chest 局部 (0, -1, 2.5) — 上背中线(肩胛骨之间)、背面外侧 0.5px。
        // 此处只做：让物品面朝身体外侧 + 斜挎角度 + 微调位置。
        switch (weaponType) {
            case SPEAR -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));   // 物品正面朝外(远离身体)
                poseStack.mulPose(Axis.ZP.rotationDegrees(30.0f));    // 斜挎 30°
                poseStack.translate(-2.0f / 16.0f, 1.0f / 16.0f, 0);  // 略偏右肩 + 微下移让中段贴背
                poseStack.scale(1.0f, 1.0f, 1.0f);
            }
            case SWORD -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));   // 物品正面朝外
                poseStack.mulPose(Axis.ZP.rotationDegrees(45.0f));    // 斜挎 45° (剑柄在右上、剑尖在左下)
                poseStack.translate(-1.5f / 16.0f, 0, 0);             // 略偏右肩
                poseStack.scale(0.9f, 0.9f, 0.9f);
            }
            default -> {
            }
        }
    }

    private static Player resolvePlayer(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(state.id);
        return entity instanceof Player p ? p : null;
    }
}
