package org.example.mod_1.mod_1.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.example.mod_1.mod_1.combat.client.CombatAvatarRenderer;
import org.example.mod_1.mod_1.combat.client.CombatRendererManager;
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
        if (entity instanceof AbstractClientPlayer) {
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
        if (state instanceof AvatarRenderState) {
            CombatAvatarRenderer renderer = CombatRendererManager.getRenderer();
            if (renderer != null) {
                cir.setReturnValue(renderer);
            }
        }
    }
}
