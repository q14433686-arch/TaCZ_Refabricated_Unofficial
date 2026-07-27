# 瞄具三阶段实施方案 · 26.2 具体做法

**日期**：2026-07-26（第 26 轮）
**前置**：代码已 revert 到 r21；机制精读见 `SCOPE_UPSTREAM_MECHANISM_2026-07-26.md`
**用户指示**：「这三步都做，但是要考虑的就是怎么在该版本实现」

本文只回答一件事：**在 26.2 上，每一步具体怎么落地**。全部 API 已用字节码验证。

---

## 0. 三阶段回顾与 26.2 对应物

| 阶段 | 上游做法 | 26.2 做法 | API 是否已验证 |
|---|---|---|---|
| 1. 摘除节点 | `part.visible = false` 副作用 | 同样改 `visible`，但要在 submit 前后成对处理 | ✅ 纯逻辑 |
| 2. 圆形裁剪 | `stencilFunc` | **自定义 RenderPipeline + 片元 `discard`** | ✅ 见 §2 |
| 3. 准星最上层 | `disableDepthTest()` | `DepthStencilState(compareOp, writeDepth=false)` | ✅ 见 §3 |

---

## 1. ⚠️ 动手前必须先修一个既有的**定时炸弹**

### 1.1 发现

`com/tacz/guns/client/renderer/feature/LaserBeamPipeline.java` 里有：

```java
public static final RenderPipeline LASER_BEAM_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withVertexShader(Identifier.fromNamespaceAndPath("tacz", "core/laser_beam"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("tacz", "core/laser_beam"))
                ...
```

但实测：

```
find src/main/resources -path "*shaders*"   →  【无 shaders 目录】
grep -rn "LaserBeamPipeline"                →  【零引用】
```

**即：注册了一个指向不存在着色器的管线，且没人用它。**

### 1.2 为什么这是炸弹

`ShaderManager#apply`（字节码确认）：

```java
List<Identifier> failed = ...;                       // 收集编译失败的管线
for (RenderPipeline p : RenderPipelines.getStaticPipelines()) { ... }
if (!failed.isEmpty()) {
    device.clearPipelineCache();
    device.loadCriticalShaders();
    throw new RuntimeException(failed.stream().map(...).collect(joining("\n")) + ...);
                                                     // ★ 直接抛异常
}
```

**每次资源重载都会遍历所有已注册管线并尝试编译，失败即抛 `RuntimeException`。**

它现在没炸，只可能因为该类**从未被加载**（Java 静态字段惰性初始化，无人引用 → `<clinit>` 不执行）。
但只要有任何代码碰它一次（哪怕 `LaserBeamPipeline.class` 被反射/调试触及），就会注册进去，
**下次资源重载必崩**。

### 1.3 处理

**阶段 0（先做）**：要么补上 `laser_beam` 着色器，要么把这个未使用的管线删掉。
我倾向**删掉**——它没有任何引用，留着只是风险。

**同时这给了我们一条铁律**：
> 本次新增的任何 `RenderPipelines.register(...)`，**必须同时提交配套的 `.vsh`/`.fsh`**，
> 否则会让所有玩家在资源重载时崩溃。

---

## 2. 阶段 2 的具体做法：圆形裁剪着色器

### 2.1 shader 放哪

26.2 从 `assets/<namespace>/shaders/core/` 加载（jar 内确认为
`assets/minecraft/shaders/core/entity.fsh` 等）。我们放：

```
src/main/resources/assets/tacz/shaders/core/scope_mask.vsh
src/main/resources/assets/tacz/shaders/core/scope_mask.fsh
```

引用时写 `Identifier("tacz", "core/scope_mask")`（与 vanilla 的 `"core/entity"` 同构）。

### 2.2 GLSL 怎么写——直接抄 vanilla `entity.fsh` 的骨架

vanilla `assets/minecraft/shaders/core/entity.fsh` 实测内容（节选）：

```glsl
#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;                      // ★ discard 是 vanilla 自己就在用的手法
    }
#endif
    ...
}
```

**关键收获**：
- `discard` 就是 vanilla 的原生手法，不是我们发明的 hack；
- `#ifdef XXX` 由 `withShaderDefine("XXX")` 打开（见 §2.3），可编译期特化；
- `#moj_import` 可复用 vanilla 的 fog/transform 逻辑，不必重写。

我们的 `scope_mask.fsh` 只需在 vanilla 骨架上加一段：

```glsl
// 屏幕空间到目镜中心的距离裁剪
// ScopeCenter/ScopeRadius 通过 uniform 传入（见 §2.4 的两种传参方式）
vec2 d = gl_FragCoord.xy - ScopeCenter;
float r = length(d) / ScopeRadius;

#ifdef SCOPE_INSIDE
    if (r > 1.0) discard;          // 只画圆内（准星、镜内内容）
#else
    if (r <= 1.0) discard;         // 只画圆外（镜身）—— 等价 stencilFunc(EQUAL,0)
#endif
```

