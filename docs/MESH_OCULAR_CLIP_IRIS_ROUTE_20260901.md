# 目镜裁剪收尾与 PIP 边界三修（2026-09-01 第三轮）

本轮处理上一轮实机反馈的三个问题。**全部运行期行为未验证**，待维护者/用户实机复验。

## 问题 1：光影 + 二次渲染，开/退镜时「闪一下」

- **机制**：「开镜即接管」（d3f0fdc）后，二次渲染合成在整个滑入期间运行。开镜第 1 帧
  （以及退镜最后几帧）镜孔掩码还落在髋部枪身位置——合成把镜内画面贴片直接贴在枪身/机匣
  上，开关镜边界各闪现一次。旧静态贴图 bug（上一轮已修）让贴片常驻，反而掩盖了这个边界
  闪现；静态层消失后它才显形。
- **修法**（`7eca413`）：二次渲染合成（`compositeAfterHand` 与 `compositeAfterIrisFinal`
  同步）加滑入显示阈 `RERENDER_REVEAL_THRESHOLD = 0.35`——进度低于阈值的滑入段不画贴片。
  接管时机不变（窄遍/捕获/预热仍在开镜瞬间启动），只遮掉「贴着枪身」的一小段；
  重投影路径的全 ADS 门（`IRIS_FULL_AIM_THRESHOLD = 0.995`）不动。

## 问题 2：无光影 + 二次渲染 PIP，目镜初始/退出位置出现「透视面」

- **机制**：`maskValid` 只在 BACKUP/APERTURE_COPY 翻转，没有帧界。瞄具不提交的帧（腰射态）
  它带着退镜帧（髋部镜孔位置）的真值跨帧滞留——上一轮给 poly_mesh 手部批次加的孔外剔除
  以它为闸门，于是腰射态枪身被按「退镜那一刻的镜孔」永久裁出一个洞（目镜形状/大小、
  穿透枪体与配件）。与 PIP 开关无关（视图懒建使现象在 PIP 开启时更先被观察到）。
- **修法**（`752ee9e`）：`GameRenderer.render` HEAD 调 `ScopeDepthCopyState.onClientFrameStart()`
  帧首失效 maskValid/backupValid/maskWorldValid，闸门收紧为「本帧手部阶段确有完整掩码周期」。
  当帧 BACKUP→APERTURE_COPY 照常翻回真；帧内全部消费者（mesh 剔除、PIP 合成、终局叠加）
  都晚于手部阶段，不受影响。滑入期间的剔除行为与 vanilla 枪一致（clipForViewmodel 全程生效）。

## 问题 3：光影下高模枪体仍不被目镜裁剪（上一轮 Bug A 的光影半区）

- **机制**：Iris 的 `GlCommandEncoder#trySetup` 把自研管线替换成光影包的 gbuffers_hand
  ExtendedShader——`mesh_entity_scope_clip.fsh` 根本不参与绘制，RenderPass 采样器绑定也随之
  失效。光影下真正在跑的裁剪代码是 `IrisDepthRestoreShaderMixin` 注入 hand 着色器的休眠
  `tacz_ScopeMaskMode` 分支（vanilla viewmodel 路径靠 `DepthCopyRenderType` 的 GL-uniform
  翻转驱动它），mesh 批次此前无人翻转。
- **修法**（`d6743e5`）：`drawList` 分流——无光影保持 fsh + RenderPass 采样器路线（实机已验证）；
  光影下改走 vanilla 同款 GL-uniform 路线：新公有入口
  `ScopeDepthCopyState.beginExternalMaskOutsideDraw()`（= `begin(MASK_OUTSIDE)` + `beforeDraw()`，
  即身份守卫 + 绑 aperture 拷贝单元 + 置 mode 2，world 深度取 Iris depthtex2），批次绘制完
  `end()` 归还纹理单元（try/finally 配对，与 ScopeRenderTypes setup/clear 同构）。
  注入分支缺失或掩码失效时 mode 恒 0 = 不裁剪（fail-open）。

## 补遗（同日第四笔反馈：一帧「截图」贴屏，同源不同窗）

用户澄清：上一条「闪一下」实指**与 Bug B 同源的贴屏**，只是只在开/退镜边界存活一帧——
随机位置贴上一张全视界「截图」，下一帧自愈。机制与修法：

- **机制**：Iris 终局钩子（`finalizeLevelRendering`，即 `compositeAfterIrisFinal` 运行处）
  跑在本帧手部阶段**之前**——合成用的镜孔/世界深度拷贝永远是上一帧的。连续开镜中两者只差
  一帧、无感；开镜第 1 帧（或中断后恢复）上一帧没有掩码周期，手头是上一段开镜遗留的拷贝，
  遗留镜孔在哪，镜内画面就按那个位置贴出去，一帧后掩码收敛自愈。
