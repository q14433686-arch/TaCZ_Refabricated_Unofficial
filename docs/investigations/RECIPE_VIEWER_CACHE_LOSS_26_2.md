# 26.2：枪包同步后 JEI 丢失模组原版工作台配方

调查日期：2026-10-15。反馈日志：<https://mclo.gs/lGmrtNC>（2191 行）。

后续补充：远端 `build-reports/compile-java.log` 已记录修复提交 `4397753` 的 `compileJava` 成功，
但仍没有游戏内验证。另见[跨分支双向审计](RECIPE_VIEWER_CROSS_BRANCH_AUDIT_20261015.md)：
其中发现独立的 REI 全量重载丢失服务端显示风险；本篇 JEI 修复没有修复或证明 REI 路径安全。

## 结论及边界

确认发现本仓库 `RecipeViewerReloadBridge` 的兼容性缺陷：为了重新注册枪包内容，
它手动触发 JEI 的 `AFTER_RECIPES_UPDATED`。较新的 26.2 JEI 已赋予此事件
“确认本轮是否收到服务端配方，否则清理旧连接配方”的语义，因此它不再是通用刷新入口。
真实配方更新已消费同步标志后，再触发一次就会把同一连接的服务端配方清掉。

这能解释日志中刷新后只剩原版配方、反馈中的十种模组配方整批不可查询。
**不能仅凭此日志证明服务端手动摆材料合成也失败，更不能声称已经实机验证全部修复。**
本次修复的是确切存在的 JEI 配方缓存丢失路径；手动合成与配方书行为仍需下面的运行矩阵验证。

## 环境对照

| 项目 | 反馈日志 | 当前源码 |
|---|---|---|
| Minecraft | 26.2 | 26.2 |
| Java | 25 | 目标 Java 25 |
| TaCZ | `1.1.8+fabric.26.2.R3-hotfix2` | `1.1.8+fabric.26.2.R3-hotfix3` |
| Fabric Loader | 0.19.5 | 0.19.3 |
| Fabric API | 0.161.0+26.2 | 0.155.2+26.2 |
| Forge Config API Port | 26.2.1 | 26.2.1 |
| JEI | 日志版本被打码，无法确认确切构建 | 编译依赖 30.13.0.86 |

不能把 Loader/API 的版本差异直接归因为故障。较新的 JEI 源码及反馈中的实际事件顺序
提供了更具体的解释。未要求玩家降级，也未修改这些依赖版本。

## 日志中的因果顺序

1. **07:56:32**：服务端资源加载报告 `Loaded 2237 recipes`。
2. **07:57:32–36**：JEI 首次启动并注册配方。
3. **07:57:38**：本模组输出
   `[TACZ Recipe Viewer] Refreshing after gun-pack sync (4 table(s), 182 recipe(s)).`。
4. 随后 JEI 停止、输出 **`Loaded 1585 vanilla recipes from client resources.`** 并重新启动。
5. **07:57:39**：JEI 提示
   `This fabric server does not provide recipes to JEI. JEI is showing default recipes from your client...`。

第四、第五步是关键：刷新后选中的是纯原版 fallback，而非服务端配方。
日志中未见所列十个配方的 JSON/codec 解析失败信息。
Realms/账号 401、Physics Mod 光影编译失败、资源包模型缺失及 pack metadata 警告
没有给出这条配方丢失的直接因果链，本次不据此修改无关兼容代码。

## 代码与上游核对

旧调用链：

```text
ServerMessageSyncGunPack.doSync
  -> CommonNetworkCache.fromNetwork / ClientIndexManager.reload
  -> RecipeViewerReloadBridge.requestReload
  -> END_CLIENT_TICK
  -> JeiLifecycleEvents.AFTER_RECIPES_UPDATED.invoker().run()
```

