package org.example.mod_1.mod_1.combat.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.example.mod_1.mod_1.Mod_1;

public class CombatKeyBindings {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Mod_1.MODID, "combat"));

    public static final KeyMapping COMBAT_TOGGLE = new KeyMapping(
            "key." + Mod_1.MODID + ".combat_toggle",
            InputConstants.KEY_R,
            CATEGORY
    );

    public static final KeyMapping DODGE = new KeyMapping(
            "key." + Mod_1.MODID + ".dodge",
            InputConstants.KEY_LALT,
            CATEGORY
    );

    public static final KeyMapping INSPECT = new KeyMapping(
            "key." + Mod_1.MODID + ".inspect",
            InputConstants.KEY_V,
            CATEGORY
    );

    public static final KeyMapping HEAVY_ATTACK = new KeyMapping(
            "key." + Mod_1.MODID + ".heavy_attack",
            InputConstants.KEY_F,
            CATEGORY
    );

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(COMBAT_TOGGLE);
        event.register(DODGE);
        event.register(INSPECT);
        event.register(HEAVY_ATTACK);
    }
}
