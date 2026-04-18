package org.example.mod_1.mod_1.combat;

import com.mojang.logging.LogUtils;
import org.example.mod_1.mod_1.Config;
import org.example.mod_1.mod_1.combat.capability.ICombatCapability;
import org.slf4j.Logger;

public class CombatStateMachine {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean canTransition(ICombatCapability cap, CombatState target) {
        CombatState current = cap.getState();

        // Same state — no transition needed
        if (current == target) return target == CombatState.ATTACK_LIGHT; // allow combo re-entry

        // Non-interruptible state with remaining timer blocks transition
        if (!current.isInterruptible() && cap.getStateTimer() > 0) {
            return target.getPriority() > current.getPriority(); // only higher priority can override
        }

        // Attack requires weapon drawn
        if ((target == CombatState.ATTACK_LIGHT || target == CombatState.ATTACK_HEAVY
                || target == CombatState.ATTACK_HEAVY_CHARGING) && !cap.isWeaponDrawn()) {
            return false;
        }

        // Dodge requires cooldown expired
        if (target == CombatState.DODGE && cap.getDodgeCooldown() > 0) {
            return false;
        }

        // Draw/sheath weapon checks
        if (target == CombatState.DRAW_WEAPON && cap.isWeaponDrawn()) return false;
        if (target == CombatState.SHEATH_WEAPON && !cap.isWeaponDrawn()) return false;

        // Inspect requires weapon drawn
        if (target == CombatState.INSPECT && !cap.isWeaponDrawn()) return false;

        return true;
    }

    public static void requestTransition(ICombatCapability cap, CombatState target) {
        if (!canTransition(cap, target)) return;

        CombatState prev = cap.getState();
        cap.setState(target);

        if (target.isTimed()) {
            cap.setStateTimer(target.getDurationTicks());
        }

        switch (target) {
            case DRAW_WEAPON -> {
                // 拔刀动画播放中, timer到期后在tick()里setWeaponDrawn(true)+回IDLE
            }
            case SHEATH_WEAPON -> {
                // 收刀动画播放中, timer到期后在tick()里setWeaponDrawn(false)+回IDLE
            }
            case ATTACK_LIGHT -> handleLightAttack(cap);
            case ATTACK_HEAVY_CHARGING -> cap.setHeavyChargeMultiplier(1.0f); // reset; final value set on release
            case DODGE -> {
                cap.setDodgeCooldown(Config.dodgeCooldownTicks);
                cap.setDodgeInvulnTicks(Config.dodgeInvulnTicks);
            }
            case BLOCK -> cap.setParryWindowTicks(Config.parryWindowTicks); // first N ticks = parry window
            default -> {}
        }

        LOGGER.debug("Combat state: {} -> {} (combo={}, drawn={})",
                prev, target, cap.getComboCount(), cap.isWeaponDrawn());
    }

    private static void handleLightAttack(ICombatCapability cap) {
        if (cap.getWeaponType() == WeaponType.SWORD && cap.getComboCount() == 99) {
            cap.setLastAttackTime(0);
            return;
        }

        int combo = cap.getComboCount() + 1;
        int maxCombo = cap.getWeaponType().getMaxCombo();

        if (combo > maxCombo) {
            combo = 1; // wrap around
        }

        cap.setComboCount(combo);
        cap.setLastAttackTime(0); // will be set to gameTime by tick caller
    }

    /**
     * Maps held-ticks to a damage/knockback multiplier. 0 ticks → 1.0 (instant heavy);
     * scales linearly to {@link Config#heavyChargeMaxMult} at {@link Config#heavyChargeMaxTicks}.
     */
    public static float computeHeavyChargeMultiplier(int heldTicks) {
        if (heldTicks <= 0) return 1.0f;
        int max = Math.max(1, Config.heavyChargeMaxTicks);
        float t = Math.min(1.0f, (float) heldTicks / (float) max);
        return 1.0f + ((float) Config.heavyChargeMaxMult - 1.0f) * t;
    }

    public static void tick(ICombatCapability cap, long gameTime) {
        cap.tickTimers();

        CombatState state = cap.getState();

        // Update last attack time reference on light attack start
        if (state == CombatState.ATTACK_LIGHT && cap.getLastAttackTime() == 0) {
            cap.setLastAttackTime(gameTime);
        }

        // Timed state expired
        if (state.isTimed() && cap.getStateTimer() <= 0) {
            if (state == CombatState.DRAW_WEAPON) {
                cap.setWeaponDrawn(true);
            } else if (state == CombatState.SHEATH_WEAPON) {
                cap.setWeaponDrawn(false);
                cap.resetCombo();
            }
            cap.setState(CombatState.IDLE);
        }

        // Combo timeout: reset if idle too long after last attack
        if (state == CombatState.IDLE && cap.getComboCount() > 0) {
            if (cap.getLastAttackTime() > 0 && gameTime - cap.getLastAttackTime() > Config.comboWindowTicks) {
                cap.resetCombo();
            }
        }
    }
}
