// Loads biped.json into Armature + SkinnedMesh for skinned player rendering
package org.example.combatarts.combat.client.render.mesh;

import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class MeshManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CombatArts");
    private static final Identifier BIPED_MODEL = Identifier.parse("combat_arts:models/entity/biped.json");

    /** Blender exports with Z-up; Minecraft uses Y-up.  -90 deg around X fixes it. */
    private static final OpenMatrix4f BLENDER_TO_MC = OpenMatrix4f.createRotatorDeg(-90.0F, Vec3f.X_AXIS);

    private static Armature armature;
    private static SkinnedMesh mesh;
    private static Map<String, Map<String, TransformSheet>> loadedAnims = Maps.newHashMap();

    private MeshManager() {}

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /** Called once during client setup to parse the model JSON. */
    public static void init() {
        try {
            JsonObject root = loadJson(BIPED_MODEL);
            if (root == null) {
                LOGGER.error("[MeshManager] Failed to load biped.json - resource not found");
                return;
            }
            armature = parseArmature(root.getAsJsonObject("armature"));
            mesh = parseMesh(root.getAsJsonObject("vertices"));
            LOGGER.info("[MeshManager] Loaded biped model: {} joints, {} mesh parts",
                    armature.getJointNumber(), mesh.getAllParts().size());

            loadEFAnimation("idle", "animations/biped/living/idle.json");
            loadEFAnimation("hold_longsword", "animations/biped/living/hold_longsword.json");
            loadEFAnimation("walk", "animations/biped/living/walk.json");
            loadEFAnimation("walk_longsword", "animations/biped/living/walk_longsword.json");
            loadEFAnimation("run", "animations/biped/living/run.json");
            loadEFAnimation("run_longsword", "animations/biped/living/run_longsword.json");
            loadEFAnimation("sneak", "animations/biped/living/sneak.json");
            LOGGER.info("[MeshManager] Loaded {} EF animations", loadedAnims.size());

            // Combat animations
            loadEFAnimation("draw_weapon", "animations/biped/combat/draw_weapon.json");
            loadEFAnimation("sheath_weapon", "animations/biped/combat/sheath_weapon.json");
            loadEFAnimation("sword_light_1", "animations/biped/combat/sword_auto1.json");
            loadEFAnimation("sword_light_2", "animations/biped/combat/sword_auto2.json");
            loadEFAnimation("sword_light_3", "animations/biped/combat/sword_auto3.json");
            loadEFAnimation("sword_dash", "animations/biped/combat/sword_dash.json");
            loadEFAnimation("dodge", "animations/biped/combat/step_backward.json");
            createBlockAnimation();        // 程序化格挡姿势(替换 EF guard_sword)
            createInspectAnimation();      // 程序化检视动画
            // 重击蓄力 hold：取自 EF steel_whirlwind_charging.json t=0 帧的右臂 5 关节，
            // 静态保持。比手写 Euler 准确（坐标系自动转换）。
            loadEFAnimation("sword_heavy_charge", "animations/biped/combat/sword_heavy_charge.json");
            loadEFAnimation("parry", "animations/biped/combat/guard_sword_hit.json");
            LOGGER.info("[MeshManager] Total animations: {}", loadedAnims.size());
        } catch (Exception e) {
            LOGGER.error("[MeshManager] Failed to load biped model", e);
        }
    }

    @Nullable
    public static Armature getArmature() {
        return armature;
    }

    @Nullable
    public static SkinnedMesh getMesh() {
        return mesh;
    }

    public static Pose getPoseAtTime(String animName, float time) {
        Map<String, TransformSheet> anim = loadedAnims.get(animName);
        if (anim == null) return new Pose();

        Pose pose = new Pose();
        for (Map.Entry<String, TransformSheet> entry : anim.entrySet()) {
            pose.putJointData(entry.getKey(), entry.getValue().getInterpolatedTransform(time));
        }
        return pose;
    }

    public static float getAnimLength(String animName) {
        Map<String, TransformSheet> anim = loadedAnims.get(animName);
        if (anim == null) return 1.0f;
        float max = 0;
        for (TransformSheet sheet : anim.values()) {
            Keyframe[] kfs = sheet.getKeyframes();
            if (kfs.length > 0) max = Math.max(max, kfs[kfs.length - 1].time());
        }
        return max > 0 ? max : 1.0f;
    }

    // ---------------------------------------------------------------
    // JSON loading
    // ---------------------------------------------------------------

    @Nullable
    private static JsonObject loadJson(Identifier id) {
        try {
            Resource resource = Minecraft.getInstance().getResourceManager()
                    .getResource(id).orElse(null);
            if (resource == null) return null;
            try (InputStream is = resource.open();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception e) {
            LOGGER.error("[MeshManager] Error reading {}", id, e);
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Armature parsing  (mirrors JsonAssetLoader.loadArmature)
    // ---------------------------------------------------------------

    private static Armature parseArmature(JsonObject armObj) {
        // Build joint-name -> id map from the "joints" array
        JsonArray jointNames = armObj.getAsJsonArray("joints");
        Map<String, Integer> jointIdMap = Maps.newLinkedHashMap();
        int nextId = 0;
        for (int i = 0; i < jointNames.size(); i++) {
            String name = jointNames.get(i).getAsString();
            jointIdMap.put(name, nextId++);
        }

        // Parse hierarchy (always a single-element array whose element is the root)
        JsonObject rootObj = armObj.getAsJsonArray("hierarchy").get(0).getAsJsonObject();
        Map<String, Joint> jointMap = Maps.newHashMap();
        Joint rootJoint = parseJoint(rootObj, jointIdMap, jointMap, true);

        // Compute inverse bind matrices
        rootJoint.initOriginTransform(new OpenMatrix4f());

        Armature arm = new Armature("biped", jointMap.size(), rootJoint, jointMap);
        return arm;
    }

    private static Joint parseJoint(JsonObject obj, Map<String, Integer> idMap,
                                    Map<String, Joint> jointMap, boolean isRoot) {
        String name = obj.get("name").getAsString();
        if (!idMap.containsKey(name)) {
            throw new IllegalStateException("Joint '" + name + "' not in armature joints list");
        }

        // Parse 16-element local-transform matrix (row-major in JSON)
        JsonArray xformArr = obj.getAsJsonArray("transform");
        float[] elements = new float[16];
        for (int i = 0; i < 16; i++) {
            elements[i] = xformArr.get(i).getAsFloat();
        }
        OpenMatrix4f localMatrix = OpenMatrix4f.load(null, elements);
        // JSON stores row-major; EF convention is column-major, so transpose
        localMatrix.transpose();

        if (isRoot) {
            localMatrix.mulFront(BLENDER_TO_MC);
        }

        Joint joint = new Joint(name, idMap.get(name), localMatrix);
        jointMap.put(name, joint);

        // Recurse children
        if (obj.has("children")) {
            for (JsonElement childElem : obj.getAsJsonArray("children")) {
                joint.addSubJoints(parseJoint(childElem.getAsJsonObject(), idMap, jointMap, false));
            }
        }

        return joint;
    }

    // ---------------------------------------------------------------
    // SkinnedMesh parsing  (mirrors JsonAssetLoader.loadSkinnedMesh)
    // ---------------------------------------------------------------

    private static SkinnedMesh parseMesh(JsonObject vertObj) {
        // --- positions (apply Blender->MC coord transform) ---
        float[] positions = toFloatArray(vertObj.getAsJsonObject("positions").getAsJsonArray("array"));
        for (int i = 0; i < positions.length / 3; i++) {
            int k = i * 3;
            Vec4f v = new Vec4f(positions[k], positions[k + 1], positions[k + 2], 1.0F);
            OpenMatrix4f.transform(BLENDER_TO_MC, v, v);
            positions[k]     = v.x;
            positions[k + 1] = v.y;
            positions[k + 2] = v.z;
        }

        // --- normals (same coord transform) ---
        float[] normals = toFloatArray(vertObj.getAsJsonObject("normals").getAsJsonArray("array"));
        for (int i = 0; i < normals.length / 3; i++) {
            int k = i * 3;
            Vec4f n = new Vec4f(normals[k], normals[k + 1], normals[k + 2], 1.0F);
            OpenMatrix4f.transform(BLENDER_TO_MC, n, n);
            normals[k]     = n.x;
            normals[k + 1] = n.y;
            normals[k + 2] = n.z;
        }

        // --- UVs ---
        float[] uvs = toFloatArray(vertObj.getAsJsonObject("uvs").getAsJsonArray("array"));

        // --- skinning weights data ---
        float[] weights  = toFloatArray(vertObj.getAsJsonObject("weights").getAsJsonArray("array"));
        float[] vcounts  = toFloatArray(vertObj.getAsJsonObject("vcounts").getAsJsonArray("array"));
        float[] vindices = toFloatArray(vertObj.getAsJsonObject("vindices").getAsJsonArray("array"));

        Map<String, float[]> arrayMap = Maps.newHashMap();
        arrayMap.put("positions", positions);
        arrayMap.put("normals",   normals);
        arrayMap.put("uvs",       uvs);
        arrayMap.put("weights",   weights);
        arrayMap.put("vcounts",   vcounts);
        arrayMap.put("vindices",  vindices);

        // --- mesh parts ---
        Map<MeshPartDefinition, List<VertexBuilder>> meshMap = Maps.newLinkedHashMap();
        JsonObject partsObj = vertObj.getAsJsonObject("parts");
        if (partsObj != null) {
            for (Map.Entry<String, JsonElement> entry : partsObj.entrySet()) {
                String partName = entry.getKey();
                JsonObject partJson = entry.getValue().getAsJsonObject();
                int[] indices = toIntArray(partJson.getAsJsonArray("array"));
                List<VertexBuilder> verts = VertexBuilder.create(indices);
                meshMap.put(simplePart(partName), verts);
            }
        }

        SkinnedMesh result = new SkinnedMesh(arrayMap, meshMap, null, null);
        result.initialize();
        return result;
    }

    // ---------------------------------------------------------------
    // Simple MeshPartDefinition implementation
    // ---------------------------------------------------------------

    private static MeshPartDefinition simplePart(String name) {
        return new MeshPartDefinition() {
            @Override public String partName() { return name; }
            @Override public Mesh.RenderProperties renderProperties() { return null; }
            @Override public Supplier<OpenMatrix4f> getModelPartAnimationProvider() { return null; }
        };
    }

    // ---------------------------------------------------------------
    // JSON array -> primitive array helpers
    // ---------------------------------------------------------------

    private static float[] toFloatArray(JsonArray arr) {
        float[] result = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.get(i).getAsFloat();
        }
        return result;
    }

    private static int[] toIntArray(JsonArray arr) {
        int[] result = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.get(i).getAsInt();
        }
        return result;
    }

    // ---------------------------------------------------------------
    // EF animation loading
    // ---------------------------------------------------------------

    private static final Identifier EF_ANIM_BASE = Identifier.parse("combat_arts:models/");

    private static void loadEFAnimation(String name, String path) {
        try {
            Identifier id = Identifier.parse("combat_arts:models/" + path);
            JsonObject root = loadJson(id);
            if (root == null) {
                LOGGER.warn("[MeshManager] Animation not found: {}", path);
                return;
            }
            JsonArray animArr = root.getAsJsonArray("animation");
            Map<String, TransformSheet> sheets = Maps.newHashMap();

            for (int i = 0; i < animArr.size(); i++) {
                JsonObject entry = animArr.get(i).getAsJsonObject();
                String jointName = entry.get("name").getAsString();
                JsonArray times = entry.getAsJsonArray("time");
                JsonArray transforms = entry.getAsJsonArray("transform");

                Joint joint = armature != null ? armature.searchJointByName(jointName) : null;
                if (joint == null) continue;

                List<Keyframe> keyframes = new java.util.ArrayList<>();
                for (int k = 0; k < times.size(); k++) {
                    float time = times.get(k).getAsFloat();
                    // Each transform entry is a nested 16-element array
                    JsonArray matArr = transforms.get(k).getAsJsonArray();
                    float[] m = new float[16];
                    for (int j = 0; j < 16; j++) {
                        m[j] = matArr.get(j).getAsFloat();
                    }
                    OpenMatrix4f mat = OpenMatrix4f.load(null, m);
                    mat.transpose(); // row-major → column-major

                    // Apply Blender→MC transform to root bone
                    if (jointName.equals("Root")) {
                        mat.mulFront(BLENDER_TO_MC);
                    }

                    // Pre-multiply by inverse local transform (delta from bind pose)
                    OpenMatrix4f invLocal = new OpenMatrix4f(joint.getLocalTransform());
                    invLocal.invert();
                    mat.mulFront(invLocal);

                    // EF's correctRootJoint: zero out Root X/Z translation in the final delta.
                    // Root translation in EF attacks is "root motion" meant to move the entity,
                    // NOT to displace the model geometry. Keeping it warps the body geometry.
                    // Must be done AFTER all transforms, on the final delta translation.
                    if (jointName.equals("Root")) {
                        mat.m30 = 0.0F;
                        mat.m32 = 0.0F;
                    }

                    JointTransform jt = JointTransform.fromMatrix(mat);
                    jt.rotation().normalize();
                    keyframes.add(new Keyframe(time, jt));
                }
                sheets.put(jointName, new TransformSheet(keyframes));
            }
            loadedAnims.put(name, sheets);
        } catch (Exception e) {
            LOGGER.error("[MeshManager] Failed to load animation: {}", name, e);
        }
    }

    // ---------------------------------------------------------------
    // Programmatic draw/sheath animations (EF-native delta values)
    // ---------------------------------------------------------------

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private static TransformSheet sheet(float[][] data) {
        java.util.List<Keyframe> kfs = new java.util.ArrayList<>();
        for (float[] row : data) {
            float t = row[0];
            float rx = row[1] * DEG_TO_RAD, ry = row[2] * DEG_TO_RAD, rz = row[3] * DEG_TO_RAD;
            org.joml.Quaternionf q = new org.joml.Quaternionf().rotationZYX(rz, ry, rx);
            kfs.add(new Keyframe(t, new JointTransform(new Vec3f(0, 0, 0), q, new Vec3f(1, 1, 1))));
        }
        return new TransformSheet(kfs);
    }

    private static void createDrawAnimation() {
        Map<String, TransformSheet> sheets = Maps.newHashMap();

        // Right arm: raise up (rx) + reach behind (ry) → grab sword → return to hold
        // rx: arm raise/lower, ry: arm forward/backward
        sheets.put("Shoulder_R", sheet(new float[][] {
            {0.00f,    0.0f,    0.0f,    5.0f},   // idle
            {0.08f,   50.0f,  -40.0f,    0.0f},   // arm raises forward-up
            {0.15f,   80.0f,   30.0f,    0.0f},   // arm up and reaching behind
            {0.20f,   80.0f,   50.0f,    0.0f},   // at back (grab sword at tick 4)
            {0.35f,   30.0f,    0.0f,    0.0f},   // pulling forward-down
            {0.55f,    0.0f,  -17.8f,    0.0f},   // hold position
            {0.80f,    0.0f,  -17.8f,    0.0f},   // settle
        }));

        sheets.put("Arm_R", sheet(new float[][] {
            {0.00f,    0.0f,    0.0f,    0.0f},   // idle
            {0.12f,   15.0f,   70.0f,    0.0f},   // elbow bent, reaching behind
            {0.20f,   15.0f,   70.0f,    0.0f},   // hold
            {0.35f,   12.0f,   35.0f,   -5.0f},   // relaxing
            {0.55f,   11.0f,   18.5f,   -7.6f},   // hold position
            {0.80f,   11.0f,   18.5f,   -7.6f},   // settle
        }));

        sheets.put("Hand_R", sheet(new float[][] {
            {0.00f,    7.0f,    0.0f,    0.0f},   // idle
            {0.20f,   15.0f,    0.0f,    0.0f},   // reaching
            {0.55f,   12.8f,    0.0f,    0.0f},   // hold position
            {0.80f,   12.8f,    0.0f,    0.0f},   // settle (拔刀完成)
            {1.20f,   12.8f,    0.0f,    0.0f},   // 持续保持(转刀期间手腕不动)
        }));

        // 拔刀转刀: Tool_R 在握刀稳定后(t=0.55)绕 X 轴转一整圈到 t=0.85,然后保持。
        // NLERP 走最短弧,所以必须用 5 段 ≤90° 的中间帧强制完整旋转(否则会被识别为
        // 没旋转/反转最短)。450° 在四元数等价 90°(初始持刀朝向),视觉无缝衔接。
        sheets.put("Tool_R", sheet(new float[][] {
            {0.00f,   90.0f,    0.0f,    0.0f},   // 鞘内(不可见)
            {0.55f,   90.0f,    0.0f,    0.0f},   // 握住,准备转刀
            {0.625f, 180.0f,    0.0f,    0.0f},   // 转 1/4
            {0.70f,  270.0f,    0.0f,    0.0f},   // 转 1/2
            {0.775f, 360.0f,    0.0f,    0.0f},   // 转 3/4
            {0.85f,  450.0f,    0.0f,    0.0f},   // 转一整圈完成 (= 90° 视觉同初始)
            {1.20f,  450.0f,    0.0f,    0.0f},   // 持续保持
        }));

        loadedAnims.put("draw_weapon", sheets);
        LOGGER.info("[MeshManager] Created draw_weapon animation");
    }

    private static void createSheathAnimation() {
        Map<String, TransformSheet> sheets = Maps.newHashMap();

        sheets.put("Shoulder_R", sheet(new float[][] {
            {0.00f,    0.0f,  -17.8f,    0.0f},   // hold position
            {0.15f,   30.0f,    0.0f,    0.0f},   // arm rises
            {0.30f,   80.0f,   50.0f,    0.0f},   // at back (place sword)
            {0.45f,   80.0f,   50.0f,    0.0f},   // hold
            {0.60f,   40.0f,  -20.0f,    0.0f},   // coming forward-down
            {0.80f,    0.0f,    0.0f,    5.0f},   // idle
        }));

        sheets.put("Arm_R", sheet(new float[][] {
            {0.00f,   11.0f,   18.5f,   -7.6f},   // hold
            {0.15f,   12.0f,   35.0f,   -5.0f},   // transitioning
            {0.35f,   15.0f,   70.0f,    0.0f},   // elbow bent at back
            {0.45f,   15.0f,   70.0f,    0.0f},   // hold
            {0.65f,    8.0f,   20.0f,    0.0f},   // relaxing
            {0.80f,    0.0f,    0.0f,    0.0f},   // idle
        }));

        sheets.put("Hand_R", sheet(new float[][] {
            {0.00f,   12.8f,    0.0f,    0.0f},   // hold
            {0.35f,   15.0f,    0.0f,    0.0f},   // reaching
            {0.80f,    7.0f,    0.0f,    0.0f},   // idle
        }));

        loadedAnims.put("sheath_weapon", sheets);
        LOGGER.info("[MeshManager] Created sheath_weapon animation");
    }

    /**
     * 程序化格挡姿势:剑横在身前(单手剑、肘抬起、剑刃竖直挡在胸前)。
     * 数值来自 commit 6ff555b "round 2 烘焙" — 之前在 Bedrock 动画格式下打磨出的值,
     * EF 骨骼名称对齐(Shoulder/Arm/Hand)。可用 BlockPoseTweaker 在 BLOCK 状态下热调微修。
     */
    private static void createBlockAnimation() {
        Map<String, TransformSheet> sheets = Maps.newHashMap();

        // 右手举剑横在身前
        sheets.put("Shoulder_R", sheet(new float[][] {
            {0.0f,    0.0f,  -20.0f,   5.0f},
            {1.0f,    0.0f,  -20.0f,   5.0f},
        }));
        sheets.put("Arm_R", sheet(new float[][] {
            {0.0f, -105.0f,   20.0f,   0.0f},
            {1.0f, -105.0f,   20.0f,   0.0f},
        }));
        sheets.put("Hand_R", sheet(new float[][] {
            {0.0f,    0.0f,  -20.0f,   0.0f},
            {1.0f,    0.0f,  -20.0f,   0.0f},
        }));

        // 左手扶柄
        sheets.put("Shoulder_L", sheet(new float[][] {
            {0.0f,  -15.0f,   37.0f,  10.0f},
            {1.0f,  -15.0f,   37.0f,  10.0f},
        }));
        sheets.put("Arm_L", sheet(new float[][] {
            {0.0f,  -70.0f,   -5.0f,  -8.0f},
            {1.0f,  -70.0f,   -5.0f,  -8.0f},
        }));
        sheets.put("Hand_L", sheet(new float[][] {
            {0.0f,    0.0f,    0.0f,   0.0f},
            {1.0f,    0.0f,    0.0f,   0.0f},
        }));

        loadedAnims.put("block", sheets);
        LOGGER.info("[MeshManager] Created programmatic block animation (baked from 6ff555b)");
    }

    private static void createInspectAnimation() {
        Map<String, TransformSheet> sheets = Maps.newHashMap();
        // 占位: 只需注册 "inspect" 名字 + 4.0s 长度，实际姿势在 SkinnedMeshLayer 里用 applyTweakToJoint
        sheets.put("Shoulder_R", sheet(new float[][] {{0.0f, 0,0,0}, {4.0f, 0,0,0}}));
        loadedAnims.put("inspect", sheets);
        LOGGER.info("[MeshManager] Created programmatic inspect animation");
    }
}
