// Extracted from Epic Fight mod (GPL v3) - adapted for Combat Arts
package org.example.combatarts.combat.client.render.mesh;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkinnedMesh extends StaticMesh<SkinnedMesh.SkinnedMeshPart> {
	private static final Logger LOGGER = LoggerFactory.getLogger("CombatArts");

	protected final float[] weights;
	protected final int[] affectingJointCounts;
	protected final int[][] affectingWeightIndices;
	protected final int[][] affectingJointIndices;

	private final int maxJointCount;

	public SkinnedMesh(@Nullable Map<String, float[]> arrayMap, @Nullable Map<MeshPartDefinition, List<VertexBuilder>> partBuilders, @Nullable SkinnedMesh parent, Mesh.RenderProperties properties) {
		super(arrayMap, partBuilders, parent, properties);

		if (parent != null) {
			this.weights = parent.weights;
			this.affectingJointCounts = parent.affectingJointCounts;
			this.affectingJointIndices = parent.affectingJointIndices;
			this.affectingWeightIndices = parent.affectingWeightIndices;
		} else {
			this.weights = arrayMap.get("weights");
			this.affectingJointCounts = toIntArray(arrayMap.get("vcounts"));

			float[] vindicesFloat = arrayMap.get("vindices");
			int[] vindices = toIntArray(vindicesFloat);
			this.affectingJointIndices = new int[this.affectingJointCounts.length][];
			this.affectingWeightIndices = new int[this.affectingJointCounts.length][];
			int idx = 0;

			for (int i = 0; i < this.affectingJointCounts.length; i++) {
				int count = this.affectingJointCounts[i];
				int[] jointId = new int[count];
				int[] weightIdx = new int[count];

				for (int j = 0; j < count; j++) {
					jointId[j] = vindices[idx * 2];
					weightIdx[j] = vindices[idx * 2 + 1];
					idx++;
				}

				this.affectingJointIndices[i] = jointId;
				this.affectingWeightIndices[i] = weightIdx;
			}
		}

		int maxJointId = 0;

		for (int[] i : this.affectingJointIndices) {
			for (int j : i) {
				if (maxJointId < j) {
					maxJointId = j;
				}
			}
		}

		this.maxJointCount = maxJointId;
	}

	/** Helper to convert float[] to int[] (for arrays parsed from JSON as floats) */
	private static int[] toIntArray(float[] floats) {
		int[] result = new int[floats.length];
		for (int i = 0; i < floats.length; i++) {
			result[i] = (int) floats[i];
		}
		return result;
	}

	@Override
	protected Map<String, SkinnedMeshPart> createModelPart(Map<MeshPartDefinition, List<VertexBuilder>> partBuilders) {
		Map<String, SkinnedMeshPart> parts = Maps.newHashMap();

		partBuilders.forEach((partDefinition, vertexBuilder) -> {
			parts.put(partDefinition.partName(), new SkinnedMeshPart(vertexBuilder, partDefinition.renderProperties(), partDefinition.getModelPartAnimationProvider()));
		});

		return parts;
	}

	@Override
	protected SkinnedMeshPart getOrLogException(Map<String, SkinnedMeshPart> parts, String name) {
		if (!parts.containsKey(name)) {
			LOGGER.debug("Cannot find the mesh part named " + name + " in " + this.getClass().getCanonicalName());
			return null;
		}
		return parts.get(name);
	}

	private static final Vec4f TRANSFORM = new Vec4f();
	private static final Vec4f POS = new Vec4f();
	private static final Vec4f TOTAL_POS = new Vec4f();

	@Override
	public void getVertexPosition(int positionIndex, Vector4f dest, @Nullable OpenMatrix4f[] poses) {
		int index = positionIndex * 3;

		POS.set(this.positions[index], this.positions[index + 1], this.positions[index + 2], 1.0F);
		TOTAL_POS.set(0.0F, 0.0F, 0.0F, 0.0F);

		for (int i = 0; i < this.affectingJointCounts[positionIndex]; i++) {
			int jointIndex = this.affectingJointIndices[positionIndex][i];
			int weightIndex = this.affectingWeightIndices[positionIndex][i];
			float weight = this.weights[weightIndex];

			Vec4f.add(OpenMatrix4f.transform(poses[jointIndex], POS, TRANSFORM).scale(weight), TOTAL_POS, TOTAL_POS);
		}

		dest.set(TOTAL_POS.x, TOTAL_POS.y, TOTAL_POS.z, 1.0F);
	}

	private static final Vec4f NORM = new Vec4f();
	private static final Vec4f TOTAL_NORM = new Vec4f();

	@Override
	public void getVertexNormal(int positionIndex, int normalIndex, Vector3f dest, @Nullable OpenMatrix4f[] poses) {
		int index = normalIndex * 3;
		NORM.set(this.normals[index], this.normals[index + 1], this.normals[index + 2], 1.0F);
		TOTAL_NORM.set(0.0F, 0.0F, 0.0F, 0.0F);

		for (int i = 0; i < this.affectingJointCounts[positionIndex]; i++) {
			int jointIndex = this.affectingJointIndices[positionIndex][i];
			int weightIndex = this.affectingWeightIndices[positionIndex][i];
			float weight = this.weights[weightIndex];
			Vec4f.add(OpenMatrix4f.transform(poses[jointIndex], NORM, TRANSFORM).scale(weight), TOTAL_NORM, TOTAL_NORM);
		}

		dest.set(TOTAL_NORM.x, TOTAL_NORM.y, TOTAL_NORM.z);
	}

	/**
	 * Draws the model without applying animation
	 */
	@Override
	public void draw(PoseStack poseStack, VertexConsumer bufferbuilder, Mesh.DrawingFunction drawingFunction, int packedLight, float r, float g, float b, float a, int overlay) {
		for (SkinnedMeshPart part : this.parts.values()) {
			part.draw(poseStack, bufferbuilder, drawingFunction, packedLight, r, g, b, a, overlay);
		}
	}

	protected static final Vector4f POSITION = new Vector4f();
	protected static final Vector3f NORMAL = new Vector3f();

	/**
	 * Draws the model with CPU skinning (posed)
	 */
	@Override
	public void drawPosed(PoseStack poseStack, VertexConsumer bufferbuilder, Mesh.DrawingFunction drawingFunction, int packedLight, float r, float g, float b, float a, int overlay, @Nullable Armature armature, OpenMatrix4f[] poses) {
		Matrix4f pose = poseStack.last().pose();
		Matrix3f normal = poseStack.last().normal();

		// Allocate per-call pose arrays for CPU skinning
		OpenMatrix4f[] totalPoses = new OpenMatrix4f[poses.length];
		OpenMatrix4f[] totalNormals = new OpenMatrix4f[poses.length];

		for (SkinnedMeshPart part : this.parts.values()) {
			if (!part.isHidden()) {
				OpenMatrix4f transform = part.getVanillaPartTransform();

				for (int i = 0; i < poses.length; i++) {
					totalPoses[i] = new OpenMatrix4f(poses[i]);

					if (armature != null) {
						totalPoses[i].mulBack(armature.searchJointById(i).getToOrigin());
					}

					if (transform != null) {
						totalPoses[i].mulBack(transform);
					}

					totalNormals[i] = totalPoses[i].removeTranslation();
				}

				List<VertexBuilder> verts = part.getVertices();
				for (int idx = 0; idx < verts.size(); idx++) {
					VertexBuilder vi = verts.get(idx);
					this.getVertexPosition(vi.position, POSITION, totalPoses);
					this.getVertexNormal(vi.position, vi.normal, NORMAL, totalNormals);

					POSITION.mul(pose);
					NORMAL.mul(normal);

					drawingFunction.draw(bufferbuilder, POSITION.x, POSITION.y, POSITION.z, NORMAL.x, NORMAL.y, NORMAL.z, packedLight, r, g, b, a, this.uvs[vi.uv * 2], this.uvs[vi.uv * 2 + 1], overlay);

					// Convert triangle to degenerate quad: duplicate every 3rd vertex
					if (idx % 3 == 2) {
						drawingFunction.draw(bufferbuilder, POSITION.x, POSITION.y, POSITION.z, NORMAL.x, NORMAL.y, NORMAL.z, packedLight, r, g, b, a, this.uvs[vi.uv * 2], this.uvs[vi.uv * 2 + 1], overlay);
					}
				}
			}
		}
	}

	/**
	 * Draws the model using CPU skinning (no compute shader path).
	 */
	@Override
	public void draw(PoseStack poseStack, MultiBufferSource bufferSources, RenderType renderType, Mesh.DrawingFunction drawingFunction, int packedLight, float r, float g, float b, float a, int overlay, @Nullable Armature armature, OpenMatrix4f[] poses) {
		this.drawPosed(poseStack, bufferSources.getBuffer(renderType), drawingFunction, packedLight, r, g, b, a, overlay, armature, poses);
	}

	/** Convenience overload matching the original EF API */
	public void draw(PoseStack poseStack, MultiBufferSource bufferSources, RenderType renderType, int packedLight, float r, float g, float b, float a, int overlay, @Nullable Armature armature, OpenMatrix4f[] poses) {
		this.draw(poseStack, bufferSources, renderType, Mesh.DrawingFunction.NEW_ENTITY, packedLight, r, g, b, a, overlay, armature, poses);
	}

	public int getMaxJointCount() {
		return this.maxJointCount;
	}

	public float[] weights() {
		return this.weights;
	}

	public int[] affectingJointCounts() {
		return this.affectingJointCounts;
	}

	public int[][] affectingWeightIndices() {
		return this.affectingWeightIndices;
	}

	public int[][] affectingJointIndices() {
		return this.affectingJointIndices;
	}

	public class SkinnedMeshPart extends MeshPart {

		public SkinnedMeshPart(List<VertexBuilder> animatedMeshPartList, @Nullable Mesh.RenderProperties renderProperties, @Nullable Supplier<OpenMatrix4f> vanillaPartTracer) {
			super(animatedMeshPartList, renderProperties, vanillaPartTracer);
		}

		@Override
		public void draw(PoseStack poseStack, VertexConsumer bufferBuilder, Mesh.DrawingFunction drawingFunction, int packedLight, float r, float g, float b, float a, int overlay) {
			if (this.isHidden()) {
				return;
			}

			Vector4f color = this.getColor(r, g, b, a);
			Matrix4f pose = poseStack.last().pose();
			Matrix3f normal = poseStack.last().normal();

			List<VertexBuilder> verts = this.getVertices();
			for (int idx = 0; idx < verts.size(); idx++) {
				VertexBuilder vi = verts.get(idx);
				getVertexPosition(vi.position, POSITION);
				getVertexNormal(vi.normal, NORMAL);
				POSITION.mul(pose);
				NORMAL.mul(normal);
				drawingFunction.draw(bufferBuilder, POSITION.x(), POSITION.y(), POSITION.z(), NORMAL.x(), NORMAL.y(), NORMAL.z(), packedLight, color.x, color.y, color.z, color.w, uvs[vi.uv * 2], uvs[vi.uv * 2 + 1], overlay);

				if (idx % 3 == 2) {
					drawingFunction.draw(bufferBuilder, POSITION.x(), POSITION.y(), POSITION.z(), NORMAL.x(), NORMAL.y(), NORMAL.z(), packedLight, color.x, color.y, color.z, color.w, uvs[vi.uv * 2], uvs[vi.uv * 2 + 1], overlay);
				}
			}
		}
	}
}
