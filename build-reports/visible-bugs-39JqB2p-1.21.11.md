# 39JqB2p 可见问题 · refab 1.21.11 验证记录

日期：2026-09-08。**静态修复、待实测；不涉及「开光影世界全透明」归因或修复。**

## 范围与来源

- 起点：`6db3af93aebf183385cce520272a5e1dd4f6cada`（refab 1.21.11）。
- 落码：`4ba3a10d61de8388c7f70ec5188bda9c7405dc98`。
- 工作分支：`arena/01a07ee5-tacz-refabricated-unofficial`，没有修改其他五条产品线。
- A 已有 `isSpecial() = true`，E 的 WARN 不存在；本轮实现 B/C/D，不改版本号。
- 指导中的目标 `.patch` 未出现在当前检出、指定源提交或查询时的远端默认分支路径，
  因而按当前源码做等价修改，**不声称本轮执行了该补丁的 `git apply --check`**。
- B 白名单、C 动画取自 `1aca7c74f8fd14f64e6a348e235eb87dfdfa8534`。
  C 与 bundle blob `82de964cee92b6b5e12e612a619c91eb67f451a9` 仅差五行删除；
  实际动画键是 `draw`，`raise` 是错误音效名的后缀。
- D 只删除 vanilla 共享管线强制 HAND 注册；保留 1.21.11 自有 scope/mesh 管线分类。
  未改 IrisShaderCreatorMixin、shader `void main`、GPU 默认值或混淆目标。

## 已通过

| 检查 | 结果 |
|---|---|
| `python3 docs/check_visible_bugs_39JqB2p.py` | 三项静态回归通过；jar 检查跳过 |
| `python3 docs/check_mesh_config_parity.py` | 18 项配置齐平 |
| `check_release_consistency.sh --strict`（脚本取自上述源提交） | 6 通过、0 失败；Arena 分支名不能推导 MC 系列，1 条预期警告 |
| `git diff --check` / `git diff --cached --check` | 通过 |
| CI compile-check | **success**，代码提交 `4ba3a10` |
| CI 全量 build | **success**，同一代码提交；包含资源处理、remapJar 与 jar 上传 |

- [compile-check #34181151326](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/34181151326)
- [build #34181151427](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/34181151427)
- 编译日志已由 CI 提交 `9702360` 回推并同步到 `build-reports/compile-java.log`，首行指向 `4ba3a10`，
  末尾为 `BUILD SUCCESSFUL` / `job status: success`。编译通过不代表零警告或运行期安全。

## 未完成的验证

- 沙箱没有 Java / Loom 的 1.21.11 merged jar。
  `python3 docs/verify_mixin_targets.py` 与 `python3 docs/verify_shader_imports.py` 已尝试，
  均因缺少缓存中止，**没有通过结论**。CI 的 mixin 配置完整性检查不是这两个脚本的替代品。
- `gh run download 34181151427` 从 Actions blob 存储下载产物时发生 EOF。
  因此虽然全量 build 成功，尚未对最终 jar 中的源码覆盖件做字节核验。
  可在能下载产物的环境继续运行：

  ```bash
  gh run download 34181151427 --dir build/visible-bugs-artifacts
  python3 docs/check_visible_bugs_39JqB2p.py --jar <下载的非-sources模组jar>
  ```

- **没有实机验证**。还需按移植指导验证：
  1. 开光影后第一人称抛壳、枪口火光、高模枪体照明，且不再打印 vanilla 管线的 HAND_CUTOUT 匹配 WARN。
  2. 村民、矿车、展示框及各运输船的交互键有效，标签加载无缺失引用。
  3. 工作台配方列表/合成、JEI/REI 分类正常，无 empty ingredients WARN（A 为既有修复）。
  4. 切换格洛克 17 不再出现 `golf17` 缺失音效日志。
