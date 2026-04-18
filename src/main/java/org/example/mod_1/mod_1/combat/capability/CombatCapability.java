package org.example.mod_1.mod_1.combat.capability;

import net.minecraft.nbt.CompoundTag;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.WeaponType;

public class CombatCapability implements ICombatCapability {

    private CombatState state = CombatState.IDLE;
    private WeaponType weaponType = WeaponType.UNARMED;
    private boolean weaponDrawn = false;
    private int comboCount = 0;
    private long lastAttackTime = 0;
    private int dodgeCooldown = 0;
    private int stateTimer = 0;
    private int parryWindowTicks = 0;
    private int dodgeInvulnTicks = 0;
    private float heavyChargeMultiplier = 1.0f;

    @Override public CombatState getState() { return state; }
    @Override public void setState(CombatState state) { this.state = state; }

    @Override public WeaponType getWeaponType() { return weaponType; }
    @Override public void setWeaponType(WeaponType type) { this.weaponType = type; }

    @Override public boolean isWeaponDrawn() { return weaponDrawn; }
    @Override public void setWeaponDrawn(boolean drawn) { this.weaponDrawn = drawn; }

    @Override public int getComboCount() { return comboCount; }
    @Override public void setComboCount(int combo) { this.comboCount = combo; }
    @Override public void resetCombo() { this.comboCount = 0; }

    @Override public long getLastAttackTime() { return lastAttackTime; }
    @Override public void setLastAttackTime(long tick) { this.lastAttackTime = tick; }

    @Override public int getDodgeCooldown() { return dodgeCooldown; }
    @Override public void setDodgeCooldown(int ticks) { this.dodgeCooldown = ticks; }

    @Override public int getStateTimer() { return stateTimer; }
    @Override public void setStateTimer(int ticks) { this.stateTimer = ticks; }

    @Override public int getParryWindowTicks() { return parryWindowTicks; }
    @Override public void setParryWindowTicks(int ticks) { this.parryWindowTicks = ticks; }

    @Override public int getDodgeInvulnTicks() { return dodgeInvulnTicks; }
    @Override public void setDodgeInvulnTicks(int ticks) { this.dodgeInvulnTicks = ticks; }

    @Override public float getHeavyChargeMultiplier() { return heavyChargeMultiplier; }
    @Override public void setHeavyChargeMultiplier(float mult) { this.heavyChargeMultiplier = mult; }

    @Override
    public void tickTimers() {
        if (stateTimer > 0) stateTimer--;
        if (dodgeCooldown > 0) dodgeCooldown--;
        if (parryWindowTicks > 0) parryWindowTicks--;
        if (dodgeInvulnTicks > 0) dodgeInvulnTicks--;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("state", state.ordinal());
        tag.putInt("weaponType", weaponType.ordinal());
        tag.putBoolean("weaponDrawn", weaponDrawn);
        tag.putInt("comboCount", comboCount);
        tag.putInt("dodgeCooldown", dodgeCooldown);
        tag.putInt("stateTimer", stateTimer);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        state = CombatState.fromOrdinal(tag.getIntOr("state", 0));
        weaponType = WeaponType.fromOrdinal(tag.getIntOr("weaponType", 0));
        weaponDrawn = tag.getBoolean("weaponDrawn").orElse(false);
        comboCount = tag.getIntOr("comboCount", 0);
        dodgeCooldown = tag.getIntOr("dodgeCooldown", 0);
        stateTimer = tag.getIntOr("stateTimer", 0);
    }
}
