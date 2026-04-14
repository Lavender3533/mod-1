# 17 骨骼自定义渲染器 — 完整技术方案

> 给接手开发者的实施文档
> 项目: MC 1.21.11 Forge 61.0.6 战斗动画 Mod
> 路径: D:/project/technique/mod_1/

---

## 一、背景

当前 mod 有完整的战斗状态机（13 状态）、Capability 系统、网络同步、按键绑定、19 个 Blockbench 17 骨骼动画文件、JSON 关键帧插值引擎。唯一缺的是：**玩家模型只有 vanilla 的 6 个骨骼**（head/body/rightArm/leftArm/rightLeg/leftLeg），无法表现弯肘、弯膝、扭腰等细节。

**目标：** 实现自定义 17 骨骼玩家渲染器，替换 vanilla 渲染（全程替换，不区分战斗/非战斗），使用玩家自己的皮肤贴图。

**已尝试过的方案（不可行）：**
- GeckoLib `GeoReplacedEntityRenderer` — 不支持玩家
- Player Animation Lib — 只支持 7 骨骼
- Mixin 拆骨骼（给 vanilla ModelPart 加子骨骼）— 接缝明显，效果差

---

## 二、架构方案

### 方案：Mixin 替换渲染器

```
EntityRenderDispatcher.getPlayerRenderer(player)
    │ 正常流程: 返回 vanilla AvatarRenderer
    │
    └─ [Mixin @Inject HEAD, cancellable]
       检查 CombatCapability → 返回 CombatAvatarRenderer
```

### 核心组件

```
CombatPlayerModel          — 17 骨骼 EntityModel，从 geo.json 数据构建
CombatAvatarRenderer       — 自定义渲染器，使用 CombatPlayerModel
EntityRenderDispatcherMixin — 拦截 getPlayerRenderer()，按需返回自定义渲染器
CombatAnimationController  — [已有] 动画插值引擎，改为接受 Map<String, ModelPart>
```

### 渲染流程

```
每帧:
1. EntityRenderDispatcher.getPlayerRenderer(player)
   → Mixin 拦截 → 返回 CombatAvatarRenderer

2. CombatAvatarRenderer.submit(renderState, poseStack, nodeCollector, camera)
   → 调用 model.setupAnim(renderState)
   → CombatPlayerModel.setupAnim():
       a) 调用 CombatAnimationController.applyTo17Bones(boneMap)
       b) 无动画时: 用 renderState 的走路/攻击参数自己算 vanilla 姿态
   → 渲染模型 (使用玩家皮肤贴图)
   → 渲染 layers (手持物品等)
```

---

## 三、文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `combat/client/CombatPlayerModel.java` | 17 骨骼模型 |
| 新建 | `combat/client/CombatAvatarRenderer.java` | 自定义渲染器 |
| 新建 | `combat/client/CombatRendererManager.java` | 渲染器缓存/管理 |
| 新建 | `mixin/EntityRenderDispatcherMixin.java` | 渲染器替换 |
| 修改 | `combat/client/CombatAnimationController.java` | 删 getAnimatable, 改 applyTo17Bones 签名 |
| 修改 | `mixin/PlayerModelMixin.java` | 可删除（不再需要 6 骨骼合并） |
| 修改 | `mod_1.mixins.json` | 添加新 mixin |
| 修改 | `Mod_1.java` | 注册模型层事件 |

---

## 四、CombatPlayerModel — 17 骨骼模型

### 4.1 类定义

```java
package org.example.mod_1.mod_1.combat.client;

public class CombatPlayerModel extends EntityModel<AvatarRenderState>
                               implements ArmedModel<AvatarRenderState>, HeadedModel {

    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(Identifier.fromNamespaceAndPath("mod_1", "combat_player"), "main");

    // 所有 17 个骨骼引用
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

    public CombatPlayerModel(ModelPart bakedRoot) {
        super(bakedRoot, RenderTypes::entityTranslucent);
        // 从 baked tree 提取所有骨骼引用并存入 boneMap
        this.root = bakedRoot.getChild("root");
        this.hip = root.getChild("hip");
        // ... 沿层级提取所有骨骼
        // boneMap.put("head", this.head); 等等
    }
}
```

