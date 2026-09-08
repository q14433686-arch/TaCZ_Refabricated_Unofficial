# 高模枪检视反射异常：Iris setup 的 RenderPass 生命周期

2026-09-08 · refab 26.1.2 · **静态修复、待实测**。

维护者反馈：26.1.2 / 1.21.11 高模枪开光影检视时，多数角度枪体偏黑、反射异常，
仅少数角度正常；26.2 无此症状。反馈明确早于日志五件套修复。本件是独立的后续修复，
不归因于删除 vanilla `assignCommonEntityPipelinesToHandIfNeeded`，也不解释世界全透明。

## 1. 源码确认的缺口（纠正 2026-09-01 的「同语义」结论）

旧记录 `MESH_GPU_NORMAL_MATRIX_2612_20260901.md` 认定「每根骨骼 draw 前压入 MV，
画完弹出」与 26.2 等价，漏查了 **Iris setup 的一次性守卫及其清除边界**。
压栈仍然必要，但在多骨骼共用 pass 时并不充分。

本次固定版本源码审计：

- Iris **26.1** `f4c06978f3a1c64869e40cd5cc7c8ed383085cc0`：
  [`MixinGlCommandEncoder.java` L162–178](https://github.com/IrisShaders/Iris/blob/f4c06978f3a1c64869e40cd5cc7c8ed383085cc0/common/src/main/java/net/irisshaders/iris/mixin/MixinGlCommandEncoder.java#L162-L178)
  在 `trySetup` RETURN 仅当 `program instanceof IrisProgram && !iris$isSetUp()` 时调用
  `onSetAlbedoTex` 和 `iris$setupState`，并加入 `programsToClear`；
  **到 `finishRenderPass` 才 `iris$clearState()` 并清空列表**。
- 同版 [`ExtendedShader.java` L161–190](https://github.com/IrisShaders/Iris/blob/f4c06978f3a1c64869e40cd5cc7c8ed383085cc0/common/src/main/java/net/irisshaders/iris/pipeline/programs/ExtendedShader.java#L161-L190)：
  `clearState` 置 `isSetup=false`，`setupState` 置 `isSetup=true`；
  `iris_ModelViewMatInverse` / `iris_NormalMat` 在 setup 时由
  `RenderSystem.getModelViewMatrix()` 求逆 / 逆转置上传。
  **26.1 的方法名不是 26.2 的 `getModelViewMatrixCopy()`**，旧注释也应纠正。
- 同版 [`IrisRenderingPipeline.java` L849–869](https://github.com/IrisShaders/Iris/blob/f4c06978f3a1c64869e40cd5cc7c8ed383085cc0/common/src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java#L849-L869)：
  `onSetAlbedoTex` 在适用条件下更新对应 albedo 的 normal/specular PBR 贴图。
- Iris **1.21.11** `11f566b7fbc8da1f62437eda86ee68fda9cf2ee0`：
  [`MixinGlCommandEncoder.java` L185–201](https://github.com/IrisShaders/Iris/blob/11f566b7fbc8da1f62437eda86ee68fda9cf2ee0/common/src/main/java/net/irisshaders/iris/mixin/MixinGlCommandEncoder.java#L185-L201)
  和 [`ExtendedShader.java` L159–192](https://github.com/IrisShaders/Iris/blob/11f566b7fbc8da1f62437eda86ee68fda9cf2ee0/common/src/main/java/net/irisshaders/iris/pipeline/programs/ExtendedShader.java#L159-L192)
  是同一个 setup/clear 边界；虽每次 draw 都到 `trySetup`，并不意味着每次都执行 setup。

两线原实现：一个 RenderPass 包住全部纹理组/全部骨骼，逐骨骼只更新
`DynamicTransforms` 和 MV 栈。因此第一个骨骼完成 setup 后：

1. 后续骨骼的位置使用自己的 transform 切片；
2. 法线/逆 MV uniform 却仍是第一次 setup 的值，骨骼旋转不同时两者失配；
3. 后续纹理组仅 `bindTexture("Sampler0", ...)`，不会重新经过 albedo/PBR 通知。

这能解释为什么检视等骨骼相对旋转时明暗/高光更容易异常，也可能影响多材质枪。
**源码证明的是上述状态错用，尚未用维护者的枪包/光影包复现并确认全部外观症状。**
如果玩家实际走的是 collector 而非 GPU，这个缺口不能直接解释该次复现。

## 2. 三线差异

- **refab 26.1.2**：本会话代码基线 `2e2682a`，`drawList` 一个自建 pass 绘制多根骨骼。
- **refab 1.21.11**：实拉 HEAD `6db3af93aebf183385cce520272a5e1dd4f6cada`，同样共用 pass；
  虽也有 per-draw MV push/pop，仍受上述守卫影响。**本会话未修改、未编译此线**。
- **refab 26.2**：对比 HEAD `457285c25b508d865189a7cb166d03f31c65a903` 的
  `PolyMeshGpuRenderer.drawViaRenderTypeCore`，光影路径每根骨骼分别调用
  `RenderType.prepare()` / `PreparedRenderType.drawFromBuffer()`，不是本线那种
  把所有骨骼放进一个自建 pass 的结构；维护者反馈该线无此症状。

## 3. 本线改动与边界

- 用小型、无 Minecraft 依赖的 `MeshRenderPassBatches.partition` 决定 pass 边界：
  **Iris 每根骨骼一个 pass；无光影仍为一个批次**。
- 每个 pass 重绑 pipeline / 默认 UBO / lightmap / scissor / 对应 albedo，并保留
  骨骼 MV 的 push/try/finally-pop。pass 的 try-with-resources 结束后，Iris 清掉 setup
  标记，下一根才会按自己的矩阵和材质重新 setup。
- 保留纹理分组顺序；无光影时相邻同纹理不重复 bind。VBO 烘焙、缓存与顶点格式不变。
- 所有 `writeTransform`、索引缓冲预热、纹理懒加载仍在所有 pass **之前**完成，
  不引入 open-pass 内 map/upload，不嵌套 pass，不清空颜色/深度。
- 每个 pass 保留 scope mask begin/end 配对与深度纹理绑定；需回归高倍镜/PIP。
- 手部/世界 GPU 路径共用 `drawList`；不启用原本关闭的世界 GPU 开关，不影响 collector。
- 不新增 Iris 内部 mixin/反射，不手改 GL normal uniform，不改光影包，不翻转法线，
  不改版本、依赖版本或配置默认值。
- 性能代价：光影下 pass/setup 次数增加到骨骼数，draw 数及 VBO 数不增加。
  **性能未测**；不能把多个同纹理、不同骨骼矩阵的 draw 重新合并而不处理 Iris setup。

## 4. 验证

- [x] `git diff --check`
- [x] `bash scripts/check_release_consistency.sh --strict`
- [x] `python3 docs/check_mixin_registration.py`
- [x] `python3 docs/check_lang_keys.py`
- [x] `python3 scripts/check_visible_bug_resources.py`（前一修复的资源回归）
- [x] 代码提交 `9412e08ec139191c91f1345679f3ad86feaad112` 的 CI
  [compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/34187214365)
  通过；编译日志已回推并同步 `build-reports/compile-java.log`。
- [x] 同一代码提交的 CI [全量 build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/34187214382)
  通过（`check` 强制依赖新增的 `meshRenderPassTest`），JAR artifact 上传成功。
  该提交的 PR compile-check / build 也均成功（runs `34187216605` / `34187216634`）。
  本机无 Java，测试经 CI 执行；Actions 全量日志下载仍被沙箱网络 EOF 阻断，
  以上依据为 Actions 作业成功状态、`build` 任务依赖及回推的编译日志，而非实机验证。

新增无第三方测试依赖的 `./gradlew meshRenderPassTest`，挂入 `check` / `build`：
实际调用生产用的分批策略，并用小型状态机模拟上述已审计的 Iris 一次 setup / pass-close
清除协议。覆盖：旧共享 pass 确实产生两根骨骼法线错用及一次材质错用；仅按纹理分 pass
仍会失败；修复后的 24 组检视旋转、同纹理不同骨骼、多纹理、空表/单骨骼和 vanilla
单批次保持正确。**这是生命周期模型回归，不是真实 Iris/OpenGL 集成测试。**

## 5. 实机待测 / 请维护者提供

请提供枪包名、枪械名、光影包名和版本，确认同一枪包/光影配置在 26.2 正常；最好附
检视短视频和本次日志中的 `[TacZMeshLoader] GPU mesh pass drew ... in Iris hand flush`。
该日志或配置可确认实际是否命中本次修改的 GPU 路径。

同一存档/光源/光影配置下：

1. 第一人称腰射与完整检视：枪体、弹匣等旋转不同的部件，明暗、高光与反射连续正常。
2. 多材质枪的 PBR normal/specular 贴图正确，切枪/换包/F3+T 无错贴或新异常。
3. 同一把枪把 `MeshGpuUnderShaders=false` 作为 GPU/collector **诊断对照**，不是永久修复。
4. 高倍镜/PIP 下枪身裁剪仍正确；无光影、单骨骼及普通非高模枪无回归。
5. 若开启世界 GPU，回归第三人称、掉落物、展示框；记录帧时间，评估额外 setup 开销。

所有实机项仍待测。本次不声称已实测解决维护者的反射症状。
