package org.example.mod_1.mod_1.combat.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces MC cube polygons with new ones that have correct per-face UV
 * from the geo.json. Uses GeckoLib's approach: swap U for non-mirrored cubes.
 */
public class GeoQuadRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float TEX_W = 64f;
    private static final float TEX_H = 64f;

    /**
     * Replace all polygon UVs on split body part cubes.
     * Each face gets new Polygon with same vertices but correct UV from geo.json.
     */
    public static void replacePolygonUVs(CombatPlayerModel model) {
        // geo.json per-face UV: {u, v, uSize, vSize}
        fix(model.waist, uv(
                new float[]{20,26,8,6}, new float[]{32,26,8,6},
                new float[]{28,26,4,6}, new float[]{16,26,4,6},
                new float[]{20,26,8,-4}, new float[]{28,16,8,4}));
        fix(model.chest, uv(
                new float[]{20,20,8,6}, new float[]{32,20,8,6},
                new float[]{28,20,4,6}, new float[]{16,20,4,6},
                new float[]{20,16,8,4}, new float[]{28,20,8,-4}));
        if (model.slim) {
            fix(model.rightUpperArm, uv(
                    new float[]{44,20,3,6}, new float[]{51,20,3,6},
                    new float[]{47,20,4,6}, new float[]{40,20,4,6},
                    new float[]{44,16,3,4}, new float[]{47,20,3,-4}));
            fix(model.rightLowerArm, uv(
                    new float[]{44,26,3,6}, new float[]{51,26,3,6},
                    new float[]{47,26,4,6}, new float[]{40,26,4,6},
                    new float[]{44,26,3,-4}, new float[]{47,16,3,4}));
            fix(model.leftUpperArm, uv(
                    new float[]{36,52,3,6}, new float[]{43,52,3,6},
                    new float[]{39,52,4,6}, new float[]{32,52,4,6},
                    new float[]{36,48,3,4}, new float[]{39,52,3,-4}));
            fix(model.leftLowerArm, uv(
                    new float[]{36,58,3,6}, new float[]{43,58,3,6},
                    new float[]{39,58,4,6}, new float[]{32,58,4,6},
                    new float[]{36,58,3,-4}, new float[]{39,48,3,4}));
            fix(model.rightSleeve, uv(
                    new float[]{44,36,3,6}, new float[]{51,36,3,6},
                    new float[]{47,36,4,6}, new float[]{40,36,4,6},
                    new float[]{44,32,3,4}, new float[]{47,36,3,-4}));
            fix(model.rightLowerSleeve, uv(
                    new float[]{44,42,3,6}, new float[]{51,42,3,6},
                    new float[]{47,42,4,6}, new float[]{40,42,4,6},
                    new float[]{44,42,3,-4}, new float[]{47,32,3,4}));
            fix(model.leftSleeve, uv(
                    new float[]{52,52,3,6}, new float[]{59,52,3,6},
                    new float[]{55,52,4,6}, new float[]{48,52,4,6},
                    new float[]{52,48,3,4}, new float[]{55,52,3,-4}));
            fix(model.leftLowerSleeve, uv(
                    new float[]{52,58,3,6}, new float[]{59,58,3,6},
                    new float[]{55,58,4,6}, new float[]{48,58,4,6},
                    new float[]{52,58,3,-4}, new float[]{55,48,3,4}));
        } else {
            fix(model.rightUpperArm, uv(
                    new float[]{44,20,4,6}, new float[]{52,20,4,6},
                    new float[]{48,20,4,6}, new float[]{40,20,4,6},
                    new float[]{44,16,4,4}, new float[]{48,20,4,-4}));
            fix(model.rightLowerArm, uv(
                    new float[]{44,26,4,6}, new float[]{52,26,4,6},
                    new float[]{48,26,4,6}, new float[]{40,26,4,6},
                    new float[]{44,26,4,-4}, new float[]{48,16,4,4}));
            fix(model.leftUpperArm, uv(
                    new float[]{36,52,4,6}, new float[]{44,52,4,6},
                    new float[]{40,52,4,6}, new float[]{32,52,4,6},
                    new float[]{36,48,4,4}, new float[]{40,52,4,-4}));
            fix(model.leftLowerArm, uv(
                    new float[]{36,58,4,6}, new float[]{44,58,4,6},
                    new float[]{40,58,4,6}, new float[]{32,58,4,6},
                    new float[]{36,58,4,-4}, new float[]{40,48,4,4}));
            fix(model.rightSleeve, uv(
                    new float[]{44,36,4,6}, new float[]{52,36,4,6},
                    new float[]{48,36,4,6}, new float[]{40,36,4,6},
                    new float[]{44,32,4,4}, new float[]{48,36,4,-4}));
            fix(model.rightLowerSleeve, uv(
                    new float[]{44,42,4,6}, new float[]{52,42,4,6},
                    new float[]{48,42,4,6}, new float[]{40,42,4,6},
                    new float[]{44,42,4,-4}, new float[]{48,32,4,4}));
            fix(model.leftSleeve, uv(
                    new float[]{52,52,4,6}, new float[]{60,52,4,6},
                    new float[]{56,52,4,6}, new float[]{48,52,4,6},
                    new float[]{52,48,4,4}, new float[]{56,52,4,-4}));
            fix(model.leftLowerSleeve, uv(
                    new float[]{52,58,4,6}, new float[]{60,58,4,6},
                    new float[]{56,58,4,6}, new float[]{48,58,4,6},
                    new float[]{52,58,4,-4}, new float[]{56,48,4,4}));
        }
        fix(model.rightUpperLeg, uv(
                new float[]{4,20,4,6}, new float[]{12,20,4,6},
                new float[]{8,20,4,6}, new float[]{0,20,4,6},
                new float[]{4,16,4,4}, new float[]{8,20,4,-4}));
        fix(model.rightLowerLeg, uv(
                new float[]{4,26,4,6}, new float[]{12,26,4,6},
                new float[]{8,26,4,6}, new float[]{0,26,4,6},
                new float[]{4,26,4,-4}, new float[]{8,16,4,4}));
        fix(model.leftUpperLeg, uv(
                new float[]{20,52,4,6}, new float[]{28,52,4,6},
                new float[]{24,52,4,6}, new float[]{16,52,4,6},
                new float[]{20,48,4,4}, new float[]{24,52,4,-4}));
        fix(model.leftLowerLeg, uv(
                new float[]{20,58,4,6}, new float[]{28,58,4,6},
                new float[]{24,58,4,6}, new float[]{16,58,4,6},
                new float[]{20,58,4,-4}, new float[]{24,48,4,4}));
    }

    private static Map<Direction, float[]> uv(float[] n, float[] s, float[] e, float[] w, float[] u, float[] d) {
        Map<Direction, float[]> m = new EnumMap<>(Direction.class);
        m.put(Direction.NORTH, n);
        m.put(Direction.SOUTH, s);
        m.put(Direction.EAST, e);
        m.put(Direction.WEST, w);
        m.put(Direction.UP, u);
        m.put(Direction.DOWN, d);
        return m;
    }

    /**
     * Replace polygon UVs on a single ModelPart's first cube.
     * Creates new Polygon objects with the existing vertex positions but correct UV.
     *
     * 用反射拿 ModelPart.cubes，避免依赖 ModelPartAccessor mixin（生产环境 mixin 配置不一定能加载上）。
     */
    private static Field CUBES_FIELD;
    static {
        try {
            CUBES_FIELD = ModelPart.class.getDeclaredField("cubes");
            CUBES_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            LOGGER.error("Cannot find ModelPart.cubes field via reflection", e);
        }
    }

    private static void fix(ModelPart part, Map<Direction, float[]> faceUVs) {
        List<ModelPart.Cube> cubes;
        try {
            @SuppressWarnings("unchecked")
            List<ModelPart.Cube> tmp = (List<ModelPart.Cube>) CUBES_FIELD.get(part);
            cubes = tmp;
        } catch (Exception e) {
            LOGGER.error("Failed to read ModelPart.cubes via reflection", e);
            return;
        }
        if (cubes.isEmpty()) return;

        ModelPart.Cube cube = cubes.get(0);

        for (int pi = 0; pi < cube.polygons.length; pi++) {
            ModelPart.Polygon polygon = cube.polygons[pi];
            Direction actualDir = dirFromNormal(polygon.normal());
            if (actualDir == null) continue;

            Direction uvDir = switch (actualDir) {
                case UP -> Direction.DOWN;
                case DOWN -> Direction.UP;
                default -> actualDir;
            };

            float[] faceUV = faceUVs.get(uvDir);
            if (faceUV == null) continue;

            // Calculate normalized UV
            float rawU1 = faceUV[0] / TEX_W;
            float rawV1 = faceUV[1] / TEX_H;
            float rawU2 = (faceUV[0] + faceUV[2]) / TEX_W;
            float rawV2 = (faceUV[1] + faceUV[3]) / TEX_H;

            // GeckoLib approach: swap U for non-mirrored cubes (Bedrock convention)
            float u = rawU2;
            float v = rawV1;
            float u2 = rawU1;
            float v2 = rawV2;

            // Build new vertices with same positions but correct UV
            ModelPart.Vertex[] oldVerts = polygon.vertices();
            ModelPart.Vertex[] newVerts = new ModelPart.Vertex[4];

            if (actualDir == Direction.UP) {
                float vHi = Math.max(rawV1, rawV2);
                float vLo = Math.min(rawV1, rawV2);
                newVerts[0] = oldVerts[0].remap(rawU2, vHi);
                newVerts[1] = oldVerts[1].remap(rawU1, vHi);
                newVerts[2] = oldVerts[2].remap(rawU1, vLo);
                newVerts[3] = oldVerts[3].remap(rawU2, vLo);
            } else if (actualDir == Direction.DOWN) {
                float vLo = Math.min(rawV1, rawV2);
                float vHi = Math.max(rawV1, rawV2);
                newVerts[0] = oldVerts[0].remap(rawU2, vLo);
                newVerts[1] = oldVerts[1].remap(rawU1, vLo);
                newVerts[2] = oldVerts[2].remap(rawU1, vHi);
                newVerts[3] = oldVerts[3].remap(rawU2, vHi);
            } else {
                newVerts[0] = oldVerts[0].remap(u, v);
                newVerts[1] = oldVerts[1].remap(u2, v);
                newVerts[2] = oldVerts[2].remap(u2, v2);
                newVerts[3] = oldVerts[3].remap(u, v2);
            }

            // Replace polygon with new one (simple constructor, no UV remapping)
            cube.polygons[pi] = new ModelPart.Polygon(newVerts, polygon.normal());

            LOGGER.info("Fixed {} face using {} UV: u={}-{}, v={}-{}", actualDir, uvDir,
                    String.format("%.0f", faceUV[0]), String.format("%.0f", faceUV[0]+faceUV[2]),
                    String.format("%.0f", faceUV[1]), String.format("%.0f", faceUV[1]+faceUV[3]));
        }
    }

    private static Direction dirFromNormal(Vector3fc n) {
        if (n.z() < -0.5f) return Direction.NORTH;
        if (n.z() > 0.5f) return Direction.SOUTH;
        if (n.x() > 0.5f) return Direction.EAST;
        if (n.x() < -0.5f) return Direction.WEST;
        if (n.y() > 0.5f) return Direction.UP;
        if (n.y() < -0.5f) return Direction.DOWN;
        return null;
    }
}