### 4.2 MeshDefinition 构建 — createBodyLayer()

从 `player_combat.geo.json` 转换而来。坐标转换公式：

**Bedrock → MC 模型坐标：**
- PartPose 相对偏移: `x = child_bx - parent_bx`, `y = parent_by - child_by`, `z = child_bz - parent_bz`
- Cube addBox: `x = origin_x - pivot_x`, `y = pivot_y - origin_y - size_y`, `z = origin_z - pivot_z`
- 纹理: 64×64, 使用 `texOffs(u, v)` 标准 MC 皮肤纹理映射

```java
public static LayerDefinition createBodyLayer() {
    MeshDefinition mesh = new MeshDefinition();
    PartDefinition meshRoot = mesh.getRoot();

    // root — 在 MC 模型坐标系 Y=24 处（脚底）
    PartDefinition root = meshRoot.addOrReplaceChild("root",
        CubeListBuilder.create(), PartPose.offset(0, 24, 0));

    // hip — 12 units above root (MC Y-down: -12)
    PartDefinition hip = root.addOrReplaceChild("hip",
        CubeListBuilder.create(), PartPose.offset(0, -12, 0));

    // waist — 6 units above hip
    PartDefinition waist = hip.addOrReplaceChild("waist",
        CubeListBuilder.create().texOffs(16, 22)
            .addBox(-4, 0, -2, 8, 6, 4),
        PartPose.offset(0, -6, 0));

    // chest — 4 units above waist
    PartDefinition chest = waist.addOrReplaceChild("chest",
        CubeListBuilder.create().texOffs(16, 16)
            .addBox(-4, -2, -2, 8, 6, 4),
        PartPose.offset(0, -4, 0));

    // neck — 2 units above chest
    PartDefinition neck = chest.addOrReplaceChild("neck",
        CubeListBuilder.create(), PartPose.offset(0, -2, 0));

    // head — same position as neck
    PartDefinition head = neck.addOrReplaceChild("head",
        CubeListBuilder.create().texOffs(0, 0)
            .addBox(-4, -8, -4, 8, 8, 8),
        PartPose.ZERO);

    // rightUpperArm — from chest, offset (-5, 0, 0)
    PartDefinition rightUpperArm = chest.addOrReplaceChild("rightUpperArm",
        CubeListBuilder.create().texOffs(40, 16)
            .addBox(-3, -2, -2, 4, 6, 4),
        PartPose.offset(-5, 0, 0));

    // rightLowerArm — from rightUpperArm, offset (-1, 4, 0)
    PartDefinition rightLowerArm = rightUpperArm.addOrReplaceChild("rightLowerArm",
        CubeListBuilder.create().texOffs(40, 22)
            .addBox(-2, 0, -2, 4, 6, 4),
        PartPose.offset(-1, 4, 0));

    // rightHand — from rightLowerArm, offset (0, 6, 0)
    PartDefinition rightHand = rightLowerArm.addOrReplaceChild("rightHand",
        CubeListBuilder.create(), PartPose.offset(0, 6, 0));

    // weaponMount — from rightHand, same position
    rightHand.addOrReplaceChild("weaponMount",
        CubeListBuilder.create(), PartPose.ZERO);

    // leftUpperArm — from chest, offset (5, 0, 0)
    PartDefinition leftUpperArm = chest.addOrReplaceChild("leftUpperArm",
        CubeListBuilder.create().texOffs(32, 48)
            .addBox(-1, -2, -2, 4, 6, 4),
        PartPose.offset(5, 0, 0));

    // leftLowerArm — from leftUpperArm, offset (1, 4, 0)
    PartDefinition leftLowerArm = leftUpperArm.addOrReplaceChild("leftLowerArm",
        CubeListBuilder.create().texOffs(32, 54)
            .addBox(-2, 0, -2, 4, 6, 4),
        PartPose.offset(1, 4, 0));

    // leftHand
    leftLowerArm.addOrReplaceChild("leftHand",
        CubeListBuilder.create(), PartPose.offset(0, 6, 0));

    // rightUpperLeg — from hip, offset (-2, 0, 0)
    PartDefinition rightUpperLeg = hip.addOrReplaceChild("rightUpperLeg",
        CubeListBuilder.create().texOffs(0, 16)
            .addBox(-2, 0, -2, 4, 6, 4),
        PartPose.offset(-2, 0, 0));

    // rightLowerLeg — from rightUpperLeg, offset (0, 6, 0)
    rightUpperLeg.addOrReplaceChild("rightLowerLeg",
        CubeListBuilder.create().texOffs(0, 22)
            .addBox(-2, 0, -2, 4, 6, 4),
        PartPose.offset(0, 6, 0));

    // leftUpperLeg — from hip, offset (2, 0, 0)
    PartDefinition leftUpperLeg = hip.addOrReplaceChild("leftUpperLeg",
        CubeListBuilder.create().texOffs(16, 48)
            .addBox(-2, 0, -2, 4, 6, 4),
        PartPose.offset(2, 0, 0));

    // leftLowerLeg — from leftUpperLeg, offset (0, 6, 0)
    leftUpperLeg.addOrReplaceChild("leftLowerLeg",
        CubeListBuilder.create().texOffs(16, 54)
            .addBox(-2, 0, -2, 4, 6, 4),
        PartPose.offset(0, 6, 0));

    // sheathBack — from chest
    chest.addOrReplaceChild("sheathBack",
        CubeListBuilder.create(), PartPose.offset(0, 2, 2));

    return LayerDefinition.create(mesh, 64, 64);
}
```

