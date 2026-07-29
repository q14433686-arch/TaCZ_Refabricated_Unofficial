# TACZ 从 Minecraft 26.2 降级/移植到 26.1.2 技术分析报告与移植指南

本报告基于 **Minecraft 26.2 与 26.1.2 (Tiny Takeover hotfix) 的源码、官方迁移文档（NeoForge 26.1/26.2 迁移指南）** 以及本项目现有的 26.2 移植经验（详见 `PORTING_NOTES.md` 与 `COMPAT_AND_ROADMAP.md`），为将当前代码库回移植/降级到 **26.1.2** 提供详尽的架构对比和步骤说明。

---

## 一、 核心技术架构对比 (26.2 vs 26.1.2)

将项目从 26.2 移植到 26.1.2 实质上是一个**降级 (Backport/Downgrade)** 过程。由于 26.1（2026年3月）和 26.2（2026年6月）都属于 2026 年新版命名规范与技术周期的一部分，它们在很多基础系统上高度一致（例如：**两者都强制要求 Java 25**，数据包目录也均已完成**单数化**）。

然而，Mojang 在 26.2 中引入了革命性的**多线程 Vulkan 渲染器**，导致渲染和 GUI 相关的底层 API 被彻底重写或删除。这是本次移植需要攻克的最主要技术差异。

### 1. 渲染引擎与图形 API：Vulkan vs 纯 OpenGL

*   **26.2 现状**：引入了实验性的、运行在独立专用渲染线程的 **Vulkan 后端**。为了实现多后端统一，Mojang 删除了大量 OpenGL 特有的 API，最致命的是 **`GlStateManager` 级联状态管理被彻底删除**，且 **模板缓冲 (Stencil Buffer) 概念在抽象层完全消失**。
*   **26.1.2 现状**：仍使用**纯 OpenGL 渲染引擎**（无官方 Vulkan 支持，部分玩家通过第三方 Mod 开发）。因此，**`GlStateManager` 及其模板缓冲 (Stencil) APIs 依然完全可用且完好无损**。
*   **移植决策（瞄具镜内裁剪机制）**：
    *   **方案 A（推荐，恢复 Stencil 裁剪）**：上游 `1.21.1` 的瞄具裁剪完全依赖 Stencil。在 26.2 移植版中，由于 Stencil 被删，我们被迫开发了一套极为复杂的“离屏 RenderTarget 绘制掩码纹理 + 自定义片元着色器采样 discard”方案。降级到 26.1.2 后，由于 `GlStateManager` 依然存在，我们**完全可以恢复成上游原生的 Stencil 模板测试**。这不仅能极大地简化管线、避免离屏渲染带来的 GPU 显存开销与 OOM 风险，还能彻底解决第三方枪包中非实心目镜（如 PU 镜）因缺乏几何投影导致的准星错位问题。
    *   **方案 B（保留当前 26.2 掩码着色器）**：如果不希望变动渲染管线逻辑，可以保留 26.2 的离屏渲染方案，但在 26.1.2 下必须检查 `RenderTarget` 与着色器管线（如 `RenderPipeline`）是否有编译期的方法签名冲突。

### 2. GUI 系统的重组 (Gui Reorganization)

*   **26.2 现状**：GUI 相关的类被进行了彻底重组。原先挂载在 `Minecraft` 主类上的许多界面字段（如当前的 `Screen`、`ChatListener` 等）被移动到了 `Gui` 和 `Hud` 类中：
    *   获取当前屏幕：`Minecraft.getInstance().gui.screen()`
    *   设置快捷栏上方提示文字：`Minecraft.getInstance().gui.hud.setOverlayMessage(...)`
*   **26.1.2 现状**：尚未进行此重组。所有的 GUI 状态依然挂载在 `Minecraft` 类以及旧版的 `Gui` 上：
    *   获取当前屏幕：`Minecraft.getInstance().screen`
    *   设置快捷栏上方提示文字：`Minecraft.getInstance().gui.setOverlayMessage(...)`
