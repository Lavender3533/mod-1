package org.example.combatarts.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.example.combatarts.combat.CombatState;
import org.example.combatarts.combat.capability.CombatCapabilityEvents;
import org.example.combatarts.combat.capability.ICombatCapability;
import org.example.combatarts.combat.client.render.mesh.*;
import org.joml.Quaternionf;

public class SkinnedMeshLayer extends RenderLayer<AvatarRenderState, CombatPlayerModel> {

    private static final float TRANSITION_DURATION = 0.15f;
    private static final float COMBAT_TRANSITION_DURATION = 0.12f;

    private String prevAnimName = "idle";
    private Pose prevPose = new Pose();
    private float transitionTimer = 0f;
    private float currentTransitionDuration = TRANSITION_DURATION;
    private long lastFrameTime = 0;

    private CombatState lastCombatState = CombatState.IDLE;

    public SkinnedMeshLayer(RenderLayerParent<AvatarRenderState, CombatPlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        Armature armature = MeshManager.getArmature();
        SkinnedMesh mesh = MeshManager.getMesh();
        if (armature == null || mesh == null) return;

        Player player = resolvePlayer(state);

        // Use EF animations for all states
        String animName = resolveAnimation(player);

        // Freeze pose for EF joint tweaking
        boolean tweakMode = BlockPoseTweaker.isEfTweakActive();

        // Draw/sheath: only right arm from combat, everything else from locomotion
        java.util.Set<String> drawArmJoints = java.util.Set.of(
                "Shoulder_R", "Arm_R", "Hand_R", "Tool_R", "Elbow_R");

        boolean isDrawSheath = "draw_weapon".equals(animName) || "sheath_weapon".equals(animName);
        boolean isAttack = java.util.Set.of(
                "sword_light_1", "sword_light_2", "sword_light_3", "sword_dash",
                "block", "parry"
        ).contains(animName);
        boolean isDodge = "dodge".equals(animName);

        Pose targetPose;
        if (tweakMode) {
            targetPose = MeshManager.getPoseAtTime("idle", 0);
            applyTweakToJoint(targetPose, armature, "Arm_R",
                    BlockPoseTweaker.getEfShoulder(0),
                    BlockPoseTweaker.getEfShoulder(1),
                    BlockPoseTweaker.getEfShoulder(2));
            applyTweakToJoint(targetPose, armature, "Hand_R",
                    BlockPoseTweaker.getEfArm(0),
                    BlockPoseTweaker.getEfArm(1),
                    BlockPoseTweaker.getEfArm(2));
        } else if (isDrawSheath) {
            // Draw/sheath: right arm from combat, everything else from locomotion
            float combatTime = computeAnimTime(player, animName, state);
            Pose combatPose = MeshManager.getPoseAtTime(animName, combatTime);
            String locoAnim = resolveLocomotion(player);
            float locoTime = computeAnimTime(player, locoAnim, state);
            Pose locoPose = MeshManager.getPoseAtTime(locoAnim, locoTime);

            targetPose = new Pose();
            locoPose.forEachEnabledTransforms((name, jt) ->
                    targetPose.putJointData(name, jt.copy()));
            combatPose.forEachEnabledTransforms((name, jt) -> {
                if (drawArmJoints.contains(name))
                    targetPose.putJointData(name, jt.copy());
            });
        } else if (isAttack) {
            // Attacks/block/parry: full body from combat animation (EF-style).
            // Movement is hard-slowed by SLOWNESS V during attacks (see CombatCapabilityEvents),
            // so we don't need to blend in locomotion legs — sliding won't be visible.
            float combatTime = computeAnimTime(player, animName, state);
            targetPose = MeshManager.getPoseAtTime(animName, combatTime);
        } else if (isDodge) {
            // Dodge: full body
            float combatTime = computeAnimTime(player, animName, state);
            targetPose = MeshManager.getPoseAtTime(animName, combatTime);
        } else {
            float animTime = computeAnimTime(player, animName, state);
            targetPose = MeshManager.getPoseAtTime(animName, animTime);
        }

        // Transition blending
        long now = System.nanoTime();
        float dt = lastFrameTime == 0 ? 0.016f : (now - lastFrameTime) / 1_000_000_000f;
        lastFrameTime = now;
        dt = Math.min(dt, 0.1f);

        if (!animName.equals(prevAnimName)) {
            currentTransitionDuration = isTimedAnimation(animName) || isTimedAnimation(prevAnimName)
                    ? COMBAT_TRANSITION_DURATION : TRANSITION_DURATION;
            prevAnimName = animName;
            transitionTimer = currentTransitionDuration;
        }

        Pose finalPose;
        if (transitionTimer > 0) {
            transitionTimer -= dt;
            float alpha = 1.0f - Math.max(0, transitionTimer) / currentTransitionDuration;
            alpha = alpha * alpha * (3 - 2 * alpha);
            finalPose = Pose.interpolatePose(prevPose, targetPose, alpha);
        } else {
            finalPose = targetPose;
        }

        prevPose = new Pose();
        finalPose.forEachEnabledTransforms((name, jt) ->
            prevPose.putJointData(name, jt.copy()));

        // Head rotation tracking
        if (player != null && finalPose.hasTransform("Head")) {
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            float headYawRel = net.minecraft.util.Mth.rotLerp(partialTick,
                    net.minecraft.util.Mth.wrapDegrees(player.yBodyRotO - player.yHeadRotO),
                    net.minecraft.util.Mth.wrapDegrees(player.yBodyRot - player.yHeadRot));
            float headPitch = -net.minecraft.util.Mth.rotLerp(partialTick, player.xRotO, player.getXRot());

            OpenMatrix4f headRotMatrix = OpenMatrix4f.createRotatorDeg(headYawRel, Vec3f.Y_AXIS)
                    .rotateDeg(headPitch, Vec3f.X_AXIS);

            finalPose.orElseEmpty("Head").frontResult(
                    JointTransform.fromMatrixWithoutScale(headRotMatrix), OpenMatrix4f::mul);
        }

        armature.setPose(finalPose);

        Identifier skinTex = state.skin.body().texturePath();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(
                TriangulatedRenderType.entityTriangles(skinTex));

        poseStack.pushPose();
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        mesh.drawPosed(poseStack, buffer, Mesh.DrawingFunction.NEW_ENTITY,
                packedLight, 1.0f, 1.0f, 1.0f, 1.0f,
                OverlayTexture.NO_OVERLAY, armature, armature.getPoseMatrices());
        poseStack.popPose();

        bufferSource.endBatch();
    }

