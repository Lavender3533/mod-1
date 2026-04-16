package org.example.mod_1.mod_1.combat.capability;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.example.mod_1.mod_1.Mod_1;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.CombatStateMachine;
import org.slf4j.Logger;
import net.minecraft.world.phys.Vec3;

@Mod.EventBusSubscriber(modid = Mod_1.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CombatCapabilityEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Capability<ICombatCapability> COMBAT_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final Identifier CAP_KEY =
            Identifier.fromNamespaceAndPath(Mod_1.MODID, "combat");

    public static LazyOptional<ICombatCapability> getCombat(Player player) {
        return player.getCapability(COMBAT_CAPABILITY);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent.Entities event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(CAP_KEY, new CombatCapabilityProvider());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player newPlayer = event.getEntity();

        original.reviveCaps();
        getCombat(original).ifPresent(oldCap -> {
            getCombat(newPlayer).ifPresent(newCap -> {
                newCap.deserializeNBT(oldCap.serializeNBT());
            });
        });
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.side() == LogicalSide.SERVER) {
            getCombat(event.player()).ifPresent(cap -> {
                CombatStateMachine.tick(cap, event.player().level().getGameTime());

                if (cap.getState() == CombatState.DODGE) {
                    int elapsed = CombatState.DODGE.getDurationTicks() - cap.getStateTimer();
                    if (elapsed == 1) {
                        applyDodgeImpulse(event.player());
                    }
                }
            });
        }
    }

    public static void applyDodgeImpulse(Player player) {
        Vec3 moveInput = new Vec3(player.xxa, 0, player.zza);
        Vec3 direction;
        if (moveInput.lengthSqr() > 0.001) {
            float yawRad = (float) Math.toRadians(player.getYRot());
            double sin = Math.sin(yawRad);
            double cos = Math.cos(yawRad);
            direction = new Vec3(
                    moveInput.x * cos - moveInput.z * sin,
                    0,
                    moveInput.x * sin + moveInput.z * cos
            ).normalize();
        } else {
            // No movement input → dodge backward (away from look direction)
            float yawRad = (float) Math.toRadians(player.getYRot());
            direction = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
        }
        player.setDeltaMovement(direction.scale(0.8));
        player.hurtMarked = true;
    }
}
