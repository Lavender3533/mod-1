package org.example.combatarts.combat.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import org.example.combatarts.combat.CombatState;
import org.example.combatarts.combat.WeaponType;

@AutoRegisterCapability
public interface ICombatCapability {

    CombatState getState();
    void setState(CombatState state);

    WeaponType getWeaponType();
    void setWeaponType(WeaponType type);

    boolean isWeaponDrawn();
    void setWeaponDrawn(boolean drawn);

    int getComboCount();
    void setComboCount(int combo);
    void resetCombo();

    long getLastAttackTime();
    void setLastAttackTime(long tick);

    int getDodgeCooldown();
    void setDodgeCooldown(int ticks);

    int getStateTimer();
    void setStateTimer(int ticks);

    int getParryWindowTicks();
    void setParryWindowTicks(int ticks);

    int getDodgeInvulnTicks();
    void setDodgeInvulnTicks(int ticks);

    float getHeavyChargeMultiplier();
    void setHeavyChargeMultiplier(float mult);

    int getChargeTicks();
    void setChargeTicks(int ticks);

    boolean hasQueuedLightAttack();
    void setQueuedLightAttack(boolean queued);

    void tickTimers();

    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);
}