*   **移植影响**：必须全局搜索并还原这些调用，否则会导致编译期严重报错（类或方法未定义）。

### 3. 特征渲染系统与 `MultiBufferSource` 的存废

*   **26.2 现状**：特征渲染系统（Feature Rendering）彻底吞并了原本用于传递顶点的 `MultiBufferSource` 接口（该接口已被删除）。取而代之的是 `StagedVertexBuffer` 配合 `PreparedRenderType` 和自定义的 `FeatureRenderer` 进行多线程安全的数据提交。
*   **26.1.2 现状**：特征渲染系统已经处于半完成状态（双阶段提取：`extractRenderState` 和 `submit` 已经引入），但 **`MultiBufferSource` 及其配套的 `OutlineBufferSource`、`crumblingBufferSource` 依然存在并广泛使用**。
*   **移植影响**：
    *   `OrderedSubmitNodeCollector` 在 26.1.2 下的方法签名存在微调。例如：
        *   `submitModelPart` 方法在 26.1.2 中仍需传入 **是否为贴图片 (sheeted)** 以及 **是否发光 (hasFoil)** 的 `boolean` 参数（26.2 将其移除了）。
        *   `submitNameTag` 方法在 26.1.2 中需要额外传入一个表示**距摄像机距离平方的 `double` 属性**。
    *   我们必须对照 26.1.2 的源码，调整所有在 `submit()` 路径中调用的 `OrderedSubmitNodeCollector` 方法签名。

### 4. 字体准备与文本绘制 (Font Preparations)

*   **26.2 现状**：彻底移除了 `Font` 类上的直接绘制方法（如 `drawInBatch` 或者是旧版的 `draw`）。必须通过 `font.prepareText` 将文本转换为 `Font.PreparedText`，然后实现自定义的 `GlyphVisitor` 去迭代像素字符以提交给缓冲区。
*   **26.1.2 现状**：经典的直接绘制方法仍然被完全保留（例如 `font.drawInBatch`）。
*   **移植影响**：降级到 26.1.2 后，所有的 HUD 和枪身 3D 浮动文字绘制逻辑既可以保留 26.2 的 `PreparedText + GlyphVisitor` 写法（如果 26.1.2 已支持），也可以**大幅简化并重构回直接调 `font.drawInBatch(...)` 的高可读性写法**。

### 5. 格式化属性：ChatFormatting 的弱化

*   **26.2 现状**：`ChatFormatting`（格式化代码如粗体、斜体、颜色等）被大量剔除，转而全面强制使用链式的 `Style`（例如 `Style#withColor`、`withBold(true)` 等）。
*   **26.1.2 现状**：`ChatFormatting` 作为主要的文本格式化枚举依然被完整支持。

### 6. 数据目录与组件系统（一致性分析）

*   **相同点**：
    *   **Java 版本**：两者都处于 Java 25 时代（26.1 是首个需要 Java 25 的正式分支），因此不需要降低项目的 JDK 工具链。
    *   **目录结构**：由于 1.21 已经完成了数据包目录的**单数化**（如 `recipe/`, `loot_table/`, `tags/item/` 等），26.1.2 与 26.2 在这方面完全一致，不需要重命名数据包目录。
    *   **NBT 转换为 Data Component**：两者都处于组件化时代，但在具体的组件细节上可能存在部分差异（如 26.2 额外规范了某些实体属性组件，而 26.1.2 则相对少一些）。

---

## 二、 详细移植步骤 (Step-by-Step Porting Guide)

由于项目是在没有 JDK 和物理 GPU 环境的沙盒中分析，我们将移植步骤划分为以下几大模块，便于逐一跟进：

### 第一步：构建配置文件与依赖项降级

修改根目录的 `gradle.properties`，将 26.2 相关的依赖和版本号对齐到 26.1.2：

