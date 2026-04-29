package org.example.combatarts.combat.client.render.mesh;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class TriangulatedRenderType {
    public static RenderType entityTriangles(Identifier texture) {
        return RenderTypes.entityCutoutNoCull(texture);
    }
}