### 4.3 骨骼层级总览

```
meshRoot
└── root          offset(0, 24, 0)           无 cube
    └── hip       offset(0, -12, 0)          无 cube
        ├── waist     offset(0, -6, 0)       texOffs(16,22) addBox(-4, 0, -2, 8, 6, 4)
        │   └── chest offset(0, -4, 0)       texOffs(16,16) addBox(-4, -2, -2, 8, 6, 4)
        │       ├── neck offset(0, -2, 0)    无 cube
        │       │   └── head offset(0, 0, 0) texOffs(0,0) addBox(-4, -8, -4, 8, 8, 8)
        │       ├── rightUpperArm offset(-5, 0, 0)  texOffs(40,16) addBox(-3, -2, -2, 4, 6, 4)
        │       │   └── rightLowerArm offset(-1, 4, 0) texOffs(40,22) addBox(-2, 0, -2, 4, 6, 4)
        │       │       └── rightHand offset(0, 6, 0)   无 cube
        │       │           └── weaponMount offset(0, 0, 0)
        │       ├── leftUpperArm offset(5, 0, 0)   texOffs(32,48) addBox(-1, -2, -2, 4, 6, 4)
        │       │   └── leftLowerArm offset(1, 4, 0) texOffs(32,54) addBox(-2, 0, -2, 4, 6, 4)
        │       │       └── leftHand offset(0, 6, 0)
        │       └── sheathBack offset(0, 2, 2)
        ├── rightUpperLeg offset(-2, 0, 0)   texOffs(0,16) addBox(-2, 0, -2, 4, 6, 4)
        │   └── rightLowerLeg offset(0, 6, 0) texOffs(0,22) addBox(-2, 0, -2, 4, 6, 4)
        ├── leftUpperLeg offset(2, 0, 0)     texOffs(16,48) addBox(-2, 0, -2, 4, 6, 4)
        │   └── leftLowerLeg offset(0, 6, 0) texOffs(16,54) addBox(-2, 0, -2, 4, 6, 4)
```

