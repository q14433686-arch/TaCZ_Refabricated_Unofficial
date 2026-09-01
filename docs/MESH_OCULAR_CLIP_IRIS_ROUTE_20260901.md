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

## 实机复验清单（全部未验证）

1. 光影 + 高模枪：开镜后镜内枪身被目镜裁剪（与无光影一致）；光照/阴影无回归。
2. 无光影 ± PIP：腰射态枪身完好；开镜滑入途中镜内即有画面；无静态透视面。
3. 光影 + 二次渲染：开/退镜无贴片闪现；滑入中后段镜内有画面。
4. 回归面：reticle/终局叠加、PIP 合成、ESC 压制（d3f0fdc 三道闸）均应无感变化。

## 已知留痕

- CI run 33452875706 为 Modrinth 526 下载抖动（非编译错误）；空提交不触发 push 事件、
  workflow_dispatch 403，故以本 docs 提交重触发编译验证。
