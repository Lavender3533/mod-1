package org.example.mod_1.mod_1.combat.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.example.mod_1.mod_1.Mod_1;
import org.example.mod_1.mod_1.combat.CombatState;
import org.example.mod_1.mod_1.combat.WeaponType;
import org.example.mod_1.mod_1.combat.capability.ICombatCapability;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CombatAnimationController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final float TRANSITION_SECS = 0.18f;
    private static final float LOCOMOTION_TRANSITION_SECS = 0.10f;
    private static final float STOP_TRANSITION_SECS = 0.14f;
    private static final float ATTACK_CHAIN_TRANSITION_SECS = 0.06f;
    private static final float ACTION_ENTRY_TRANSITION_SECS = 0.08f;

    // animName -> boneName -> keyframes [time, rx, ry, rz]
    private static final Map<String, Map<String, List<float[]>>> ANIM_DATA = new HashMap<>();
    private static final Map<String, Float> ANIM_LENGTHS = new HashMap<>();
    private static final Map<String, Boolean> ANIM_LOOPS = new HashMap<>();
    private static boolean loaded = false;
    private static final Map<Integer, AnimationRuntime> RUNTIMES = new HashMap<>();

    // 上半身专属动作：攻击/格挡/招架/重击蓄力/拔刀/收刀。这些状态下，下半身改播 locomotion（idle/walk/run/crouch/jump）
    // DRAW/SHEATH 加进来是为了走路时拔刀/收刀腿能跟着 walk 动画走, 否则腿停在拔刀关键帧静态值很怪。
    private static final Set<CombatState> UPPER_BODY_ONLY_STATES = EnumSet.of(
            CombatState.ATTACK_LIGHT,
            CombatState.ATTACK_HEAVY,
            CombatState.ATTACK_HEAVY_CHARGING,
            CombatState.BLOCK,
            CombatState.PARRY,
            CombatState.DRAW_WEAPON,
            CombatState.SHEATH_WEAPON
    );

    private static final Set<String> LOWER_BODY_BONES = Set.of(
            "rightUpperLeg", "rightLowerLeg",
            "leftUpperLeg", "leftLowerLeg"
    );

    // 攻击时左手交给 locomotion 层 (走路/跑步会自然摆动) — 攻击动画里左手关键帧只有 5-9° 几乎看不出,
    // 让左手跟着 walk/run 的 ±16°/±28° 摆动, 攻击手感更"重"。
    // BLOCK / PARRY / 蓄力等需要双臂保持姿势的状态不走这条路。
    private static boolean useLowerLayerForBone(String boneName, CombatState state) {
        if (LOWER_BODY_BONES.contains(boneName)) return true;
        if ((state == CombatState.ATTACK_LIGHT || state == CombatState.ATTACK_HEAVY)
                && ("leftUpperArm".equals(boneName) || "leftLowerArm".equals(boneName) || "leftHand".equals(boneName))) {
            return true;
        }
        return false;
    }

    // 17→6 bone merge mapping: child bones whose rotations get ADDED to a target vanilla bone
    private static final Map<String, String> BONE_TO_VANILLA = Map.ofEntries(
            Map.entry("head", "head"),
            Map.entry("neck", "head"),
            Map.entry("chest", "body"),
            Map.entry("waist", "body"),
            Map.entry("rightUpperArm", "rightArm"),
            Map.entry("rightLowerArm", "rightArm"),
            Map.entry("right_upper_arm", "rightArm"),
            Map.entry("right_lower_arm", "rightArm"),
            Map.entry("leftUpperArm", "leftArm"),
            Map.entry("leftLowerArm", "leftArm"),
            Map.entry("left_upper_arm", "leftArm"),
            Map.entry("left_lower_arm", "leftArm"),
            Map.entry("rightUpperLeg", "rightLeg"),
            Map.entry("rightLowerLeg", "rightLeg"),
            Map.entry("right_upper_leg", "rightLeg"),
            Map.entry("right_lower_leg", "rightLeg"),
            Map.entry("leftUpperLeg", "leftLeg"),
            Map.entry("leftLowerLeg", "leftLeg"),
            Map.entry("left_upper_leg", "leftLeg"),
            Map.entry("left_lower_leg", "leftLeg")
    );

    private static final String[] ANIMATION_FILES = {
            "animations/basic/anim_idle.animation.json",
            "animations/basic/anim_walk.animation.json",
            "animations/basic/anim_run.animation.json",
            "animations/basic/anim_crouch.animation.json",
            "animations/basic/anim_jump.animation.json",
            "animations/combat/anim_draw_weapon.animation.json",
            "animations/combat/anim_sheath_weapon.animation.json",
            "animations/combat/anim_dodge.animation.json",
            "animations/combat/anim_block.animation.json",
            "animations/combat/anim_parry.animation.json",
            "animations/sword/anim_sword_idle.animation.json",
            "animations/sword/anim_sword_light_1.animation.json",
            "animations/sword/anim_sword_light_2.animation.json",
            "animations/sword/anim_sword_light_3.animation.json",
            "animations/sword/anim_sword_heavy.animation.json",
            "animations/sword/anim_sword_heavy_charge.animation.json",
            "animations/sword/anim_sword_dash_attack.animation.json",
            "animations/spear/anim_spear_idle.animation.json",
            "animations/spear/anim_spear_light.animation.json",
            "animations/spear/anim_spear_light_2.animation.json",
            "animations/spear/anim_spear_light_3.animation.json",
            "animations/spear/anim_spear_heavy.animation.json",
            "animations/spear/anim_spear_heavy_charge.animation.json",
            "animations/sword/anim_sword_inspect.animation.json",
            "animations/spear/anim_spear_inspect.animation.json",
    };

    public static void init() {
        LOGGER.info("Combat animation controller initialized (17-bone merge engine)");
    }

    private static final class AnimationRuntime {
        // Upper layer (or full body when no override active)
        private String currentAnim = null;
        private String prevAnim = null;
        private long animStartNano = 0L;
        private long prevAnimStartNano = 0L;
        private long transitionStartNano = 0L;
        private float currentAnimSpeed = 1.0f;
        private float prevAnimSpeed = 1.0f;
        private boolean active = false;
        private String lastMovementAnim = "animation.player.idle";
        private int movementHoldTicks = 0;
        private float currentTransitionSecs = TRANSITION_SECS;
        private float prevAnimFrozenTime = -1.0f;
        private boolean freezePrevOnNextApply = false;
        private boolean currentAnimFinished = false;
        private CombatState lastState = CombatState.IDLE;
        private int lastStateTimer = -1;

        // Lower layer (only active during upper-body-only combat states)
        private String lowerCurrentAnim = null;
        private String lowerPrevAnim = null;
        private long lowerAnimStartNano = 0L;
        private long lowerPrevAnimStartNano = 0L;
        private long lowerTransitionStartNano = 0L;
        private float lowerCurrentAnimSpeed = 1.0f;
        private float lowerPrevAnimSpeed = 1.0f;
        private float lowerCurrentTransitionSecs = LOCOMOTION_TRANSITION_SECS;
        private float lowerPrevAnimFrozenTime = -1.0f;
        private boolean lowerFreezePrevOnNextApply = false;
    }


    /**
     * 热重载入口:清缓存+运行时 → 从磁盘重读所有动画 json。
     * @return 本次加载成功的动画数量
     */
    public static int reloadAnimations() {
        ANIM_DATA.clear();
        ANIM_LENGTHS.clear();
        ANIM_LOOPS.clear();
        RUNTIMES.clear();
        loaded = false;
        loadAnimations();
        LOGGER.info("Animations reloaded: {}", ANIM_DATA.size());
        return ANIM_DATA.size();
    }

    private static void loadAnimations() {
        if (loaded) return;
        for (String file : ANIMATION_FILES) {
            try {
                Identifier id = Identifier.fromNamespaceAndPath(Mod_1.MODID, file);
                Resource resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id);
                try (InputStream is = resource.open()) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonObject animations = root.getAsJsonObject("animations");
                    for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
                        String animName = entry.getKey();
                        JsonObject animObj = entry.getValue().getAsJsonObject();
                        float length = animObj.has("animation_length") ? animObj.get("animation_length").getAsFloat() : 1.0f;
                        boolean loop = animObj.has("loop") && animObj.get("loop").getAsBoolean();
                        ANIM_LENGTHS.put(animName, length);
                        ANIM_LOOPS.put(animName, loop);

                        Map<String, List<float[]>> bones = new HashMap<>();
                        JsonObject bonesObj = animObj.getAsJsonObject("bones");
                        if (bonesObj != null) {
                            for (Map.Entry<String, JsonElement> boneEntry : bonesObj.entrySet()) {
                                String boneName = boneEntry.getKey();
                                JsonObject boneData = boneEntry.getValue().getAsJsonObject();
                                JsonObject rotation = boneData.getAsJsonObject("rotation");
                                if (rotation != null) {
                                    List<float[]> keyframes = new ArrayList<>();
                                    for (Map.Entry<String, JsonElement> kf : rotation.entrySet()) {
                                        float time = Float.parseFloat(kf.getKey());
                                        float[] rot = parseVec3(kf.getValue());
                                        keyframes.add(new float[]{time, rot[0], rot[1], rot[2]});
                                    }
                                    keyframes.sort(Comparator.comparingDouble(a -> a[0]));
                                    bones.put(boneName, keyframes);
                                }
                            }
                        }
                        ANIM_DATA.put(animName, bones);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load animation: {}", file, e);
            }
        }
        loaded = true;
        LOGGER.info("Loaded {} animations (17-bone)", ANIM_DATA.size());
    }

    private static float[] parseVec3(JsonElement element) {
        if (element.isJsonArray()) {
            var arr = element.getAsJsonArray();
            return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
        }
        return new float[]{0, 0, 0};
    }

    public static void updateAnimation(AbstractClientPlayer player, ICombatCapability cap) {
        loadAnimations();
        AnimationRuntime runtime = getRuntime(player);

        // 检视打断：移动时自动退出 INSPECT
        if (cap.getState() == CombatState.INSPECT) {
            double dx = player.getX() - player.xOld;
            double dz = player.getZ() - player.zOld;
            if (dx * dx + dz * dz > 0.001) {
                return; // 移动检测到，由 CombatInputHandler 发送状态切换
            }
        }

        String animName = resolveAnimationName(player, cap, runtime);
        if (animName == null) {
            runtime.currentAnim = null;
            runtime.prevAnim = null;
            runtime.active = false;
            runtime.currentAnimSpeed = 1.0f;
            runtime.currentAnimFinished = false;
            runtime.lastState = cap.getState();
            runtime.lastStateTimer = cap.getStateTimer();
            clearLowerLayer(runtime);
            return;
        }
        long now = System.nanoTime();
        float animSpeed = resolveAnimationSpeed(player, cap, animName);
        boolean restartCurrentAnim = shouldRestartCurrentAnimation(runtime, cap, animName);
        if (restartCurrentAnim || !animName.equals(runtime.currentAnim)) {
            boolean instantSwitch = restartCurrentAnim || shouldInstantSwitch(runtime.currentAnim, animName);
            boolean freezePrevPose = shouldFreezePreviousPose(runtime.currentAnim, animName);
            runtime.prevAnim = instantSwitch ? null : runtime.currentAnim;
            runtime.prevAnimStartNano = runtime.animStartNano;
            runtime.prevAnimSpeed = runtime.currentAnimSpeed;
            runtime.transitionStartNano = now;
            runtime.currentTransitionSecs = restartCurrentAnim
                    ? 0.0f
                    : resolveTransitionDuration(runtime.currentAnim, animName);
            runtime.prevAnimFrozenTime = -1.0f;
            runtime.freezePrevOnNextApply = !restartCurrentAnim && !instantSwitch && freezePrevPose;
            runtime.currentAnim = animName;
            runtime.currentAnimSpeed = animSpeed;
            runtime.animStartNano = resolveAnimationStartNano(now, cap);
            runtime.currentAnimFinished = false;
        } else {
            runtime.currentAnimSpeed = animSpeed;
        }
        runtime.active = true;
        runtime.lastState = cap.getState();
        runtime.lastStateTimer = cap.getStateTimer();

        updateLowerLayer(player, cap, runtime);
    }

    private static void updateLowerLayer(AbstractClientPlayer player, ICombatCapability cap, AnimationRuntime runtime) {
        if (!UPPER_BODY_ONLY_STATES.contains(cap.getState())) {
            // Drop lower layer instantly — upper layer will cover all bones
            clearLowerLayer(runtime);
            return;
        }

        String lowerAnim = resolveLocomotionAnim(player, cap, runtime);
        if (lowerAnim == null) {
            clearLowerLayer(runtime);
            return;
        }
        float lowerSpeed = resolveAnimationSpeed(player, cap, lowerAnim);

        if (!lowerAnim.equals(runtime.lowerCurrentAnim)) {
            boolean firstActivation = runtime.lowerCurrentAnim == null;
            // Locomotion anims are phase-locked to walkAnimationPos so first activation needs no transition
            runtime.lowerPrevAnim = firstActivation ? null : runtime.lowerCurrentAnim;
            runtime.lowerPrevAnimStartNano = runtime.lowerAnimStartNano;
            runtime.lowerPrevAnimSpeed = runtime.lowerCurrentAnimSpeed;
            runtime.lowerTransitionStartNano = System.nanoTime();
            runtime.lowerCurrentTransitionSecs = firstActivation
                    ? 0.0f
                    : resolveTransitionDuration(runtime.lowerCurrentAnim, lowerAnim);
            runtime.lowerPrevAnimFrozenTime = -1.0f;
            runtime.lowerFreezePrevOnNextApply = !firstActivation
                    && shouldFreezePreviousPose(runtime.lowerCurrentAnim, lowerAnim);
            runtime.lowerCurrentAnim = lowerAnim;
            runtime.lowerCurrentAnimSpeed = lowerSpeed;
            runtime.lowerAnimStartNano = System.nanoTime();
        } else {
            runtime.lowerCurrentAnimSpeed = lowerSpeed;
        }
    }

    private static void clearLowerLayer(AnimationRuntime runtime) {
        runtime.lowerCurrentAnim = null;
        runtime.lowerPrevAnim = null;
        runtime.lowerAnimStartNano = 0L;
        runtime.lowerPrevAnimStartNano = 0L;
        runtime.lowerTransitionStartNano = 0L;
        runtime.lowerCurrentAnimSpeed = 1.0f;
        runtime.lowerPrevAnimSpeed = 1.0f;
        runtime.lowerPrevAnimFrozenTime = -1.0f;
        runtime.lowerFreezePrevOnNextApply = false;
    }

    public static boolean isActive(AvatarRenderState state) {
        AnimationRuntime runtime = getRuntime(state);
        return runtime != null && runtime.active && runtime.currentAnim != null;
    }

    public static boolean isCurrentAnimFinished(AbstractClientPlayer player) {
        AnimationRuntime runtime = getRuntime(player);
        return runtime.currentAnimFinished;
    }

    /**
     * Apply animation directly to 17-bone model via boneMap.
     * Upper layer covers all bones unless a lower layer is active — then lower layer covers
     * hip/legs while upper layer covers everything else.
     */
    public static void applyTo17Bones(Map<String, ModelPart> boneMap, AvatarRenderState state) {
        AnimationRuntime runtime = getRuntime(state);
        if (runtime == null || !runtime.active || runtime.currentAnim == null || !ANIM_DATA.containsKey(runtime.currentAnim)) {
            return;
        }

        // Upper layer prep
        float length = ANIM_LENGTHS.getOrDefault(runtime.currentAnim, 1.0f);
        boolean loop = ANIM_LOOPS.getOrDefault(runtime.currentAnim, false);
        float timeSec = resolveCurrentTime(state, runtime.currentAnim, length, loop, runtime.animStartNano, runtime.currentAnimSpeed);

        if (!loop && timeSec > length) {
            runtime.currentAnimFinished = true;
            timeSec = length; // Hold last frame pose
        }
        if (loop) timeSec = timeSec % length;

        Map<String, List<float[]>> bones = ANIM_DATA.get(runtime.currentAnim);
        Map<String, List<float[]>> prevBones = runtime.prevAnim != null ? ANIM_DATA.get(runtime.prevAnim) : null;
        float prevLength = ANIM_LENGTHS.getOrDefault(runtime.prevAnim, 1.0f);
        boolean prevLoop = ANIM_LOOPS.getOrDefault(runtime.prevAnim, false);
        if (runtime.freezePrevOnNextApply && runtime.prevAnim != null && state != null) {
            runtime.prevAnimFrozenTime = resolveCurrentTime(state, runtime.prevAnim, prevLength, prevLoop, runtime.prevAnimStartNano, runtime.prevAnimSpeed);
            runtime.freezePrevOnNextApply = false;
        }
        float prevTimeSec = runtime.prevAnim != null
                ? (runtime.prevAnimFrozenTime >= 0.0f
                ? runtime.prevAnimFrozenTime
                : resolveCurrentTime(state, runtime.prevAnim, prevLength, prevLoop, runtime.prevAnimStartNano, runtime.prevAnimSpeed))
                : 0;

        float transitionAlpha = 1.0f;
        if (runtime.prevAnim != null && ANIM_DATA.containsKey(runtime.prevAnim)) {
            float transElapsed = (System.nanoTime() - runtime.transitionStartNano) / 1_000_000_000.0f;
            if (transElapsed < runtime.currentTransitionSecs) {
                transitionAlpha = smoothStep01(transElapsed / runtime.currentTransitionSecs);
            } else {
                runtime.prevAnim = null;
                runtime.prevAnimStartNano = 0L;
                runtime.prevAnimFrozenTime = -1.0f;
                runtime.freezePrevOnNextApply = false;
            }
        }

        // Lower layer prep (only when an upper-body-only combat state is active)
        boolean lowerActive = runtime.lowerCurrentAnim != null && ANIM_DATA.containsKey(runtime.lowerCurrentAnim);
        Map<String, List<float[]>> lowerBones = null;
        Map<String, List<float[]>> lowerPrevBones = null;
        float lowerTimeSec = 0f, lowerPrevTimeSec = 0f, lowerPrevLength = 1f, lowerTransitionAlpha = 1f;
        boolean lowerPrevLoop = false;
        if (lowerActive) {
            float lowerLength = ANIM_LENGTHS.getOrDefault(runtime.lowerCurrentAnim, 1.0f);
            boolean lowerLoop = ANIM_LOOPS.getOrDefault(runtime.lowerCurrentAnim, false);
            lowerTimeSec = resolveCurrentTime(state, runtime.lowerCurrentAnim, lowerLength, lowerLoop, runtime.lowerAnimStartNano, runtime.lowerCurrentAnimSpeed);
            if (!lowerLoop && lowerTimeSec > lowerLength) lowerTimeSec = lowerLength;
            if (lowerLoop) lowerTimeSec = lowerTimeSec % lowerLength;

            lowerBones = ANIM_DATA.get(runtime.lowerCurrentAnim);
            lowerPrevBones = runtime.lowerPrevAnim != null ? ANIM_DATA.get(runtime.lowerPrevAnim) : null;
            lowerPrevLength = ANIM_LENGTHS.getOrDefault(runtime.lowerPrevAnim, 1.0f);
            lowerPrevLoop = ANIM_LOOPS.getOrDefault(runtime.lowerPrevAnim, false);
            if (runtime.lowerFreezePrevOnNextApply && runtime.lowerPrevAnim != null && state != null) {
                runtime.lowerPrevAnimFrozenTime = resolveCurrentTime(state, runtime.lowerPrevAnim, lowerPrevLength, lowerPrevLoop, runtime.lowerPrevAnimStartNano, runtime.lowerPrevAnimSpeed);
                runtime.lowerFreezePrevOnNextApply = false;
            }
            lowerPrevTimeSec = runtime.lowerPrevAnim != null
                    ? (runtime.lowerPrevAnimFrozenTime >= 0.0f
                    ? runtime.lowerPrevAnimFrozenTime
                    : resolveCurrentTime(state, runtime.lowerPrevAnim, lowerPrevLength, lowerPrevLoop, runtime.lowerPrevAnimStartNano, runtime.lowerPrevAnimSpeed))
                    : 0f;
            if (runtime.lowerPrevAnim != null && ANIM_DATA.containsKey(runtime.lowerPrevAnim)) {
                float trans = (System.nanoTime() - runtime.lowerTransitionStartNano) / 1_000_000_000.0f;
                if (trans < runtime.lowerCurrentTransitionSecs) {
                    lowerTransitionAlpha = smoothStep01(trans / runtime.lowerCurrentTransitionSecs);
                } else {
                    runtime.lowerPrevAnim = null;
                    runtime.lowerPrevAnimStartNano = 0L;
                    runtime.lowerPrevAnimFrozenTime = -1.0f;
                    runtime.lowerFreezePrevOnNextApply = false;
                }
            }
        }

        for (Map.Entry<String, ModelPart> boneEntry : boneMap.entrySet()) {
            String boneName = boneEntry.getKey();
            ModelPart part = boneEntry.getValue();

            boolean useLower = lowerActive && useLowerLayerForBone(boneName, runtime.lastState);
            Map<String, List<float[]>> srcBones = useLower ? lowerBones : bones;
            Map<String, List<float[]>> srcPrevBones = useLower ? lowerPrevBones : prevBones;
            float srcTime = useLower ? lowerTimeSec : timeSec;
            float srcPrevTime = useLower ? lowerPrevTimeSec : prevTimeSec;
            float srcPrevLength = useLower ? lowerPrevLength : prevLength;
            boolean srcPrevLoop = useLower ? lowerPrevLoop : prevLoop;
            float srcAlpha = useLower ? lowerTransitionAlpha : transitionAlpha;

            List<float[]> kfs = srcBones.get(boneName);
            if (kfs == null) continue;

            float[] rot = interpolate(kfs, srcTime);

            if (srcPrevBones != null && srcAlpha < 1.0f) {
                List<float[]> prevKfs = srcPrevBones.get(boneName);
                if (prevKfs != null) {
                    float prevT = sampleAnimationTime(srcPrevTime, srcPrevLength, srcPrevLoop);
                    float[] prevRot = interpolate(prevKfs, prevT);
                    rot[0] = prevRot[0] + (rot[0] - prevRot[0]) * srcAlpha;
                    rot[1] = prevRot[1] + (rot[1] - prevRot[1]) * srcAlpha;
                    rot[2] = prevRot[2] + (rot[2] - prevRot[2]) * srcAlpha;
                }
            }

            part.xRot = rot[0] * DEG_TO_RAD;
            part.yRot = rot[1] * DEG_TO_RAD;
            part.zRot = rot[2] * DEG_TO_RAD;

            // Live tweaker: BLOCK 和 蓄力 动画激活时叠加偏移，便于在游戏内调姿势
            String anim = runtime.currentAnim;
            if ("animation.player.block".equals(anim)
                    || "animation.player.sword_heavy_charge".equals(anim)
                    || "animation.player.spear_heavy_charge".equals(anim)) {
                BlockPoseTweaker.applyDelta(part, boneName);
            }
        }
    }

    /**
     * Called from Mixin after vanilla setupAnim (6-bone fallback).
     * When the lower layer is active, leg bones are sampled from the lower anim instead.
     */
    public static void applyToBones(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm,
                                    ModelPart rightLeg, ModelPart leftLeg, AvatarRenderState state) {
        AnimationRuntime runtime = getRuntime(state);
        if (runtime == null || !runtime.active || runtime.currentAnim == null || !ANIM_DATA.containsKey(runtime.currentAnim)) {
            return;
        }

        float length = ANIM_LENGTHS.getOrDefault(runtime.currentAnim, 1.0f);
        boolean loop = ANIM_LOOPS.getOrDefault(runtime.currentAnim, false);
        float timeSec = resolveCurrentTime(state, runtime.currentAnim, length, loop, runtime.animStartNano, runtime.currentAnimSpeed);

        if (!loop && timeSec > length) {
            timeSec = length;
        }
        if (loop) timeSec = timeSec % length;

        Map<String, List<float[]>> bones = ANIM_DATA.get(runtime.currentAnim);

        boolean lowerActive = runtime.lowerCurrentAnim != null && ANIM_DATA.containsKey(runtime.lowerCurrentAnim);

        // Accumulate rotations from all 17 bones into the 6 vanilla parts
        float[] headRot = {0, 0, 0}, bodyRot = {0, 0, 0};
        float[] rArmRot = {0, 0, 0}, lArmRot = {0, 0, 0};
        float[] rLegRot = {0, 0, 0}, lLegRot = {0, 0, 0};

        for (Map.Entry<String, List<float[]>> entry : bones.entrySet()) {
            String boneName = entry.getKey();
            String target = BONE_TO_VANILLA.get(boneName);
            if (target == null) continue;
            // Lower layer covers some bones (legs always; left arm during attacks)
            if (lowerActive && useLowerLayerForBone(boneName, runtime.lastState)) continue;

            float[] rot = interpolate(entry.getValue(), timeSec);
            float[] accumulator = switch (target) {
                case "head" -> headRot;
                case "body" -> bodyRot;
                case "rightArm" -> rArmRot;
                case "leftArm" -> lArmRot;
                case "rightLeg" -> rLegRot;
                case "leftLeg" -> lLegRot;
                default -> null;
            };
            if (accumulator != null) {
                accumulator[0] += rot[0];
                accumulator[1] += rot[1];
                accumulator[2] += rot[2];
            }
        }

        // Lower layer: sample leg bones from lowerCurrentAnim
        if (lowerActive) {
            float lowerLength = ANIM_LENGTHS.getOrDefault(runtime.lowerCurrentAnim, 1.0f);
            boolean lowerLoop = ANIM_LOOPS.getOrDefault(runtime.lowerCurrentAnim, false);
            float lowerTimeSec = resolveCurrentTime(state, runtime.lowerCurrentAnim, lowerLength, lowerLoop, runtime.lowerAnimStartNano, runtime.lowerCurrentAnimSpeed);
            if (!lowerLoop && lowerTimeSec > lowerLength) lowerTimeSec = lowerLength;
            if (lowerLoop) lowerTimeSec = lowerTimeSec % lowerLength;

            Map<String, List<float[]>> lowerBones = ANIM_DATA.get(runtime.lowerCurrentAnim);
            for (Map.Entry<String, List<float[]>> entry : lowerBones.entrySet()) {
                String boneName = entry.getKey();
                String target = BONE_TO_VANILLA.get(boneName);
                if (target == null) continue;
                if (!useLowerLayerForBone(boneName, runtime.lastState)) continue;

                float[] rot = interpolate(entry.getValue(), lowerTimeSec);
                float[] accumulator = switch (target) {
                    case "rightLeg" -> rLegRot;
                    case "leftLeg" -> lLegRot;
                    case "leftArm" -> lArmRot;
                    default -> null;
                };
                if (accumulator == null) continue;
                accumulator[0] += rot[0];
                accumulator[1] += rot[1];
                accumulator[2] += rot[2];
            }
        }

        // Apply merged rotations (additive on top of vanilla)
        head.xRot += headRot[0] * DEG_TO_RAD;
        head.yRot += headRot[1] * DEG_TO_RAD;
        head.zRot += headRot[2] * DEG_TO_RAD;
        body.xRot += bodyRot[0] * DEG_TO_RAD;
        body.yRot += bodyRot[1] * DEG_TO_RAD;
        body.zRot += bodyRot[2] * DEG_TO_RAD;
        rightArm.xRot += rArmRot[0] * DEG_TO_RAD;
        rightArm.yRot += rArmRot[1] * DEG_TO_RAD;
        rightArm.zRot += rArmRot[2] * DEG_TO_RAD;
        leftArm.xRot += lArmRot[0] * DEG_TO_RAD;
        leftArm.yRot += lArmRot[1] * DEG_TO_RAD;
        leftArm.zRot += lArmRot[2] * DEG_TO_RAD;
        rightLeg.xRot += rLegRot[0] * DEG_TO_RAD;
        rightLeg.yRot += rLegRot[1] * DEG_TO_RAD;
        rightLeg.zRot += rLegRot[2] * DEG_TO_RAD;
        leftLeg.xRot += lLegRot[0] * DEG_TO_RAD;
        leftLeg.yRot += lLegRot[1] * DEG_TO_RAD;
        leftLeg.zRot += lLegRot[2] * DEG_TO_RAD;
    }

    private static float[] interpolate(List<float[]> keyframes, float time) {
        if (keyframes.isEmpty()) return new float[]{0, 0, 0};
        if (keyframes.size() == 1) return new float[]{keyframes.get(0)[1], keyframes.get(0)[2], keyframes.get(0)[3]};
        if (time <= keyframes.get(0)[0]) {
            return new float[]{keyframes.get(0)[1], keyframes.get(0)[2], keyframes.get(0)[3]};
        }
        float[] last = keyframes.get(keyframes.size() - 1);
        if (time >= last[0]) {
            return new float[]{last[1], last[2], last[3]};
        }

        float[] before = keyframes.get(0);
        float[] after = last;

        for (int i = 0; i < keyframes.size() - 1; i++) {
            if (keyframes.get(i)[0] <= time && keyframes.get(i + 1)[0] >= time) {
                before = keyframes.get(i);
                after = keyframes.get(i + 1);
                break;
            }
        }

        float dt = after[0] - before[0];
        float t = dt > 0 ? (time - before[0]) / dt : 0;

        return new float[]{
                before[1] + (after[1] - before[1]) * t,
                before[2] + (after[2] - before[2]) * t,
                before[3] + (after[3] - before[3]) * t
        };
    }

    // --- Animation name resolution ---

    private static String resolveAnimationName(AbstractClientPlayer player, ICombatCapability cap, AnimationRuntime runtime) {
        CombatState state = cap.getState();
        WeaponType weapon = cap.getWeaponType();

        // Combat actions always take priority
        switch (state) {
            case DRAW_WEAPON: return "animation.player.draw_weapon";
            case SHEATH_WEAPON: return "animation.player.sheath_weapon";
            case DODGE: return "animation.player.dodge";
            case BLOCK: return "animation.player.block";
            case PARRY: return "animation.player.parry";
            case ATTACK_LIGHT: return resolveLightAttack(weapon, cap.getComboCount());
            case ATTACK_HEAVY_CHARGING: return weapon == WeaponType.SPEAR ? "animation.player.spear_heavy_charge" : "animation.player.sword_heavy_charge";
            case ATTACK_HEAVY: return weapon == WeaponType.SPEAR ? "animation.player.spear_heavy" : "animation.player.sword_heavy";
            case INSPECT: return weapon == WeaponType.SPEAR ? "animation.player.spear_inspect" : "animation.player.sword_inspect";
            default: break;
        }

        return resolveLocomotionAnim(player, cap, runtime);
    }

    private static String resolveLocomotionAnim(AbstractClientPlayer player, ICombatCapability cap, AnimationRuntime runtime) {
        WeaponType weapon = cap.getWeaponType();

        String detected;
        if (player.isCrouching()) {
            detected = "animation.player.crouch";
        } else if (!player.onGround()) {
            detected = "animation.player.jump";
        } else if (player.isSprinting()) {
            detected = "animation.player.run";
        } else {
            double dx = player.getX() - player.xOld;
            double dz = player.getZ() - player.zOld;
            double hSpeedSq = dx * dx + dz * dz;
            // Hysteresis: higher threshold to enter walk, lower to exit
            boolean wasMoving = "animation.player.walk".equals(runtime.lastMovementAnim)
                    || "animation.player.run".equals(runtime.lastMovementAnim);
            if (hSpeedSq > (wasMoving ? 0.00001 : 0.0004)) {
                detected = "animation.player.walk";
            } else {
                detected = cap.isWeaponDrawn() ? resolveWeaponIdle(weapon) : "animation.player.idle";
            }
        }

        // Hold timer: require consistent state for a few ticks before switching
        if (detected.equals(runtime.lastMovementAnim)) {
            runtime.movementHoldTicks = 0;
        } else {
            runtime.movementHoldTicks++;
            if (runtime.movementHoldTicks < 3) {
                return runtime.lastMovementAnim; // keep previous state during hold period
            }
            runtime.movementHoldTicks = 0;
        }
        runtime.lastMovementAnim = detected;
        return detected;
    }

    private static String resolveLightAttack(WeaponType weapon, int combo) {
        if (weapon == WeaponType.SWORD) {
            if (combo == 99) return "animation.player.sword_dash_attack";
            return switch (combo) {
                case 1 -> "animation.player.sword_light_1";
                case 2 -> "animation.player.sword_light_2";
                case 3 -> "animation.player.sword_light_3";
                default -> "animation.player.sword_light_1";
            };
        } else if (weapon == WeaponType.SPEAR) {
            return switch (combo) {
                case 1 -> "animation.player.spear_light";
                case 2 -> "animation.player.spear_light_2";
                case 3 -> "animation.player.spear_light_3";
                default -> "animation.player.spear_light";
            };
        }
        return null;
    }

    private static float resolveAnimationSpeed(AbstractClientPlayer player, ICombatCapability cap, String animName) {
        float timedSpeed = resolveTimedAnimationSpeed(cap, animName);
        if (timedSpeed > 0.0f) {
            return timedSpeed;
        }

        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);

        if ("animation.player.walk".equals(animName)) {
            return clamp((float) (horizontalSpeed / 0.10), 0.72f, 1.18f);
        }
        if ("animation.player.run".equals(animName)) {
            return clamp((float) (horizontalSpeed / 0.17), 0.90f, 1.18f);
        }
        return 1.0f;
    }

    private static float resolveTimedAnimationSpeed(ICombatCapability cap, String animName) {
        CombatState state = cap.getState();
        if (!state.isTimed() || ANIM_LOOPS.getOrDefault(animName, false)) {
            return -1.0f;
        }

        int durationTicks = state.getDurationTicks();
        float animLength = ANIM_LENGTHS.getOrDefault(animName, 0.0f);
        if (durationTicks <= 0 || animLength <= 0.0f) {
            return -1.0f;
        }
        return animLength * 20.0f / durationTicks;
    }

    private static long resolveAnimationStartNano(long now, ICombatCapability cap) {
        CombatState state = cap.getState();
        if (!state.isTimed()) {
            return now;
        }

        int durationTicks = state.getDurationTicks();
        int remainingTicks = Math.max(0, Math.min(durationTicks, cap.getStateTimer()));
        double elapsedSeconds = (durationTicks - remainingTicks) / 20.0;
        return now - (long) (elapsedSeconds * 1_000_000_000L);
    }

    private static boolean shouldRestartCurrentAnimation(AnimationRuntime runtime, ICombatCapability cap, String animName) {
        if (runtime.currentAnim == null || !animName.equals(runtime.currentAnim)) {
            return false;
        }

        CombatState state = cap.getState();
        return state.isTimed()
                && runtime.lastState == state
                && runtime.lastStateTimer >= 0
                && cap.getStateTimer() > runtime.lastStateTimer;
    }

    private static boolean isLocomotionAnimation(String animName) {
        return "animation.player.walk".equals(animName)
                || "animation.player.run".equals(animName);
    }

    private static boolean isIdleAnimation(String animName) {
        return "animation.player.idle".equals(animName)
                || "animation.player.sword_idle".equals(animName)
                || "animation.player.spear_idle".equals(animName);
    }

    private static boolean isJumpAnimation(String animName) {
        return "animation.player.jump".equals(animName);
    }

    private static boolean isAttackAnimation(String animName) {
        return "animation.player.sword_light_1".equals(animName)
                || "animation.player.sword_light_2".equals(animName)
                || "animation.player.sword_light_3".equals(animName)
                || "animation.player.sword_dash_attack".equals(animName)
                || "animation.player.sword_heavy".equals(animName)
                || "animation.player.spear_light".equals(animName)
                || "animation.player.spear_heavy".equals(animName);
    }

    private static boolean isCombatActionAnimation(String animName) {
        return isAttackAnimation(animName)
                || "animation.player.draw_weapon".equals(animName)
                || "animation.player.sheath_weapon".equals(animName)
                || "animation.player.dodge".equals(animName)
                || "animation.player.block".equals(animName)
                || "animation.player.parry".equals(animName);
    }

    private static boolean isGroundedMovementAnimation(String animName) {
        return isIdleAnimation(animName) || isLocomotionAnimation(animName);
    }

    private static boolean shouldUseRecoveryBlend(String fromAnim, String toAnim) {
        if (fromAnim == null || toAnim == null) {
            return false;
        }
        if (isLocomotionAnimation(fromAnim) && "animation.player.idle".equals(toAnim)) {
            return true;
        }
        return isJumpAnimation(fromAnim) && isGroundedMovementAnimation(toAnim);
    }

    private static boolean shouldInstantSwitch(String fromAnim, String toAnim) {
        if (fromAnim == null || toAnim == null) {
            return false;
        }
        if (shouldUseRecoveryBlend(fromAnim, toAnim)) {
            return false;
        }
        return false;
    }

    private static boolean shouldFreezePreviousPose(String fromAnim, String toAnim) {
        return shouldUseRecoveryBlend(fromAnim, toAnim);
    }

    private static float resolveTransitionDuration(String fromAnim, String toAnim) {
        if (shouldFreezePreviousPose(fromAnim, toAnim)) {
            return STOP_TRANSITION_SECS;
        }
        if (isAttackAnimation(fromAnim) && isAttackAnimation(toAnim)) {
            return ATTACK_CHAIN_TRANSITION_SECS;
        }
        if ((isGroundedMovementAnimation(fromAnim) && isCombatActionAnimation(toAnim))
                || (isCombatActionAnimation(fromAnim) && isGroundedMovementAnimation(toAnim))) {
            return ACTION_ENTRY_TRANSITION_SECS;
        }
        if (isGroundedMovementAnimation(fromAnim) && isGroundedMovementAnimation(toAnim)) {
            return LOCOMOTION_TRANSITION_SECS;
        }
        return TRANSITION_SECS;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float resolveCurrentTime(AvatarRenderState state, String animName, float length, boolean loop, long startNano, float animSpeed) {
        if (state != null && isLocomotionAnimation(animName)) {
            float gaitPhase = state.walkAnimationPos / ((float) (Math.PI * 2.0));
            gaitPhase = gaitPhase - (float) Math.floor(gaitPhase);
            return gaitPhase * length;
        }

        float timeSec = ((System.nanoTime() - startNano) / 1_000_000_000.0f) * animSpeed;
        if (loop && length > 0.0f) {
            return timeSec % length;
        }
        return timeSec;
    }

    private static AnimationRuntime getRuntime(AbstractClientPlayer player) {
        return RUNTIMES.computeIfAbsent(player.getId(), id -> new AnimationRuntime());
    }

    private static AnimationRuntime getRuntime(AvatarRenderState state) {
        if (state == null) {
            return null;
        }
        return RUNTIMES.get(state.id);
    }

    private static String resolveWeaponIdle(WeaponType weapon) {
        return switch (weapon) {
            case SWORD -> "animation.player.sword_idle";
            case SPEAR -> "animation.player.spear_idle";
            default -> "animation.player.idle";
        };
    }

    private static float sampleAnimationTime(float timeSec, float length, boolean loop) {
        if (length <= 0.0f) {
            return 0.0f;
        }
        if (loop) {
            return timeSec % length;
        }
        return Math.min(timeSec, length);
    }

    private static float smoothStep01(float t) {
        float clamped = clamp(t, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }
}
