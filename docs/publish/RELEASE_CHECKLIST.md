# 发布检查单（Release Checklist）

> `AGENTS.md` §4 与 `.github/workflows/consistency.yml` 引用的正式清单。
> 首刊 2026-08-31（此前该文件名被引用但从未写出——本文补上这笔账）。
> 逐条打勾后再点 "Publish release"，顺序即建议执行顺序。

## 0. 版本号（最容易错，先做）

- [ ] `gradle.properties` 的 `mod_version` 已是目标版本
      （26.2 的 1.2.0 基线为 `1.2.0+fabric.26.2.R1`）。升级 SemVer 核心会改变枪包版本检查；目标 MC 和本线修订号写在 `+` 后，不写成预发布号。
- [ ] `fabric.mod.json` 展示名标注正确的 MC 与修订；构建后 `version` 精确等于 `mod_version`。
- [ ] README 顶部版本句、版本提示、环境表、源码导航和 SemVer 说明一致；不要链接尚不存在的 Release tag。
- [ ] 机器校验通过：
      ```bash
      bash scripts/check_release_consistency.sh --strict --links
      ```
      （`--links` 附带校验 README 导航表分支链接；发布前完整门禁可用
      `--all --links --strict`。）

## 1. 代码状态

- [ ] 目标 commit 的 `compile-check` 与 `build` 作业通过（在 Actions 核对 SHA）。
- [ ] 若有 build.yml（全量构建流程）：Actions artifact 里的 jar 存在且大小正常。
- [ ] CHANGELOG（`docs/CHANGELOG_26_2_*.md`）已有本版本段落，且区分
      「实机 PASS」与「源码级未实测」——措辞纪律见 `AGENTS.md` §2。
- [ ] 已知边界（README §7）没有因本版本改动而失真。

## 2. 构建与冒烟

- [ ] 用发布 jar（不是 IDE 运行）在干净实例冒烟：主菜单 → 进世界 →
      默认枪包加载 → 开枪/开镜各一次。
- [ ] 有光影环境的话：开一个主流 pack 开镜看一眼（掩码桥日志行
      `[TACZ Scope] Iris scope-mask bridge active` 出现即基本正常）。
- [ ] 服务端保险（可选，2 分钟）：`java -jar server.jar nogui` 到
      `Done!`，无 mixin/ClassNotFound 报错。

## 3. GitHub Release

- [ ] tag 与文件名含完整版本（`TACZ-Refabricated-26.2-1.2.0+fabric.26.2.R1.jar`）。
- [ ] **Release 正文首行必须是环境行**（MC + 加载器 + Java + Fabric API +
      Forge Config API Port 的版本要求）——`AGENTS.md` §4 的硬规定。
- [ ] 正文的修复/特性声明全部有实测或 commit 依据，无「顺嘴写上」的项。

## 4. 三站同步（如本次要发）

- [ ] `docs/publish/CurseForge.md` / `Modrinth.md` / `MCMOD.md` 的版本号、
      changelog 块已刷到本版本；`[[ ]]` 占位符全部替换（发布前搜 `[[`）。
- [ ] 三站红线复核（项目名不带版本号、NC 许可、非官方声明）见
      `docs/publish/README.md`。

## 5. 发完之后

- [ ] README 的「未随发布打包的主线增量」清单清空或改写（增量已随本版本发出）。
- [ ] `docs/lineage/HANDOFF_LEDGER.md` 如有因本版本状态变化的行（如某 handoff
      随发布 DONE），顺手更新。
- [ ] 姊妹分支（26.1.2 / 1.21.11）若需要同版本节奏，往对应 SYNC_GUIDE 里记一笔。
