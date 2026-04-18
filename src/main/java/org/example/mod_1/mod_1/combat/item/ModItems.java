package org.example.mod_1.mod_1.combat.item;

// 战斗武器物品注册
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.mod_1.mod_1.Mod_1;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Mod_1.MODID);

    private static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Mod_1.MODID, name));
    }

    private static RegistryObject<Item> registerSword(String name, ToolMaterial material, float attackDamage, float attackSpeed) {
        return ITEMS.register(name, () -> new CombatSwordItem(
                material.applySwordProperties(
                        new Item.Properties().setId(key(name)),
                        attackDamage, attackSpeed
                )
        ));
    }

    private static RegistryObject<Item> registerSpear(String name, ToolMaterial material, float attackDamage, float attackSpeed) {
        return ITEMS.register(name, () -> new CombatSpearItem(
                material.applySwordProperties(
                        new Item.Properties().setId(key(name)),
                        attackDamage, attackSpeed
                )
        ));
    }

    // ===== 剑类 (Swords) =====
    public static final RegistryObject<Item> COMBAT_WOOD_SWORD =
            registerSword("combat_wood_sword", ToolMaterial.WOOD, 3.0f, -2.4f);

    public static final RegistryObject<Item> COMBAT_STONE_SWORD =
            registerSword("combat_stone_sword", ToolMaterial.STONE, 3.0f, -2.4f);

    public static final RegistryObject<Item> COMBAT_IRON_SWORD =
            registerSword("combat_iron_sword", ToolMaterial.IRON, 3.0f, -2.4f);

    public static final RegistryObject<Item> COMBAT_GOLD_SWORD =
            registerSword("combat_gold_sword", ToolMaterial.GOLD, 3.0f, -2.4f);

    public static final RegistryObject<Item> COMBAT_DIAMOND_SWORD =
            registerSword("combat_diamond_sword", ToolMaterial.DIAMOND, 3.0f, -2.4f);

    public static final RegistryObject<Item> COMBAT_NETHERITE_SWORD =
            registerSword("combat_netherite_sword", ToolMaterial.NETHERITE, 3.0f, -2.4f);

    public static final RegistryObject<Item>[] ALL_SWORDS = new RegistryObject[]{
            COMBAT_WOOD_SWORD, COMBAT_STONE_SWORD, COMBAT_IRON_SWORD,
            COMBAT_GOLD_SWORD, COMBAT_DIAMOND_SWORD, COMBAT_NETHERITE_SWORD
    };

    // ===== 矛类 (Spears) — 突刺攻击，距离更长，速度较慢 =====
    public static final RegistryObject<Item> COMBAT_WOOD_SPEAR =
            registerSpear("combat_wood_spear", ToolMaterial.WOOD, 4.0f, -2.8f);

    public static final RegistryObject<Item> COMBAT_STONE_SPEAR =
            registerSpear("combat_stone_spear", ToolMaterial.STONE, 4.0f, -2.8f);

    public static final RegistryObject<Item> COMBAT_IRON_SPEAR =
            registerSpear("combat_iron_spear", ToolMaterial.IRON, 4.0f, -2.8f);

    public static final RegistryObject<Item> COMBAT_GOLD_SPEAR =
            registerSpear("combat_gold_spear", ToolMaterial.GOLD, 4.0f, -2.8f);

    public static final RegistryObject<Item> COMBAT_DIAMOND_SPEAR =
            registerSpear("combat_diamond_spear", ToolMaterial.DIAMOND, 4.0f, -2.8f);

    public static final RegistryObject<Item> COMBAT_NETHERITE_SPEAR =
            registerSpear("combat_netherite_spear", ToolMaterial.NETHERITE, 4.0f, -2.8f);

    public static final RegistryObject<Item>[] ALL_SPEARS = new RegistryObject[]{
            COMBAT_WOOD_SPEAR, COMBAT_STONE_SPEAR, COMBAT_IRON_SPEAR,
            COMBAT_GOLD_SPEAR, COMBAT_DIAMOND_SPEAR, COMBAT_NETHERITE_SPEAR
    };
}
