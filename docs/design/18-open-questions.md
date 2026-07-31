# 第 18 章 · 开放问题清单（实现前必须调研/验证）

> 约定：Q-xx 在 P0 Spike 阶段逐一回答并回写"结论"列。阻断型=不回答不允许进 P1/P2。

| # | 问题 | 所属 | 调研方式 | 阻断型 | 结论（待填） |
|---|---|---|---|---|---|
| Q-01 | 本系统作为**仓库内子包**(`cn.sh1rocu.tacz.industry`)还是**独立附属 mod**(modid `taczind`)?涉及打包/发布形态 | 全局 | 与维护者确认；技术均可 | 否 | ☐ |
| Q-02 | Create 的实际依赖坐标（"飞翔版" Fabric 移植的 maven/版本）与 `KineticBlockEntity` API 兼容面 | M | P0 拉依赖跑通最小挂网 BE | 是(P5 前) | ☐ |
| Q-03 | `GunSmithTableRecipe` 是否可继承/`RecipeSerializer` 可否包装饰加 `required_blueprint` 字段 | A-7 | 读 `crafting/` 源码 | 是(P1 前) | ☐ |
| Q-04 | `AttachmentType` 是否硬编码枚举？modifier 体系能否注册自定义来源（用于 TS→散布、携行具→换弹等） | J/C/L | 读 `AttachmentType` 与 `resource/modifier/` | 是(P0) | ☐ |
| Q-05 | 弹壳拾取物 60% 投掷是否造成实体风暴？是否需要"批次合并拾取" | B-3 | 性能白盒（TickProfiler） | 否 | ☐ |
| Q-06 | **射击总入口**：服务端从 `AbstractGunItem.shoot` 到实体生成的完整链路；伤害/初速在何处可注入乘算；打断点（状态机守卫）放哪层 | B/C/E/F | 读 `AbstractGunItem`、`api/event`、`entity/` | 是(P0，最高优先级) | ☐ ETA |
| Q-07 | 平衡 JSON 热更通道：复用 TACZ 资源重载（GunPackLoader 生命周期）还是独立 datapack listener | 全局 | 读 `resource/GunPackLoader` | 是(P0) | ☐ |
| Q-08 | 散布最终消费点（服务端命中 or 客户端方向扩散?）与子弹撞方块逻辑现状（即停 or 支持穿透） | C | 读 `entity/projectile` 与命中判定 | 是(P2 前) | ☐ |
| Q-09 | `GunRecoil` 关键帧可否运行期替换；`IGunOperator` 有哪些同步字段可挂 bloom/呼吸态 | D | 读 `GunRecoil`+`IGunOperator` | 是(P2 前) | ☐ |
| Q-10 | 炸膛全局系数默认值：`风险池‰` 的玩家可接受频次（白盒模拟定 0.5–2/1000 发） | F | 模拟器跑 10 万发分布 | 否(调参) | ☐ |
| Q-11 | 枪渲染管线的贴图替换/hook 点：锈斑、老化磨损、名牌刻印叠层的最佳实现（shader mask vs 换贴图） | H/N-7 | 读 `client/render/`+模型 LOD | 否 | ☐ |
| Q-12 | 独立弹匣物品与 `GunCurrentAmmoCount` 的同步权威源策略（状态机镜像方案的边界：丢弃/拾取/多人并发） | N-1/B-9 | 写 PoC 联测 | 是(P4 前) | ☐ |
| Q-13 | 生物惊动走 vanilla game event 通道是否足够，还是要自建 NoiseEvent→Brain memory 监听表 | K | 读原版 Mob goal 机制 | 否 | ☐ |
| Q-14 | 携行具槽位：自研槽 vs Curios/Trinkets 移植版依赖，26.2 生态现状 | L | 生态调研 | 否 | ☐ |
| Q-15 | 游戏内手册选型：Ponder(Create 自带) vs Patchouli；蓝图/教学场景写哪套 | M/N-5 | 生态调研 | 否 | ☐ |
| Q-16 | 枪内弹药队列（牵引 Squib/曳光位置记忆）的最大长度与存档膨胀评估（弹链 250 发 JSON 摘要体积） | B-9 | 体积估算+压缩 | 是(P4 前) | ☐ |
| Q-17 | 26.2 Fabric 端键位注册与 TACZ 现有 `client/input` 的冲突矩阵（排障/屏息/归零/换管/保险至少 5 个新键） | E/D/J/G | 读 `client/input/` | 是(P2 前) | ☐ |
| Q-18 | 服务器侧模拟测试框架：headless 射击循环 10 万发的可运行单元（P2/P3 DoD 依赖） | 全局 | 搭 JUnit+FakePlayer harness | 是(P2 前) | ☐ |
| Q-19 | 弹壳/漏夹等拾取物模型与渲染成本（床岩实体或 ItemEntity） | B/N-1 | 渲染压测 | 否 | ☐ |
| Q-20 | 与既有社区"Create×TaCZ 配方包"（调研 0.1）的版本共存策略：我们的硬核电是否与其配方冲突 | M | 社区兼容说明文档 | 否 | ☐ |
