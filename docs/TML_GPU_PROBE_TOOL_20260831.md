# 渲染时机字节码探针（`scripts/mesh_render_probe.gradle`）

> 2026-08-31（R3）。这份工具从 TML GPU 第 2/3 步的**临时**诊断任务固化而来。
> 之前它是 `build.gradle` 里两段 `TEMP …` + `compileJava.finalizedBy`，每轮都要记得在发布前删；
> 现在是默认**不接入**的独立脚本，任何分支/任何 MC 版本都能改改就用。

## 1. 为什么本仓需要这种工具

本仓库的 Agent 沙箱**没有 JDK、没有 loom 缓存、也没有 Minecraft/Iris 的 jar**。
于是「1.21.11 的 `RenderType#draw` 到底在哪一刻读 model-view 栈」「Iris 1.10.7 的
`IrisProgram` 有没有 `ENTITY` 这个常量」这类问题**无法在本地回答**，而这些问题恰恰决定
渲染类改动的生死（AGENTS.md §3：1.21.11 是混淆版本，编译通过 ≠ 运行期安全）。

解法是把 javap 塞进 CI：**任务输出落进 `build-reports/compile-java.log`，由 compile-check
流程用 GitHub Contents API 回推到分支**，沙箱再用 API 读回来。闭环如下：

```bash
# 1) 改 scripts/mesh_render_probe.gradle 里的类名/成员名，提交并推送
git add -A && git commit -q -m "probe(temp): <question>" && git push -q origin <本分支>
# 2) 等 CI 跑完（该任务需要 -PmeshProbeEveryBuild 才会挂到 compileJava 上，见 §3）
# 3) 读回来
gh api -H "Accept: application/vnd.github.raw" \
  "repos/q14433686-arch/TaCZ_Refabricated_Unofficial/contents/build-reports/compile-java.log?ref=<本分支>" \
  > /tmp/ci/log
grep -n "probe\|=>" /tmp/ci/log
```

> 用 `sed -n '/TEMP-DUMP/,/END-TEMP-DUMP/p'` 之类的分界符比 grep 关键词更快定位；
> 日志被 CI 截成「头 200 行 + 尾 800 行」，所以**探针输出要放在末尾**（`finalizedBy` 天然是末尾）。

## 2. 两个任务各自回答什么

| 任务 | 回答的问题 | 历史结论（已固化在别处） |
|---|---|---|
| `dumpHandFlushApi` | Iris 侧：`HandRenderer`/`IrisRenderingPipeline` 有哪些成员、`LEVEL_HAND_FLUSH` 事件签名、`IrisProgram` **全量枚举** | 「`IrisProgram` 没有 `ENTITY` 也没有 `MAIN`，只有 `ENTITIES`」⇒ `IrisCompat#assignMeshPipelineToEntity` 钉死 `ENTITIES`；全量枚举见 `TML_GPU_STEP2_HANDFLUSH_20260831.md` §4.2 |
| `dumpWorldFlushProbe` | vanilla 侧：谁调 `renderAllFeatures`、那一刻 `RenderSystem` 的 model-view 栈归谁管、有没有东西在附近覆盖渲染目标/`outputColorTextureOverride` | 「1.21.11 的 `RenderType#draw` 自己在**绘制那一刻**读 `getModelViewMatrix()`」⇒ 变换必须在世界 flush 时刻读，不在 submit 时刻抓；`GameRenderer#renderItemInHand` 在 `renderHandsWithItems` 前后 push/pop MV ⇒ 世界钩子必须避手部 pass。见同文档 §4.1 |

## 3. 用法

```bash
./gradlew -PmeshProbe dumpHandFlushApi            # 单独跑一个，只看 stdout
./gradlew -PmeshProbe -PmeshProbeEveryBuild build  # 同时挂到 compileJava 上（走 CI 日志回推通道）
```

- `-PmeshProbe`：**注册**这两个任务（`build.gradle` 末尾的 `apply from:` 被这个属性罩住）。
  不带它，两个任务连解析都不会发生 → 正常构建零成本。
- `-PmeshProbeEveryBuild`：额外 `compileJava.finalizedBy` 它们，也就是「让输出进 CI 回推的日志」。
  CI 里不带这个属性 ⇒ 即使 `meshProbe` 也不会跑；两者都要在命令行上显式给。

**给 CI 跑的最省事办法**：临时在 `build.gradle` 的 `if (project.hasProperty('meshProbe'))` 里
加一行 `apply from` 无条件执行，或把 `./gradlew compileJava` 改成
`./gradlew -PmeshProbe -PmeshProbeEveryBuild compileJava`（在 workflow 里），跑完记得撤。
不要长期保留 —— 那正是本轮把它拆出来的原因。

## 4. 搬到别的 MC 版本时只改三处

1. `javap` 的目标类名（26.x 用 Mojang 正式名、1.21.11 混淆 ⇒ 类名一致但**私有合成成员**不同：
   1.21.11 的 `LevelRenderer` 私有方法叫 `method_62214` 这种 intermediary 名，26.x 是 `executeSolid`
   一类真名）；
2. 成员名列表（脚本里每个 `probe('类', ['方法1','方法2'], 上下文行数)`）；
3. `dumpMembers` 里那几个 Iris 类/事件的包名（Iris 版本不同会挪包，例如
   `net.irisshaders.iris.pathways.HandRenderer`）。

**别照抄结论**：跨纪元可搬的是「问题清单 + 取证据的手法」，不是答案。
26.2 的 `PreparedFrame#executeSolid` / `RenderType#prepare()` / `drawFromBuffer` 在 1.21.11
上根本不存在，反过来 1.21.11 的「`renderAllFeatures` 只有三个调用点」也未必适用于 26.1.2。

## 5. 硬规矩

- 探针体必须整段包在 `try { … } catch (Throwable t) { println … }` 里：
  挂了 `finalizedBy` 的诊断任务一旦抛异常，就会把 `compileJava` 判成失败 ——
  一个本来只是「少了一行输出」的问题会伪装成「代码编不过」。
- 探针只 `println`，不写文件、不改源码树。
- 用完就在 CHANGELOG 里记一句「探针输出 → 结论落在哪份文档」，否则下一个 agent 会重跑一遍取证。
