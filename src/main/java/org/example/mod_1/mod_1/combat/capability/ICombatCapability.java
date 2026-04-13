package org.example.mod_1.mod_1.combat.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.WeaponType;

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

    void tickTimers();

    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);
}
