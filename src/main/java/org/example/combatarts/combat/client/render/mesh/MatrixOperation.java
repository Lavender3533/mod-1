// Extracted from Epic Fight mod (GPL v3) - adapted for Combat Arts
package org.example.combatarts.combat.client.render.mesh;

@FunctionalInterface
public interface MatrixOperation {
	OpenMatrix4f mul(OpenMatrix4f left, OpenMatrix4f right, OpenMatrix4f dest);
}
