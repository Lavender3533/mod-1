package org.example.mod_1.mod_1.combat.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.example.mod_1.mod_1.Mod_1;
import org.lwjgl.glfw.GLFW;

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

    public static final KeyMapping RELOAD_ANIMATIONS = new KeyMapping(
            "key." + Mod_1.MODID + ".reload_animations",
            InputConstants.KEY_F10,
            CATEGORY
    );

    // === BLOCK 姿势实时调试 ===
    public static final KeyMapping POSE_CYCLE_BONE = new KeyMapping(
            "key." + Mod_1.MODID + ".pose_cycle_bone",
            GLFW.GLFW_KEY_LEFT_BRACKET,    // [
            CATEGORY
    );
    public static final KeyMapping POSE_CYCLE_AXIS = new KeyMapping(
            "key." + Mod_1.MODID + ".pose_cycle_axis",
            GLFW.GLFW_KEY_RIGHT_BRACKET,   // ]
            CATEGORY
    );
    public static final KeyMapping POSE_DECREASE = new KeyMapping(
            "key." + Mod_1.MODID + ".pose_decrease",
            GLFW.GLFW_KEY_COMMA,           // ,
            CATEGORY
    );
    public static final KeyMapping POSE_INCREASE = new KeyMapping(
            "key." + Mod_1.MODID + ".pose_increase",
            GLFW.GLFW_KEY_PERIOD,          // .
            CATEGORY
    );
    public static final KeyMapping POSE_PRINT = new KeyMapping(
            "key." + Mod_1.MODID + ".pose_print",
            GLFW.GLFW_KEY_SLASH,           // /
            CATEGORY
    );
    public static final KeyMapping POSE_RESET_ALL = new KeyMapping(
            "key." + Mod_1.MODID + ".pose_reset_all",
            GLFW.GLFW_KEY_APOSTROPHE,      // '
            CATEGORY
    );

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(COMBAT_TOGGLE);
        event.register(DODGE);
        event.register(INSPECT);
        event.register(HEAVY_ATTACK);
        event.register(RELOAD_ANIMATIONS);
        event.register(POSE_CYCLE_BONE);
        event.register(POSE_CYCLE_AXIS);
        event.register(POSE_DECREASE);
        event.register(POSE_INCREASE);
        event.register(POSE_PRINT);
        event.register(POSE_RESET_ALL);
    }
}
