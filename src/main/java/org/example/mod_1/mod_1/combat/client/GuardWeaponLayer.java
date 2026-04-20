package org.example.mod_1.mod_1.combat.client;

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
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.WeaponType;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;
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
            if (cap.getWeaponType() != WeaponType.SWORD) return;

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) return;

            itemModelResolver.updateForLiving(scratchState, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player);
            if (scratchState.isEmpty()) return;

            poseStack.pushPose();

            CombatPlayerModel model = this.getParentModel();
            model.root.translateAndRotate(poseStack);
            model.hip.translateAndRotate(poseStack);
            model.waist.translateAndRotate(poseStack);
            model.chest.translateAndRotate(poseStack);
            model.rightUpperArm.translateAndRotate(poseStack);
            model.rightLowerArm.translateAndRotate(poseStack);
            model.rightHand.translateAndRotate(poseStack);
            model.weaponMount.translateAndRotate(poseStack);

            // THIRD_PERSON_RIGHT_HAND already carries the held-item display transform.
            // Below is the BLOCK-specific guard correction baseline (调到满意后烘焙的值)。
            poseStack.mulPose(Axis.YP.rotationDegrees(-10.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-8.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-20.0F));   // baked from sword_rot
            poseStack.translate(-0.11F, 0.01F, 0.01F);             // baked from sword_pos
            // baked from sword_blade_roll = -305° around blade axis
            poseStack.mulPose(new Quaternionf().rotateAxis(
                    (float) Math.toRadians(-305.0F), 0.0F, 0.574F, -0.819F));

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
