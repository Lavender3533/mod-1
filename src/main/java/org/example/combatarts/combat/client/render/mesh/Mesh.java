// Extracted from Epic Fight mod (GPL v3) - adapted for Combat Arts
package org.example.combatarts.combat.client.render.mesh;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public interface Mesh {

	void initialize();

	/** Draw without mesh deformation */
	void draw(PoseStack poseStack, VertexConsumer vertexConsumer, Mesh.DrawingFunction drawingFunction, int packedLight, float r, float g, float b, float a, int overlay);

	/** Draw with mesh deformation */
	void drawPosed(PoseStack poseStack, VertexConsumer vertexConsumer, Mesh.DrawingFunction drawingFunction, int packedLight, float r, float g, float b, float a, int overlay, @Nullable Armature armature, OpenMatrix4f[] poses);

	/** Universal method - subclasses override for GPU path */
	default void draw(PoseStack poseStack, MultiBufferSource bufferSources, RenderType renderType, Mesh.DrawingFunction drawingFunction, int packedLight, float r, float g, float b, float a, int overlay, @Nullable Armature armature, OpenMatrix4f[] poses) {
		this.drawPosed(poseStack, bufferSources.getBuffer(renderType), drawingFunction, packedLight, r, g, b, a, overlay, armature, poses);
	}

	public static record RenderProperties(Identifier customTexturePath, Vec3f customColor, boolean isTransparent) {
		public static class Builder {
			protected String customTexturePath;
			protected Vec3f customColor = new Vec3f();
			protected boolean isTransparent;

			public RenderProperties.Builder customTexturePath(String path) {
				this.customTexturePath = path;
				return this;
			}

			public RenderProperties.Builder transparency(boolean isTransparent) {
				this.isTransparent = isTransparent;
				return this;
			}

			public RenderProperties.Builder customColor(float r, float g, float b) {
				this.customColor.x = r;
				this.customColor.y = g;
				this.customColor.z = b;
				return this;
			}

			public RenderProperties build() {
				return new RenderProperties(this.customTexturePath == null ? null : Identifier.parse(this.customTexturePath), this.customColor, this.isTransparent);
			}

			public static RenderProperties.Builder create() {
				return new RenderProperties.Builder();
			}
		}
	}

	@FunctionalInterface
	public interface DrawingFunction {
		public static final DrawingFunction NEW_ENTITY = (builder, posX, posY, posZ, normX, normY, normZ, packedLight, r, g, b, a, u, v, overlay) -> {
			builder.addVertex(posX, posY, posZ)
			        .setColor(r, g, b, a)
			        .setUv(u, v)
			        .setOverlay(overlay)
			        .setLight(packedLight)
			        .setNormal(normX, normY, normZ);
		};

		public static final DrawingFunction POSITION_TEX = (builder, posX, posY, posZ, normX, normY, normZ, packedLight, r, g, b, a, u, v, overlay) -> {
			builder.addVertex(posX, posY, posZ)
					.setUv(u, v);
		};

		public static final DrawingFunction POSITION_TEX_COLOR_NORMAL = (builder, posX, posY, posZ, normX, normY, normZ, packedLight, r, g, b, a, u, v, overlay) -> {
			builder.addVertex(posX, posY, posZ)
					.setUv(u, v)
					.setColor(r, g, b, a)
					.setNormal(normX, normY, normZ);
		};

		public static final DrawingFunction POSITION_COLOR_NORMAL = (builder, posX, posY, posZ, normX, normY, normZ, packedLight, r, g, b, a, u, v, overlay) -> {
			builder.addVertex(posX, posY, posZ)
					.setColor(r, g, b, a)
					.setNormal(normX, normY, normZ);
		};

		public void draw(VertexConsumer vertexConsumer, float posX, float posY, float posZ, float normX, float normY, float normZ, int packedLight, float r, float g, float b, float a, float u, float v, int overlay);
	}
}
