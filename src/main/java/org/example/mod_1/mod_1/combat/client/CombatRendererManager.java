package org.example.mod_1.mod_1.combat.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class CombatRendererManager {
    private static CombatAvatarRenderer renderer;

    public static void init(EntityRendererProvider.Context context) {
        renderer = new CombatAvatarRenderer(context);
    }

    public static CombatAvatarRenderer getRenderer() {
        return renderer;
    }
}
