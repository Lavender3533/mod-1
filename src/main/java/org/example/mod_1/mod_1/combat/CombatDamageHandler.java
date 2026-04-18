package org.example.mod_1.mod_1.combat;

// 服务端攻击判定 + 伤害计算
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.example.mod_1.mod_1.Config;
import org.example.mod_1.mod_1.Mod_1;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;
import org.example.mod_1.mod_1.combat.capability.ICombatCapability;
import org.slf4j.Logger;

import java.util.List;

@Mod.EventBusSubscriber(modid = Mod_1.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CombatDamageHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (!(event.player() instanceof ServerPlayer player)) return;

        CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
            CombatState state = cap.getState();
            int elapsed = state.getDurationTicks() - cap.getStateTimer();

            if (state == CombatState.ATTACK_LIGHT) {
                if (elapsed == Config.lightHitFrameStart) {
                    performHit(player, cap, false);
                }
            } else if (state == CombatState.ATTACK_HEAVY) {
                if (elapsed == Config.heavyHitFrameStart) {
                    performHit(player, cap, true);
                }
            }
        });
    }

    private static void performHit(ServerPlayer player, ICombatCapability cap, boolean heavy) {
        WeaponType weapon = cap.getWeaponType();
        float range = getRange(weapon, heavy);
        float angle = getAngle(weapon, heavy);
        float baseDamage = getBaseDamage(player);
        float multiplier = getDamageMultiplier(cap, heavy);

        float totalDamage = baseDamage * multiplier;
        float knockbackStrength = getKnockbackStrength(cap, heavy);

        List<Entity> targets = getEntitiesInArc(player, range, angle);
        DamageSource source = player.damageSources().playerAttack(player);

        for (Entity target : targets) {
            if (target instanceof LivingEntity living) {
                living.hurt(source, totalDamage);
                CombatSoundPlayer.playHitSound(player);

                double dx = player.getX() - living.getX();
                double dz = player.getZ() - living.getZ();
                living.knockback(knockbackStrength, dx, dz);

                if (player.level() instanceof ServerLevel serverLevel) {
                    Vec3 hitPos = living.position().add(0, living.getBbHeight() / 2.0, 0);
                    if (heavy) {
                        CombatParticles.spawnHeavyHitParticles(serverLevel, hitPos);
                    } else {
                        CombatParticles.spawnHitParticles(serverLevel, hitPos);
                    }
                }

                LOGGER.debug("Combat hit: {} -> {} for {} damage (combo={}, heavy={})",
                        player.getName().getString(), living.getName().getString(),
                        totalDamage, cap.getComboCount(), heavy);
            }
        }
    }

    private static float getKnockbackStrength(ICombatCapability cap, boolean heavy) {
        if (heavy) return 1.0f * cap.getHeavyChargeMultiplier();
        int combo = cap.getComboCount();
        if (combo == 99) return 0.8f;
        return switch (combo) {
            case 1 -> 0.3f;
            case 2 -> 0.4f;
            case 3 -> 0.6f;
            default -> 0.3f;
        };
    }

    private static float getBaseDamage(ServerPlayer player) {
        ItemStack weapon = player.getMainHandItem();
        // 使用武器的攻击伤害属性，包含材质加成
        return (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
    }

    private static float getDamageMultiplier(ICombatCapability cap, boolean heavy) {
        float mult = 1.0f;
        if (heavy) {
            mult = (float) Config.damageMultHeavy * cap.getHeavyChargeMultiplier();
        } else {
            int combo = cap.getComboCount();
            if (combo == 99) {
                mult = (float) Config.damageMultSprint; // 冲刺攻击
            } else {
                mult = switch (combo) {
                    case 1 -> (float) Config.damageMultComboOne;
                    case 2 -> (float) Config.damageMultComboTwo;
                    case 3 -> (float) Config.damageMultComboThree;
                    default -> (float) Config.damageMultComboOne;
                };
            }
        }
        return mult;
    }

    private static float getRange(WeaponType weapon, boolean heavy) {
        return switch (weapon) {
            case SWORD -> heavy ? (float) Config.swordHeavyRange : (float) Config.swordLightRange;
            case SPEAR -> heavy ? (float) Config.spearHeavyRange : (float) Config.spearLightRange;
            default -> 2.5f;
        };
    }

    private static float getAngle(WeaponType weapon, boolean heavy) {
        return switch (weapon) {
            case SWORD -> heavy ? (float) Config.swordHeavyAngle : (float) Config.swordLightAngle;
            case SPEAR -> heavy ? (float) Config.spearHeavyAngle : (float) Config.spearLightAngle;
            default -> 90.0f;
        };
    }

    private static List<Entity> getEntitiesInArc(ServerPlayer player, float range, float angleDeg) {
        AABB searchBox = player.getBoundingBox().inflate(range);
        Vec3 lookVec = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();
        double cosThreshold = Math.cos(Math.toRadians(angleDeg / 2.0));

        return player.level().getEntities(player, searchBox, entity -> {
            if (entity == player) return false;
            if (!(entity instanceof LivingEntity)) return false;
            Vec3 toEntity = entity.position().add(0, entity.getBbHeight() / 2, 0).subtract(eyePos);
            double dist = toEntity.length();
            if (dist > range || dist < 0.1) return false;
            Vec3 toEntityNorm = toEntity.normalize();
            double dot = lookVec.x * toEntityNorm.x + lookVec.y * toEntityNorm.y + lookVec.z * toEntityNorm.z;
            return dot >= cosThreshold;
        });
    }
}
