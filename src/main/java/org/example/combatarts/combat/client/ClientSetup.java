package org.example.combatarts.combat.client;

// 客户端专属注册. 所有引用 client-only 类 (renderer / KeyMapping / GUI overlay / input event)
// 的代码必须在这里, 然后从 CombatArts 构造函数里通过 if (FMLEnvironment.dist.isClient()) 调用,
// 否则服务端启动时会因为类加载器看到 LocalPlayer/AddGuiOverlayLayersEvent 等 client-only 类
// 直接 ClassNotFoundException crash.
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.example.combatarts.combat.client.render.mesh.MeshManager;
import org.example.combatarts.combat.input.CombatInputHandler;
import org.example.combatarts.combat.input.CombatKeyBindings;

public final class ClientSetup {
    private ClientSetup() {}

    public static void register() {
        // 按键
        RegisterKeyMappingsEvent.BUS.addListener(CombatKeyBindings::registerKeys);

        // 17 骨架 + cape model layer 注册
        EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(event -> {
            event.registerLayerDefinition(CombatPlayerModel.LAYER_LOCATION, CombatPlayerModel::createBodyLayer);
            event.registerLayerDefinition(CombatPlayerModel.LAYER_LOCATION_SLIM, CombatPlayerModel::createSlimBodyLayer);
            event.registerLayerDefinition(CombatPlayerModel.LAYER_LOCATION_CAPE, CombatPlayerModel::createCapeLayer);
        });

        // 把渲染器换成我们的
        EntityRenderersEvent.AddLayers.BUS.addListener(event -> {
            CombatRendererManager.init(event.getContext());
            MeshManager.init();  // Load skinned mesh model (biped.json)
        });

        // HUD overlay
        AddGuiOverlayLayersEvent.BUS.addListener(CombatHudOverlay::register);

        // 鼠标拦截 (拔刀状态下取消 vanilla 攻击/使用)
        InputEvent.MouseButton.Pre.BUS.addListener(CombatInputHandler::onMouseButton);
    }
}
