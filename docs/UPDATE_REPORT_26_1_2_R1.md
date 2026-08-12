# TaCZ Refabricated 26.1.2 R1 更新报告

> 发布版本：`1.1.8+fabric.26.1.2.R1`
>
> 目标环境：Minecraft 26.1.2 / Fabric / Java 25
>
> 性质：非官方社区移植，不隶属于 TACZ Dev Team

## 一、版本定位

R1 是 26.1.2 移植线首个使用正式发布后缀的公开构建。它以 TACZ
`1.1.8-hotfix` 为兼容基线，保留 `1.1.8` 作为 SemVer 核心版本，并把
`fabric.26.1.2.R1` 放在 `+` 后作为构建元数据。

因此，枪包依赖中常见的：

```json
{ "tacz": ">=1.1.8" }
```

仍可正常通过版本检查；`R1` 不会把本模组错误地比较成低于 `1.1.8` 的预发布版。

预期发布文件名：

```text
TACZ-Refabricated-26.1.2-1.1.8+fabric.26.1.2.R1.jar
```

## 二、本次重点：内置 LRTactical（LR）兼容层

R1 已将 LesRaisins Tactical Equipements / LRTactical 的代码兼容层直接整合进
TaCZ Refabricated，Fabric 元数据同时提供 `lrtactical` 依赖标识。用户不需要、
也不应再额外安装一个同 ID 的独立 LR jar。

### ZIP 刀包支持

R1 支持**大多数普通、未加密、采用标准 LR 数据结构的 ZIP 刀包**。安装方法与枪包一致：

```text
.minecraft/
└── tacz/
    ├── example_gun_pack.zip
    └── example_knife_pack.zip
```

ZIP 无需解压，但须满足：

1. `gunpack.meta.json` 位于 ZIP 根目录，不能额外套一层文件夹；
2. 包使用标准 LR `index/melee`、`display/melee`、模型、动画和脚本结构；
3. 包内资源未由 TacZ:Arcana 或其他闭源前置加密；
4. 包不依赖尚未移植的 LR 高级模块。

已接入的常用能力包括：

- 近战武器索引和客户端 display；
- 左键/右键攻击、攻击延迟与冷却；
- cone、ray、OBB 等碰撞体和武器属性；
- 模型、贴图、动画、Lua 脚本及工作台配方；
- 投掷物、消耗品、C4/遥控起爆的基础流程；
- 服务端索引加载、登录/重载同步和客户端重建。

内置的是 GPL-3.0 代码与数据驱动框架，不包含 LRTactical 原作标注为
All Rights Reserved 的贴图、模型和音效资源。

## 三、主要修复与改进

### 1. PAL 第三人称动画

- 规避 Player Animation Library 1.2.5 完成态 fade-out modifier 无法摘除、导致切枪后
  第三人称动画整局失效的问题；
- 在 TACZ 趴姿切回站姿时清理不兼容的趴姿 fade 快照与播放状态，避免手臂旋转累积；
- 保留 ROTATION 层的永久 `SafeAdjustmentModifier`，不重新引入 PAL 的损坏 fade-out 路径；
- 修正已停止 controller 因仍记录同一 clip 而无法重播的一次性动画问题。

PAL 仍是可选模组；未安装 PAL 时，本兼容路径不会启动。

### 2. 瞄准镜、Iris 与光照

- 按 26.1.2 的深度孔径架构完善镜内视模反向裁剪；
- 目镜内不再显示枪身、非瞄具配件和两层枪口火光；
- `ocular_ring` 在 cleanup 后独立恢复，避免物理黑环被裁掉；
- cleanup 仅恢复不可见目镜真正占据的深度，减少 Iris 下水、粒子和云错误覆盖镜体；
- 发光准星使用自发光渲染路径，修复随朝向明暗反转；
- 修正枪械法线重复变换造成的平视过暗和光照方向错误。

### 3. 弹道与动画约束

- 实体生成包改用精确 double 坐标，消除 BlockPos 取整误差；
- 修正 ADS 开枪/换弹约束在不同朝向下产生的固定侧偏；
- 第一人称弹体/炮烟枪口世界偏移试验因实测位置高于枪口而保持回退，R1 不宣称
  已用未经验证的坐标变换解决该问题。

### 4. 游戏状态、界面和兼容

- 修复玩家跨维度后枪械服务端状态不复位；
- 修复退出到标题再进入同一存档后持枪无法正常开火；
- 修复左利手玩家第三人称主手枪不渲染；
- 修复枪包过滤器行高、复选框和透明标签问题；
- 恢复热度条、交互提示和工作台预览缩放/旋转；
- 长版本号按 HUD 可用宽度自适应缩放；
- 隐藏尚未实现、始终显示 `0 (MAX)` 的武器等级占位文案；
- 恢复 26.1.2 可用的 Controllable、Shoulder Surfing 和 Carry On 兼容。

## 四、安装与升级

### 必需环境

| 项目 | 版本 |
|---|---|
| Minecraft | 26.1.2 |
| Java | 25+ |
| Fabric Loader | 0.19.3+ |
| Fabric API | 0.155.2+26.1.2 |
| Forge Config API Port | 26.1.5+ |

### 升级步骤

1. 备份存档、配置及 `.minecraft/tacz/` 内容包目录；
2. 删除旧 TaCZ Refabricated jar，只保留 R1；
3. 若此前手动安装过独立 LRTactical/LR jar，请移除，避免重复 `lrtactical` ID；
4. 保留原有枪包和刀包，首次启动后查看日志中的内容包扫描结果；
5. 使用 PAL 的玩家应安装与 Minecraft 26.1.2 对应的 PAL 构建，而不是 26.2 jar。

## 五、已知限制

- 内置 LRTactical 是代码兼容层而非完整原作：`flash_shield`、自定义冷却的客户端
  物品栏遮罩、效果云专属 tooltip、JSON 自定义尾迹粒子及部分高级系统尚未完成；
- LRTactical 原作受限授权的弹跳/死亡音效不随本项目分发；
- 依赖 TacZ:Arcana 的加密内容包无法解密，通常表现为条目存在但模型和贴图为紫黑缺失资源；
- KubeJS 与 Accelerated Rendering 没有可用于本目标版本的对应 Fabric 集成；
- 武器经验/等级系统在官方 TACZ 与上游 Refabricated 中都只有未完成 API，R1 没有擅自新增
  一套不兼容的等级规则；
- 第三方模组与内容包组合数量较多，R1 仍可能存在未覆盖的兼容问题。

## 六、发布前验证状态

当前源码已通过：

- 851 个 Java 文件的 tree-sitter 语法解析；
- 显式 JSON 资源解析；
- `git diff --check`；
- 可选兼容 API 合约检查；
- 武器等级占位与 collector 路径回归断言。

本沙箱中的完整 Gradle 构建仍受 Gradle 9.5.1 下载 TLS 握手故障阻塞；正式上传前应在
正常联网的 Java 25 发布环境执行：

```bash
./gradlew clean build
```

并至少完成一次纯净客户端、纯净服务端、标准 ZIP 枪包及标准 ZIP 刀包的启动测试。

## 七、反馈说明

本项目是非官方移植。问题应提交到本项目仓库，并附上完整 `latest.log`、模组列表、内容包
名称及复现步骤；请勿要求 TACZ 或 LRTactical 原作者为本移植构建提供支持。
