# 接收单：NeoForge 姊妹仓 TaCZ_Renovated

**这是给姊妹仓的指导，不是 NeoForge 构建或合并回执。** 建议先在 **Neo 26.2** 接：游戏版本、Blaze3D 和离屏镜孔最接近供体。注册、配置、网络、库存事务和可选模组入口则留在 Neo。哪一块能复用，以[公共用例](README.md)在 Neo 成品上的结果为准。

### 先接上已有工作

Neo 26.2 长期分支基线是 [`2334cfe`](https://github.com/q14433686-arch/TaCZ_Renovated/tree/2334cfe4701d2724bfa27a60912e5f5f9166ca73)（`1.1.8+neoforge.26.2.R3-hotfix4`）。另有 [`bf911b9` 的 1.2 工作记录](https://github.com/q14433686-arch/TaCZ_Renovated/blob/bf911b9b99049bc00ceaad2019a312c4f1c4983c/docs/PORTING_STATUS.md)：B1-1 已接 GZIP 分片，B1-2 已接配件脚本预编译/沙箱和部分脚本异常保护；该记录明确区分了源码、CI 和未实机项。**先和这条工作线对表**，不要另起一份分片器、重复注册同一消息，或把 B2–B5 当成已到位。Fabric 补充的是经过验收的行为和对照，不取代姊妹仓[自己的上游审计](https://github.com/q14433686-arch/TaCZ_Renovated/blob/bf911b9b99049bc00ceaad2019a312c4f1c4983c/docs/records/UPSTREAM_120_SCOPE_AUDIT_20261107.md)。

| Neo 接收线（已核源码快照） | Java / NeoForge 构建 pin | 渲染与移植方向 |
|---|---|---|
| [26.2 · `2334cfe`](https://github.com/q14433686-arch/TaCZ_Renovated/blob/2334cfe4701d2724bfa27a60912e5f5f9166ca73/gradle.properties) | 25 / `26.2.0.64` | `ScopeMaskRenderer` 离屏孔径；先保原有 Iris HAND 裁剪，再移本次镀膜、分划和热像修正。 |
| [26.1.2 · `400b986`](https://github.com/q14433686-arch/TaCZ_Renovated/blob/400b9864ff898a748a76253ce6c82360733cd59b/gradle.properties) | 25 / `26.1.2.97` | 深度孔径；参照 [Fabric 26.1.2](26.1.2.md) 的几何与测试，不搬 Fabric bundle、事件或网络注册。 |
| [1.21.11 · `682adce`](https://github.com/q14433686-arch/TaCZ_Renovated/blob/682adce2ddda535326afd7a31938c450c44edd51/gradle.properties) | 21 / `21.11.45` | 深度孔径；对照 [Fabric 1.21.11](1.21.11.md) 的目标字节码与 shader 风险，构建仍走 **ModDevGradle/FML**，不引入 Loom remap。 |
| [26.3 · `d086f0d`](https://github.com/q14433686-arch/TaCZ_Renovated/blob/d086f0d61b7de9271dc98ae56d04bb9c16765b04/gradle.properties) | 25 / `26.3.0.51-beta` | 如接这条线，先比[同游戏版本 Fabric 26.3](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/c5d536b7b1e61bcacb0cd660df73de78b7db6796)的 RenderPearl 接口；用 26.2 作行为/图像对照，不能搬 Blaze3D pass。 |

### 26.2 的接线清单

| 领域 | 姊妹仓落点与验收 |
|---|---|
| 启动和注册 | 沿 [`GunMod`](https://github.com/q14433686-arch/TaCZ_Renovated/blob/2334cfe4701d2724bfa27a60912e5f5f9166ca73/src/main/java/com/tacz/guns/GunMod.java) 的 mod event bus、`DeferredRegister` 和 `ModContainer.registerConfig` 接入方块、方块实体、菜单、配方和配置；按 FML 区分客户端与公共事件。元数据在 `src/main/templates/META-INF/neoforge.mods.toml`，用本仓 access transformer、Jar-in-Jar 和可选依赖声明。不要移 `fabric.mod.json`、Fabric entrypoints、access widener 或 FCAP；检查 MAE 类只有一个提供方。用不装客户端可选模组的专服查类加载。 |
| 同步与版本 | [`NetworkHandler`](https://github.com/q14433686-arch/TaCZ_Renovated/blob/bf911b9b99049bc00ceaad2019a312c4f1c4983c/src/main/java/com/tacz/guns/network/NetworkHandler.java) 已区分 `RegisterPayloadHandlersEvent` 的 play/configuration。Neo 工作线把一个 `Part` 编进既有 `server_sync_gun_pack` 并用协议 `1.0.14`；**不是** Fabric `ServerMessageSyncGunPackPart` 的通道协商。先决定支持哪些同加载器对端；不支持的在握手明确拒绝，支持旧端才另协商旧格式。保持 `ClientPacketBridge`、连接身份检查、大小上限、收齐后换索引；确认退出清空、重载和重连接新的快照。不要用 Fabric 客户端验 Neo 服务端或反过来。 |
| 内容、库存、脚本 | Neo 有源码资源树，不需要 Fabric 旧线的资源 bundle。先比最终有效路径，按本版 codec 生成配方、进度、loot 与包元数据。`GunPackLoader` 在 Neo 用 Maven 版本区间并剥离 `+…`；为 `>=1.2.0`、`[1.2.0]` 与排除 1.2.0 的区间各做正反对照。弹药库与展示位接现有 Neo `ResourceHandler<ItemResource>`/事务而非 Fabric Transfer API，测取消、容量不足、升档、保存重进与拆除只掉一次。保留配件脚本沙箱和 `GunScriptGuard` 的异常兜底，单独验证玩法时钟和取消动作不重复结算。 |
| 界面与可选模组 | 在 `com.tacz.guns.compat.rei` / `compat.jei` 及 Neo 客户端事件接入，查各线实际 pin（例如 Neo 26.2 的 JEI 是 `30.24.0.176`，不是 Fabric 的 `.86`）。先做无查看器的真实合成，再分装 JEI/REI 核对包同步、普通配方、同一基础物品不同 `BlockId`。账号 HUD 验证保存和取消。保留姊妹仓已有 Carry On、PAL 等兼容；未运行的集成不标绿。 |
| 渲染 | 在 Neo 现有 `IrisScopePipelineCompat` 的真实手部阶段传入本次 draw 的裁剪方向，不全局重绑原版管线；实现原始分划和实际实体表面的深度匹配，而不是抄一组 GL 状态。先无光影，再在该版 Iris/Sodium + r5.8.1 跑[正反对照](README.md#optics)；单列 OpenGL/其他后端。没有合适可选构件或不能观测原始遮挡时，写明限制，不以 Fabric 的 Mesa 原帧代测。 |

**Neo 26.2 第一轮命令**（在姊妹仓该目标执行，其他版本先核对其 Gradle 任务）：

```bash
bash scripts/check_release_consistency.sh --strict
./gradlew clean build workbenchRecipeValidationTest targetBlockEntitiesTest
sha256sum "${PRODUCT_JAR:?指定生成的 NeoForge 模组 JAR}"
```

这些 Gradle 回归使用测试桩，不能代替游戏。把同一成品装入两个匹配的 NeoForge 实例（真实专服 + TCP 客户端），依次验证旧导出备份、仅专服包、`/reload`/退出/重连、存档组件，再加可选模组。按**每个 Neo 版本分别**在姊妹仓记 SHA、日志、原帧与未测项；不沿用 Fabric 的散列、通过状态或版本名。