1.  **Minecraft 版本降级**：
    ```properties
    minecraft_version=26.1.2
    ```
2.  **Fabric API 版本对齐**：
    根据官方发布记录，寻找 26.1.2 的稳定版 Fabric API 并更新：
    ```properties
    fabric_version=0.151.0+26.1.2  # 或者 0.146.1+26.1.2
    ```
3.  **其他前置及可选依赖降级**：
    ```properties
    cloth_config_fabric=26.1.154
    jei_version=29.5.0.26          # 26.1.2 对应的 JEI 稳定版
    zoomify_version=2.16.1+26.1.2  # 或者是暂时禁用 compileOnly
    # 重新命名项目版本号，保持 SemVer Core 并标注 26.1.2
    mod_version=1.1.8+fabric.26.1.2.alpha.1
    ```
4.  修改 `src/main/resources/fabric.mod.json` 中的硬编码版本门禁：
    ```json
    "depends": {
      "fabricloader": ">=0.19.3",
      "fabric-api": "*",
      "minecraft": "26.1.2",
      "java": ">=25",
      "forgeconfigapiport": ">=26.1.0"
    }
    ```

### 第二步：全局修复 GUI 重组的方法和字段访问

使用全局正则或编译器批量替换以下因 GUI 重组导致的无效访问：

1.  **替换当前屏幕获取**：
    *   **源代码**：`Minecraft.getInstance().gui.screen()`
    *   **目标代码**：`Minecraft.getInstance().screen`
2.  **替换快捷栏 HUD 提示调用**：
    *   **源代码**：`Minecraft.getInstance().gui.hud.setOverlayMessage(text, ...)`
    *   **目标代码**：`Minecraft.getInstance().gui.setOverlayMessage(text, ...)`
3.  **替换其他 HUD/Gui 指针**：
    检查 `GuiGraphicsExtractor` 相关的混淆方法，确保 26.1.2 的客户端能够成功定位该绘制类（26.1.2 中已引入该类名）。

### 第三步：修复特征渲染提交 API (`OrderedSubmitNodeCollector`)

在所有涉及实体、物品和方块实体自定义提交的方法中（如 `MeleeItemRenderer`、`ThrowableEntityRenderer`），调整 `OrderedSubmitNodeCollector` 的参数：

1.  **修复 `submitModelPart`**：
    *   **26.2 签名**：`submitModelPart(part, poseStack, ...)`
    *   **26.1.2 签名**：`submitModelPart(part, poseStack, ..., boolean sheeted, boolean hasFoil)`
    *   **修改方案**：补齐缺省的 boolean 参数（通常为 `false` / `false`）。
2.  **修复 `submitNameTag`**：
    *   **26.2 签名**：`submitNameTag(text, poseStack, collector)`
    *   **26.1.2 签名**：`submitNameTag(text, poseStack, collector, double distanceToCameraSq)`
    *   **修改方案**：调用 `TranslucentSubmit.computeDistanceToCameraSq(poseStack)` 计算平方距离并传入。

### 第四步：决定并重构瞄具镜内裁剪机制

这是本次移植的关键决策点：

*   **若选择方案 A（回归 Stencil 模板测试，强烈推荐）**：
    1.  删除 26.2 移植中自建的 `ScopeMaskRenderer` 蒙版生成类与配套的离屏 FBO（`RenderTarget`）。
    2.  还原 `RenderHelper`（或者是 Mixin 注入点）中的 OpenGL Stencil 状态操作：
        ```java
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GlStateManager._glFramebufferTexture2D(..., GL30.GL_DEPTH_STENCIL_ATTACHMENT, ...);
        RenderSystem.stencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
        ```
    3.  移除着色器 `scope_body.fsh` 中不必要的自定义离屏采样，直接改回原版的 OpenGL 标准管线测试。这会大幅提升客户端的帧率和稳定性。