JEI 26.2 的代理服切换修复
[`db02208b`](https://github.com/mezz/JustEnoughItems/commit/db02208b1a822a290fc8120a0644b3024b6cea81)
在 `ClientLifecycleHandler` 中加入 `receivedRecipeSync`：

- 真正的同步回调设置配方和标志；
- `AFTER_RECIPES_UPDATED` 若未收到同步，调用 `Internal.clearClientRecipes()`；
- 消费完事件后标志回到 false。

本仓库再次伪造事件时没有新的配方同步，于是触发清理。这不是普通的 JEI 停止/启动问题：
`Internal.onRuntimeStopped()` 在连接未变时会保留配方；真正有破坏性的是事件前置清理。

分别核对了 JEI
[`v30.13.0` 的生命周期实现](https://github.com/mezz/JustEnoughItems/blob/v30.13.0/Fabric/src/main/java/mezz/jei/fabric/startup/ClientLifecycleHandler.java)
及上述修复提交。两者都有：

```java
ResourceManagerReloadListener getReloadListener()
```

这个监听器在 JEI 正在运行且处于客户端线程时执行 stop/start，不触发配方同步标志或清理。
因此选择此共同入口，而不是依赖新版本才添加的 `Internal.restartJei()`，也不读写私有缓存、
不从单人服务端偷取 RecipeManager、更不把本地配方伪装成服务端权威数据。

## 本次改动

- 新增客户端可选 Mixin，在 JEI `getReloadListener()` 返回时取得该监听器。
- `RecipeViewerReloadBridge` 在原有合并请求、延迟到客户端 tick 的位置调用监听器。
- Mixin 位于 `compat.jei`，沿用现有 `MixinPlugin` 的 `isModLoaded("jei")` 门禁；
  `@Pseudo`、字符串 target、`require = 0` 避免强制依赖未安装/改动实现的 JEI。
- 监听器属于客户端生命周期，断线 `clear()` 只清理待刷新状态，不丢弃监听器。
  不保留任何连接的配方副本，因此不会把 A 服配方带到 B 服。
- 保留 REI 独立刷新和一次资源重载的兼容性兜底；兜底不能保证未知 JEI 版本的所有
  注册都已刷新，但不会回退到伪造配方更新事件。

十种配方的 JSON 已存在于正确的单数 `data/<ns>/recipe/` 目录，使用 `result.id`、
字符串材料及 `minecraft:custom_data`；所引用的非原版材料标签也随模组提供。
本次不重写配方、不更改材料、不新增重复配方，也不启用废弃的 `ShapedRecipeMixin`。
这些是 `minecraft:crafting_shaped` 配方，不能拿枪械工作台 `TableRecipeManager` 的
182 条自定义配方数量来判断它们是否被原版加载。

## 已执行验证

- `python3 scripts/verify_recipe_viewer_reload.py`：4 项静态检查通过，覆盖所列十种配方的
  现行 schema/产物自定义数据/材料标签、禁止伪造同步事件、可选 Mixin 注册、跨连接生命周期。
- 本地缓存的 26.2 Minecraft 字节码核对：
  `ResourceManagerReloadListener.onResourceManagerReload(ResourceManager): void` 和
  `Minecraft.getResourceManager(): ResourceManager` 均存在。
- `bash scripts/check_release_consistency.sh --strict`：通过；未更改版本号或 README 环境声明。
- `git diff --check`：通过。
- `bash ./gradlew build --offline`：**未能构建**，环境无系统 Java/JDK。
  工具侧取得的精简 Java 25 runtime 不含 Java 编译器，完整 JDK 的下载也被网络限制阻断。
  未执行 Gradle 编译、Mixin 实际应用或游戏内测试；静态检查不是它们的替代品。

## 待执行的实机矩阵

先在 Java 25 开发机执行 `./gradlew build`，然后：

1. **无 JEI/REI 的最小实例**：逐项手摆十种配方，确认服务端合成实际可用，
   特别检查三档弹药箱的 Level 和三个扩展工作台的 BlockId。手摆与配方书解锁要分开检查；
   “配方书没条目”并不等同于“服务端没有配方”。
2. **固定 JEI 30.13.0.86**：进服、枪包同步后仍有十种原版工作台合成配方，
   枪包分类、弹药查询也完成刷新。
3. **带 `receivedRecipeSync` 逻辑的新 JEI**：先复现旧版本桥接在枪包同步后
   退回 1585 条原版配方的行为；安装补丁后同样操作不得再触发该 fallback 警告，
   十种配方可查询、填充并合成。请记录确切 JEI 构建号（反馈日志里不可见）。
4. **专用服务器**：带服务端数据包的修改配方经过枪包同步、`/reload` 后仍保持服务端版本，
   不能只恢复本地内置配方。
5. **断线重连/切服**：A/B 服采用不同配方，重连后只显示当前服配方；同 JVM 第二次
   进服仍能刷新，不丢失客户端级监听器。
6. **仅 REI / 同时安装两个查看器**：REI 路径不受此次 JEI 修正影响；两个刷新不应短路。

若最小实例中“按 JSON 手摆后无产物”仍成立，继续取服务端日志、配方 id 是否存在、
实际材料及网格截图、受限合成规则状态；这属于本日志尚未证明的另一条故障路径。
