# PR #92 mesh pass 生命周期修复 · 1.21.11 验证记录

日期：2026-09-08。**静态修复、待实测**；与日志 B/C/D 独立，不宣称解决世界全透明。

## 提交与范围

- 来源：26.1.2 PR #92 的 `9412e08ec139191c91f1345679f3ad86feaad112`。
- 本线代码：`c112a23495f4e580eae0572da854745a950210e7`，基于 `95fedbf`。
- 目标 PR：[#93](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/pull/93)，base `1.21.11`。
- 光影每骨骼独立 RenderPass；无光影单批次；保留本线 MV、纹理/UBO/索引预加载及深度孔径路径。
- 生产分批策略与独立 Java 测试取自来源提交，Gradle 按本线 `targetJavaVersion = 21` 编译运行。
  不修改 mixin 目标、资源文件、版本、依赖版本或 GPU 默认值。
- 机制与实机清单：`docs/MESH_GPU_IRIS_PASS_LIFETIME_1211_20260908.md`。

## 通过

| 检查 | 结果 |
|---|---|
| [push compile-check #34187790509](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/34187790509) | **success**，`c112a23`，job 1m26s |
| [push build #34187790532](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/34187790532) | **success**，同提交，job 2m11s，含 remapJar 与产物上传 |
| `meshRenderPassTest` | 已挂入 `check` / `build`，上述 CI 全量构建通过 |
| `python3 docs/check_visible_bugs_39JqB2p.py` | 三项通过，最终 jar 检查跳过；前轮 B/C/D 无静态回归 |
| `python3 docs/check_mesh_config_parity.py` | 18 项齐平 |
| 版本一致性 `--strict` | 6 通过、0 失败、1 条 Arena 分支名系列推导预期警告 |
| `git diff --check` / `git diff --cached --check` | 通过 |

编译日志由 CI 提交 `30a733f` 回推，已同步至 `build-reports/compile-java.log`。
首行指向 `c112a23`，末尾 `BUILD SUCCESSFUL in 1m 13s` / `job status: success`。

## 限制

- 本地 `./gradlew meshRenderPassTest` 因没有 Java 中止，未声称本地运行通过；验证依赖本线 CI。
- `verify_mixin_targets.py` / `verify_shader_imports.py` 仍因无 Loom merged jar 中止；没有新增混淆目标。
- `gh run view 34187790532 --log` 下载完整构建日志遇到 EOF，不能提供其中的模型测试输出摘录。
  全量 build 成功来自 GitHub Actions run/job 状态；编译日志则通过回推文件取得。
- 模型测试只验证真实生产分批策略对模拟 Iris 生命周期的行为，**不是实际 OpenGL/Iris 集成测试**。
- 检视反射、PBR、多材质、scope/PIP、手部/世界 GPU、无光影回归与帧时间均待实机。
  特别是新增的逐骨骼 pass/setup 开销未测；没有禁用 GPU 作为替代方案。
