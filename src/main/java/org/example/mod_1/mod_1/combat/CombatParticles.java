package org.example.mod_1.mod_1.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class CombatParticles {

    public static void spawnHitParticles(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.CRIT,
                pos.x, pos.y, pos.z,
                8,
                0.3, 0.3, 0.3,
                0.1);
    }

    public static void spawnHeavyHitParticles(ServerLevel level, Vec3 pos) {
        // 重击命中：更密的暴击粒子 + 一圈扫风
        level.sendParticles(ParticleTypes.CRIT,
                pos.x, pos.y, pos.z,
                18,
                0.5, 0.5, 0.5,
                0.25);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                pos.x, pos.y, pos.z,
                1,
                0.0, 0.0, 0.0,
                0.0);
    }

    public static void spawnParryParticles(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                pos.x, pos.y, pos.z,
                12,
                0.4, 0.4, 0.4,
                0.15);
    }

    public static void spawnDodgeParticles(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.CLOUD,
                pos.x, pos.y, pos.z,
                6,
                0.3, 0.05, 0.3,
                0.05);
    }

    public static void spawnBlockSparkParticles(ServerLevel level, Vec3 pos) {
        // 普通格挡命中：少量电火花,反馈"挡住了"
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y, pos.z,
                6,
                0.2, 0.2, 0.2,
                0.1);
    }

    public static void spawnHeavyChargeAura(ServerLevel level, Vec3 pos) {
        // 蓄力中身周环绕,周期性发射,告诉旁观者"在蓄力"
        level.sendParticles(ParticleTypes.ENCHANT,
                pos.x, pos.y + 0.5, pos.z,
                3,
                0.4, 0.4, 0.4,
                0.0);
    }

    public static void spawnHeavyChargeReady(ServerLevel level, Vec3 pos) {
        // 蓄满瞬间 burst,告诉玩家"可以放了"
        level.sendParticles(ParticleTypes.FLAME,
                pos.x, pos.y + 1.0, pos.z,
                10,
                0.3, 0.3, 0.3,
                0.05);
    }
}
