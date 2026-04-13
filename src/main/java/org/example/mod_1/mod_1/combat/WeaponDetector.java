package org.example.mod_1.mod_1.combat;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

public class WeaponDetector {

    public static WeaponType detect(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return WeaponType.UNARMED;

        if (held.is(ItemTags.SWORDS)) return WeaponType.SWORD;
        if (held.getItem() instanceof TridentItem) return WeaponType.SPEAR;

        return WeaponType.UNARMED;
    }

    public static boolean canEnterCombat(Player player) {
        return detect(player) != WeaponType.UNARMED;
    }
}
