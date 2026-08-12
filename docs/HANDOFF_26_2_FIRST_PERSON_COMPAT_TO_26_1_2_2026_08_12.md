# 26.2 → 26.1.2：第一人称动画让出层语义移植清单

日期：2026-08-12

目标基线：`origin/26.1.2@08b3559f936c7c4569874786f8fb094271f615e3`

来源功能提交：`67d5107 feat(compat): isolate TACZ first-person viewmodels`

> **禁止整提交 cherry-pick。** 26.1.2 的第一人称方法名、Iris hand-phase 门禁、scope
> depth-aperture 管线及已有兼容层都与 26.2 不完全相同。必须按本文逐项语义移植。

## 0. 先确认目标分支进度

26.1.2 当前基线已经是：

```text
mod_version=1.1.8+fabric.26.1.2.R1
name=[UNOFFICIAL] TaCZ Refabricated (26.1.2 R1)
```

所以：

- **不要**把 26.2 的 `+fabric.26.2.R1` 版本号移过去；
- **不要**覆盖 26.1.2 已有 R1 发布文案；
- 如果此前 Agent 已经移植 LRTactical tooltip/HUD/custom cooldown，保留其更完整实现，只补本清单。

## 1. `.30-06 孤星 手炮` 带镜换弹子弹消失：核对结论

### 1.1 已确认对象

问题枪是：

```text
tacz:lonetrail
中文名：.30-06 孤星 手炮
默认配镜：tacz:scope_contender
```

不是 `tacz:taurus500`。

### 1.2 不是 26.2 移植制造的资源差异

以下 Lonetrail 文件在本仓库、直接 Fabric 上游
`Sh1roCu/TACZ-Refabricated@d2903554` 与官方最新 1.20.1
`MCModderAnchor/TACZ@b43eb84c` 中一致：

- `animations/lonetrail.animation.json`
- `geo_models/gun/lonetrail_geo.json`
- `display/guns/lonetrail_display.json`
- `tacz_tags/attachments/allow_attachments/lonetrail.json`

模型树中：

```text
rh_and_bullet_inspect
└─ rh_and_bullet
   └─ bullet_in_barrel
      └─ round_shell
```

换弹动画直接驱动 `rh_and_bullet` 与 `bullet_in_barrel`。

### 1.3 根因类别

官方 1.20.1 `BedrockGunModel#render` 的逻辑是：

```text
先渲染 scope，写 stencil
安装纯筒镜时 stencilFunc(GL_EQUAL, 0)
然后 super.render(...) 一次性渲染整个枪模型
```

所以 `bullet_in_barrel` 和枪身共用同一个“镜内不画枪体”批次；它在换弹动画中经过
目镜投影区域时，也被当作枪体裁掉。26.1.2/26.2 的移植虽分别改成 depth-aperture 与
离屏 mask，但 `BedrockGunModel` 仍把整个 gun snapshot 交给一个 clipped RenderType，
因此保留了同类问题。是否开启光影不改变这条模型批次关系。

### 1.4 为什么本轮不做猜测修复

这不是安全的一行资源修复：

- 改 `lonetrail.animation.json` 的子弹轨迹会同时影响右手定位和作者动画；
- 通用代码修复要把 `bullet_in_barrel` 动态子树从 clipped gun snapshot 摘出，再用普通
  RenderType 重画；
- 若对所有枪生效，可能让闭膛待机弹、其他枪的 chamber round 穿过目镜可见；
- 若只针对 Lonetrail，还必须可靠判断 reload 时间窗，不能仅凭节点名；
- 26.1.2 是 depth-aperture，26.2 是 offscreen mask，提交顺序不能互抄。

结论：**这是上游遗留的 scope/body 批次设计问题，本轮不移植未经运行验证的硬编码修复。**
请把它保留为已知问题；若以后单独修，必须分别对 Lonetrail 的 empty/tactical reload、
有镜/无镜、ADS/腰射、vanilla/Iris 做逐帧测试。

## 2. 第一人称动画兼容层：要移植的功能

兼容契约：

- 普通物品继续由外部第一人称 Mod 控制；
- 主手是“注册了 `AnimateGeoItemRenderer` 且当前 stack 确实有模型”的枪/手雷/刀时，
  外部渲染层让出给 TACZ/LRTactical；
- 内容包模型不存在时不接管；
- 放下动画物品后立即恢复外部 Mod；
- 不修改其他 Mod 的用户配置文件。

