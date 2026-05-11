package org.example.combatarts.combat.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.example.combatarts.combat.client.render.mesh.Armature;
import org.example.combatarts.combat.client.render.mesh.Joint;
import org.example.combatarts.combat.client.render.mesh.MeshManager;
import org.example.combatarts.combat.client.render.mesh.OpenMatrix4f;

/**
 * 让 vanilla 盔甲渲染跟随我们的 mod armature。
 * setupAnim 时, 从 mod 骨骼 matrix 提取 ZYX Euler, 设到 vanilla 对应 ModelPart 上。
 * vanilla EquipmentLayerRenderer 读这些 part 的 rotation 来定位盔甲 cube。
 *
 * 不修改 mod armature, 不影响 mod mesh 渲染或战斗逻辑 — 纯被动消费骨骼数据。
 */
public class BridgedHumanoidModel extends HumanoidModel<AvatarRenderState> {

    public BridgedHumanoidModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state); // vanilla 默认 setup (走路摆动等), 给我们一个合理 baseline 兜底

        Armature armature = MeshManager.getArmature();
        if (armature == null) return;
        OpenMatrix4f[] poses = armature.getPoseMatrices();
        if (poses == null) return;

        // mod armature joint 的 world-space matrix → ZYX Euler → 设到 vanilla part。
        // vanilla 的 head/body/leftArm/rightArm/leftLeg/rightLeg 都直接挂 model root,
        // 它们的 xRot/yRot/zRot 就是 world rotation, 跟 mod armature joint 的 world matrix 直接对应。
        applyJointToPart(armature, poses, "Head", this.head);
        applyJointToPart(armature, poses, "Head", this.hat);
        applyJointToPart(armature, poses, "Chest", this.body);
        applyJointToPart(armature, poses, "Arm_R", this.rightArm);
        applyJointToPart(armature, poses, "Arm_L", this.leftArm);
        applyJointToPart(armature, poses, "Thigh_R", this.rightLeg);
        applyJointToPart(armature, poses, "Thigh_L", this.leftLeg);
    }

    private static void applyJointToPart(Armature armature, OpenMatrix4f[] poses, String jointName, ModelPart part) {
        Joint joint = armature.searchJointByName(jointName);
        if (joint == null) return;
        int id = joint.getId();
        if (id < 0 || id >= poses.length) return;
        OpenMatrix4f m = poses[id];
        if (m == null) return;

        float[] euler = matrixToEulerZYX(m);
        part.xRot = euler[0];
        part.yRot = euler[1];
        part.zRot = euler[2];
    }

    /**
     * 从 4x4 旋转矩阵提取 ZYX Euler (R = Rz * Ry * Rx)。返回 [xRot, yRot, zRot] 弧度。
     * 跟 JOML Quaternionf.rotationZYX(zRot, yRot, xRot) 的约定一致 — vanilla ModelPart 用的就是这个。
     */
    private static float[] matrixToEulerZYX(OpenMatrix4f m) {
        float yRot = (float) Math.asin(Math.max(-1f, Math.min(1f, -m.m20)));
        float xRot, zRot;
        if (Math.abs(m.m20) < 0.99999f) {
            xRot = (float) Math.atan2(m.m21, m.m22);
            zRot = (float) Math.atan2(m.m10, m.m00);
        } else {
            // 万向锁: m20 = ±1 → yRot = ±π/2, xRot 和 zRot 不可分, 把 zRot 设 0
            xRot = (float) Math.atan2(-m.m12, m.m11);
            zRot = 0f;
        }
        return new float[]{xRot, yRot, zRot};
    }
}
