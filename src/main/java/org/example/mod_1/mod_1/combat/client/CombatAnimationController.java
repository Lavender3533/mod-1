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

    // animName -> boneName -> keyframes [time, rx, ry, rz]
    private static final Map<String, Map<String, List<float[]>>> ANIM_DATA = new HashMap<>();
    private static final Map<String, Float> ANIM_LENGTHS = new HashMap<>();
    private static final Map<String, Boolean> ANIM_LOOPS = new HashMap<>();

    private static String currentAnim = null;
    private static String prevAnim = null;
    private static long animStartNano = 0;
    private static long prevAnimStartNano = 0;
    private static long transitionStartNano = 0;
    private static float currentAnimSpeed = 1.0f;
    private static float prevAnimSpeed = 1.0f;
    private static final int TRANSITION_TICKS = 5;
    private static final float TRANSITION_SECS = TRANSITION_TICKS / 20.0f;
    private static final float STOP_TRANSITION_SECS = 0.14f;
    private static boolean loaded = false;
    private static boolean active = false;
    private static String lastMovementAnim = "animation.player.idle";
    private static int movementHoldTicks = 0;
    private static float currentTransitionSecs = TRANSITION_SECS;
    private static float prevAnimFrozenTime = -1.0f;
    private static boolean freezePrevOnNextApply = false;

    private static final Set<String> UPPER_BODY_BONES = Set.of(
            "waist", "chest", "neck", "head",
            "rightUpperArm", "rightLowerArm", "rightHand",
            "right_upper_arm", "right_lower_arm",
            "leftUpperArm", "leftLowerArm", "leftHand",
            "left_upper_arm", "left_lower_arm"
    );
    private static final Set<String> LOWER_BODY_BONES = Set.of(
            "hip",
            "rightUpperLeg", "rightLowerLeg",
            "right_upper_leg", "right_lower_leg",
            "leftUpperLeg", "leftLowerLeg",
            "left_upper_leg", "left_lower_leg"
    );

    private static String currentMovementAnim = "animation.player.idle";
    private static String currentCombatAnim = null;

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
            "animations/sword/anim_sword_dash_attack.animation.json",
            "animations/spear/anim_spear_idle.animation.json",
            "animations/spear/anim_spear_light.animation.json",
            "animations/spear/anim_spear_heavy.animation.json",
            "animations/sword/anim_sword_inspect.animation.json",
            "animations/spear/anim_spear_inspect.animation.json",
    };

    public static void init() {
        LOGGER.info("Combat animation controller initialized (17-bone merge engine)");
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

        // 检视打断：移动时自动退出 INSPECT
        if (cap.getState() == CombatState.INSPECT) {
            double dx = player.getX() - player.xOld;
            double dz = player.getZ() - player.zOld;
            if (dx * dx + dz * dz > 0.001) {
                return; // 移动检测到，由 CombatInputHandler 发送状态切换
            }
        }

        String animName = resolveAnimationName(player, cap);
        if (animName == null) {
            currentAnim = null;
            active = false;
            currentAnimSpeed = 1.0f;
            return;
        }
        float animSpeed = resolveAnimationSpeed(player, animName);
        if (!animName.equals(currentAnim)) {
            boolean instantSwitch = shouldInstantSwitch(currentAnim, animName);
            boolean freezePrevPose = shouldFreezePreviousPose(currentAnim, animName);
            prevAnim = instantSwitch ? null : currentAnim;
            prevAnimStartNano = animStartNano;
            prevAnimSpeed = currentAnimSpeed;
            transitionStartNano = System.nanoTime();
            currentTransitionSecs = resolveTransitionDuration(currentAnim, animName);
            prevAnimFrozenTime = -1.0f;
            freezePrevOnNextApply = !instantSwitch && freezePrevPose;
            currentAnim = animName;
            currentAnimSpeed = animSpeed;
            animStartNano = System.nanoTime();
        } else {
            currentAnimSpeed = animSpeed;
        }
        active = true;
    }

    public static boolean isActive() {
        return active && currentAnim != null;
    }

    /**
     * Apply animation directly to 17-bone model via boneMap.
     * No merging needed — each bone gets its own rotation.
     */
    public static void applyTo17Bones(Map<String, ModelPart> boneMap, AvatarRenderState state) {
        if (!active || currentAnim == null || !ANIM_DATA.containsKey(currentAnim)) return;

        float length = ANIM_LENGTHS.getOrDefault(currentAnim, 1.0f);
        boolean loop = ANIM_LOOPS.getOrDefault(currentAnim, false);
        float timeSec = resolveCurrentTime(state, currentAnim, length, loop, animStartNano, currentAnimSpeed);

        if (!loop && timeSec > length) {
            active = false;
            return;
        }
        if (loop) timeSec = timeSec % length;

        Map<String, List<float[]>> bones = ANIM_DATA.get(currentAnim);
        Map<String, List<float[]>> prevBones = prevAnim != null ? ANIM_DATA.get(prevAnim) : null;
        float prevLength = ANIM_LENGTHS.getOrDefault(prevAnim, 1.0f);
        boolean prevLoop = ANIM_LOOPS.getOrDefault(prevAnim, false);
        if (freezePrevOnNextApply && prevAnim != null && state != null) {
            prevAnimFrozenTime = resolveCurrentTime(state, prevAnim, prevLength, prevLoop, prevAnimStartNano, prevAnimSpeed);
            freezePrevOnNextApply = false;
        }
        float prevTimeSec = prevAnim != null
                ? (prevAnimFrozenTime >= 0.0f
                ? prevAnimFrozenTime
                : resolveCurrentTime(state, prevAnim, prevLength, prevLoop, prevAnimStartNano, prevAnimSpeed))
                : 0;

        // 过渡融合权重
        float transitionAlpha = 1.0f;
        if (prevAnim != null && ANIM_DATA.containsKey(prevAnim)) {
            float transElapsed = (System.nanoTime() - transitionStartNano) / 1_000_000_000.0f;
            if (transElapsed < currentTransitionSecs) {
                transitionAlpha = transElapsed / currentTransitionSecs;
            } else {
                prevAnim = null;
                prevAnimStartNano = 0;
                prevAnimFrozenTime = -1.0f;
                freezePrevOnNextApply = false;
            }
        }

        for (Map.Entry<String, ModelPart> boneEntry : boneMap.entrySet()) {
            String boneName = boneEntry.getKey();
            ModelPart part = boneEntry.getValue();

            List<float[]> kfs = bones.get(boneName);
            if (kfs == null) continue;

            float[] rot = interpolate(kfs, timeSec);

            if (prevBones != null && transitionAlpha < 1.0f) {
                List<float[]> prevKfs = prevBones.get(boneName);
                if (prevKfs != null) {
                    float prevLen = ANIM_LENGTHS.getOrDefault(prevAnim, 1.0f);
                    float prevT = prevLen > 0 ? prevTimeSec % prevLen : 0;
                    float[] prevRot = interpolate(prevKfs, prevT);
                    rot[0] = prevRot[0] + (rot[0] - prevRot[0]) * transitionAlpha;
                    rot[1] = prevRot[1] + (rot[1] - prevRot[1]) * transitionAlpha;
                    rot[2] = prevRot[2] + (rot[2] - prevRot[2]) * transitionAlpha;
                }
            }

            part.xRot = rot[0] * DEG_TO_RAD;
            part.yRot = rot[1] * DEG_TO_RAD;
            part.zRot = rot[2] * DEG_TO_RAD;
        }
    }

    /**
     * Called from Mixin after vanilla setupAnim (6-bone fallback).
     */
    public static void applyToBones(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
        if (!active || currentAnim == null || !ANIM_DATA.containsKey(currentAnim)) return;

        float length = ANIM_LENGTHS.getOrDefault(currentAnim, 1.0f);
        boolean loop = ANIM_LOOPS.getOrDefault(currentAnim, false);
        float timeSec = (System.nanoTime() - animStartNano) / 1_000_000_000.0f;

        if (!loop && timeSec > length) {
            active = false;
            return;
        }
        if (loop) timeSec = timeSec % length;

        Map<String, List<float[]>> bones = ANIM_DATA.get(currentAnim);

        // Accumulate rotations from all 17 bones into the 6 vanilla parts
        float[] headRot = {0, 0, 0}, bodyRot = {0, 0, 0};
        float[] rArmRot = {0, 0, 0}, lArmRot = {0, 0, 0};
        float[] rLegRot = {0, 0, 0}, lLegRot = {0, 0, 0};

        for (Map.Entry<String, List<float[]>> entry : bones.entrySet()) {
            String boneName = entry.getKey();
            String target = BONE_TO_VANILLA.get(boneName);
            if (target == null) continue;

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

        float[] before = keyframes.get(0);
        float[] after = keyframes.get(keyframes.size() - 1);

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

    private static String resolveAnimationName(AbstractClientPlayer player, ICombatCapability cap) {
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
            case ATTACK_HEAVY: return weapon == WeaponType.SPEAR ? "animation.player.spear_heavy" : "animation.player.sword_heavy";
            case INSPECT: return weapon == WeaponType.SPEAR ? "animation.player.spear_inspect" : "animation.player.sword_inspect";
            default: break;
        }

        // Detect actual movement from the player entity
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
            boolean wasMoving = "animation.player.walk".equals(lastMovementAnim)
                    || "animation.player.run".equals(lastMovementAnim);
            if (hSpeedSq > (wasMoving ? 0.00001 : 0.0004)) {
                detected = "animation.player.walk";
            } else {
                detected = "animation.player.idle";
            }
        }

        // Hold timer: require consistent state for a few ticks before switching
        if (detected.equals(lastMovementAnim)) {
            movementHoldTicks = 0;
        } else {
            movementHoldTicks++;
            if (movementHoldTicks < 3) {
                return lastMovementAnim; // keep previous state during hold period
            }
            movementHoldTicks = 0;
        }
        lastMovementAnim = detected;
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
            return "animation.player.spear_light";
        }
        return null;
    }

    private static float resolveAnimationSpeed(AbstractClientPlayer player, String animName) {
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

    private static boolean isLocomotionAnimation(String animName) {
        return "animation.player.walk".equals(animName)
                || "animation.player.run".equals(animName);
    }

    private static boolean isJumpAnimation(String animName) {
        return "animation.player.jump".equals(animName);
    }

    private static boolean isGroundedMovementAnimation(String animName) {
        return "animation.player.idle".equals(animName) || isLocomotionAnimation(animName);
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
            return true;
        }
        if (shouldUseRecoveryBlend(fromAnim, toAnim)) {
            return false;
        }
        return isLocomotionAnimation(fromAnim)
                || isLocomotionAnimation(toAnim)
                || "animation.player.idle".equals(fromAnim)
                || "animation.player.idle".equals(toAnim);
    }

    private static boolean shouldFreezePreviousPose(String fromAnim, String toAnim) {
        return shouldUseRecoveryBlend(fromAnim, toAnim);
    }

    private static float resolveTransitionDuration(String fromAnim, String toAnim) {
        if (shouldFreezePreviousPose(fromAnim, toAnim)) {
            return STOP_TRANSITION_SECS;
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
}
