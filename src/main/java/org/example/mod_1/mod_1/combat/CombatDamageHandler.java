package org.example.mod_1.mod_1.combat;

// 服务端攻击判定 + 伤害计算
import com.mojang.logging.LogUtils;
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
import org.example.mod_1.mod_1.Mod_1;
import org.example.mod_1.mod_1.combat.capability.CombatCapabilityEvents;
import org.example.mod_1.mod_1.combat.capability.ICombatCapability;
import org.slf4j.Logger;

import java.util.List;

@Mod.EventBusSubscriber(modid = Mod_1.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CombatDamageHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final float SWORD_LIGHT_RANGE = 3.0f;
    private static final float SWORD_HEAVY_RANGE = 3.5f;
    private static final float SWORD_LIGHT_ANGLE = 90.0f;
    private static final float SWORD_HEAVY_ANGLE = 120.0f;

    private static final float SPEAR_LIGHT_RANGE = 4.5f;
    private static final float SPEAR_HEAVY_RANGE = 5.0f;
    private static final float SPEAR_LIGHT_ANGLE = 30.0f;
    private static final float SPEAR_HEAVY_ANGLE = 45.0f;

    private static final int LIGHT_HIT_FRAME_START = 3;
    private static final int LIGHT_HIT_FRAME_END = 5;
    private static final int HEAVY_HIT_FRAME_START = 8;
    private static final int HEAVY_HIT_FRAME_END = 12;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (!(event.player() instanceof ServerPlayer player)) return;

        CombatCapabilityEvents.getCombat(player).ifPresent(cap -> {
            CombatState state = cap.getState();
            int elapsed = state.getDurationTicks() - cap.getStateTimer();

            if (state == CombatState.ATTACK_LIGHT) {
                if (elapsed == LIGHT_HIT_FRAME_START) {
                    performHit(player, cap, false);
                }
            } else if (state == CombatState.ATTACK_HEAVY) {
                if (elapsed == HEAVY_HIT_FRAME_START) {
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

        List<Entity> targets = getEntitiesInArc(player, range, angle);
        DamageSource source = player.damageSources().playerAttack(player);

        for (Entity target : targets) {
            if (target instanceof LivingEntity living) {
                living.hurt(source, totalDamage);
                CombatSoundPlayer.playHitSound(player);
                LOGGER.debug("Combat hit: {} -> {} for {} damage (combo={}, heavy={})",
                        player.getName().getString(), living.getName().getString(),
                        totalDamage, cap.getComboCount(), heavy);
            }
        }
    }

    private static float getBaseDamage(ServerPlayer player) {
        ItemStack weapon = player.getMainHandItem();
        // 使用武器的攻击伤害属性，包含材质加成
        return (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
    }

    private static float getDamageMultiplier(ICombatCapability cap, boolean heavy) {
        float mult = 1.0f;
        if (heavy) {
            mult = 1.5f;
        } else {
            int combo = cap.getComboCount();
            if (combo == 99) {
                mult = 1.5f; // 冲刺攻击
            } else {
                mult = switch (combo) {
                    case 1 -> 1.0f;
                    case 2 -> 1.1f;
                    case 3 -> 1.3f;
                    default -> 1.0f;
                };
            }
        }
        return mult;
    }

    private static float getRange(WeaponType weapon, boolean heavy) {
        return switch (weapon) {
            case SWORD -> heavy ? SWORD_HEAVY_RANGE : SWORD_LIGHT_RANGE;
            case SPEAR -> heavy ? SPEAR_HEAVY_RANGE : SPEAR_LIGHT_RANGE;
            default -> 2.5f;
        };
    }

    private static float getAngle(WeaponType weapon, boolean heavy) {
        return switch (weapon) {
            case SWORD -> heavy ? SWORD_HEAVY_ANGLE : SWORD_LIGHT_ANGLE;
            case SPEAR -> heavy ? SPEAR_HEAVY_ANGLE : SPEAR_LIGHT_ANGLE;
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
