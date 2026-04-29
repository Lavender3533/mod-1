// Extracted from Epic Fight mod (GPL v3) - adapted for Combat Arts
package org.example.combatarts.combat.client.render.mesh;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.world.phys.Vec3;

public abstract class StaticMesh<P extends MeshPart> implements Mesh {
	protected final float[] positions;
	protected final float[] normals;
	protected final float[] uvs;

	protected final int vertexCount;
	protected final Mesh.RenderProperties renderProperties;
	protected final Map<String, P> parts;

	/**
	 * @param arrayMap Null if parent is not null
	 * @param partBuilders Null if parent is not null
	 * @param parent Null if arrayMap and parts are not null
	 * @param renderProperties
	 */
	public StaticMesh(@Nullable Map<String, float[]> arrayMap, @Nullable Map<MeshPartDefinition, List<VertexBuilder>> partBuilders, @Nullable StaticMesh<P> parent, Mesh.RenderProperties renderProperties) {
		this.positions = (parent == null) ? arrayMap.get("positions") : parent.positions;
		this.normals = (parent == null) ? arrayMap.get("normals") : parent.normals;
		this.uvs = (parent == null) ? arrayMap.get("uvs") : parent.uvs;
		this.parts = (parent == null) ? this.createModelPart(partBuilders) : parent.parts;
		this.renderProperties = renderProperties;

		int totalV = 0;

		for (MeshPart modelpart : this.parts.values()) {
			totalV += modelpart.getVertices().size();
		}

		this.vertexCount = totalV;
	}

	protected abstract Map<String, P> createModelPart(Map<MeshPartDefinition, List<VertexBuilder>> partBuilders);
	protected abstract P getOrLogException(Map<String, P> parts, String name);

	public boolean hasPart(String part) {
		return this.parts.containsKey(part);
	}

	public MeshPart getPart(String part) {
		return this.parts.get(part);
	}

	public Collection<P> getAllParts() {
		return this.parts.values();
	}

	public Set<Map.Entry<String, P>> getPartEntry() {
		return this.parts.entrySet();
	}

	public Mesh.RenderProperties getRenderProperties() {
		return this.renderProperties;
	}

	public void getVertexPosition(int positionIndex, Vector4f dest) {
		int index = positionIndex * 3;
		dest.set(this.positions[index], this.positions[index + 1], this.positions[index + 2], 1.0F);
	}

	public void getVertexNormal(int normalIndex, Vector3f dest) {
		int index = normalIndex * 3;
		dest.set(this.normals[index], this.normals[index + 1], this.normals[index + 2]);
	}

	public void getVertexPosition(int positionIndex, Vector4f dest, @Nullable OpenMatrix4f[] poses) {
		this.getVertexPosition(positionIndex, dest);
	}

	public void getVertexNormal(int positionIndex, int normalIndex, Vector3f dest, @Nullable OpenMatrix4f[] poses) {
		this.getVertexNormal(normalIndex, dest);
	}

	public float[] positions() {
		return this.positions;
	}

	public float[] normals() {
		return this.normals;
	}

	public float[] uvs() {
		return this.uvs;
	}

	@Override
	public void initialize() {
		this.parts.values().forEach((part) -> part.setHidden(false));
	}
}
