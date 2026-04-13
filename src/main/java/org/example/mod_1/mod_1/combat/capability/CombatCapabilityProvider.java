package org.example.mod_1.mod_1.combat.capability;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CombatCapabilityProvider implements ICapabilitySerializable<CompoundTag> {

    private final CombatCapability instance = new CombatCapability();
    private final LazyOptional<ICombatCapability> optional = LazyOptional.of(() -> instance);

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return CombatCapabilityEvents.COMBAT_CAPABILITY.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return instance.serializeNBT();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        instance.deserializeNBT(tag);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