26.1.2 可用的对应目标版本已经存在：

- First-person Model `2.7.2`（26.1.x Fabric）；
- Not Enough Animations `1.12.4`（26.1.x Fabric）；
- Punchy `2.6.2`（26.1/26.1.1/26.1.2 Fabric）；
- Hide Hands、SkyHands、Viewmodel Changer 等也有 26.1.2 路径。

## 3. 文件级移植步骤

### 3.1 新增通用 helper

语义来源：

```text
src/main/java/com/tacz/guns/compat/firstperson/FirstPersonAnimationCompat.java
```

可复用部分：

1. `getMainRenderStack(LocalPlayer)`：优先 `KeepingItemRenderer` 当前保留物品；
2. `isTaczViewmodel(ItemStack)`：
   - 非空；
   - `BuiltinItemRendererRegistry` 中是 `AnimateGeoItemRenderer`；
   - `getModel(stack) != null`；
3. FPM：通过反射调用公开
   `FirstPersonAPI.registerPlayerHandler(ActivationHandler)`；
4. NEA：桥接公开的
   `PlayerTransformer#renderingFirstPersonArm(boolean)`；
5. 目标 Mod 不存在时不得硬类加载或报错。

不要把 FPM/NEA 变成 required dependency。

### 3.2 改造 26.1.2 `ItemInHandRendererMixin`

26.2 来源不能逐字复制。两分支实际差异：

| 含义 | 26.2 | 26.1.2 |
|---|---|---|
| 外层方法 | `submitHandsWithItems` | `renderHandsWithItems` |
| 单手方法 | `submitArmWithItem` | `renderArmWithItem` |
| Iris phase 门禁 | 26.2 当前路径不在这里 | **26.1.2 必须保留** |

保留现有：

```java
@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
```

把对 `renderArmWithItem` HEAD 的整段接管，改成在外层调用点包裹：

```java
@WrapOperation(
    method = "renderHandsWithItems",
    at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(" +
                 "Lnet/minecraft/client/player/AbstractClientPlayer;" +
                 "FFLnet/minecraft/world/InteractionHand;F" +
                 "Lnet/minecraft/world/item/ItemStack;F" +
                 "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                 "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
    )
)
```

正式写入前必须用 26.1.2 jar 再核 descriptor，不要只相信本文字符串。

包裹逻辑：

1. 非本地玩家或不是真第一人称：`original.call(...)`；
2. 解析 `mainRenderStack`；
3. 主手由 TACZ 接管时，独立 offhand invocation 直接跳过；
4. 当前 stack 不是可渲染 TACZ viewmodel：`original.call(...)`；
5. **保留 26.1.2 原代码的 Iris 门禁：**

```java
if (!IrisCompat.shouldRenderInCurrentHandPhase(renderStack)) {
    return; // 不调用 original，避免错误 HAND_TRANSLUCENT pass 重画
}
```

6. main hand 调 `needReInit/tryInit/renderFirstPerson`；
7. 不再进入下游 `renderArmWithItem`，从而绕过 Viewmodel Changer 的 overwrite、
   Hide Hands/Collective 的取消，以及 SkyHands/swing Mod 的变换；
8. 普通物品必须完整调用 original，不能全局关掉外部 Mod。

### 3.3 `RenderHelper` 桥接 NEA

在 26.1.2 的：

```java
AvatarRenderer#renderRightHand / renderLeftHand
```

外层加入：

```java
FirstPersonAnimationCompat.beginDirectArmRender();
try {
    // 原有左右手提交
} finally {
    FirstPersonAnimationCompat.endDirectArmRender();
}
```

保留 26.1.2 自己的 `RenderSystem`、scope depth helper 与其他实现，不要用 26.2 文件覆盖。

### 3.4 初始化 FPM handler

在 `ClientSetupEvent#onClientSetup` 中调用：

```java
FirstPersonAnimationCompat.init();
```

放在可选兼容初始化区域即可。不得在 common/server 类中触发客户端反射。

### 3.5 Punchy 可选 mixin

语义来源文件：

```text
cn/sh1rocu/tacz/mixin/compat/punchy/PunchyArmRendererMixin.java
cn/sh1rocu/tacz/mixin/compat/punchy/PunchyHandEquipStateMachineMixin.java
cn/sh1rocu/tacz/mixin/compat/punchy/PunchyHandRenderBobContextMixin.java
cn/sh1rocu/tacz/mixin/compat/punchy/PunchyMovementStateMachineMixin.java
```