**一份 shader + 两个 define，就复刻了上游 stencil 的两种模板条件。**

### 2.3 pipeline 怎么建——照抄 vanilla `EYES` 的写法

字节码还原出的 vanilla `RenderPipelines.EYES` 构建过程：

```java
RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
    .withLocation("pipeline/eyes")
    .withVertexShader("core/entity")
    .withFragmentShader("core/entity")
    .withShaderDefine("EMISSIVE")            // ← define 就是这么加的
    .withShaderDefine("NO_OVERLAY")
    .withShaderDefine("NO_CARDINAL_LIGHTING")
    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
    .withVertexBinding(0, DefaultVertexFormat.ENTITY)
    .withPrimitiveTopology(PrimitiveTopology.QUADS)
    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
    .build()
```

我们照搬，只换 shader 路径与 define：

```java
// 镜身：只画圆外
SCOPE_BODY_OUTSIDE = RenderPipelines.register(
    RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("tacz", "pipeline/scope_outside"))
        .withVertexShader(Identifier.fromNamespaceAndPath("tacz", "core/scope_mask"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("tacz", "core/scope_mask"))
        // 不加 SCOPE_INSIDE → 走 #else 分支 → 圆内 discard
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .build());

// 准星：只画圆内 + 不写深度（阶段 3 合并在这里）
SCOPE_RETICLE_INSIDE = ... .withShaderDefine("SCOPE_INSIDE")
                           .withShaderDefine("EMISSIVE")
                           .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS, false))
                           ...
```

`RenderPipelines.register(...)` 是 **public static**（字节码确认），mod 可以调。

### 2.4 圆心/半径怎么传给 shader（两个方案）

这是本阶段唯一还没定的技术点。

**方案 A：`withShaderDefine(String, float)` 编译期常量**
- 字节码确认存在该重载
- 但半径随 `aimingProgress` 变化 → 需要为每个进度档位建一个 pipeline，**不可行**

**方案 B（已确认可行，采用）：走 vanilla 的 `DynamicTransforms` UBO**

反编译 `assets/minecraft/shaders/include/dynamictransforms.glsl` 得到完整定义：

```glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
```

写入方是 `net.minecraft.client.renderer.DynamicUniforms`（字节码确认）：

```java
GpuBufferSlice writeTransform(Matrix4f modelView, Vector4f colorModulator,
                              Vector3f modelOffset, Matrix4f textureMat);
```

**结论**：`TextureMat` 是一个**逐次绘制可写的 mat4**，vanilla 默认只填
`IDENTITY_TEXTURE_TRANSFORM`。我们可以把「圆心 xy + 半径 + 羽化宽度」
塞进它闲置的分量里传给片元着色器，**不需要新增任何 UBO 或 mixin**。

备选：`RenderSetup.Builder#withTexture(String, Identifier)` 传一张参数贴图
（已确认存在），但 UBO 方案更轻。

---

## 2.6 【阶段2 传参链路已完整打通】TextureTransform 是官方预留的扩展点

第 27 轮定下「用 `DynamicTransforms.TextureMat` 传圆心/半径」后，
本轮把**从 Java 写值到 GLSL 读值**的整条链路验穿了：

### 2.6.1 写入侧：`RenderSetup.textureTransform`

`RenderType#writeDynamicTransforms`（字节码）：

```java
RenderSystem.getDynamicUniforms().writeTransform(
        modelViewMatrix,
        this.state.textureTransform.createMatrix());   // ← TextureMat 来源
```

`TextureTransform#createMatrix()` 的实现就是 `supplier.get()`：

```java
public Matrix4f createMatrix() { return this.supplier.get(); }
```

而它有 **public 构造函数**：

```java
public TextureTransform(String name, Supplier<Matrix4f> supplier)
```

**这是 Mojang 官方预留的扩展点**（vanilla 自己用它做附魔光效滚动
`GLINT_TEXTURING`，每帧按时间算矩阵）。我们只需：

```java
new TextureTransform("tacz:scope_mask", () -> currentScopeMaskMatrix())
```

再经 `RenderSetup.Builder#setTextureTransform(...)`（已确认存在）挂到自己的 RenderType 上。
**每次绘制都会重新调 supplier**，所以半径随 `aimingProgress` 变化天然支持。

> 这条路完全不需要 mixin，也不需要新增 UBO —— 走的是引擎自己的机制，
> 与 vanilla 的附魔光效同构。

### 2.6.2 读取侧：`TextureMat` 在顶点着色器里的现状

`entity.vsh`（实测 jar 内资源）：

```glsl
texCoord0 = UV0;
#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
```

即 vanilla **只在 `APPLY_TEXTURE_MATRIX` 打开时**才用它变换 UV。
我们的 `scope_mask` 是**自己的着色器**，不定义该宏，
因此可以把 `TextureMat` 的分量**自由挪作参数用**（圆心 xy / 半径 / 羽化），
不会与任何 vanilla 语义冲突。

### 2.6.3 完整链路

