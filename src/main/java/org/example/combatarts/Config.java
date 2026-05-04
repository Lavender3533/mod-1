package org.example.combatarts;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = CombatArts.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ===== Combat config =====
    static final ForgeConfigSpec.DoubleValue SWORD_LIGHT_RANGE;
    static final ForgeConfigSpec.DoubleValue SWORD_HEAVY_RANGE;
    static final ForgeConfigSpec.DoubleValue SWORD_LIGHT_ANGLE;
    static final ForgeConfigSpec.DoubleValue SWORD_HEAVY_ANGLE;

    static final ForgeConfigSpec.DoubleValue SPEAR_LIGHT_RANGE;
    static final ForgeConfigSpec.DoubleValue SPEAR_HEAVY_RANGE;
    static final ForgeConfigSpec.DoubleValue SPEAR_LIGHT_ANGLE;
    static final ForgeConfigSpec.DoubleValue SPEAR_HEAVY_ANGLE;

    static final ForgeConfigSpec.IntValue LIGHT_HIT_FRAME_START;
    static final ForgeConfigSpec.IntValue HEAVY_HIT_FRAME_START;

    static final ForgeConfigSpec.DoubleValue DAMAGE_MULT_COMBO_1;
    static final ForgeConfigSpec.DoubleValue DAMAGE_MULT_COMBO_2;
    static final ForgeConfigSpec.DoubleValue DAMAGE_MULT_COMBO_3;
    static final ForgeConfigSpec.DoubleValue DAMAGE_MULT_HEAVY;
    static final ForgeConfigSpec.DoubleValue DAMAGE_MULT_SPRINT;

    static final ForgeConfigSpec.DoubleValue BLOCK_DAMAGE_REDUCTION;
    static final ForgeConfigSpec.DoubleValue BLOCK_ANGLE;
    static final ForgeConfigSpec.IntValue PARRY_SLOWNESS_DURATION;
    static final ForgeConfigSpec.IntValue PARRY_SLOWNESS_AMPLIFIER;
    static final ForgeConfigSpec.DoubleValue PARRY_KNOCKBACK;
    static final ForgeConfigSpec.IntValue PARRY_STUN_TICKS;

    static final ForgeConfigSpec.IntValue COMBO_WINDOW_TICKS;
    static final ForgeConfigSpec.IntValue BLOCK_HOLD_THRESHOLD_TICKS;
    static final ForgeConfigSpec.IntValue LIGHT_CHAIN_TRIGGER_TICKS;
    static final ForgeConfigSpec.IntValue DODGE_COOLDOWN_TICKS;
    static final ForgeConfigSpec.IntValue DODGE_INVULN_TICKS;
    static final ForgeConfigSpec.IntValue PARRY_WINDOW_TICKS;
    static final ForgeConfigSpec.IntValue HEAVY_CHARGE_MAX_TICKS;
    static final ForgeConfigSpec.DoubleValue HEAVY_CHARGE_MAX_MULT;

    // ===== Dev / debug =====
    static final ForgeConfigSpec.BooleanValue DEV_MODE;

    static {
        BUILDER.comment("Combat system settings").push("combat");

        BUILDER.push("sword");
        SWORD_LIGHT_RANGE = BUILDER.comment("Sword light attack range (blocks)")
                .defineInRange("lightRange", 3.0, 0.5, 20.0);
        SWORD_HEAVY_RANGE = BUILDER.comment("Sword heavy attack range (blocks)")
                .defineInRange("heavyRange", 3.5, 0.5, 20.0);
        SWORD_LIGHT_ANGLE = BUILDER.comment("Sword light attack arc angle (degrees)")
                .defineInRange("lightAngle", 90.0, 1.0, 360.0);
        SWORD_HEAVY_ANGLE = BUILDER.comment("Sword heavy attack arc angle (degrees)")
                .defineInRange("heavyAngle", 120.0, 1.0, 360.0);
        BUILDER.pop();

        BUILDER.push("spear");
        SPEAR_LIGHT_RANGE = BUILDER.comment("Spear light attack range (blocks)")
                .defineInRange("lightRange", 4.5, 0.5, 20.0);
        SPEAR_HEAVY_RANGE = BUILDER.comment("Spear heavy attack range (blocks)")
                .defineInRange("heavyRange", 5.0, 0.5, 20.0);
        SPEAR_LIGHT_ANGLE = BUILDER.comment("Spear light attack arc angle (degrees)")
                .defineInRange("lightAngle", 30.0, 1.0, 360.0);
        SPEAR_HEAVY_ANGLE = BUILDER.comment("Spear heavy attack arc angle (degrees)")
                .defineInRange("heavyAngle", 45.0, 1.0, 360.0);
        BUILDER.pop();

        BUILDER.push("hitFrame");
        LIGHT_HIT_FRAME_START = BUILDER.comment("Tick offset within light attack state when damage is applied")
                .defineInRange("lightHitFrameStart", 3, 0, 200);
        HEAVY_HIT_FRAME_START = BUILDER.comment("Tick offset within heavy attack state when damage is applied")
                .defineInRange("heavyHitFrameStart", 8, 0, 200);
        BUILDER.pop();

        BUILDER.push("damageMultiplier");
        DAMAGE_MULT_COMBO_1 = BUILDER.comment("Light attack damage multiplier for combo hit 1")
                .defineInRange("combo1", 1.0, 0.0, 10.0);
        DAMAGE_MULT_COMBO_2 = BUILDER.comment("Light attack damage multiplier for combo hit 2")
                .defineInRange("combo2", 1.1, 0.0, 10.0);
        DAMAGE_MULT_COMBO_3 = BUILDER.comment("Light attack damage multiplier for combo hit 3")
                .defineInRange("combo3", 1.3, 0.0, 10.0);
        DAMAGE_MULT_HEAVY = BUILDER.comment("Heavy attack damage multiplier")
                .defineInRange("heavy", 1.5, 0.0, 10.0);
        DAMAGE_MULT_SPRINT = BUILDER.comment("Sprint attack damage multiplier")
                .defineInRange("sprint", 1.5, 0.0, 10.0);
        BUILDER.pop();

        BUILDER.push("defense");
        BLOCK_DAMAGE_REDUCTION = BUILDER.comment("Block damage reduction fraction (0.0 = no reduction, 1.0 = full block)")
                .defineInRange("blockDamageReduction", 0.6, 0.0, 1.0);
        BLOCK_ANGLE = BUILDER.comment("Block frontal arc angle (degrees)")
                .defineInRange("blockAngle", 120.0, 1.0, 360.0);
        PARRY_SLOWNESS_DURATION = BUILDER.comment("Parry slowness duration applied to attacker (ticks)")
                .defineInRange("parrySlownessDuration", 20, 0, 6000);
        PARRY_SLOWNESS_AMPLIFIER = BUILDER.comment("Parry slowness amplifier (potion level - 1)")
                .defineInRange("parrySlownessAmplifier", 2, 0, 10);
        PARRY_KNOCKBACK = BUILDER.comment("Parry knockback strength applied to attacker")
                .defineInRange("parryKnockback", 1.5, 0.0, 10.0);
        PARRY_STUN_TICKS = BUILDER.comment("Parry stun (Mob.setNoActionTime) duration (ticks)")
                .defineInRange("parryStunTicks", 15, 0, 200);
        BUILDER.pop();

        BUILDER.push("timing");
        COMBO_WINDOW_TICKS = BUILDER.comment("Combo window after last attack before combo resets (ticks)")
                .defineInRange("comboWindowTicks", 10, 1, 200);
        BLOCK_HOLD_THRESHOLD_TICKS = BUILDER.comment("Right mouse hold ticks before block starts (lower = more responsive)")
                .defineInRange("blockHoldThresholdTicks", 1, 0, 20);
        LIGHT_CHAIN_TRIGGER_TICKS = BUILDER.comment("Remaining light attack ticks at which queued combo continues (higher = earlier chaining)")
                .defineInRange("lightChainTriggerTicks", 4, 0, 20);
        DODGE_COOLDOWN_TICKS = BUILDER.comment("Dodge cooldown (ticks)")
                .defineInRange("dodgeCooldownTicks", 20, 0, 600);
        DODGE_INVULN_TICKS = BUILDER.comment("Dodge invulnerability frames (ticks)")
                .defineInRange("dodgeInvulnTicks", 6, 0, 200);
        PARRY_WINDOW_TICKS = BUILDER.comment("Parry window at the start of BLOCK state (ticks)")
                .defineInRange("parryWindowTicks", 4, 0, 100);
        HEAVY_CHARGE_MAX_TICKS = BUILDER.comment("Hold-time (ticks) at which heavy attack charge reaches its maximum multiplier")
                .defineInRange("heavyChargeMaxTicks", 20, 1, 200);
        HEAVY_CHARGE_MAX_MULT = BUILDER.comment("Damage multiplier applied at full heavy charge (1.0 = same as instant heavy)")
                .defineInRange("heavyChargeMaxMult", 2.0, 1.0, 10.0);
        BUILDER.pop();

        BUILDER.pop(); // combat

        BUILDER.comment("Developer / debug tooling — leave off for normal play").push("dev");
        DEV_MODE = BUILDER.comment(
                "Enable in-game pose-tweaker GUI, animation hot-reload (F10), and bracket/comma/period pose-edit hotkeys.",
                "Off by default; flip to true if you need to retune animations or骨骼姿势 in-world.")
                .define("devMode", false);
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // Combat runtime values
    public static double swordLightRange;
    public static double swordHeavyRange;
    public static double swordLightAngle;
    public static double swordHeavyAngle;
    public static double spearLightRange;
    public static double spearHeavyRange;
    public static double spearLightAngle;
    public static double spearHeavyAngle;
    public static int lightHitFrameStart;
    public static int heavyHitFrameStart;
    public static double damageMultComboOne;
    public static double damageMultComboTwo;
    public static double damageMultComboThree;
    public static double damageMultHeavy;
    public static double damageMultSprint;
    public static double blockDamageReduction;
    public static double blockAngle;
    public static int parrySlownessDuration;
    public static int parrySlownessAmplifier;
    public static double parryKnockback;
    public static int parryStunTicks;
    public static int comboWindowTicks;
    public static int blockHoldThresholdTicks;
    public static int lightChainTriggerTicks;
    public static int dodgeCooldownTicks;
    public static int dodgeInvulnTicks;
    public static int parryWindowTicks;
    public static int heavyChargeMaxTicks;
    public static double heavyChargeMaxMult;
    public static boolean devMode;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // combat
        swordLightRange = SWORD_LIGHT_RANGE.get();
        swordHeavyRange = SWORD_HEAVY_RANGE.get();
        swordLightAngle = SWORD_LIGHT_ANGLE.get();
        swordHeavyAngle = SWORD_HEAVY_ANGLE.get();
        spearLightRange = SPEAR_LIGHT_RANGE.get();
        spearHeavyRange = SPEAR_HEAVY_RANGE.get();
        spearLightAngle = SPEAR_LIGHT_ANGLE.get();
        spearHeavyAngle = SPEAR_HEAVY_ANGLE.get();
        lightHitFrameStart = LIGHT_HIT_FRAME_START.get();
        heavyHitFrameStart = HEAVY_HIT_FRAME_START.get();
        damageMultComboOne = DAMAGE_MULT_COMBO_1.get();
        damageMultComboTwo = DAMAGE_MULT_COMBO_2.get();
        damageMultComboThree = DAMAGE_MULT_COMBO_3.get();
        damageMultHeavy = DAMAGE_MULT_HEAVY.get();
        damageMultSprint = DAMAGE_MULT_SPRINT.get();
        blockDamageReduction = BLOCK_DAMAGE_REDUCTION.get();
        blockAngle = BLOCK_ANGLE.get();
        parrySlownessDuration = PARRY_SLOWNESS_DURATION.get();
        parrySlownessAmplifier = PARRY_SLOWNESS_AMPLIFIER.get();
        parryKnockback = PARRY_KNOCKBACK.get();
        parryStunTicks = PARRY_STUN_TICKS.get();
        comboWindowTicks = COMBO_WINDOW_TICKS.get();
        blockHoldThresholdTicks = BLOCK_HOLD_THRESHOLD_TICKS.get();
        lightChainTriggerTicks = LIGHT_CHAIN_TRIGGER_TICKS.get();
        dodgeCooldownTicks = DODGE_COOLDOWN_TICKS.get();
        dodgeInvulnTicks = DODGE_INVULN_TICKS.get();
        parryWindowTicks = PARRY_WINDOW_TICKS.get();
        heavyChargeMaxTicks = HEAVY_CHARGE_MAX_TICKS.get();
        heavyChargeMaxMult = HEAVY_CHARGE_MAX_MULT.get();
        devMode = DEV_MODE.get();
    }
}
