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
}
