# 玩家验收探针（测试专用，不是默认枪包）

这些 JSON 为本仓库原创测试配置；只引用默认枪包已安装的模型/纹理/状态脚本，不复制或修改默认资源。不要装进常玩存档；只用于独立测试实例。

将此目录内的 gunpack.meta.json、assets/、data/ 放入游戏目录 `tacz/player-acceptance/` 后重启。
创造栏应出现“验收用 inventory / magazine”；前者背包直读 7.62×39，后者是普通弹匣对照。
持枪按绑定的“检视”键：0秒枪口 end_rod，0.2秒默认原生火光/烟，0.4秒 root 的 happy_villager。
这是关键帧到实际渲染的探针，不是通用 Snowstorm/Molang 兼容性证明；绿色 INV 数字是枪模文字探针。


## Snowstorm runtime probe (26.1.2 backport)

The inspect variants additionally trigger `port_acceptance:runtime_probe` at 0.3 seconds.
Its authored definition lives in `assets/port_acceptance/particle_definitions/`; the animation
sets `variable.count = 3` with `pre_effect_script`. This must use the pack-defined runtime,
not native smoke. Check first-person locator follow, F5 and a separate tracking client with
matching packs. The cosmetic relay never transmits script text. Each target client workflow keeps its own logs and frames.
