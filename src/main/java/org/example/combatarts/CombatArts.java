package org.example.combatarts;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.example.combatarts.combat.client.CombatAnimationController;
import org.example.combatarts.combat.ModSounds;
import org.example.combatarts.combat.item.ModItems;
import org.example.combatarts.combat.network.CombatNetworkChannel;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(CombatArts.MODID)
public class CombatArts {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "combat_arts";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "combat_arts" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Combat Arts
    public static final RegistryObject<CreativeModeTab> COMBAT_ARTS_TAB = CREATIVE_MODE_TABS.register(
            "combat_arts",
            () -> CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.combat_arts.combat_arts"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.COMBAT_DIAMOND_SWORD.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        for (var sword : ModItems.ALL_SWORDS) output.accept(sword.get());
                        for (var spear : ModItems.ALL_SPEARS) output.accept(spear.get());
                    })
                    .build());

    public CombatArts(FMLJavaModLoadingContext context) {
        BusGroup modBusGroup = context.getModBusGroup();

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

        // 客户端专属注册 (按键/渲染/HUD/鼠标输入) — 必须在 Dist.CLIENT 检查里, 否则
        // 服务端 (DEDICATED_SERVER) 加载 CombatArts 类时会触发 ClassNotFoundException.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            org.example.combatarts.combat.client.ClientSetup.register();
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {

        @SubscribeEvent
        public static void commonSetup(final FMLCommonSetupEvent event) {
            LOGGER.info("Combat Arts common setup complete");
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Combat Arts client setup complete");
            CombatAnimationController.init();
        }
    }
}
