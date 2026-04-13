package org.example.mod_1.mod_1.combat;

import com.mojang.logging.LogUtils;
import org.example.mod_1.mod_1.combat.capability.ICombatCapability;
import org.slf4j.Logger;

public class CombatStateMachine {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int COMBO_WINDOW_TICKS = 10; // 0.5s
    private static final int DODGE_COOLDOWN_TICKS = 20; // 1.0s

    public static boolean canTransition(ICombatCapability cap, CombatState target) {
        CombatState current = cap.getState();

        // Same state — no transition needed
        if (current == target) return target == CombatState.ATTACK_LIGHT; // allow combo re-entry

        // Non-interruptible state with remaining timer blocks transition
        if (!current.isInterruptible() && cap.getStateTimer() > 0) {
            return target.getPriority() > current.getPriority(); // only higher priority can override
        }

        // Attack requires weapon drawn
        if ((target == CombatState.ATTACK_LIGHT || target == CombatState.ATTACK_HEAVY) && !cap.isWeaponDrawn()) {
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
                cap.setWeaponDrawn(true);
                cap.setState(CombatState.IDLE); // instant, go straight to IDLE
            }
            case SHEATH_WEAPON -> {
                cap.setWeaponDrawn(false);
                cap.resetCombo();
                cap.setState(CombatState.IDLE); // instant
            }
            case ATTACK_LIGHT -> handleLightAttack(cap);
            case DODGE -> cap.setDodgeCooldown(DODGE_COOLDOWN_TICKS);
            case BLOCK -> cap.setParryWindowTicks(4); // first 4 ticks = parry window
            default -> {}
        }

        LOGGER.debug("Combat state: {} -> {} (combo={}, drawn={})",
                prev, target, cap.getComboCount(), cap.isWeaponDrawn());
    }

    private static void handleLightAttack(ICombatCapability cap) {
        int combo = cap.getComboCount() + 1;
        int maxCombo = cap.getWeaponType().getMaxCombo();

        if (combo > maxCombo) {
            combo = 1; // wrap around
        }

        cap.setComboCount(combo);
        cap.setLastAttackTime(0); // will be set to gameTime by tick caller
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
            if (state == CombatState.SHEATH_WEAPON) {
                cap.setWeaponDrawn(false);
                cap.resetCombo();
            }
            cap.setState(CombatState.IDLE);
        }

        // Combo timeout: reset if idle too long after last attack
        if (state == CombatState.IDLE && cap.getComboCount() > 0) {
            if (cap.getLastAttackTime() > 0 && gameTime - cap.getLastAttackTime() > COMBO_WINDOW_TICKS) {
                cap.resetCombo();
            }
        }
    }
}
