package org.example.combatarts.combat;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Plays combat sounds on the server side (audible to all nearby players).
 */
public class CombatSoundPlayer {

    public static void playStateSound(Player player, CombatState state, WeaponType weapon, int combo) {
        SoundEvent sound = resolveSound(state, weapon, combo);
        if (sound != null) {
            float volume = resolveVolume(state);
            float pitch = resolvePitch(state, combo);
            player.level().playSound(
                    null, // null = play for all players nearby
                    player.getX(), player.getY(), player.getZ(),
                    sound, SoundSource.PLAYERS,
                    volume, pitch
            );
        }
    }

    public static void playHitSound(Player attacker) {
        attacker.level().playSound(
                null,
                attacker.getX(), attacker.getY(), attacker.getZ(),
                ModSounds.ATTACK_HIT.get(), SoundSource.PLAYERS,
                0.8f, 0.9f + attacker.getRandom().nextFloat() * 0.2f
        );
    }

    public static void playBlockSound(Player defender) {
        defender.level().playSound(
                null,
                defender.getX(), defender.getY(), defender.getZ(),
                ModSounds.BLOCK.get(), SoundSource.PLAYERS,
                1.0f, 0.8f + defender.getRandom().nextFloat() * 0.3f
        );
    }

    public static void playParrySound(Player defender) {
        defender.level().playSound(
                null,
                defender.getX(), defender.getY(), defender.getZ(),
                ModSounds.PARRY.get(), SoundSource.PLAYERS,
                1.2f, 1.5f // higher pitch for parry
        );
    }

    private static SoundEvent resolveSound(CombatState state, WeaponType weapon, int combo) {
        return switch (state) {
            case DRAW_WEAPON -> ModSounds.DRAW_WEAPON.get();
            case SHEATH_WEAPON -> ModSounds.SHEATH_WEAPON.get();
            case DODGE -> ModSounds.DODGE.get();
            case ATTACK_LIGHT -> resolveLightAttackSound(weapon, combo);
            case ATTACK_HEAVY -> weapon == WeaponType.SPEAR
                    ? ModSounds.SPEAR_HEAVY.get()
                    : ModSounds.SWORD_HEAVY.get();
            default -> null;
        };
    }

    private static SoundEvent resolveLightAttackSound(WeaponType weapon, int combo) {
        if (weapon == WeaponType.SPEAR) {
            return ModSounds.SPEAR_LIGHT.get();
        }
        if (combo == 99) {
            return ModSounds.SWORD_DASH.get();
        }
        return switch (combo) {
            case 2 -> ModSounds.SWORD_LIGHT_2.get();
            case 3 -> ModSounds.SWORD_LIGHT_3.get();
            default -> ModSounds.SWORD_LIGHT_1.get();
        };
    }

    private static float resolveVolume(CombatState state) {
        return switch (state) {
            case DRAW_WEAPON, SHEATH_WEAPON -> 0.6f;
            case DODGE -> 0.5f;
            case ATTACK_LIGHT -> 0.7f;
            case ATTACK_HEAVY -> 0.9f;
            default -> 0.5f;
        };
    }

    private static float resolvePitch(CombatState state, int combo) {
        return switch (state) {
            case ATTACK_LIGHT -> {
                // Slightly increase pitch with each combo hit for escalation feel
                float base = 0.9f;
                yield base + Math.min(combo, 3) * 0.1f;
            }
            case ATTACK_HEAVY -> 0.7f; // lower pitch for heavy
            case DODGE -> 1.2f;
            default -> 1.0f;
        };
    }
}
