package org.example.mod_1.mod_1.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import java.util.HashMap;
import java.util.Map;

public class CombatPlayerModel extends EntityModel<AvatarRenderState>
        implements ArmedModel<AvatarRenderState>, HeadedModel {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath("mod_1", "combat_player"), "main");

    public final Map<String, ModelPart> boneMap = new HashMap<>();

    public final ModelPart root;
    public final ModelPart hip;
    public final ModelPart waist;
    public final ModelPart chest;
    public final ModelPart neck;
    public final ModelPart head;
    public final ModelPart rightUpperArm;
    public final ModelPart rightLowerArm;
    public final ModelPart rightHand;
    public final ModelPart leftUpperArm;
    public final ModelPart leftLowerArm;
    public final ModelPart leftHand;
    public final ModelPart rightUpperLeg;
    public final ModelPart rightLowerLeg;
    public final ModelPart leftUpperLeg;
    public final ModelPart leftLowerLeg;
    public final ModelPart weaponMount;

    // Overlay (second layer) parts — full-size, no splits
    public final ModelPart hat;
    public final ModelPart jacket;
    public final ModelPart rightSleeve;
    public final ModelPart leftSleeve;
    public final ModelPart rightPants;
    public final ModelPart leftPants;

    public CombatPlayerModel(ModelPart bakedRoot) {
        // Use entityTranslucent like vanilla PlayerModel for proper overlay alpha
        super(bakedRoot, RenderTypes::entityTranslucent);
        this.root = bakedRoot.getChild("root");
        this.hip = root.getChild("hip");
        this.waist = hip.getChild("waist");
        this.chest = waist.getChild("chest");
        this.neck = chest.getChild("neck");
        this.head = neck.getChild("head");
        this.rightUpperArm = chest.getChild("rightUpperArm");
        this.rightLowerArm = rightUpperArm.getChild("rightLowerArm");
        this.rightHand = rightLowerArm.getChild("rightHand");
        this.leftUpperArm = chest.getChild("leftUpperArm");
        this.leftLowerArm = leftUpperArm.getChild("leftLowerArm");
        this.leftHand = leftLowerArm.getChild("leftHand");
        this.rightUpperLeg = hip.getChild("rightUpperLeg");
        this.rightLowerLeg = rightUpperLeg.getChild("rightLowerLeg");
        this.leftUpperLeg = hip.getChild("leftUpperLeg");
        this.leftLowerLeg = leftUpperLeg.getChild("leftLowerLeg");
        this.weaponMount = rightHand.getChild("weaponMount");

        // Full-size overlay parts (like vanilla — no splitting)
        this.hat = head.getChild("hat");
        this.jacket = chest.getChild("jacket");
        this.rightSleeve = rightUpperArm.getChild("right_sleeve");
        this.leftSleeve = leftUpperArm.getChild("left_sleeve");
        this.rightPants = rightUpperLeg.getChild("right_pants");
        this.leftPants = leftUpperLeg.getChild("left_pants");

        boneMap.put("root", root);
        boneMap.put("hip", hip);
        boneMap.put("waist", waist);
        boneMap.put("chest", chest);
        boneMap.put("neck", neck);
        boneMap.put("head", head);
        boneMap.put("rightUpperArm", rightUpperArm);
        boneMap.put("rightLowerArm", rightLowerArm);
        boneMap.put("rightHand", rightHand);
        boneMap.put("leftUpperArm", leftUpperArm);
        boneMap.put("leftLowerArm", leftLowerArm);
        boneMap.put("leftHand", leftHand);
        boneMap.put("rightUpperLeg", rightUpperLeg);
        boneMap.put("rightLowerLeg", rightLowerLeg);
        boneMap.put("leftUpperLeg", leftUpperLeg);
        boneMap.put("leftLowerLeg", leftLowerLeg);
        boneMap.put("weaponMount", weaponMount);

        // Replace MC cube polygons with correctly UV-mapped ones from geo.json
        try {
            GeoQuadRenderer.replacePolygonUVs(this);
            com.mojang.logging.LogUtils.getLogger().info("CombatPlayerModel: Polygon UV replacement completed");
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().error("CombatPlayerModel: Polygon replacement FAILED", t);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition meshRoot = mesh.getRoot();

        PartDefinition root = meshRoot.addOrReplaceChild("root",
                CubeListBuilder.create(), PartPose.offset(0, 24, 0));

        PartDefinition hip = root.addOrReplaceChild("hip",
                CubeListBuilder.create(), PartPose.offset(0, -12, 0));

        // === BODY (split into waist + chest) ===
        PartDefinition waist = hip.addOrReplaceChild("waist",
                CubeListBuilder.create().texOffs(16, 22)
                        .addBox(-4, 0, -2, 8, 6, 4),
                PartPose.offset(0, -6, 0));

        PartDefinition chest = waist.addOrReplaceChild("chest",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4, -2, -2, 8, 6, 4),
                PartPose.offset(0, -4, 0));

        // Jacket overlay — full 12px tall on chest (extends through waist)
        chest.addOrReplaceChild("jacket",
                CubeListBuilder.create().texOffs(16, 32)
                        .addBox(-4, -2, -2, 8, 12, 4, new CubeDeformation(0.25F)),
                PartPose.ZERO);

        // === HEAD ===
        PartDefinition neck = chest.addOrReplaceChild("neck",
                CubeListBuilder.create(), PartPose.offset(0, -2, 0));

        PartDefinition head = neck.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4, -8, -4, 8, 8, 8),
                PartPose.ZERO);

        // Hat overlay (hidden by default until sizing is resolved)
        head.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.5F)),
                PartPose.ZERO);

        // === RIGHT ARM (split into upper + lower) ===
        PartDefinition rightUpperArm = chest.addOrReplaceChild("rightUpperArm",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-3, -2, -2, 4, 6, 4),
                PartPose.offset(-5, 0, 0));

        // Right sleeve overlay — full 12px tall on upper arm
        rightUpperArm.addOrReplaceChild("right_sleeve",
                CubeListBuilder.create().texOffs(40, 32)
                        .addBox(-3, -2, -2, 4, 12, 4, new CubeDeformation(0.25F)),
                PartPose.ZERO);

        PartDefinition rightLowerArm = rightUpperArm.addOrReplaceChild("rightLowerArm",
                CubeListBuilder.create().texOffs(40, 22)
                        .addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(-1, 4, 0));

        PartDefinition rightHand = rightLowerArm.addOrReplaceChild("rightHand",
                CubeListBuilder.create(), PartPose.offset(0, 6, 0));

        rightHand.addOrReplaceChild("weaponMount",
                CubeListBuilder.create(), PartPose.ZERO);

        // === LEFT ARM (split into upper + lower) ===
        PartDefinition leftUpperArm = chest.addOrReplaceChild("leftUpperArm",
                CubeListBuilder.create().texOffs(32, 48)
                        .addBox(-1, -2, -2, 4, 6, 4),
                PartPose.offset(5, 0, 0));

        // Left sleeve overlay — full 12px tall on upper arm
        leftUpperArm.addOrReplaceChild("left_sleeve",
                CubeListBuilder.create().texOffs(48, 48)
                        .addBox(-1, -2, -2, 4, 12, 4, new CubeDeformation(0.25F)),
                PartPose.ZERO);

        PartDefinition leftLowerArm = leftUpperArm.addOrReplaceChild("leftLowerArm",
                CubeListBuilder.create().texOffs(32, 54)
                        .addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(1, 4, 0));

        leftLowerArm.addOrReplaceChild("leftHand",
                CubeListBuilder.create(), PartPose.offset(0, 6, 0));

        chest.addOrReplaceChild("sheathBack",
                CubeListBuilder.create(), PartPose.offset(0, 2, 2));

        // === RIGHT LEG (split into upper + lower) ===
        PartDefinition rightUpperLeg = hip.addOrReplaceChild("rightUpperLeg",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(-2, 0, 0));

        // Right pants overlay — full 12px tall on upper leg
        rightUpperLeg.addOrReplaceChild("right_pants",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-2, 0, -2, 4, 12, 4, new CubeDeformation(0.25F)),
                PartPose.ZERO);

        PartDefinition rightLowerLeg = rightUpperLeg.addOrReplaceChild("rightLowerLeg",
                CubeListBuilder.create().texOffs(0, 22)
                        .addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(0, 6, 0));

        // === LEFT LEG (split into upper + lower) ===
        PartDefinition leftUpperLeg = hip.addOrReplaceChild("leftUpperLeg",
                CubeListBuilder.create().texOffs(16, 48)
                        .addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(2, 0, 0));

        // Left pants overlay — full 12px tall on upper leg
        leftUpperLeg.addOrReplaceChild("left_pants",
                CubeListBuilder.create().texOffs(0, 48)
                        .addBox(-2, 0, -2, 4, 12, 4, new CubeDeformation(0.25F)),
                PartPose.ZERO);

        PartDefinition leftLowerLeg = leftUpperLeg.addOrReplaceChild("leftLowerLeg",
                CubeListBuilder.create().texOffs(16, 54)
                        .addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(0, 6, 0));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        this.resetPose();

        // Overlay visibility based on player settings
        this.hat.visible = state.showHat;
        this.jacket.visible = state.showJacket;
        this.rightSleeve.visible = state.showRightSleeve;
        this.leftSleeve.visible = state.showLeftSleeve;
        this.rightPants.visible = state.showRightPants;
        this.leftPants.visible = state.showLeftPants;

        if (CombatAnimationController.isActive()) {
            CombatAnimationController.applyTo17Bones(this.boneMap);
        } else {
            applyVanillaFallback(state);
        }
    }

    @Override
    public void translateToHand(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack) {
        // Only traverse to upper arm pivot — matches vanilla's expected offset
        // so ItemInHandLayer transforms work correctly
        this.root.translateAndRotate(poseStack);
        this.hip.translateAndRotate(poseStack);
        this.waist.translateAndRotate(poseStack);
        this.chest.translateAndRotate(poseStack);
        ModelPart upperArm = (arm == HumanoidArm.RIGHT) ? this.rightUpperArm : this.leftUpperArm;
        upperArm.translateAndRotate(poseStack);
    }

    @Override
    public ModelPart getHead() {
        return this.head;
    }

    private void applyVanillaFallback(AvatarRenderState state) {
        this.head.xRot = state.xRot * ((float) Math.PI / 180F);
        this.head.yRot = state.yRot * ((float) Math.PI / 180F);

        float walkPos = state.walkAnimationPos;
        float walkSpeed = state.walkAnimationSpeed;

        // Upper arms swing opposite to legs
        float rightArmSwing = Mth.cos(walkPos * 0.6662F + (float) Math.PI) * 2.0F * walkSpeed * 0.5F;
        float leftArmSwing = Mth.cos(walkPos * 0.6662F) * 2.0F * walkSpeed * 0.5F;
        this.rightUpperArm.xRot = rightArmSwing;
        this.leftUpperArm.xRot = leftArmSwing;
        // Lower arms bend slightly when swinging
        this.rightLowerArm.xRot = Math.min(0, rightArmSwing) * 0.5F;
        this.leftLowerArm.xRot = Math.min(0, leftArmSwing) * 0.5F;

        // Upper legs stride
        float rightLegSwing = Mth.cos(walkPos * 0.6662F) * 1.4F * walkSpeed;
        float leftLegSwing = Mth.cos(walkPos * 0.6662F + (float) Math.PI) * 1.4F * walkSpeed;
        this.rightUpperLeg.xRot = rightLegSwing;
        this.leftUpperLeg.xRot = leftLegSwing;
        // Lower legs bend on back-swing (when upper leg goes backward)
        this.rightLowerLeg.xRot = Math.max(0, -rightLegSwing) * 0.6F;
        this.leftLowerLeg.xRot = Math.max(0, -leftLegSwing) * 0.6F;

        // Subtle body sway
        this.waist.yRot = Mth.cos(walkPos * 0.6662F) * 0.05F * walkSpeed;
        this.chest.yRot = -this.waist.yRot;

        // Crouch
        if (state.isCrouching) {
            this.waist.xRot = 0.5F;
            this.rightUpperLeg.xRot -= 0.4F;
            this.leftUpperLeg.xRot -= 0.4F;
        }
    }
}
