package org.example.combatarts.combat.client;

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
import org.example.combatarts.combat.CombatState;
import org.example.combatarts.combat.WeaponType;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;
import org.example.combatarts.combat.client.render.mesh.*;
import org.joml.Quaternionf;

public class GuardWeaponLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {

    private final ItemModelResolver itemModelResolver;
    private final ItemStackRenderState scratchState = new ItemStackRenderState();

    public GuardWeaponLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent,
                            ItemModelResolver itemModelResolver) {
        super(parent);
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        Player player = resolvePlayer(state);
        if (player == null) return;

        var combatOpt = CombatCapabilityEvents.getCombat(player);
        if (!combatOpt.isPresent()) return;

        combatOpt.ifPresent(cap -> {
            CombatState combatState = cap.getState();
            if (!cap.isWeaponDrawn()) return;
            if (combatState != CombatState.BLOCK && combatState != CombatState.PARRY) return;
            // 剑/矛都用同一套 GuardWeaponLayer + 调试通道 (sword_rot/sword_pos/sword_blade_roll).
            // 想要分开的话以后可以拆出 SpearGuardWeaponLayer。
            if (cap.getWeaponType() != WeaponType.SWORD && cap.getWeaponType() != WeaponType.SPEAR) return;

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) return;

            itemModelResolver.updateForLiving(scratchState, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player);
            if (scratchState.isEmpty()) return;

            poseStack.pushPose();

            Armature armature = MeshManager.getArmature();
            if (armature != null && MeshManager.getMesh() != null && armature.hasJoint("Tool_R")) {
                Joint toolR = armature.searchJointByName("Tool_R");
                OpenMatrix4f jointMatrix = armature.getPoseMatrices()[toolR.getId()];
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                poseStack.translate(0.0, -1.501, 0.0);
                MathUtils.mulStack(poseStack, jointMatrix);
                OpenMatrix4f correction = new OpenMatrix4f()
                        .translate(0F, 0F, -0.13F)
                        .rotateDeg(-90.0F, Vec3f.X_AXIS);
                MathUtils.mulStack(poseStack, correction);
            } else {
                CombatPlayerModel model = this.getParentModel();
                model.root.translateAndRotate(poseStack);
                model.hip.translateAndRotate(poseStack);
                model.waist.translateAndRotate(poseStack);
                model.chest.translateAndRotate(poseStack);
                model.rightUpperArm.translateAndRotate(poseStack);
                model.rightLowerArm.translateAndRotate(poseStack);
                model.rightHand.translateAndRotate(poseStack);
                model.weaponMount.translateAndRotate(poseStack);
            }

            // THIRD_PERSON_RIGHT_HAND already carries the held-item display transform.
            // Below is the BLOCK-specific guard correction baseline (调到满意后烘焙的值)。
            // 注意: rotation 用 mulPose 矩阵串联, 不可像 Euler 角那样直接相加 — 烘焙 tweaker 时
            // 必须保留原 baseline 顺序 + 在末尾"追加"新 delta, 不能把度数合并到一行。
            poseStack.mulPose(Axis.YP.rotationDegrees(-15.0F));   // baseline Y (round 1)
            poseStack.mulPose(Axis.ZP.rotationDegrees(-13.0F));   // baseline Z (round 1)
            poseStack.mulPose(Axis.XP.rotationDegrees(-30.0F));   // baseline X (round 1)
            poseStack.translate(0.04F, 0.11F, 0.01F);             // baseline pos (round 1)
            poseStack.mulPose(new Quaternionf().rotateAxis(       // baseline blade_roll (round 1)
                    (float) Math.toRadians(-310.0F), 0.0F, 0.574F, -0.819F));

            // 烘焙第 2 轮 tweaker delta (剑专用, 顺序与 BlockPoseTweaker 完全一致):
            poseStack.mulPose(Axis.XP.rotationDegrees(-5.0F));    // sword_rot X delta
            poseStack.mulPose(Axis.YP.rotationDegrees(30.0F));    // sword_rot Y delta
            poseStack.mulPose(Axis.ZP.rotationDegrees(-20.0F));   // sword_rot Z delta
            poseStack.translate(0.15F, 0.0F, -0.10F);             // sword_pos delta
            poseStack.mulPose(new Quaternionf().rotateAxis(       // sword_blade_roll delta
                    (float) Math.toRadians(-10.0F), 0.0F, 0.574F, -0.819F));

            // Live tweaker — 叠加调试偏移（继续微调时使用，记得调完按 ' 重置）
            poseStack.mulPose(Axis.XP.rotationDegrees(BlockPoseTweaker.getSwordRot(0)));
            poseStack.mulPose(Axis.YP.rotationDegrees(BlockPoseTweaker.getSwordRot(1)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(BlockPoseTweaker.getSwordRot(2)));
            poseStack.translate(
                    BlockPoseTweaker.getSwordPos(0),
                    BlockPoseTweaker.getSwordPos(1),
                    BlockPoseTweaker.getSwordPos(2)
            );
            float bladeRoll = BlockPoseTweaker.getSwordBladeRoll();
            if (bladeRoll != 0.0F) {
                poseStack.mulPose(new Quaternionf().rotateAxis(
                        (float) Math.toRadians(bladeRoll), 0.0F, 0.574F, -0.819F));
            }

            scratchState.submit(poseStack, collector, packedLight, 0, 0);

            poseStack.popPose();
        });
    }

    private static Player resolvePlayer(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(state.id);
        return entity instanceof Player p ? p : null;
    }
}
