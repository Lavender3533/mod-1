package org.example.combatarts.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.example.combatarts.combat.client.CombatAnimationController;
import org.example.combatarts.combat.client.FlyingDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {

    private PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL"))
    private void mod1_applyComabatAnimation(AvatarRenderState state, CallbackInfo ci) {
        Player player = resolvePlayer(state);
        if (state.isFallFlying || (player != null && FlyingDetector.isFlying(player))) {
            return;
        }
        CombatAnimationController.applyToBones(this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg, state);
    }

    private static Player resolvePlayer(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity entity = mc.level.getEntity(state.id);
        return entity instanceof Player player ? player : null;
    }
}
