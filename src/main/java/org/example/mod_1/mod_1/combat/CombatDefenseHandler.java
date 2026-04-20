package org.example.mod_1.mod_1.combat;

// 格挡减伤 + 完美格挡(Parry) + 箭矢反弹
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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

    /**
     * 在 hurt 之前拦截弹射物攻击 — LivingAttackEvent 取消(返回 true)后不会触发击退/红屏/受击音/无敌帧。
     * 仅处理弹射物;近战伤害仍走 LivingHurtEvent 走减伤。
     */
    @SubscribeEvent
    public static boolean onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return false;
        if (player.level().isClientSide()) return false;

        Entity directEntity = event.getSource().getDirectEntity();
        if (!(directEntity instanceof Projectile projectile)) return false;

        return CombatCapabilityEvents.getCombat(player).map(cap -> {
            CombatState state = cap.getState();
            if (state != CombatState.BLOCK && state != CombatState.PARRY) return false;
            if (!isProjectileFromFront(player, projectile)) return false;

            boolean isParry = cap.getParryWindowTicks() > 0;
            if (isParry) {
                cap.setState(CombatState.PARRY);
                cap.setStateTimer(CombatState.PARRY.getDurationTicks());
                CombatCapabilityEvents.broadcastCombatState(player, cap);
                CombatSoundPlayer.playParrySound(player);

                reflectProjectile(player, projectile);
                projectile.discard();

                if (projectile.getOwner() instanceof LivingEntity shooter) {
                    applyParryStun(player, shooter);
                }
                if (player.level() instanceof ServerLevel sl) {
                    CombatParticles.spawnParryParticles(sl, player.getEyePosition());
                }
                LOGGER.debug("PARRY (LivingAttack): {} reflected projectile", player.getName().getString());
            } else {
                deflectProjectile(player, projectile);
                CombatSoundPlayer.playBlockSound(player);
                if (player.level() instanceof ServerLevel sl) {
                    CombatParticles.spawnBlockSparkParticles(sl, player.getEyePosition());
                }
                LOGGER.debug("BLOCK (LivingAttack): {} deflected projectile {}",
                        player.getName().getString(), projectile.getType());
            }
            return true;
        }).orElse(false);
    }

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

                Entity directEntity = event.getSource().getDirectEntity();
                boolean isProjectile = directEntity instanceof Projectile;

                if (cap.getParryWindowTicks() > 0) {
                    event.setAmount(0);
                    cap.setState(CombatState.PARRY);
                    cap.setStateTimer(CombatState.PARRY.getDurationTicks());
                    CombatCapabilityEvents.broadcastCombatState(player, cap);
                    CombatSoundPlayer.playParrySound(player);

                    Entity attacker = event.getSource().getEntity();
                    if (attacker instanceof LivingEntity living) {
                        applyParryStun(player, living);
                    }

                    // 弹射物 parry：反弹箭矢、销毁原箭
                    if (isProjectile) {
                        Projectile proj = (Projectile) directEntity;
                        reflectProjectile(player, proj);
                        proj.discard();
                    }

                    if (player.level() instanceof ServerLevel sl) {
                        CombatParticles.spawnParryParticles(sl, player.getEyePosition());
                    }
                    LOGGER.debug("PARRY! {} blocked damage from {}",
                            player.getName().getString(), event.getSource().getMsgId());
                } else if (isProjectile) {
                    // 普通格挡 + 弹射物：弹开（不吃掉）
                    event.setAmount(0);
                    deflectProjectile(player, (Projectile) directEntity);
                    CombatSoundPlayer.playBlockSound(player);
                    if (player.level() instanceof ServerLevel sl) {
                        CombatParticles.spawnBlockSparkParticles(sl, player.getEyePosition());
                    }
                    LOGGER.debug("BLOCK projectile (LivingHurt path): {} deflected {}",
                            player.getName().getString(), directEntity.getType());
                } else {
                    // 普通格挡 + 近战：减伤
                    float reduced = event.getAmount() * (1.0f - (float) Config.blockDamageReduction);
                    event.setAmount(reduced);
                    CombatSoundPlayer.playBlockSound(player);
                    if (player.level() instanceof ServerLevel sl) {
                        CombatParticles.spawnBlockSparkParticles(sl, player.getEyePosition());
                    }
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
            CombatState state = cap.getState();
            if (state != CombatState.BLOCK && state != CombatState.PARRY) return;
            if (!isProjectileFromFront(player, projectile)) return;

            boolean isParry = cap.getParryWindowTicks() > 0;
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);

            if (isParry) {
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

                if (projectile.getOwner() instanceof LivingEntity shooter) {
                    applyParryStun(player, shooter);
                }

                LOGGER.debug("PARRY REFLECT: {} reflected projectile", player.getName().getString());
            } else {
                // 普通格挡：箭矢弹开（与原版盾牌一致，不吃掉）
                deflectProjectile(player, projectile);
                CombatSoundPlayer.playBlockSound(player);
                if (player.level() instanceof ServerLevel sl) {
                    CombatParticles.spawnBlockSparkParticles(sl, player.getEyePosition());
                }
                LOGGER.debug("BLOCK projectile: {} deflected {}", player.getName().getString(), projectile.getType());
            }
        });
    }

    private static void reflectProjectile(Player player, Projectile projectile) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (!(projectile instanceof AbstractArrow arrow)) return; // 反弹只支持箭类

        Vec3 lookVec = player.getLookAngle();
        Entity reflected = arrow.getType().create(serverLevel, EntitySpawnReason.TRIGGERED);
        if (reflected instanceof AbstractArrow reflectedArrow) {
            reflectedArrow.setPos(player.getEyePosition());
            float speed = Math.max(0.5f, (float) arrow.getDeltaMovement().length()) * 1.2f;
            reflectedArrow.shoot(lookVec.x, lookVec.y, lookVec.z, speed, 0.0f);
            reflectedArrow.setOwner(player);
            reflectedArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            serverLevel.addFreshEntity(reflectedArrow);
        }
    }

    /**
     * 普通格挡：箭矢弹开（不吃掉，模仿 vanilla 盾牌行为）。
     *
     * 实现注意：原箭命中后会立即进入 inGround 卡入状态，即使 setDeltaMovement 也会被
     * 自己的 tick 逻辑覆盖。所以这里 discard 原箭并 spawn 一支新箭，给新箭弹开速度。
     */
    private static void deflectProjectile(Player player, Projectile projectile) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (!(projectile instanceof AbstractArrow arrow)) {
            projectile.discard();
            return;
        }

        Vec3 vel = arrow.getDeltaMovement();
        Vec3 bounce;
        if (vel.lengthSqr() < 1.0e-3) {
            // 已减速到接近 0：向玩家朝向反方向 + 向上轻推
            Vec3 lookDir = player.getLookAngle();
            bounce = new Vec3(-lookDir.x * 0.35, 0.25, -lookDir.z * 0.35);
        } else {
            // 反向 + 衰减 60%，加向上偏置避免立刻坠地
            bounce = vel.scale(-0.4).add(0, 0.15, 0);
        }
        var rng = serverLevel.random;
        bounce = bounce.add(
                (rng.nextFloat() - 0.5f) * 0.15,
                (rng.nextFloat() - 0.5f) * 0.15,
                (rng.nextFloat() - 0.5f) * 0.15
        );

        // 用新箭代替原箭
        Entity spawned = arrow.getType().create(serverLevel, EntitySpawnReason.TRIGGERED);
        if (spawned instanceof AbstractArrow newArrow) {
            // 出生在玩家胸口稍前位置
            Vec3 spawnPos = player.getEyePosition().add(player.getLookAngle().scale(0.5));
            newArrow.setPos(spawnPos.x, spawnPos.y - 0.3, spawnPos.z);
            newArrow.setDeltaMovement(bounce);
            newArrow.setOwner(null);
            newArrow.pickup = AbstractArrow.Pickup.ALLOWED;
            serverLevel.addFreshEntity(newArrow);
        }
        arrow.discard();
    }

    private static void applyParryStun(Player player, LivingEntity attacker) {
        // 击退 + 强制 AI 停摆 + 减速。三层叠加确保即使非 Mob 子类(如其他玩家)也至少有击退/减速。
        attacker.knockback((float) Config.parryKnockback,
                player.getX() - attacker.getX(),
                player.getZ() - attacker.getZ());
        attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                Config.parrySlownessDuration, Config.parrySlownessAmplifier));
        if (attacker instanceof Mob mob) {
            mob.setNoActionTime(Config.parryStunTicks);
        }
    }

    private static boolean isFromFront(Player player, DamageSource source) {
        // 弹射物伤害用速度方向判定（命中时坐标常常贴在玩家身上，会让 normalize 退化）
        if (source.getDirectEntity() instanceof Projectile proj) {
            return isProjectileFromFront(player, proj);
        }
        if (source.getSourcePosition() == null) return true;
        Vec3 attackDir = source.getSourcePosition().subtract(player.getEyePosition()).normalize();
        Vec3 lookDir = player.getLookAngle();
        double dot = attackDir.x * lookDir.x + attackDir.z * lookDir.z;
        double cosThreshold = Math.cos(Math.toRadians(Config.blockAngle / 2.0));
        return dot >= cosThreshold;
    }

    private static boolean isProjectileFromFront(Player player, Projectile projectile) {
        Vec3 vel = projectile.getDeltaMovement();
        if (vel.lengthSqr() < 1.0e-6) return true; // 静止弹射物不判方向
        Vec3 incoming = vel.normalize();
        Vec3 lookDir = player.getLookAngle();
        // 箭沿 incoming 方向飞行；从前方袭来意味着 -incoming 与 look 方向同向
        double dot = -(incoming.x * lookDir.x + incoming.z * lookDir.z);
        double cosThreshold = Math.cos(Math.toRadians(Config.blockAngle / 2.0));
        return dot >= cosThreshold;
    }
}