### 4.4 关键方法

```java
// setupAnim — 每帧调用
@Override
public void setupAnim(AvatarRenderState state) {
    this.resetAllPoses();

    if (CombatAnimationController.isActive()) {
        // 有自定义动画: 直接驱动 17 骨骼
        CombatAnimationController.applyTo17Bones(this.boneMap);
    } else {
        // 无自定义动画: 用 renderState 参数模拟 vanilla 姿态
        // 需要自己实现走路摆手、头部跟随视角等基础动画
        // 参考 HumanoidModel.setupAnim() 的逻辑
        applyVanillaFallback(state);
    }
}

// resetAllPoses — 重置所有骨骼到初始姿态
public void resetAllPoses() {
    for (ModelPart part : boneMap.values()) {
        part.resetPose();  // ModelPart 自带方法，重置到 initialPose
    }
}

// ArmedModel 接口 — 手持物品定位
@Override
public void translateToHand(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack) {
    // 沿骨骼链 transform: root → hip → waist → chest → upperArm → lowerArm → hand
    this.root.translateAndRotate(poseStack);
    this.hip.translateAndRotate(poseStack);
    this.waist.translateAndRotate(poseStack);
    this.chest.translateAndRotate(poseStack);
    ModelPart upperArm = (arm == HumanoidArm.RIGHT) ? this.rightUpperArm : this.leftUpperArm;
    ModelPart lowerArm = (arm == HumanoidArm.RIGHT) ? this.rightLowerArm : this.leftLowerArm;
    ModelPart hand = (arm == HumanoidArm.RIGHT) ? this.rightHand : this.leftHand;
    upperArm.translateAndRotate(poseStack);
    lowerArm.translateAndRotate(poseStack);
    hand.translateAndRotate(poseStack);
}

// HeadedModel 接口 — 头部定位（用于自定义头盔等）
@Override
public ModelPart getHead() {
    return this.head;
}

// Vanilla 走路/待机 fallback
private void applyVanillaFallback(AvatarRenderState state) {
    // 头部跟随视角
    this.head.xRot = state.xRot * ((float)Math.PI / 180F);
    this.head.yRot = state.yRot * ((float)Math.PI / 180F);

    // 走路摆手摆腿 — 参考 HumanoidModel.setupAnim()
    // state.walkAnimationPos = 走路动画位置
    // state.walkAnimationSpeed = 走路动画速度
    float walkPos = state.walkAnimationPos;
    float walkSpeed = state.walkAnimationSpeed;

    // 右臂/左臂交替摆动 (与腿相反)
    this.rightUpperArm.xRot = Mth.cos(walkPos * 0.6662F + (float)Math.PI) * 2.0F * walkSpeed * 0.5F;
    this.leftUpperArm.xRot = Mth.cos(walkPos * 0.6662F) * 2.0F * walkSpeed * 0.5F;

    // 腿交替迈步
    this.rightUpperLeg.xRot = Mth.cos(walkPos * 0.6662F) * 1.4F * walkSpeed;
    this.leftUpperLeg.xRot = Mth.cos(walkPos * 0.6662F + (float)Math.PI) * 1.4F * walkSpeed;
}
```

### 4.5 UV 映射参考表

所有 UV 使用标准 64×64 MC 玩家皮肤纹理。以下是每个骨骼的 texOffs 和 cube 参数：

