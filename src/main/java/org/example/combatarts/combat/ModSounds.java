package org.example.combatarts.combat;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.combatarts.CombatArts;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CombatArts.MODID);

    private static RegistryObject<SoundEvent> register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(CombatArts.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    // Combat sounds
    public static final RegistryObject<SoundEvent> DRAW_WEAPON = register("combat.draw_weapon");
    public static final RegistryObject<SoundEvent> SHEATH_WEAPON = register("combat.sheath_weapon");
    public static final RegistryObject<SoundEvent> SWORD_LIGHT_1 = register("combat.sword_light_1");
    public static final RegistryObject<SoundEvent> SWORD_LIGHT_2 = register("combat.sword_light_2");
    public static final RegistryObject<SoundEvent> SWORD_LIGHT_3 = register("combat.sword_light_3");
    public static final RegistryObject<SoundEvent> SWORD_HEAVY = register("combat.sword_heavy");
    public static final RegistryObject<SoundEvent> SWORD_DASH = register("combat.sword_dash");
    public static final RegistryObject<SoundEvent> SPEAR_LIGHT = register("combat.spear_light");
    public static final RegistryObject<SoundEvent> SPEAR_HEAVY = register("combat.spear_heavy");
    public static final RegistryObject<SoundEvent> ATTACK_HIT = register("combat.attack_hit");
    public static final RegistryObject<SoundEvent> DODGE = register("combat.dodge");
    public static final RegistryObject<SoundEvent> BLOCK = register("combat.block");
    public static final RegistryObject<SoundEvent> PARRY = register("combat.parry");
}
