// Extracted from Epic Fight mod (GPL v3) - adapted for Combat Arts
package org.example.combatarts.combat.client.render.mesh;

import java.util.function.Supplier;

public interface MeshPartDefinition {
	String partName();
	Mesh.RenderProperties renderProperties();
	Supplier<OpenMatrix4f> getModelPartAnimationProvider();
}
