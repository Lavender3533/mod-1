package org.example.mod_1.mod_1.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.example.mod_1.mod_1.combat.item.CombatSwordItem;

public class WeaponDetector {

    // vanilla 1.21.1 的 spear tag
    private static final TagKey<Item> SPEARS_TAG = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", "spears"));

    public static WeaponType detect(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return WeaponType.UNARMED;

        if (held.getItem() instanceof CombatSwordItem) return WeaponType.SWORD;
        if (held.is(ItemTags.SWORDS)) return WeaponType.SWORD;
        if (held.is(SPEARS_TAG)) return WeaponType.SPEAR;

        return WeaponType.UNARMED;
    }

    public static boolean canEnterCombat(Player player) {
        return detect(player) != WeaponType.UNARMED;
    }
}