| 骨骼 | texOffs(u,v) | addBox(x, y, z, w, h, d) | 说明 |
|------|-------------|--------------------------|------|
| head | (0, 0) | (-4, -8, -4, 8, 8, 8) | 标准头部 |
| chest | (16, 16) | (-4, -2, -2, 8, 6, 4) | 身体上半 |
| waist | (16, 22) | (-4, 0, -2, 8, 6, 4) | 身体下半 |
| rightUpperArm | (40, 16) | (-3, -2, -2, 4, 6, 4) | 右上臂 |
| rightLowerArm | (40, 22) | (-2, 0, -2, 4, 6, 4) | 右下臂 |
| leftUpperArm | (32, 48) | (-1, -2, -2, 4, 6, 4) | 左上臂 |
| leftLowerArm | (32, 54) | (-2, 0, -2, 4, 6, 4) | 左下臂 |
| rightUpperLeg | (0, 16) | (-2, 0, -2, 4, 6, 4) | 右大腿 |
| rightLowerLeg | (0, 22) | (-2, 0, -2, 4, 6, 4) | 右小腿 |
| leftUpperLeg | (16, 48) | (-2, 0, -2, 4, 6, 4) | 左大腿 |
| leftLowerLeg | (16, 54) | (-2, 0, -2, 4, 6, 4) | 左小腿 |

---

## 五、CombatAvatarRenderer — 自定义渲染器

### 5.1 类定义

```java
package org.example.mod_1.mod_1.combat.client;

public class CombatAvatarRenderer
    extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, CombatPlayerModel> {

    public CombatAvatarRenderer(EntityRendererProvider.Context context) {
        super(context, new CombatPlayerModel(context.bakeLayer(CombatPlayerModel.LAYER_LOCATION)), 0.5F);
        // 添加必要的 layers
        this.addLayer(new PlayerItemInHandLayer<>(this));
        // 可选: 后续添加盔甲、披风等
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(AbstractClientPlayer player, AvatarRenderState state, float partialTick) {
        super.extractRenderState(player, state, partialTick);
        // 从 player 提取皮肤信息到 renderState
        state.skin = player.getSkin();
        // 提取其他需要的状态...
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.texture();
    }
}
```

### 5.2 关键说明

1. **继承 `LivingEntityRenderer`** 而不是 `AvatarRenderer`
   - `AvatarRenderer` 的构造函数硬编码创建 `PlayerModel`，我们需要 `CombatPlayerModel`
   - `LivingEntityRenderer` 的泛型参数: `<AbstractClientPlayer, AvatarRenderState, CombatPlayerModel>`

2. **模型类型**: `CombatPlayerModel` 必须实现 `ArmedModel` 和 `HeadedModel` 接口
   - `PlayerItemInHandLayer` 需要这两个接口才能正确定位手持物品

3. **extractRenderState()**: 必须正确填充 `AvatarRenderState` 的字段
   - 参考 `AvatarRenderer.extractRenderState()` 的实现
   - 关键字段: `skin`, `xRot`, `yRot`, `walkAnimationPos`, `walkAnimationSpeed`, `isSpectator` 等

4. **PlayerItemInHandLayer** 调用 `model.translateToHand()` 来定位手持物品
   - 我们的 `translateToHand()` 需要沿 17 骨骼链正确变换 PoseStack

---

## 六、EntityRenderDispatcherMixin — 渲染器替换

```java
package org.example.mod_1.mod_1.mixin;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(
        method = "getPlayerRenderer",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mod1_swapCombatRenderer(
            AbstractClientPlayer player,
            CallbackInfoReturnable<AvatarRenderer<AbstractClientPlayer>> cir) {
        // 返回值类型不完全匹配 (CombatAvatarRenderer 不是 AvatarRenderer)
        // 需要确认这里的类型兼容性
        // 方案: CombatRendererManager 返回缓存的渲染器实例
        CombatAvatarRenderer renderer = CombatRendererManager.getRenderer();
        if (renderer != null) {
            cir.setReturnValue((AvatarRenderer) (Object) renderer);
        }
    }
}
```

### 6.1 类型兼容性问题

`getPlayerRenderer()` 返回 `AvatarRenderer<AbstractClientPlayer>`。我们的 `CombatAvatarRenderer extends LivingEntityRenderer`，不是 `AvatarRenderer` 的子类。

