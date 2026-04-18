package org.example.mod_1.mod_1.combat;

// 格挡减伤 + 完美格挡(Parry) + 箭矢反弹
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.example.mod_1.mod_1.Config;
import org.example.mod_1.mod_1.Mod_1;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Mod_1.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CombatDefenseHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
            CombatState state = cap.getState();

            if (state == CombatState.DODGE && cap.getDodgeInvulnTicks() > 0) {
                event.setAmount(0);
                LOGGER.debug("DODGE i-frame: {} avoided damage", player.getName().getString());
                return;
            }

            if (state == CombatState.BLOCK || state == CombatState.PARRY) {
                if (!isFromFront(player, event.getSource())) return;

                if (cap.getParryWindowTicks() > 0) {
                    event.setAmount(0);
                    cap.setState(CombatState.PARRY);
                    cap.setStateTimer(CombatState.PARRY.getDurationTicks());
                    CombatCapabilityEvents.broadcastCombatState(player, cap);
                    CombatSoundPlayer.playParrySound(player);

                    Entity attacker = event.getSource().getEntity();
                    if (attacker instanceof LivingEntity living) {
                        living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Config.parrySlownessDuration, Config.parrySlownessAmplifier));
                        living.knockback(0.5f,
                                player.getX() - living.getX(),
                                player.getZ() - living.getZ());
                    }
                    if (player.level() instanceof ServerLevel sl) {
                        CombatParticles.spawnParryParticles(sl, player.getEyePosition());
                    }
                    LOGGER.debug("PARRY! {} blocked damage from {}",
                            player.getName().getString(), event.getSource().getMsgId());
                } else {
                    float reduced = event.getAmount() * (1.0f - (float) Config.blockDamageReduction);
                    event.setAmount(reduced);
                    CombatSoundPlayer.playBlockSound(player);
                    LOGGER.debug("BLOCK: reduced to {} damage", reduced);
                }
            }
        });
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hitResult)) return;
        if (!(hitResult.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        Projectile projectile = event.getProjectile();

        CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
            if (cap.getState() == CombatState.BLOCK && cap.getParryWindowTicks() > 0) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);

                cap.setState(CombatState.PARRY);
                cap.setStateTimer(CombatState.PARRY.getDurationTicks());
                CombatCapabilityEvents.broadcastCombatState(player, cap);
                CombatSoundPlayer.playParrySound(player);

                if (projectile instanceof AbstractArrow arrow && player.level() instanceof ServerLevel serverLevel) {
                    Vec3 lookVec = player.getLookAngle();
                    Entity reflected = arrow.getType().create(serverLevel, EntitySpawnReason.TRIGGERED);
                    if (reflected instanceof AbstractArrow reflectedArrow) {
                        reflectedArrow.setPos(player.getEyePosition());
                        reflectedArrow.shoot(lookVec.x, lookVec.y, lookVec.z,
                                (float) arrow.getDeltaMovement().length() * 1.2f, 0.0f);
                        reflectedArrow.setOwner(player);
                        reflectedArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                        serverLevel.addFreshEntity(reflectedArrow);
                    }
                    arrow.discard();
                    CombatParticles.spawnParryParticles(serverLevel, player.getEyePosition());
                } else if (player.level() instanceof ServerLevel serverLevel) {
                    CombatParticles.spawnParryParticles(serverLevel, player.getEyePosition());
                }

                LOGGER.debug("PARRY REFLECT: {} reflected projectile", player.getName().getString());
            }
        });
    }

    private static boolean isFromFront(Player player, DamageSource source) {
        if (source.getSourcePosition() == null) return true;
        Vec3 attackDir = source.getSourcePosition().subtract(player.getEyePosition()).normalize();
        Vec3 lookDir = player.getLookAngle();
        double dot = attackDir.x * lookDir.x + attackDir.z * lookDir.z;
        double cosThreshold = Math.cos(Math.toRadians(Config.blockAngle / 2.0));
        return dot >= cosThreshold;
    }
}
