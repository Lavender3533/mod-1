package org.example.mod_1.mod_1;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.mod_1.mod_1.combat.client.CombatAnimationController;
import org.example.mod_1.mod_1.combat.client.CombatHudOverlay;
import org.example.mod_1.mod_1.combat.client.CombatPlayerModel;
import org.example.mod_1.mod_1.combat.client.CombatRendererManager;
import org.example.mod_1.mod_1.combat.input.CombatKeyBindings;
import org.example.mod_1.mod_1.combat.item.ModItems;
import org.example.mod_1.mod_1.combat.ModSounds;
import org.example.mod_1.mod_1.combat.network.CombatNetworkChannel;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Mod_1.MODID)
public class Mod_1 {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "mod_1";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "mod_1" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "mod_1" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "mod_1" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, name));
    }

    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, name));
    }

    // Creates a new Block with the id "mod_1:example_block", combining the namespace and path
    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of().setId(blockKey("example_block")).mapColor(MapColor.STONE)));
    // Creates a new BlockItem with the id "mod_1:example_block", combining the namespace and path
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties().setId(itemKey("example_block"))));

    // Combat Arts 专属创意栏 — 图标用我们自己的钻石剑, 列出全部 sword + spear.
    public static final RegistryObject<CreativeModeTab> COMBAT_ARTS_TAB = CREATIVE_MODE_TABS.register(
            "combat_arts",
            () -> CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.mod_1.combat_arts"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.COMBAT_DIAMOND_SWORD.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        for (var sword : ModItems.ALL_SWORDS) output.accept(sword.get());
                        for (var spear : ModItems.ALL_SPEARS) output.accept(spear.get());
                    })
                    .build());

    public Mod_1(FMLJavaModLoadingContext context) {
        BusGroup modBusGroup = context.getModBusGroup();

        // Register the Deferred Register to the mod bus group so blocks get registered
        BLOCKS.register(modBusGroup);
        // Register the Deferred Register to the mod bus group so items get registered
        ITEMS.register(modBusGroup);
        // Register the Deferred Register to the mod bus group so tabs get registered
        CREATIVE_MODE_TABS.register(modBusGroup);

        // Register combat items
        ModItems.ITEMS.register(modBusGroup);

        // Register combat sounds
        ModSounds.SOUNDS.register(modBusGroup);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Initialize combat network channel
        CombatNetworkChannel.init();

        // Register key mappings via the mod bus group's specific event bus
        RegisterKeyMappingsEvent.getBus(modBusGroup).addListener(CombatKeyBindings::registerKeys);

        // Register 17-bone model layer definitions (wide + slim) + 单独的 cape layer
        EntityRenderersEvent.RegisterLayerDefinitions.getBus(modBusGroup).addListener(event -> {
            event.registerLayerDefinition(CombatPlayerModel.LAYER_LOCATION, CombatPlayerModel::createBodyLayer);
            event.registerLayerDefinition(CombatPlayerModel.LAYER_LOCATION_SLIM, CombatPlayerModel::createSlimBodyLayer);
            event.registerLayerDefinition(CombatPlayerModel.LAYER_LOCATION_CAPE, CombatPlayerModel::createCapeLayer);
        });

        // Initialize combat renderer when layers are added
        EntityRenderersEvent.AddLayers.getBus(modBusGroup).addListener(event -> {
            CombatRendererManager.init(event.getContext());
        });

        // Register combat HUD overlay
        AddGuiOverlayLayersEvent.BUS.addListener(CombatHudOverlay::register);

        // Register mouse button interceptor (cancel vanilla attack/use when weapon drawn)
        net.minecraftforge.client.event.InputEvent.MouseButton.Pre.BUS.addListener(
                org.example.mod_1.mod_1.combat.input.CombatInputHandler::onMouseButton);
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            // Do something when the server starts
            LOGGER.info("HELLO from server starting");
        }

        // Add the example block item to the building blocks tab
        @SubscribeEvent
        public static void addCreative(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) event.accept(EXAMPLE_BLOCK_ITEM);
            // 战斗武器只放在我们自己的 COMBAT_ARTS_TAB (在 tab 注册时通过 displayItems 添加),
            // 不再塞 vanilla COMBAT tab 避免重复显示。
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {

        @SubscribeEvent
        public static void commonSetup(final FMLCommonSetupEvent event) {
            // Some common setup code
            LOGGER.info("HELLO FROM COMMON SETUP");
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

            if (Config.logDirtBlock) LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

            LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

            Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            // Initialize combat animation layer
            CombatAnimationController.init();
        }
    }
}
