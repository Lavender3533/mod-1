package org.example.combatarts.combat;

public enum CombatState {
    IDLE(0, true, 0),
    WALK(1, true, 0),
    RUN(2, true, 0),
    CROUCH(3, true, 0),
    JUMP(4, true, 0),
    DRAW_WEAPON(10, false, 16),
    SHEATH_WEAPON(10, false, 14),
    ATTACK_LIGHT(20, false, 14),
    ATTACK_HEAVY(25, false, 24),
    DODGE(30, false, 14),
    BLOCK(15, true, 0),
    PARRY(35, false, 8),
    INSPECT(5, true, 0),
    // Appended at end to preserve ordinals for any NBT-persisted state
    ATTACK_HEAVY_CHARGING(22, true, 0);

    private final int priority;
    private final boolean interruptible;
    private final int durationTicks;

    CombatState(int priority, boolean interruptible, int durationTicks) {
        this.priority = priority;
        this.interruptible = interruptible;
        this.durationTicks = durationTicks;
    }

    public int getPriority() { return priority; }
    public boolean isInterruptible() { return interruptible; }
    public int getDurationTicks() { return durationTicks; }
    public boolean isTimed() { return durationTicks > 0; }

    public static CombatState fromOrdinal(int ordinal) {
        CombatState[] values = values();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        return IDLE;
    }
}
