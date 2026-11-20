# 26.2 → Fabric 1.21.11：TaCZ 1.2.0 全量移植验收记录

这是一份**未完成**的移植记录，不是发布说明。比较基线：目标 `aac343af891858589eaea69bbefc413c0f2b1977`，供体 `307f063a8828894917302b2b73cbc104a951dc4e`。以这两个提交的源码、资源及目标版本 API 为准，不以供体的通过日志代替目标验证。

- 目标保持 Minecraft 1.21.11、Java 21、Fabric API 0.141.6、FCAP 21.11.1 与 Loom remap。`1.2.0+fabric.1.21.11.R1` 是工作分支源码版本，不代表已有可安装的 1.2.0 发布包。
- `DEFAULT_PACK_120.tsv` 的 4,176 条文件 SHA-256 均与当前内置包一致，包目录没有未登录的文件。台配方、进度、脚本、材质与模型是否实际解码和可用，仍须在目标游戏中确认。
- 初次导入后的目标编译失败。已恢复目标网格选项与 FCAP 落盘桥，适配了若干存储、菜单、GUI、渲染材质 API。编译报告 `e0e696f`（Actions `44686575249`）中的 `commit:` 对得上该次源码；其中仍有世界渲染提交、热成像、REI/JEI 和其他 API 缺口。下一次 `7da553f` 检查（`44686911662`）在三个误改的矩阵调用处先遇到语法错误，本轮后续提交已更正，尚不等于 Java 类型检查通过。应读取与待测源码 SHA 相符的报告。

## 用 26.2 同型方式验收，但实际运行 1.21.11

本分支的 `game-smoke-12111.yml` 在构建通过后运行 Fabric GameTest。开发环境可在 Java 21 下运行：

```sh
./gradlew -PtaczGameTests runGameTest --console=plain --no-daemon
LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null \
  xvfb-run -a ./gradlew -PtaczGameTests runClientGameTest --console=plain --no-daemon
```

- Dedicated GameTest：加载并检查默认目录、工作台材料结算与拒绝路径、弹药箱库存事务及升级保留组件。看 `build/run/gameTest/logs/latest.log`，不能只看进程退出码。
- Xvfb 客户端 GameTest：建真实单人世界，等新增索引同步，再核对主手物品并截屏。看 `build/run/clientGameTest/logs/latest.log` 和截图。截图是实机记录，不是像素自动判定，也不是独立服务器联机证据。
- 这两个测试入口是 Loom 开发运行，**不替代**用 remapped JAR 在独立 Fabric 客服端上验证。后者需要目标版本的依赖组合，并分别记录新版分片协议、旧协议回退、重载/退服、第三方枪包转换和导出、实际玩法、JEI/REI、普通渲染和 Iris 的 PiP/准星/热成像及粒子。

当前无本轮可安装 JAR、无目标游戏通过凭证，不把第一批移植构建或供体游戏结果算作本轮通过。