```
Java: ScopeMaskState.set(centerX, centerY, radius, feather)
  ↓
TextureTransform supplier → Matrix4f(把四个参数塞进闲置分量)
  ↓
RenderType#writeDynamicTransforms → DynamicUniforms#writeTransform
  ↓
UBO DynamicTransforms.TextureMat
  ↓
GLSL: float r = length(gl_FragCoord.xy - center) / radius;
      #ifdef SCOPE_INSIDE  if (r > 1.0) discard;
      #else                if (r <= 1.0) discard;
      #endif
```

**每一环都已用字节码或 jar 内资源确认，无一处推测。**

---

## 3. 阶段 3：准星恒在最上

上游用 `RenderSystem.disableDepthTest()`。26.2 的等价物在 pipeline 上：

```java
new DepthStencilState(CompareOp.ALWAYS, false)
//                    ↑ 深度测试恒通过    ↑ 不写深度
```

vanilla `EYES` 用的是 `(GREATER_THAN_OR_EQUAL, false)`，即测试但不写。
准星要**恒在最上**，用 `ALWAYS` 更贴近 `disableDepthTest`。

配合 `.withShaderDefine("EMISSIVE")` 跳过 lightmap，
再加节点名 `_illuminated` → `LightTexture.pack(15,15)`（本项目已继承），即得发光红点。

**准星几何位置保持原样，不做任何补偿**——视差感由「准星离目镜有距离 + 圆形裁剪」自然产生
（见机制文档 §2.4）。

---

## 4. 阶段 1：摘除节点（零风险，可先做）

在 `BedrockAttachmentModel#submit` 里，把
`ocular` / `ocular_ring` / `scope_body` / `division` 四类节点在
`super.submit(...)` 期间置 `visible=false`，之后用我们自己的 pipeline 按顺序提交。

与 r22~r24 的区别：
- **不做**任何几何判定（不分内外壁、不按半径筛 cube）；
- 只是「摘出来、稍后自己画」，与上游语义完全一致；
- 圆内/圆外的区分**交给 §2 的 shader**，而不是 Java 层。

`finally` 必须还原（跨帧共享，r4/r18 的教训）。

---

## 5. 实施顺序与验收点

| 步骤 | 内容 | 可独立验收的现象 |
|---|---|---|
| ~~0~~ ✅ | 删除 `LaserBeamPipeline` 定时炸弹 | **已完成**（第 27 轮） |
| ~~1~~ ✅ | 反编译 `dynamictransforms.glsl`，定下 §2.4 传参方案 | **已完成**：走 `DynamicTransforms.TextureMat` |
| **2** | 阶段 1 摘除 + 用**普通 RenderType** 原样重画 | 画面应与现在**完全一致**（证明摘除/重画链路正确） |
| **3** | 加 `scope_mask` shader + 两条 pipeline | 镜身圆内被裁掉 → 镜内能看到世界 |
| **4** | 准星走 `SCOPE_INSIDE` + `ALWAYS/false` | 发光准星浮在镜内，超出目镜自动裁掉 |

**第 2 步是关键防线**：先做「摘出来再原样画回去」，画面不变才说明链路对。
这样即使后面 shader 出问题，也能立刻定位是 shader 而非摘除逻辑。

---

## 6. 待你确认

1. **`LaserBeamPipeline` 删掉还是补 shader？**（我建议删，它零引用）
2. 同意按 §5 的顺序走吗？特别是**第 2 步先做"原样重画"验证**这个防线。
3. §2.4 的 uniform 传参我先去反编译 `dynamictransforms.glsl` 确认，
   定下来再动代码——还是你已经知道该用哪个槽位？

---

## 附：本轮字节码验证清单

| API | 结果 |
|---|---|
| `RenderPipeline.Builder#withFragmentShader(Identifier)` | ✅ 存在 |
| `RenderPipeline.Builder#withShaderDefine(String)` / `(String,int)` / `(String,float)` | ✅ 三个重载都在 |
| `RenderPipeline.Builder#withDepthStencilState(DepthStencilState)` | ✅ 存在 |
| `RenderPipeline.Builder#withColorTargetState(ColorTargetState)` | ✅ 存在 |
| `RenderPipelines#register(RenderPipeline)` | ✅ **public static**，mod 可调 |
| `RenderPipelines#getStaticPipelines()` | ✅ 被 `ShaderManager#apply` 遍历 |
| `ShaderManager#apply` 编译失败 → `RuntimeException` | ✅ 字节码确认（§1.2） |
| `DepthStencilState(CompareOp, boolean)` | ✅ 存在 |
| `RenderSetup#builder(RenderPipeline)` + `withTexture/useLightmap/...` | ✅ 存在 |
| vanilla `entity.fsh` 使用 `discard` + `#ifdef` | ✅ 实测 jar 内资源 |
| 26.2 同时有 OpenGL 与 Vulkan 后端 | ✅ `blaze3d/opengl/` + `blaze3d/vulkan/` |