作用：

- `HandEquipStateMachine#wasItemBlacklisted` 对 TACZ viewmodel 返回 true；
- 取消 Punchy 第二套第一人称手臂；
- 跳过 walk/sprint/camera-lag 矩阵；
- 禁止 Punchy bob sample 泄漏到 TACZ ADS。

把四个 mixin 加入 **`tacz.fabric.mixins.json` 的 client 列表**，路径保持：

```text
compat.punchy.*
```

26.1.2 已有 `cn.sh1rocu.tacz.util.MixinPlugin`，它会从包名第 6 段读出 mod id
`punchy` 并按安装状态门控。不要另造第二个 plugin，也不要放进无 plugin 的
`tacz.mixins.json`。

Punchy 是 ARR 且不公开本体源码；当前 target 由 2.6.2 元数据、公开配置，以及
Scorched Guns / Epic Fight Compat 对 Punchy 2.5.3+ 的实际 mixin target 交叉核对。
因此保留：

```java
@Pseudo
require = 0
remap = false
```

并进行运行测试；Punchy 更新后不能只看编译通过。

### 3.6 更新过时注释

目标分支 `AnimateGeoItemRenderer` 仍写着
`tacz$submitArmWithGun / renderArmWithItem HEAD`。改成外层 call-site wrapper 的真实路径。
不要改历史归档文档。

## 4. 明确不要覆盖的 26.1.2 内容

- `IrisCompat.shouldRenderInCurrentHandPhase`；
- `GlCommandEncoderScopeDepthCopyMixin`；
- `ScopeRenderTypes` / `ScopeDepthCopyState` / depth-aperture shaders；
- 26.1.2 已有 Controllable mixin 与依赖；
- 26.1.2 的 R1 版本号与发布文案；
- 已更完整的 LRTactical feedback、枪等级 UI、Shoulder Surfing、custom-result normalizer；
- PAL 趴姿切枪的 26.1.2 已验证修复。

不要 cherry-pick 26.2 的整个 `67d5107`，尤其不要整文件覆盖
`ItemInHandRendererMixin`、`RenderHelper` 或任一 scope 文件。

## 5. 静态验证

至少执行：

```bash
git diff --check
./gradlew clean compileJava
./gradlew build
```

另做：

- 解析 `tacz.mixins.json`、`tacz.fabric.mixins.json`、`fabric.mod.json`；
- 反编译/字节码确认 `renderHandsWithItems -> renderArmWithItem` descriptor；
- 无 Punchy/FPM/NEA 时启动一次，证明 optional target 不导致 mixin/classload 崩溃；
- 有 Iris 时确认 solid/translucent hand phase 不重复提交。

## 6. 运行测试矩阵

1. 仅 TACZ：枪、刀、手雷第一人称行为不回退；ADS/开火/换弹正常。
2. 普通物品：FPM、Punchy、Hide Hands、SkyHands、Viewmodel Changer 原行为仍正常。
3. FPM + NEA：持普通物品显示身体；持枪/刀/手雷时让出；收起后恢复。
4. Punchy：普通工具动画正常；TACZ 无第二套手臂、walk bob、sprint swing。
5. Punchy + FPM + NEA。
6. Punchy + Viewmodel Changer。
7. Hide Hands 设置 always-hide 后，TACZ viewmodel 仍完整；普通物品仍服从 Hide Hands。
8. vanilla 与 Iris 各测一次，尤其检查 Iris HAND_SOLID/HAND_TRANSLUCENT 不重复。
9. 左利手、第一/第三人称快速切换、主手 TACZ 时副手不重复绘制。
10. LRTactical 无内容包时继续回退；有内容包时 throwable/melee 使用 TACZ renderer。

## 7. Lonetrail 已知问题专项测试（暂不修）

记录以下组合，供以后单独修复时使用：

```text
武器：tacz:lonetrail
瞄具：无 / scope_contender / 其他纯筒镜
换弹：reload_empty / reload_tactical
姿态：腰射开始换弹 / ADS 后开始换弹
渲染：vanilla / Iris
观察：round_shell 从抛出、手持到插入的完整时间窗
```

在没有“拆出动态子树后不会让其他枪的 chamber round 穿镜”的运行证据前，不要把
`bullet_in_barrel` 全局改成未裁剪 RenderType。
