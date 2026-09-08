# 高模枪光影检视：Iris RenderPass 生命周期修复回流

2026-09-08 · **refab 1.21.11 / Java 21** · **静态修复、待实测**。

来源：[PR #92](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/pull/92)
的独立 mesh 修复提交 `9412e08ec139191c91f1345679f3ad86feaad112`。
本线在 `95fedbf`（已含 39JqB2p 的 B/C/D）上适配，不重复引入可见日志补丁，
也不合并 26.1.2 的整份渲染器、版本或混淆设置。

维护者在 #92 的反馈早于日志五件套：26.1.2 / 1.21.11 开光影检视高模枪时，
多数角度偏黑、反射异常。**本件不归因于删除 vanilla HAND 注册，不解释世界全透明。**
源码确认的是 GPU 路径的状态生命周期缺口，尚未在维护者的枪包/光影包上确认症状消失；
若实际走 collector，本修复不能直接解释那次复现。

## 1. 本轮核对的 1.21.11 源码

固定 Iris 提交 `11f566b7fbc8da1f62437eda86ee68fda9cf2ee0`：

- [`MixinGlCommandEncoder.java` L185–201](https://github.com/IrisShaders/Iris/blob/11f566b7fbc8da1f62437eda86ee68fda9cf2ee0/common/src/main/java/net/irisshaders/iris/mixin/MixinGlCommandEncoder.java#L185-L201)：
  `trySetup` RETURN 中只有 `!iris$isSetUp()` 时才调用 `onSetAlbedoTex` / `iris$setupState`；
  到 `finishRenderPass` 才清除 setup 状态。
- [`ExtendedShader.java` L159–192](https://github.com/IrisShaders/Iris/blob/11f566b7fbc8da1f62437eda86ee68fda9cf2ee0/common/src/main/java/net/irisshaders/iris/pipeline/programs/ExtendedShader.java#L159-L192)：
  setup 从 `RenderSystem.getModelViewMatrix()` 计算并上传逆 MV 与逆转置法线矩阵。
  此版本不是 `getModelViewMatrixCopy()`。
- [`IrisRenderingPipeline.java` L847–869](https://github.com/IrisShaders/Iris/blob/11f566b7fbc8da1f62437eda86ee68fda9cf2ee0/common/src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java#L847-L869)：
  albedo 通知在适用条件下更新 normal/specular PBR 纹理及采样器。

本线原 `PolyMeshGpuRenderer.drawList` 的所有纹理组/骨骼共用一个 RenderPass。
首根骨骼 setup 后，后续骨骼虽有自己的 DynamicTransforms 切片和 MV push/pop，
法线/逆 MV 仍沿用第一次 setup 的值；切换 `Sampler0` 也不重新通知 PBR。
**逐 draw 进入 trySetup ≠ 逐 draw 执行 iris$setupState**，更正 9 月 1 日的旧推断。
仅按纹理拆分也不充分：同纹理骨骼同样会有不同姿态。

## 2. 适配与不变量

- 同步无 Minecraft 依赖的 `MeshRenderPassBatches.partition`：光影下每个 DrawEntry
  （骨骼）一个 pass，无光影继续一个 pass；保持既有纹理分组遍历顺序。
- 每个 pass 重绑 pipeline、scissor、默认 UBO、lightmap、albedo；骨骼 MV 保留到
  `drawIndexed(0, 0, count, 1)` 完成，在 `finally` 还原。pass 关闭后才开始下一根骨骼。
- 所有 `writeTransform`、纹理加载、索引预热仍在所有 pass 之前完成。
  沿用本线预先取得的 `nearestSampler`，不搬 26.1.2 的 lightmap API。
- 保留 1.21.11 的输出目标选择、深度孔径/PIP 路线、每个 pass 的 scope mask begin/end。
  不嵌套 pass、不清颜色或深度，不修改任何 mixin 目标或映射。
- 手部与世界 GPU 共用 `drawList`，两者均使用相同边界；本线光影手部/世界 GPU
  开关在 9 月 2 日已默认 true，本次**不改默认值**，不照抄 26.1.2 的默认关闭描述。
- VBO 缓存、烘焙、顶点格式、collector、法线/绕序选项、异常回退策略不变。
- 光影下 pass/setup 次数增加到骨骼数，draw 数和 VBO 数不增加。**性能未测**。

## 3. 自动验证

同步 #92 的 `tests/mesh-render-pass/MeshRenderPassBatchesTest.java`，用本线 Java 21
编译运行；`./gradlew meshRenderPassTest` 挂入 `check` / `build`。

测试调用实际生产分批策略，以小型状态机模拟一次 setup / pass-close 清除协议，覆盖：
旧共享 pass 的错误重现、仅按纹理拆分仍失败、同纹理不同骨骼、多材质、24 组检视旋转、
空表/单骨骼、绘制顺序与数量、vanilla 单批次。**不是实际 Iris/OpenGL 集成测试**，
不能据此宣称已实测修复反射或 PBR 外观。

本轮编译/构建结果单独记录在 `build-reports/mesh-pass-lifetime-1.21.11.md`；
不得用 #92 的 26.1.2 CI 代替本线验证。

## 4. 实机待测（全部未执行）

1. 确认日志为 `GPU mesh pass drew ... in Iris hand flush`，使用报告者的枪包/光影包，
   完整检视枪体、弹匣等不同旋转部件，检查明暗/高光/反射连续性。
2. 多材质枪的 PBR normal/specular，切枪、换包、F3+T 后无错贴或异常。
3. 用 `MeshGpuUnderShaders=false` 作 collector 对照（诊断而非永久禁用方案）。
4. 高倍镜/PIP 枪身裁剪、无光影、单骨骼及普通枪无回归。
5. 世界 GPU 下第三人称、掉落物、展示框；记录帧时间以评估额外 setup 开销。