**解决方案 A（推荐）：** 让 `CombatAvatarRenderer extends AvatarRenderer<AbstractClientPlayer>`
- 在构造函数中通过反射替换 `this.model` 字段
- `LivingEntityRenderer.model` 是 `protected M`，可以直接赋值（因为 CombatPlayerModel extends PlayerModel... 但实际上不行因为 PlayerModel 构造函数需要特定 ModelPart 结构）

**解决方案 B：** 使用 `RenderAvatarEvent.Pre` 事件代替 Mixin
- 取消 vanilla 渲染
- 手动调用 CombatAvatarRenderer 渲染
- 避免类型问题

**解决方案 C（最简单）：** 修改 Mixin 目标为更上层的 `getRenderer(Entity)` 方法
```java
@Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
        at = @At("HEAD"), cancellable = true)
private <T extends Entity> void mod1_swapCombatRenderer(T entity, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
    if (entity instanceof AbstractClientPlayer player) {
        CombatAvatarRenderer renderer = CombatRendererManager.getRenderer();
        if (renderer != null) {
            cir.setReturnValue(renderer);
        }
    }
}
```
`getRenderer()` 返回 `EntityRenderer<?, ?>`，类型更宽泛，不需要强转。

---

## 七、CombatRendererManager — 渲染器缓存

```java
package org.example.mod_1.mod_1.combat.client;

public class CombatRendererManager {
    private static CombatAvatarRenderer renderer;

    // 在 AddLayers 事件中调用
    public static void init(EntityRendererProvider.Context context) {
        renderer = new CombatAvatarRenderer(context);
    }

    public static CombatAvatarRenderer getRenderer() {
        return renderer;
    }
}
```

### 7.1 注册时机

在 `Mod_1.java` 的客户端事件中注册:

```java
// 注册 LayerDefinition
EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(event -> {
    event.registerLayerDefinition(CombatPlayerModel.LAYER_LOCATION,
        CombatPlayerModel::createBodyLayer);
});

// 初始化渲染器
EntityRenderersEvent.AddLayers.BUS.addListener(event -> {
    CombatRendererManager.init(event.getContext());
});
```

---

## 八、CombatAnimationController 修改

### 8.1 删除

```java
// 删除这个方法 (CombatPlayerAnimatable 类不存在)
public static CombatPlayerAnimatable getAnimatable() { ... }
```

### 8.2 修改 applyTo17Bones 签名

```java
// 旧签名
public static void applyTo17Bones(CombatPlayerModel model) { ... }

// 新签名
public static void applyTo17Bones(Map<String, ModelPart> boneMap) {
    // 逻辑不变，只是从 model.boneMap 改为直接传入 boneMap
    // 删除 model.resetAllPoses() 调用（由 CombatPlayerModel.setupAnim 负责）
}
```

---

## 九、mod_1.mixins.json

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "org.example.mod_1.mod_1.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "mod_1.refmap.json",
  "mixins": [],
  "client": [
    "PlayerModelMixin",
    "EntityRenderDispatcherMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  },
  "overwrites": {
    "requireAnnotations": true
  }
}
```

注意: `PlayerModelMixin` 保留（作为 fallback，或者如果决定全面替换可以删除）。

---

## 十、已有代码参考

### 10.1 关键 MC 1.21.11 API 签名

```java
// EntityRenderDispatcher
public AvatarRenderer<AbstractClientPlayer> getPlayerRenderer(AbstractClientPlayer player);
public <T extends Entity> EntityRenderer<? super T, ?> getRenderer(T entity);

// LivingEntityRenderer
public abstract class LivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
    extends EntityRenderer<T, S> implements RenderLayerParent<S, M>;
protected M model;
public void submit(S state, PoseStack pose, SubmitNodeCollector source, CameraRenderState camera);
public boolean addLayer(RenderLayer<S, M> layer);

// AvatarRenderer
public class AvatarRenderer<T extends Avatar & ClientAvatarEntity>
    extends LivingEntityRenderer<T, AvatarRenderState, PlayerModel>;
