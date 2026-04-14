package org.example.mod_1.mod_1.combat.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.example.mod_1.mod_1.mixin.ModelPartAccessor;
import org.joml.Vector3fc;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.List;

/**
 * Post-processes baked ModelPart cubes to apply exact per-face UV coordinates,
 * bypassing MC's texOffs() auto-UV which produces wrong top/bottom UVs on split cubes.
 *
 * UV values come directly from player_combat.geo.json (Bedrock per-face UV format).
 */
public class SkinUVFixer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float TEX_W = 64.0f;
    private static final float TEX_H = 64.0f;

    /**
     * Remap a specific face's UV on the first cube of a ModelPart.
     *
     * @param part  The baked ModelPart
     * @param dir   Which face direction to remap
     * @param u     Pixel U origin (top-left of the face region in texture)
     * @param v     Pixel V origin
     * @param uSize Width of the face region in pixels (positive = normal, negative = flipped)
     * @param vSize Height of the face region in pixels (positive = normal, negative = flipped)
     */
    public static void fixFace(ModelPart part, Direction dir, float u, float v, float uSize, float vSize) {
        List<ModelPart.Cube> cubes;
        try {
            cubes = ((ModelPartAccessor) (Object) part).getCubes();
        } catch (Exception e) {
            LOGGER.error("SkinUVFixer: Mixin accessor failed!", e);
            return;
        }
        if (cubes.isEmpty()) {
            LOGGER.warn("SkinUVFixer: No cubes found for direction {}", dir);
            return;
        }

        ModelPart.Cube cube = cubes.get(0);
        boolean found = false;
        for (ModelPart.Polygon polygon : cube.polygons) {
            if (matchesDirection(polygon.normal(), dir)) {
                float u1 = u / TEX_W;
                float u2 = (u + uSize) / TEX_W;
                float v1 = v / TEX_H;
                float v2 = (v + vSize) / TEX_H;

                ModelPart.Vertex[] verts = polygon.vertices();

                if (dir == Direction.UP) {
                    // MC's UP face (visual bottom) has V-flipped vertex ordering:
                    // vertex[0,1] get the HIGHER V, vertex[2,3] get the LOWER V
                    // Always ensure v_top > v_bottom for correct rendering
                    float vHi = Math.max(v1, v2);
                    float vLo = Math.min(v1, v2);
                    verts[0] = verts[0].remap(u2, vHi);
                    verts[1] = verts[1].remap(u1, vHi);
                    verts[2] = verts[2].remap(u1, vLo);
                    verts[3] = verts[3].remap(u2, vLo);
                } else if (dir == Direction.DOWN) {
                    // MC's DOWN face (visual top) has normal V ordering:
                    // vertex[0,1] get the LOWER V, vertex[2,3] get the HIGHER V
                    float vLo = Math.min(v1, v2);
                    float vHi = Math.max(v1, v2);
                    verts[0] = verts[0].remap(u2, vLo);
                    verts[1] = verts[1].remap(u1, vLo);
                    verts[2] = verts[2].remap(u1, vHi);
                    verts[3] = verts[3].remap(u2, vHi);
                } else {
                    // Side faces (N/S/E/W): standard mapping
                    verts[0] = verts[0].remap(u2, v1);
                    verts[1] = verts[1].remap(u1, v1);
                    verts[2] = verts[2].remap(u1, v2);
                    verts[3] = verts[3].remap(u2, v2);
                }
                found = true;
                return;
            }
        }
        if (!found) {
            // Log all polygon normals for debugging
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cube.polygons.length; i++) {
                Vector3fc n = cube.polygons[i].normal();
                sb.append(String.format("[%d]=(%.1f,%.1f,%.1f) ", i, n.x(), n.y(), n.z()));
            }
            LOGGER.warn("SkinUVFixer: No polygon matched direction {} in {} polygons: {}", dir, cube.polygons.length, sb);
        }
    }

    /**
     * Apply all 6 face UVs to a ModelPart cube.
     * Pass null for faces that should keep auto-UV (e.g., culled faces).
     */
    public static void fixAllFaces(ModelPart part,
                                   float[] north, float[] south,
                                   float[] east, float[] west,
                                   float[] up, float[] down) {
        if (north != null) fixFace(part, Direction.NORTH, north[0], north[1], north[2], north[3]);
        if (south != null) fixFace(part, Direction.SOUTH, south[0], south[1], south[2], south[3]);
        if (east != null) fixFace(part, Direction.EAST, east[0], east[1], east[2], east[3]);
        if (west != null) fixFace(part, Direction.WEST, west[0], west[1], west[2], west[3]);
        if (up != null) fixFace(part, Direction.UP, up[0], up[1], up[2], up[3]);
        if (down != null) fixFace(part, Direction.DOWN, down[0], down[1], down[2], down[3]);
    }

    private static boolean matchesDirection(Vector3fc normal, Direction dir) {
        // Compare polygon normal with direction unit vector
        // Normals are unit vectors along one axis
        return switch (dir) {
            case NORTH -> normal.z() < -0.5f;
            case SOUTH -> normal.z() > 0.5f;
            case EAST -> normal.x() > 0.5f;
            case WEST -> normal.x() < -0.5f;
            case UP -> normal.y() > 0.5f;
            case DOWN -> normal.y() < -0.5f;
        };
    }
}