- **帧闸重构**：`onClientFrameStart` 由「清三旗」改为「帧计数 +1」；APERTURE_COPY 成功时盖
  帧戳 `maskCycleFrame`。原因：清旗会让 Iris 终局钩子处的 `maskValid` 恒为 false，把终局
  叠加/reticle 掩码整体打回 mode 0（回归）。时效改为按需查询：
  - `hasMaskCycleThisFrame()`（周期落在当前帧）：poly_mesh 手部剔除闸（问题 2 修复语义不变，
    腰射帧帧戳停在上一段开镜 → 不裁）；
  - `hadMaskCycleLastFrame()`（周期落在上一帧）：Iris 终局合成闸——终局钩子拿到的拷贝天然
    是上一帧的，「上一帧确有周期」= 连续开镜中，边界帧 fail-closed。
- **合成闸**：`compositeAfterIrisFinal` 加 `hadMaskCycleLastFrame()`；`compositeAfterHand`
  （手部 RETURN，拷贝是本帧的）加 `hasMaskCycleThisFrame()`。宁可不画一帧镜内画面，
  不贴陈旧截图。

## 更正与后续（同日实机反馈：光影下二次渲染失效 + 镜内枪前端残影）

上一节的机制叙述有误，实机结果推翻了「终局钩子在手部之前」的前提：

- **帧序更正**：Iris 26.1 把手部搬进了 `LevelRenderer#renderLevel` 内部（本仓
  `renderAtWorldFlush` javadoc 的字节码结论早已记录）。因此 `finalizeLevelRendering` 与
  `compositeAfterIrisFinal` 跑在**同一 Level 遍的手部阶段之后**——合成拿到的深度拷贝是
  **本帧**的。上一节的 `hadMaskCycleLastFrame` 闸（按「上一帧周期」判）遂永假：
  **光影下二次渲染合成整体失效**（实机症状即「二次渲染不再渲染」）。
- **修正**（合成闸）：`compositeAfterIrisFinal` 改回 `hasMaskCycleThisFrame()`。一帧
  「截图」贴屏的 fail-closed 性质保留：周期被身份守卫否决的帧（拷贝纹理仍是上一段开镜的
  遗留、handle 依旧 available）合成直接跳过——不贴陈旧截图。`hadMaskCycleLastFrame`
  已删除。
- **镜内枪前端残影**（实机 2026-09-01，二次渲染 + 高模枪）：同一条帧序结论的另一面——
  光影下镜内那遍也是完整的 `LevelRenderer#renderLevel`，**包含手部阶段**，mesh 枪因此被
  画进镜内画面；孔外剔除只裁「孔内且比目镜远」的段，比目镜更近的枪口前端留在画面里，
  合成后即残影。修法：`renderAtHandFlush` 在 `isInsideScopeLevelRender()` 时清表早退——
  手部属于第一人称 viewmodel，镜内画面必须是纯世界；主画面的手部阶段会重新提交。
  世界表不受影响（镜内那遍照常画、不清表的既有裁定不变）。
- **遗留观察项**：无光影下手部阶段在 `GameRenderer#renderLevel` 尾部（Level 遍之外），
  镜内那遍天然无手部，此修为 no-op；vanilla 手部在光影下的镜内残影（枪口前端同理）
  未观察到报告、也未处理，若实机出现需在 HandRenderer 层做同款「坐过窄遍」。

## 实机复验清单（全部未验证）

1. 光影 + 高模枪：开镜后镜内枪身被目镜裁剪（与无光影一致）；光照/阴影无回归。
2. 无光影 ± PIP：腰射态枪身完好；开镜滑入途中镜内即有画面；无静态透视面。
3. 光影 + 二次渲染：开/退镜无贴片闪现；滑入中后段镜内有画面。
4. 回归面：reticle/终局叠加、PIP 合成、ESC 压制（d3f0fdc 三道闸）均应无感变化。

## 已知留痕

- CI run 33452875706 为 Modrinth 526 下载抖动（非编译错误）；空提交不触发 push 事件、
  workflow_dispatch 403，故以本 docs 提交重触发编译验证。

## 追记（同日第四轮）：过渡期贴片彻底移除

用户澄清：「随机截图贴屏」的本意是**瞬间**版本——滑入/滑出途中被贴进「镜孔」的画面一闪
即逝，而要求是它根本不许存在。`7eca413` 的 0.35 显示阈只是压短闪现窗口，作废。

- **修法**：二次渲染合成（`compositeAfterHand` 与 `compositeAfterIrisFinal` 同步）改用与
  重投影路径同一条全 ADS 门（`IRIS_FULL_AIM_THRESHOLD = 0.995`）：镜位未就位绝不合成。
  过渡期镜内由原版镜片几何自然显示（与非 PIP 一致）；「开镜即接管」（窄遍/捕获/预热在
  开镜瞬间启动）不变，镜位就位那一刻贴片即席出现。
- **代价（须实机确认可接受）**：镜内放大画面在滑入末段才出现（就位即现），而非全程骑行。
  此前 d3f0fdc 验收清单里「滑入途中镜内即有画面」按本裁定由「就位即现」取代。
- 无光影 + 重投影（二次渲染关闭）路径**不动**：其捕获在手部之前（无 viewmodel 污染），
  且用户未报告该路径有此伪影。