public AvatarRenderState createRenderState();
public void extractRenderState(T entity, AvatarRenderState state, float partialTick);
public Identifier getTextureLocation(AvatarRenderState state);

// ModelPart
public float xRot, yRot, zRot;  // 旋转 (弧度)
public float x, y, z;           // 位置偏移
public boolean visible;
public boolean skipDraw;
public void resetPose();         // 重置到 initialPose
public ModelPart getChild(String name);
public void translateAndRotate(PoseStack poseStack);

// AvatarRenderState (extends HumanoidRenderState)
public PlayerSkin skin;
public float xRot;               // 头部 pitch
public float yRot;               // 头部 yaw (相对身体)
public float walkAnimationPos;    // 走路动画位置
public float walkAnimationSpeed;  // 走路动画速度
public boolean isSpectator;

// RenderAvatarEvent.Pre — 可取消的玩家渲染事件
record Pre(...) implements Cancellable, RecordEvent, RenderAvatarEvent;
public static final CancellableEventBus<Pre> BUS;
```

### 10.2 现有动画文件

```
animations/basic/    — idle, walk, run, crouch, jump (5 个)
animations/combat/   — draw_weapon, sheath_weapon, dodge, block, parry (5 个)
animations/sword/    — idle, light_1/2/3, heavy, dash_attack (6 个)
animations/spear/    — idle, light, heavy (3 个)
```

共 19 个动画，全部 17 骨骼，Bedrock 格式 JSON。

### 10.3 动画骨骼名映射

动画 JSON 中使用的骨骼名与 boneMap 的 key 一致:
`head`, `neck`, `chest`, `waist`, `hip`, `rightUpperArm`, `rightLowerArm`, `leftUpperArm`, `leftLowerArm`, `rightUpperLeg`, `rightLowerLeg`, `leftUpperLeg`, `leftLowerLeg`

`root`, `rightHand`, `leftHand`, `weaponMount`, `sheathBack` 在动画中一般不直接旋转。

---

## 十一、注意事项

1. **Forge 61.0.6 API 差异** — 详见已有记忆文件 `feedback_forge_api.md`
   - `ResourceLocation` → `Identifier`
   - `IEventBus` → `BusGroup`
   - `CompoundTag.getInt()` → `getIntOr(key, default)`
   - 事件注册用 `EventName.BUS.addListener()` 模式

2. **坐标系验证** — 上面的骨骼 offset 和 cube 坐标是数学推导的，第一次跑可能需要微调。验证方法: 进游戏看模型是否对齐（头在上、脚在下、手臂在两侧）

3. **Vanilla fallback 动画** — `applyVanillaFallback()` 中的走路/待机逻辑需要参考 `HumanoidModel.setupAnim()` 源码。关键参数都在 `AvatarRenderState` / `HumanoidRenderState` 中

4. **手持物品** — `translateToHand()` 必须沿完整骨骼链 transform，否则武器位置错误

5. **第一人称手臂** — `AvatarRenderer` 有 `renderRightHand()` / `renderLeftHand()` 方法。如果需要第一人称手臂渲染，CombatAvatarRenderer 也需要实现这些

6. **编译顺序** — 先确保 `CombatPlayerModel` 编译通过（它被 CombatAnimationController 和 CombatAvatarRenderer 引用）

---

## 十二、实施顺序建议

1. `CombatPlayerModel.java` — 先写模型，确保 `createBodyLayer()` + 构造函数 + boneMap 正确
2. `CombatAnimationController.java` — 改签名，删 getAnimatable
3. `CombatAvatarRenderer.java` — 写渲染器，先只加 `PlayerItemInHandLayer`
4. `CombatRendererManager.java` — 简单的缓存类
5. `Mod_1.java` — 注册事件
6. `EntityRenderDispatcherMixin.java` — Mixin 替换
7. `mod_1.mixins.json` — 加新 mixin
8. 编译测试 → 调坐标 → 调动画
