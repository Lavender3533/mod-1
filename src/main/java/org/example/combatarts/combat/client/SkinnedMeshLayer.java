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

    // 按玩家 ID 存过渡状态，避免多玩家共用单例 renderer 时互相污染
    private static final java.util.Map<Integer, PlayerAnimState> perPlayerState = new java.util.HashMap<>();

    private static class PlayerAnimState {
        String prevAnimName = "idle";
        String prevLocoAnim = "idle";
        Pose prevPose = new Pose();
        float transitionTimer = 0f;
        float currentTransitionDuration = TRANSITION_DURATION;
        long lastFrameTime = 0;
        CombatState lastCombatState = CombatState.IDLE;
    }

    private static PlayerAnimState getState(int entityId) {
        return perPlayerState.computeIfAbsent(entityId, k -> new PlayerAnimState());
    }

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

        // Draw/sheath: only right arm from combat, everything else from locomotion
        java.util.Set<String> drawArmJoints = java.util.Set.of(
                "Shoulder_R", "Arm_R", "Hand_R", "Tool_R", "Elbow_R");

        boolean isDrawSheath = "draw_weapon".equals(animName) || "sheath_weapon".equals(animName);
        boolean isBlock = "block".equals(animName);
        boolean isInspect = "inspect".equals(animName);
        boolean isHeavyCharge = "sword_heavy_charge".equals(animName);
        boolean isAttack = java.util.Set.of(
                "sword_light_1", "sword_light_2", "sword_light_3", "sword_dash",
                "parry"
        ).contains(animName);
        boolean isDodge = "dodge".equals(animName);

        // 当前帧使用的 locomotion 子动画名（仅在 isBlock/isHeavyCharge/isDrawSheath 分支会赋值）。
        // 用于触发子动画切换时的过渡插值，避免 idle ↔ walk 硬切导致的腿部抽动。
        String activeLocoAnim = null;

        Pose targetPose;
        String debugAnim = BlockPoseTweaker.getDebugTargetAnim();
        if (debugAnim != null) {
            // ==== 调试冻结模式 ====
            // 模型冻结到目标 anim 的 frame 0 + EF tweaker 偏移叠加。按 ; 切换目标 anim,
            // 切到 OFF 时退出冻结。EF 偏移只在冻结时生效,不污染正常游戏中的任何状态。
            // 用 hold_longsword 作为完整身体基础,目标 anim 的 joint(通常只覆盖右臂)叠加上去。
            Pose basePose = MeshManager.getPoseAtTime("hold_longsword", 0f);
            if (!"hold_longsword".equals(debugAnim)) {
                Pose overlay = MeshManager.getPoseAtTime(debugAnim, 0f);
                overlay.forEachEnabledTransforms((name, jt) ->
                        basePose.putJointData(name, jt.copy()));
            }
            targetPose = basePose;
            // 蓄力调试时，先叠上当前 isHeavyCharge 烘焙的 tweak，让起点就是游戏里的实际姿势，
            // 然后用户的 tweaker 偏移再叠在上面 → 直接微调，不用从零摸。
            if ("sword_heavy_charge".equals(debugAnim)) {
                applyTweakToJoint(targetPose, armature, "Shoulder_R",  20,  -10,  20);
                applyTweakToJoint(targetPose, armature, "Arm_R",       40,  -60,   0);
                applyTweakToJoint(targetPose, armature, "Hand_R",     -50,  -20, -10);
                applyTweakToJoint(targetPose, armature, "Hand_L",      30,   20, -10);
            }
            for (String jointName : BlockPoseTweaker.EF_BLOCK_JOINTS) {
                applyTweakToJoint(targetPose, armature, jointName,
                        BlockPoseTweaker.getEfDelta(jointName, 0),
                        BlockPoseTweaker.getEfDelta(jointName, 1),
                        BlockPoseTweaker.getEfDelta(jointName, 2));
            }
        } else if (isDrawSheath) {
            // Draw/sheath: right arm from combat, everything else from locomotion
            float combatTime = computeAnimTime(player, animName, state);
            Pose combatPose = MeshManager.getPoseAtTime(animName, combatTime);
            String locoAnim = resolveLocomotion(player);
            activeLocoAnim = locoAnim;
            float locoTime = computeAnimTime(player, locoAnim, state);
            Pose locoPose = MeshManager.getPoseAtTime(locoAnim, locoTime);

            targetPose = new Pose();
            locoPose.forEachEnabledTransforms((name, jt) ->
                    targetPose.putJointData(name, jt.copy()));
            combatPose.forEachEnabledTransforms((name, jt) -> {
                if (drawArmJoints.contains(name))
                    targetPose.putJointData(name, jt.copy());
            });
            // 拔刀转刀: 不在 Tool_R 这里改，而是在 CombatItemInHandLayer 通过 PoseStack 直接转剑模型，
            //          配合 held_rot 的 Y/Z 偏移避免穿身/穿手臂。这里不再覆盖 Tool_R。
        } else if (isAttack) {
            // 上下半身分离：上半身用攻击动画(挥砍/突刺手感)，下半身用 locomotion，
            // 让玩家能边走边砍 + 腿正常踏步(不再滑步)。冲刺攻击 (combo 99)
            // 也走这条路，靠玩家原冲刺动量送出去。文档 6.5 要求"支持动画混合(移动+攻击)"。
            float combatTime = computeAnimTime(player, animName, state);
            Pose combatPose = MeshManager.getPoseAtTime(animName, combatTime);
            String locoAnim = resolveLocomotion(player);
            activeLocoAnim = locoAnim;
            float locoTime = computeAnimTime(player, locoAnim, state);
            Pose locoPose = MeshManager.getPoseAtTime(locoAnim, locoTime);

            java.util.Set<String> legJoints = java.util.Set.of(
                    "Thigh_R", "Leg_R", "Knee_R",
                    "Thigh_L", "Leg_L", "Knee_L");

            targetPose = new Pose();
            // 1. 全身铺攻击动画(包括 Root/Torso 扭转 → 挥砍的力道感不能丢)
            combatPose.forEachEnabledTransforms((name, jt) ->
                    targetPose.putJointData(name, jt.copy()));
            // 2. 仅腿覆盖为 locomotion → 走/跑/idle 跟着踏步
            locoPose.forEachEnabledTransforms((name, jt) -> {
                if (legJoints.contains(name)) {
                    targetPose.putJointData(name, jt.copy());
                }
            });
        } else if (isBlock) {
            // Block: hold pose. Arms from programmatic block, body/legs from locomotion
            // (player can stand still or slowly back up while blocking).
            float combatTime = computeAnimTime(player, animName, state);
            Pose combatPose = MeshManager.getPoseAtTime(animName, combatTime);
            String locoAnim = resolveLocomotion(player);
            activeLocoAnim = locoAnim;
            float locoTime = computeAnimTime(player, locoAnim, state);
            Pose locoPose = MeshManager.getPoseAtTime(locoAnim, locoTime);

            targetPose = new Pose();
            locoPose.forEachEnabledTransforms((name, jt) ->
                    targetPose.putJointData(name, jt.copy()));
            // 覆盖 block 中设置的关节(只有手臂)
            combatPose.forEachEnabledTransforms((name, jt) ->
                    targetPose.putJointData(name, jt.copy()));

            // 烘焙的 BLOCK 姿势精调值(用户 tweaker 调出的)。world-space ZYX 顺序应用,
            // 顺序: shoulder → arm → hand,与 tweaker 实时调试时一致。
            applyTweakToJoint(targetPose, armature, "Shoulder_R", -130,  20,  40);
            applyTweakToJoint(targetPose, armature, "Arm_R",       -60,  60,   0);
            applyTweakToJoint(targetPose, armature, "Hand_R",      -10,   0,  70);
            applyTweakToJoint(targetPose, armature, "Shoulder_L",  -60, -20,  20);
            applyTweakToJoint(targetPose, armature, "Arm_L",         0, -10,  20);
            applyTweakToJoint(targetPose, armature, "Hand_L",      -60,  10, -80);

            // 实时 tweaker 偏移(调好后通常归零,留作以后再微调用)
            for (String jointName : BlockPoseTweaker.EF_BLOCK_JOINTS) {
                applyTweakToJoint(targetPose, armature, jointName,
                        BlockPoseTweaker.getEfDelta(jointName, 0),
                        BlockPoseTweaker.getEfDelta(jointName, 1),
                        BlockPoseTweaker.getEfDelta(jointName, 2));
            }
        } else if (isHeavyCharge) {
            // 重击蓄力：身体/腿用 locomotion，左臂用 hold_longsword 持刀基础，
            // 右臂用 sword_heavy_charge 程序化数据（剑举到肩后/背后，准备劈下）。
            // 数值在 MeshManager.createHeavyChargeAnimation —— 想微调直接改那里。
            String locoAnim = resolveLocomotion(player);
            activeLocoAnim = locoAnim;
            float locoTime = computeAnimTime(player, locoAnim, state);
            Pose locoPose = MeshManager.getPoseAtTime(locoAnim, locoTime);
            Pose holdPose = MeshManager.getPoseAtTime("hold_longsword", 0f);
            Pose chargePose = MeshManager.getPoseAtTime("sword_heavy_charge", 0f);

            targetPose = new Pose();
            // 1. 全身铺 locomotion（含腿动画）
            locoPose.forEachEnabledTransforms((name, jt) ->
                    targetPose.putJointData(name, jt.copy()));
            // 2. 躯干/头/颈用 hold_longsword 持刀基础（避免 loco 自带的躯干摇摆破坏蓄力体感）
            holdPose.forEachEnabledTransforms((name, jt) -> {
                if (name.equals("Torso") || name.equals("Chest") || name.equals("Head")
                        || name.equals("Neck")) {
                    targetPose.putJointData(name, jt.copy());
                }
            });
            // 3. 双臂铺 hold_longsword（左臂自然下垂 + 右臂打底）
            holdPose.forEachEnabledTransforms((name, jt) -> {
                if (name.startsWith("Shoulder_") || name.startsWith("Arm_")
                        || name.startsWith("Hand_") || name.startsWith("Elbow_")
                        || name.startsWith("Tool_")) {
                    targetPose.putJointData(name, jt.copy());
                }
            });
            // 4. 用 sword_heavy_charge 的右臂关节覆盖（举剑过头）
            chargePose.forEachEnabledTransforms((name, jt) ->
                    targetPose.putJointData(name, jt.copy()));
            // 5. 烘焙的精调值（用户 tweaker 调出的）：
            //    剑往体侧外打开 + 肘前折 + 腕收 + 左手扶柄 → 真正的"备砍"体感
            applyTweakToJoint(targetPose, armature, "Shoulder_R",  20,  -10,  20);
            applyTweakToJoint(targetPose, armature, "Arm_R",       40,  -60,   0);
            applyTweakToJoint(targetPose, armature, "Hand_R",     -50,  -20, -10);
            applyTweakToJoint(targetPose, armature, "Hand_L",      30,   20, -10);
            // 6. 实时 tweaker 偏移（调好后通常归零，留作以后再微调用）
            for (String jointName : BlockPoseTweaker.EF_BLOCK_JOINTS) {
                applyTweakToJoint(targetPose, armature, jointName,
                        BlockPoseTweaker.getEfDelta(jointName, 0),
                        BlockPoseTweaker.getEfDelta(jointName, 1),
                        BlockPoseTweaker.getEfDelta(jointName, 2));
            }
        } else if (isInspect) {
            String locoAnim = resolveLocomotion(player);
            activeLocoAnim = locoAnim;
            float locoTime = computeAnimTime(player, locoAnim, state);
            Pose locoPose = MeshManager.getPoseAtTime(locoAnim, locoTime);
            Pose holdPose = MeshManager.getPoseAtTime("hold_longsword", 0f);

            targetPose = new Pose();
            locoPose.forEachEnabledTransforms((name, jt) ->
                    targetPose.putJointData(name, jt.copy()));
            holdPose.forEachEnabledTransforms((name, jt) -> {
                if (name.startsWith("Shoulder_") || name.startsWith("Arm_")
                        || name.startsWith("Hand_") || name.startsWith("Elbow_")
                        || name.startsWith("Tool_")
                        || name.equals("Torso") || name.equals("Chest") || name.equals("Head")) {
                    targetPose.putJointData(name, jt.copy());
                }
            });

            // 两段检视动画: A(看刃面) → B(换角度) → 收回，值用 applyTweakToJoint 乘到 hold 上
            float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            float gameTime = (float)(Minecraft.getInstance().level != null ?
                    Minecraft.getInstance().level.getGameTime() : 0) + partial;
            float t = (gameTime * 0.05f) % 4.0f;

            applyInspectPhase(targetPose, armature, t, "Shoulder_R",  30,0,0,     60,-10,-10);
            applyInspectPhase(targetPose, armature, t, "Arm_R",       0,0,0,      0,10,-10);
            applyInspectPhase(targetPose, armature, t, "Hand_R",      30,0,20,    0,0,40);
            applyInspectPhase(targetPose, armature, t, "Shoulder_L",  40,-10,0,   0,-10,-10);
            applyInspectPhase(targetPose, armature, t, "Arm_L",       0,0,0,      -10,-10,0);
            applyInspectPhase(targetPose, armature, t, "Hand_L",      50,-20,-30, 0,0,0);
        } else if (isDodge) {
            // Dodge: full body
            float combatTime = computeAnimTime(player, animName, state);
            targetPose = MeshManager.getPoseAtTime(animName, combatTime);
        } else {
            float animTime = computeAnimTime(player, animName, state);
            targetPose = MeshManager.getPoseAtTime(animName, animTime);
        }

        // Transition blending (per-player state)
        PlayerAnimState ps = getState(state.id);
        long now = System.nanoTime();
        float dt = ps.lastFrameTime == 0 ? 0.016f : (now - ps.lastFrameTime) / 1_000_000_000f;
        ps.lastFrameTime = now;
        dt = Math.min(dt, 0.1f);

        if (!animName.equals(ps.prevAnimName)) {
            ps.currentTransitionDuration = isTimedAnimation(animName) || isTimedAnimation(ps.prevAnimName)
                    ? COMBAT_TRANSITION_DURATION : TRANSITION_DURATION;
            ps.prevAnimName = animName;
            ps.transitionTimer = ps.currentTransitionDuration;
        } else if (activeLocoAnim != null && !activeLocoAnim.equals(ps.prevLocoAnim)) {
            ps.currentTransitionDuration = TRANSITION_DURATION;
            ps.transitionTimer = ps.currentTransitionDuration;
        }
        if (activeLocoAnim != null) ps.prevLocoAnim = activeLocoAnim;

        Pose finalPose;
        if (ps.transitionTimer > 0) {
            ps.transitionTimer -= dt;
            float alpha = 1.0f - Math.max(0, ps.transitionTimer) / ps.currentTransitionDuration;
            alpha = alpha * alpha * (3 - 2 * alpha);
            finalPose = Pose.interpolatePose(ps.prevPose, targetPose, alpha);
        } else {
            finalPose = targetPose;
        }

        ps.prevPose = new Pose();
        finalPose.forEachEnabledTransforms((name, jt) ->
            ps.prevPose.putJointData(name, jt.copy()));

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
            // 玩家不动时，walkAnimationPos 卡在上次走动的累积值，会让动画冻结在中间帧
            // (sneak 表现为"右膝深屈左膝直"的起跑姿势)。stationary 时强制回 t=0 (loop 起始)
            // 让姿态稳定。EF 走的是 playSpeed→0 路径，效果类似但起点同样不可控，所以我们直接锁死。
            if (state.walkAnimationSpeed < 0.01f) {
                return 0f;
            }
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
            getState(player.getId()).lastCombatState = state;

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
                case ATTACK_HEAVY -> result[0] = "sword_light_3";       // 重击释放复用 light_3 大幅劈砍(从蓄力举剑过头自然衔接)
                case ATTACK_HEAVY_CHARGING -> result[0] = "sword_heavy_charge";   // 静态蓄力 hold
                case DODGE -> result[0] = "dodge";
                case BLOCK -> result[0] = "block";
                case PARRY -> result[0] = "parry";
                case INSPECT -> result[0] = "inspect";
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

    // 两段检视插值: 0→0.4 过渡到A, 0.4→1.4 保持A, 1.4→2.0 过渡到B, 2.0→3.0 保持B, 3.0→3.7 收回
    private static void applyInspectPhase(Pose pose, Armature armature, float t,
            String joint,
            float ax, float ay, float az,
            float bx, float by, float bz) {
        float rx, ry, rz;
        if (t < 0.4f) {
            float a = t / 0.4f;
            rx = ax * a; ry = ay * a; rz = az * a;
        } else if (t < 1.4f) {
            rx = ax; ry = ay; rz = az;
        } else if (t < 2.0f) {
            float a = (t - 1.4f) / 0.6f;
            rx = ax + (bx - ax) * a; ry = ay + (by - ay) * a; rz = az + (bz - az) * a;
        } else if (t < 3.0f) {
            rx = bx; ry = by; rz = bz;
        } else if (t < 3.7f) {
            float a = (t - 3.0f) / 0.7f;
            rx = bx * (1 - a); ry = by * (1 - a); rz = bz * (1 - a);
        } else {
            rx = 0; ry = 0; rz = 0;
        }
        if (rx != 0 || ry != 0 || rz != 0) {
            applyTweakToJoint(pose, armature, joint, rx, ry, rz);
        }
    }

    @SuppressWarnings("unused")
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
