package org.example.mod_1.mod_1.combat;

public enum WeaponType {
    UNARMED("unarmed", 2),
    SWORD("sword", 3),
    SPEAR("spear", 2);

    private final String animPrefix;
    private final int maxCombo;

    WeaponType(String animPrefix, int maxCombo) {
        this.animPrefix = animPrefix;
        this.maxCombo = maxCombo;
    }

    public String getAnimPrefix() { return animPrefix; }
    public int getMaxCombo() { return maxCombo; }

    public static WeaponType fromOrdinal(int ordinal) {
        WeaponType[] values = values();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        return UNARMED;
    }
}