    private float computeAnimTime(Player player, String animName, AvatarRenderState state) {
        float animLength = MeshManager.getAnimLength(animName);
        if (animLength <= 0) return 0;

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

        // Timed combat animations — sync to state timer
        if (isTimedAnimation(animName) && player != null) {
            float elapsed = getTimedAnimElapsed(player, partialTick);
            if (elapsed >= 0) {
                return Math.min(elapsed, animLength);
            }
        }

        if (animName.contains("walk") || animName.contains("run") || animName.equals("sneak")) {
            float walkPos = state.walkAnimationPos;
            float speed = animName.contains("run") ? 0.05f : 0.08f;
            if (animName.equals("sneak")) speed = 0.06f;
            return (walkPos * speed) % animLength;
        }

        float gameTime = (float)(Minecraft.getInstance().level != null ?
                Minecraft.getInstance().level.getGameTime() : 0) + partialTick;
        return (gameTime * 0.05f) % animLength;
    }

    private String resolveAnimation(Player player) {
        if (player == null) return "idle";

        var combatOpt = CombatCapabilityEvents.getCombat(player);
        final String[] result = {null};

        combatOpt.ifPresent(cap -> {
            CombatState state = cap.getState();
            lastCombatState = state;

            switch (state) {
                case DRAW_WEAPON -> result[0] = "draw_weapon";
                case SHEATH_WEAPON -> result[0] = "sheath_weapon";
                case ATTACK_LIGHT -> {
                    int combo = cap.getComboCount();
                    if (combo == 99) result[0] = "sword_dash";
                    else result[0] = switch (combo) {
                        case 2 -> "sword_light_2";
                        case 3 -> "sword_light_3";
                        default -> "sword_light_1";
                    };
                }
                case ATTACK_HEAVY, ATTACK_HEAVY_CHARGING -> result[0] = "sword_light_3";
                case DODGE -> result[0] = "dodge";
                case BLOCK -> result[0] = "block";
                case PARRY -> result[0] = "parry";
                default -> {}
            }
        });

        if (result[0] != null && MeshManager.getAnimLength(result[0]) > 0) {
            return result[0];
        }

        boolean weaponDrawn = combatOpt.map(cap -> cap.isWeaponDrawn()).orElse(false);

        if (player.isCrouching()) {
            return "sneak";
        } else if (player.isSprinting()) {
            return weaponDrawn ? "run_longsword" : "run";
        } else {
            double dx = player.getX() - player.xOld;
            double dz = player.getZ() - player.zOld;
            double hSpeedSq = dx * dx + dz * dz;
            if (hSpeedSq > 0.0004) {
                return weaponDrawn ? "walk_longsword" : "walk";
            }
        }

        return weaponDrawn ? "hold_longsword" : "idle";
    }

