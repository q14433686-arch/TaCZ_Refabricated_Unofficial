# 26.3：枪包没做 LOD 时，掉在地上的枪画什么？

**取舍：不隐藏掉落物，不要求修改枪包。** 近处保留原来的 3D 路径；远处如果当前 display 没有可用 LOD，但有 `slot` 贴图，就用那张图代表这把枪。不生成假 3D 低模，也不引入 Accelerated Rendering。

## 为什么只缓存模型还会卡

`GunItemRendererWrapper` 的外显模型选择是非阻塞的。`GunLodRenderDistance = 0` 表示总是**优先**请求包里的 LOD，不是自动减面：低模烘焙结果为空时，`DisplayBake.preferOver` 会回退到高模。已缓存的是一套骨架，不是每种改装外观的最终顶点。一堆可见的枪仍要分别解析本栈配件/弹匣状态、捕获骨骼变换并提交几何。内置 TML 的常驻缓冲只覆盖符合条件的 `poly_mesh`，不等于给所有 Bedrock 方块和配件缓存了整枪。

还发现一个不必牺牲画质的开销：`BedrockCubePerFace` 过去对缺省的每个面提交四个**位置重合**的顶点。现在普通顶点写出阶段跳过这个明确的占位数组。作者写了面、只是 UV 大小为零（例如取一个纹素），仍然是有效几何；不猜测朝向、不剔除真实背面、不改瞄具掩码读取的六面布局。这项小优化在仍画 3D、没有 `slot`、关闭图标回退时也适用；它不减少 `poly_mesh` 的顶点数。

## 参考了哪些方案

| 对照源码（固定提交） | 借鉴与不照搬的地方 |
|---|---|
| TaCZ [Forge](https://github.com/MCModderAnchor/TACZ/blob/618c927fe4b1a41ddbbfd11dfcb8af638ac570f0/src/main/java/com/tacz/guns/client/renderer/item/ExternalGunItemVboRenderer.java) / [Fabric](https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/client/renderer/item/ExternalGunItemVboRenderer.java) 外显 VBO，[外观键](https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/client/renderer/external/ExternalGunAppearance.java) | 静态几何按外观和语境复用，能保留近景配件；缺 LOD 不会凭空生成低模。26.3 若移植，还要覆盖资源代际、部件显隐、材质和实例光照，不能只按 gun ID 缓存可变骨架或上帧的世界姿态。 |
| AR [物品四边形变换](https://github.com/Argon4W/AcceleratedRendering/blob/7608c0b6fd2c09bece81ed32f97366f93a64c5db/src/main/java/com/github/argon4w/acceleratedrendering/features/items/AcceleratedQuadsRenderer.java) / [网格收集](https://github.com/Argon4W/AcceleratedRendering/blob/7608c0b6fd2c09bece81ed32f97366f93a64c5db/src/main/java/com/github/argon4w/acceleratedrendering/core/meshes/collectors/CulledMeshCollector.java) | 缓存几何、将变换移向 GPU，与**少画几何**是两件事。检查的 [AR](https://github.com/Argon4W/AcceleratedRendering/blob/7608c0b6fd2c09bece81ed32f97366f93a64c5db/gradle.properties) 和 [Fabric 分支](https://github.com/Origin-Technology/AcceleratedRendering-Fabric/blob/c2e7be513d7f6d48503f80978cd75442288585fd/gradle.properties) 目标均为 1.21.1；本改动未启用旧 AR mixin，也不宣称 AR 与 26.3 兼容。 |
| [Boss Optimizations 26.2：物品堆分组限额](https://github.com/WaterBoss11/boss-optimizations/blob/6707d67b15741fe9c36d94a1356e7c78ef9d9780/src/client/java/dev/bossoptimizations/itemrendercap/RenderCap.java)；[MoreCulling 26.2：物品展示框选面](https://github.com/FxMorin/MoreCulling/blob/cf4826d6e0074d831f6254a3d9ff4fedcb89340c/common/src/main/java/ca/fxco/moreculling/mixin/entities/ItemFrameRenderer_cullMixin.java) | 前者在提取前按组决定哪些实体可见；后者针对展示框省去不用画的面，不是掉落物 LOD。这里也尽早选路径，但不扫描一遍世界或让一把仍可拾取的枪消失。 |

## 客户端规则

在「渲染」配置页或 `config/tacz-client.toml` 调 `DroppedGunSpriteDistance`。默认 **8**（格，普通视角），**0** 表示近处也用图标，**-1** 关闭图标回退。正距离沿用世界模型的开镜放大系数延长近景范围。这个键不改变 `GunLodRenderDistance` 的含义。

- 在 `TaczDynamicItemModel.update`，只有 **GROUND + `ItemEntity` 主人 + TaCZ 枪渲染器** 才有资格。不从 GUI 时间戳或模型矩阵猜是不是掉落物。第一、第三人称、展示框、挂板、枪匠桌预览及其他物品不切换成这个图标。
- 看本栈实际选择的 display。LOD 模型、贴图文件齐全且只是等待异步烘焙时，保留原外显选择；字段不全、文件不存在，或低模烘焙已确定为空，才可用图标。`slot` 未填或文件不存在时仍走原 3D 路径。文件存在性不是 PNG 内容校验器。按 display 实例记住文件查询；资源重载后新 display 重新判定。
- 代理只携带贴图 ID，交给原版 collector 画一个朝向相机的 cutout 四边形，使用该实例的光照、overlay、悬浮位置与堆叠偏移，不走持枪动画或逐骨骼快照。**远处看不到逐件配件，也不再随掉落物转到侧面。** 拾取、存档、服务端、默认资源不变。有正确的作者 LOD 时首选作者的外观。

## 如何复测

`DroppedGunSmoke` 使用外置测试 ZIP：无 `lod` 的 display 引用未修改的 AK 高模，`slot` 用单独的青色诊断纹理，使「真画出图标」可以做像素对照。在固定相机前生成真实、同步到客户端的 `ItemEntity`，同一组实体只切换配置。记录两种不同的量：

1. **几何**：用真实 `ItemEntityRenderer` 再提取并提交这批实体，重放延迟回调，计数 `VertexConsumer` 实际收到的顶点与 `submitCustomGeometry` 次数。这不是 GPU draw-call 计数。16 把必须全部在场；图标路径必须每把 4 顶点，3D 对照必须已画出高模，不能拿等待烘焙的临时槽位贴图充数。
2. **帧耗时**：另在正常游戏世界渲染中，每档预热后采 32 帧 `Minecraft.getFrameTimeNs()`，报中位数 / P95。它是 MC 的 renderFrame 计时段，不含最终 present 等待，不是独立 GPU 耗时；CI 软件渲染的绝对值不是玩家显卡的 FPS。断言选路、几何和屏幕新增的图标像素，**不**按某个速度倍率判 CI 是否通过。

复跑：JDK 25，在装有 Xvfb / Mesa Vulkan 的环境按 `build.yml` 运行 `xvfb-run -a bash scripts/run_client_smoke.sh`（本机给 `RUNNER_TEMP` 设可写临时目录）。在客户端日志搜 `PORT_DROPPED_GUN`，开关帧保存在测试截图目录。玩家复测用自己的枪包，在同一地点、朝向、距离、掉落物数量和光影条件下比 `-1` / `8`；若问题在脚边，比 `-1` / `0`。如果切成图标后仍卡，应另测实体数量和其他模组开销，而不是继续减枪模顶点。
