package org.example.combatarts.combat.client;

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
