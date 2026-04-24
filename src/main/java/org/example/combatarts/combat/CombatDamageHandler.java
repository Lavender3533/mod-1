package org.example.combatarts.combat;

// 服务端攻击判定 + 伤害计算
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.example.combatarts.Config;
import org.example.combatarts.CombatArts;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;
import org.example.combatarts.combat.capability.ICombatCapability;
import org.slf4j.Logger;

import java.util.List;

@Mod.EventBusSubscriber(modid = CombatArts.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

        float ourDamage = baseDamage * multiplier;
        float ourKnockback = getKnockbackStrength(cap, heavy);

        ItemStack weaponStack = player.getMainHandItem();
        ServerLevel serverLevel = player.level() instanceof ServerLevel sl ? sl : null;

        List<Entity> targets = getEntitiesInArc(player, range, angle);
        DamageSource source = player.damageSources().playerAttack(player);

        // 附魔: Fire Aspect tick 数 (点燃目标用), 提前算一次, 所有目标共用
        int fireAspectLevel = serverLevel != null
                ? EnchantmentHelper.getItemEnchantmentLevel(holder(serverLevel, Enchantments.FIRE_ASPECT), weaponStack)
                : 0;

        boolean anyHit = false;
        for (Entity target : targets) {
            if (target instanceof LivingEntity living) {
                // 附魔伤害加成 (Sharpness/Smite/Bane of Arthropods 等), 由 vanilla EnchantmentHelper 处理
                float finalDamage = serverLevel != null
                        ? EnchantmentHelper.modifyDamage(serverLevel, weaponStack, living, source, ourDamage)
                        : ourDamage;

                if (serverLevel == null) continue;
                living.hurtServer(serverLevel, source, finalDamage);

                CombatSoundPlayer.playHitSound(player);

                // Knockback: 我们的固定击退 + Knockback 附魔加成 (vanilla 公式)
                float finalKnockback = serverLevel != null
                        ? EnchantmentHelper.modifyKnockback(serverLevel, weaponStack, living, source, ourKnockback)
                        : ourKnockback;
                double dx = player.getX() - living.getX();
                double dz = player.getZ() - living.getZ();
                living.knockback(finalKnockback, dx, dz);

                // Fire Aspect: 每级 4 秒
                if (fireAspectLevel > 0) {
                    living.igniteForSeconds(fireAspectLevel * 4);
                }

                if (serverLevel != null) {
                    Vec3 hitPos = living.position().add(0, living.getBbHeight() / 2.0, 0);
                    if (heavy) {
                        CombatParticles.spawnHeavyHitParticles(serverLevel, hitPos);
                    } else {
                        CombatParticles.spawnHitParticles(serverLevel, hitPos);
                    }
                }

                anyHit = true;
            }
        }

        // 耐久消耗
        if (anyHit && !weaponStack.isEmpty() && !player.isCreative() && serverLevel != null) {
            weaponStack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
    }

    private static Holder<Enchantment> holder(ServerLevel level, net.minecraft.resources.ResourceKey<Enchantment> key) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
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
        // Ray-vs-AABB 兜底:从眼睛沿视线推出 range 长度的"刺击线", 哪怕角度判定漏了也能命中。
        // 主要救场矛 (angle 30°) 这种瞄准但中心点偏一两度就失之千里的情况。
        Vec3 rayEnd = eyePos.add(lookVec.scale(range));

        return player.level().getEntities(player, searchBox, entity -> {
            if (entity == player) return false;
            if (!(entity instanceof LivingEntity)) return false;

            // 用包围盒最近点判定 — 比"实体中心"更准, 尤其对马/牛这类宽实体:
            // 它们 position() 是脚下中心, 中心到玩家眼睛的方向可能完全偏离实体本身,
            // 导致明明贴在你脸上的马也被算成"不在视锥内"。
            AABB box = entity.getBoundingBox();
            Vec3 closest = new Vec3(
                    net.minecraft.util.Mth.clamp(eyePos.x, box.minX, box.maxX),
                    net.minecraft.util.Mth.clamp(eyePos.y, box.minY, box.maxY),
                    net.minecraft.util.Mth.clamp(eyePos.z, box.minZ, box.maxZ)
            );
            Vec3 toClosest = closest.subtract(eyePos);
            double dist = toClosest.length();
            if (dist > range) return false;

            // 贴身距离 (< 0.6 格) 跳过角度检测 — 实体已经撞到玩家身上了, 不该因为"中心点偏一点"就漏判
            if (dist < 0.6) return true;

            // Ray-vs-AABB: 视线刺到包围盒, 距离不超过 range → 命中 (不用走角度判定)
            if (box.clip(eyePos, rayEnd).isPresent()) return true;

            // 锥形角度判定 (向最近点的方向)
            Vec3 toClosestNorm = toClosest.normalize();
            double dot = lookVec.x * toClosestNorm.x + lookVec.y * toClosestNorm.y + lookVec.z * toClosestNorm.z;
            return dot >= cosThreshold;
        });
    }
}