*   **若选择方案 B（保留 Mask Shader）**：
    1.  需要检查 26.1.2 下 `RenderPipeline` 和 `BindGroupLayout` 的构造器是否支持 26.2 中的定义（26.1.2 的 Uniform 拼装可能更加严格，详见官方迁移指南中 `BindGroupLayout` 相关的去重约束）。

### 第五步：修复字体绘制（Font Draw）与文本样式

1.  如果在 26.1.2 中 `font.prepareText` 存在，可保持原样。如果由于签名或泛型擦除原因导致编译失败，请批量将 `PreparedText.visit(...)` 的复杂结构还原为直观的 `font.drawInBatch(...)`：
    ```java
    font.drawInBatch(text, x, y, color, shadow, matrix, buffer, DisplayMode.NORMAL, 0, light);
    ```
2.  检查 `Style` 和 `ChatFormatting` 链式调用：如果使用了 26.2 才有的 Style 语法，改写为 26.1.2 的普通样式定义。

### 第六步：验证、自检与迭代修复

由于处于沙盒开发阶段，强烈建议在移植前后利用项目现有的**静态诊断工具链**辅助发现未对齐的 API 签名。

1.  **运行自检脚本**：
    *   `python3 fieldchk.py`：检查是否存在因 26.1.2 类属性私有化（例如 `Minecraft#cameraEntity` 在 26.2 已私有化，但在 26.1.2 中可能仍为 `public` 或需要 getters 互换）导致的字段访问错误。
    *   `python3 ovr.py`：强制验证所有的 `@Override` 覆写方法在 26.1.2 的父类中确实存在，防止发生 26.2 特有的“空覆写/静默空实现”导致的渲染不显示（如 `BedrockModel#render` 在 26.2 变成了空实现，需改调用 `submit()`，但在 26.1.2 中可能仍需要 `render()` 参与）。
2.  **执行 Gradle 编译**：
    ```bash
    ./gradlew compileJava
    ```
    根据具体的报错行号，重点排查 **方法名拼写微调 (如 `isInstantaneous` 拼写错误)、参数增减以及 Holder 包装**（如属性和药水效果在 26.2 强制要求传入 `Holder<X>`，而在 26.1.2 中可能仍然允许直接传入裸类实例）。

---

## 三、 潜在风险点与排踩预案 (Audit Risk Assessment)

1.  **配方数据双通道问题**：
    由于 26.1.2 已将配方表进行了部分格式化重写，需要核实工作台配方管理器 `TableRecipeManager` 是否在 26.1.2 下能够正确在客户端、JEI 以及 REI 之间正常分发。如果发生了“界面看得到，合成点不动”的症状，按照 `PORTING_NOTES.md` §6.7 的教训，必须确保服务端校验和客户端展示数据源彻底同源。
2.  **空战包踢线 Bug 重现**：
    在降级过程中，一定要格外小心 `ServerMessageGunDraw` 或其他自定义网络消息的编写。**绝不能**在网络线程中直接序列化 `ItemStack.EMPTY` 到 `ItemStack.STREAM_CODEC`。务必全局统一采用 **`ItemStack.OPTIONAL_STREAM_CODEC`** 处理可能为空的物品槽，以防止触发“服主丢弃物品导致全服玩家掉线”的重大灾难（详见 `PORTING_NOTES.md` §6.2）。
3.  **LRTactical 战术装备的 capability/attachment 对齐**：
    当前 26.2 分支的战术装备已经整合了 `DataHolderCapabilityProvider`，以弱引用 WeakHashMap 机制替代了 CCA。降级至 26.1.2 后，需要格外小心死亡重生时网络 id 复用（重生后 `newPlayer.setId(oldPlayer.getId())`）导致冷却状态机失效的 Bug，必须确保重生事件 `RefreshClonePlayerDataEvent` 能在 26.1.2 准确捕获。
