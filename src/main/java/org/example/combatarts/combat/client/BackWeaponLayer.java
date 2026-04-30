package org.example.combatarts.combat.client;

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
import org.example.combatarts.combat.WeaponDetector;
import org.example.combatarts.combat.WeaponType;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;
import org.example.combatarts.combat.client.render.mesh.*;

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

        // 渲染开关与手里渲染同步: 拔刀第 4 tick 起背后剑消失, 收刀第 6 tick 起背后剑出现
        boolean drawn = CombatCapabilityEvents.getCombat(player)
                .map(CombatCapabilityEvents::shouldRenderWeaponInHand)
                .orElse(false);
        if (drawn) return;

        WeaponType weaponType = WeaponDetector.detect(player);
        if (weaponType == WeaponType.UNARMED) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        itemModelResolver.updateForLiving(scratchState, stack, ItemDisplayContext.FIXED, player);
        if (scratchState.isEmpty()) return;

        poseStack.pushPose();

        Armature armature = MeshManager.getArmature();
        if (armature != null && MeshManager.getMesh() != null && armature.hasJoint("Chest")) {
            Joint chest = armature.searchJointByName("Chest");
            OpenMatrix4f jointMatrix = armature.getPoseMatrices()[chest.getId()];
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0, -1.501, 0.0);
            MathUtils.mulStack(poseStack, jointMatrix);
            // EF Chest correction matrix for mainhand item on back
            OpenMatrix4f chestCorrection = new OpenMatrix4f(
                    3.3484866E-8F, -2.809714E-8F, -0.99999994F, 0.0F,
                    -0.6427876F, -0.7660444F, 0.0F, 0.0F,
                    -0.76604444F, 0.64278764F, -4.3711385E-8F, 0.0F,
                    0.25711504F, 0.30641776F, 0.14999999F, 1.0F
            );
            MathUtils.mulStack(poseStack, chestCorrection);
        } else {
            CombatPlayerModel model = this.getParentModel();
            model.root.translateAndRotate(poseStack);
            model.hip.translateAndRotate(poseStack);
            model.waist.translateAndRotate(poseStack);
            model.chest.translateAndRotate(poseStack);
            model.sheathBack.translateAndRotate(poseStack);
        }

        // Live tweaker — 在 baseline 变换之前叠加，X/Y/Z 对齐身体坐标
        //   X: 沿身体横向（左右），轴翻转可让武器从背左切到背右
        //   Y: 沿身体竖向（上下），就是你要的"剑尖往上往下转"
        //   Z: 沿身体前后向（深度），让武器从贴背→突出
        poseStack.mulPose(Axis.XP.rotationDegrees(BlockPoseTweaker.getBackRot(0)));
        poseStack.mulPose(Axis.YP.rotationDegrees(BlockPoseTweaker.getBackRot(1)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(BlockPoseTweaker.getBackRot(2)));
        poseStack.translate(
                BlockPoseTweaker.getBackPos(0),
                BlockPoseTweaker.getBackPos(1),
                BlockPoseTweaker.getBackPos(2)
        );

        applyBackWeaponTransform(poseStack, weaponType);

        scratchState.submit(poseStack, collector, packedLight, 0, 0);

        poseStack.popPose();
    }

    private static void applyBackWeaponTransform(PoseStack poseStack, WeaponType weaponType) {
        // Baked from back_rot/back_pos tweaker — 剑和矛通用
        poseStack.mulPose(Axis.ZP.rotationDegrees(35.0f));
        poseStack.translate(0.0f, 0.35f, 0.0f);

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
