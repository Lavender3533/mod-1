package org.example.combatarts.combat.client.render.mesh;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TriangulatedRenderType {

    private static final Map<Identifier, RenderType> CACHE = new ConcurrentHashMap<>();
    private static RenderPipeline TRIANGLE_PIPELINE;

    public static boolean isTriangleMode() {
        return TRIANGLE_PIPELINE != null;
    }

    static {
        try {
            RenderType sample = RenderTypes.entityCutoutNoCull(Identifier.parse("minecraft:textures/entity/steve.png"));
            RenderPipeline orig = sample.pipeline();

            // Builder 是 package-private 构造函数, 用反射创建
            Constructor<RenderPipeline.Builder> ctor = RenderPipeline.Builder.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            RenderPipeline.Builder b = ctor.newInstance();

            Field vertexShaderF = RenderPipeline.class.getDeclaredField("vertexShader");
            Field fragmentShaderF = RenderPipeline.class.getDeclaredField("fragmentShader");
            Field vertexFormatF = RenderPipeline.class.getDeclaredField("vertexFormat");
            Field depthTestF = RenderPipeline.class.getDeclaredField("depthTestFunction");
            Field cullF = RenderPipeline.class.getDeclaredField("cull");
            Field writeColorF = RenderPipeline.class.getDeclaredField("writeColor");
            Field writeAlphaF = RenderPipeline.class.getDeclaredField("writeAlpha");
            Field writeDepthF = RenderPipeline.class.getDeclaredField("writeDepth");
            Field blendF = RenderPipeline.class.getDeclaredField("blendFunction");
            Field samplersF = RenderPipeline.class.getDeclaredField("samplers");
            Field uniformsF = RenderPipeline.class.getDeclaredField("uniforms");

            for (Field f : new Field[]{vertexShaderF, fragmentShaderF, vertexFormatF, depthTestF,
                    cullF, writeColorF, writeAlphaF, writeDepthF, blendF, samplersF, uniformsF}) {
                f.setAccessible(true);
            }

            b.withLocation("combat_arts:entity_triangles")
             .withVertexShader((Identifier) vertexShaderF.get(orig))
             .withFragmentShader((Identifier) fragmentShaderF.get(orig))
             .withVertexFormat((VertexFormat) vertexFormatF.get(orig), VertexFormat.Mode.TRIANGLES)
             .withDepthTestFunction((com.mojang.blaze3d.platform.DepthTestFunction) depthTestF.get(orig))
             .withCull(false)
             .withColorWrite((boolean) writeColorF.get(orig), (boolean) writeAlphaF.get(orig))
             .withDepthWrite((boolean) writeDepthF.get(orig));

            @SuppressWarnings("unchecked")
            java.util.Optional<com.mojang.blaze3d.pipeline.BlendFunction> blend =
                    (java.util.Optional<com.mojang.blaze3d.pipeline.BlendFunction>) blendF.get(orig);
            blend.ifPresent(b::withBlend);

            @SuppressWarnings("unchecked")
            java.util.List<String> samplers = (java.util.List<String>) samplersF.get(orig);
            for (String s : samplers) b.withSampler(s);

            @SuppressWarnings("unchecked")
            java.util.List<RenderPipeline.UniformDescription> uniforms =
                    (java.util.List<RenderPipeline.UniformDescription>) uniformsF.get(orig);
            for (RenderPipeline.UniformDescription u : uniforms) {
                b.withUniform(u.name(), u.type());
            }

            TRIANGLE_PIPELINE = b.build();
            com.mojang.logging.LogUtils.getLogger().info("[TriangulatedRenderType] TRIANGLES pipeline created");
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("[TriangulatedRenderType] Failed, falling back to QUADS", e);
            TRIANGLE_PIPELINE = null;
        }
    }

    public static RenderType entityTriangles(Identifier texture) {
        if (TRIANGLE_PIPELINE == null) {
            return RenderTypes.entityCutoutNoCull(texture);
        }
        return CACHE.computeIfAbsent(texture, tex -> {
            RenderSetup setup = RenderSetup.builder(TRIANGLE_PIPELINE)
                    .withTexture("Sampler0", tex)
                    .useLightmap()
                    .useOverlay()
                    .createRenderSetup();
            return RenderType.create("combat_entity_triangles", setup);
        });
    }
}
