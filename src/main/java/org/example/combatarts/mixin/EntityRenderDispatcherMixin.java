package org.example.combatarts.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.example.combatarts.combat.client.CombatAvatarRenderer;
import org.example.combatarts.combat.client.CombatRendererManager;
import org.example.combatarts.combat.client.FlyingDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(
            method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T extends Entity> void mod1_swapCombatRenderer(T entity, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
        if (entity instanceof AbstractClientPlayer player) {
            if (FlyingDetector.isFlying(player)) {
                return;
            }
            CombatAvatarRenderer renderer = CombatRendererManager.getRenderer();
            if (renderer != null) {
                cir.setReturnValue(renderer);
            }
        }
    }

    @Inject(
            method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"),
            cancellable = true
    )
    private <S extends EntityRenderState> void mod1_swapCombatRendererByState(S state, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
        if (state instanceof AvatarRenderState avatarState) {
            Player player = resolvePlayer(avatarState);
            if (avatarState.isFallFlying || (player != null && FlyingDetector.isFlying(player))) {
                return;
            }
            CombatAvatarRenderer renderer = CombatRendererManager.getRenderer();
            if (renderer != null) {
                cir.setReturnValue(renderer);
            }
        }
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