    private float getTimedAnimElapsed(Player player, float partialTick) {
        var combatOpt = CombatCapabilityEvents.getCombat(player);
        final float[] elapsed = {-1f};
        combatOpt.ifPresent(cap -> {
            CombatState state = cap.getState();
            if (state.isTimed()) {
                int durationTicks = state.getDurationTicks();
                int remaining = cap.getStateTimer();
                elapsed[0] = (durationTicks - remaining + partialTick) / 20.0f;
            }
        });
        return elapsed[0];
    }

    private String resolveLocomotion(Player player) {
        if (player == null) return "idle";
        if (player.isCrouching()) return "sneak";
        if (player.isSprinting()) return "run";
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        if (dx * dx + dz * dz > 0.0004) return "walk";
        return "idle";
    }

    private static boolean isTimedAnimation(String animName) {
        return animName != null && !animName.contains("idle") && !animName.contains("walk")
                && !animName.contains("run") && !animName.equals("sneak")
                && !animName.equals("hold_longsword");
    }

    private static boolean hasTweakValues() {
        for (int i = 0; i < 3; i++) {
            if (BlockPoseTweaker.getHeldRot(i) != 0) return true;
            if (BlockPoseTweaker.getHeldPos(i) != 0) return true;
        }
        return false;
    }

    private static void applyTweakToJoint(Pose pose, Armature armature, String jointName,
                                           float rxDeg, float ryDeg, float rzDeg) {
        if (!armature.hasJoint(jointName)) return;
        if (rxDeg == 0 && ryDeg == 0 && rzDeg == 0) return;

        Joint joint = armature.searchJointByName(jointName);
        JointTransform existing = pose.orElseEmpty(jointName);

        // Recover absolute matrix: absolute = local * delta
        OpenMatrix4f local = joint.getLocalTransform();
        OpenMatrix4f delta = existing.toMatrix();
        OpenMatrix4f absolute = OpenMatrix4f.mul(local, delta, null);

        // Apply tweaker rotation to absolute 3x3 (same as Python apply_rot)
        OpenMatrix4f rot = OpenMatrix4f.createRotatorDeg(rxDeg, Vec3f.X_AXIS)
                .rotateDeg(ryDeg, Vec3f.Y_AXIS)
                .rotateDeg(rzDeg, Vec3f.Z_AXIS);
        OpenMatrix4f newAbsolute = OpenMatrix4f.mul(rot, absolute, null);
        // Keep original translation
        newAbsolute.m30 = absolute.m30;
        newAbsolute.m31 = absolute.m31;
        newAbsolute.m32 = absolute.m32;

        // Compute new delta: invLocal * newAbsolute
        OpenMatrix4f invLocal = new OpenMatrix4f(local);
        invLocal.invert();
        OpenMatrix4f newDelta = OpenMatrix4f.mul(invLocal, newAbsolute, null);

        JointTransform newJt = JointTransform.fromMatrix(newDelta);
        newJt.rotation().normalize();
        pose.putJointData(jointName, newJt);
    }

    private static Player resolvePlayer(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(state.id);
        return entity instanceof Player p ? p : null;
    }
}
