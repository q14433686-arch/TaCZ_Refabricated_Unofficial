# COP 357 四管德林格手枪 — TACZ 枪包（程序生成版）

本枪包由 `docs/gunpack_tools/gen_cop357.py` 一键生成，**所有尺寸都从真实数据按官方比例换算**：

| 项目 | 真实（COP 357） | 换算 (1000mm=48grid) | 模型实际 | 误差 |
|---|---|---|---|---|
| 全长 | 142 mm | 6.82 grid | 7.20 grid（150mm） | +5.6% |
| 高 | 103 mm | 4.94 grid | 4.63 grid（96mm） | -6.6% |
| 宽 | 27 mm | 1.30 grid | 1.25 grid（26mm） | -3.7% |

## 安装

1. 把 `cop357_gunpack.zip` 放进 `.minecraft/tacz/`（不用解压；新版 TACZ 读这个目录，zip 内的 `gunpack.meta.json` 必须位于 zip 根）。
2. 进游戏，`/tacz reload`，或重进存档。
3. 在枪械工匠台用 **10 个铁锭** 合成「COP 357 四管德林格手枪」。
4. 弹药：`.357 马格南`（默认枪包自带）。

> 本移植版（26.2）的枪包目录若仍是 `config/tacz/custom`，把 zip 放那里即可——两种路径按你实际版本试。

## 玩法设定

- 弹容 **4 发**（四管，每扣一次扳机发射一发），半自动
- 供弹：`manual`（手动装填——开膛塞 4 发，最贴近德林格）
- 使用 `.357 马格南`，单发伤害 10.5，后坐力大（小心手腕）
- 无配件槽（真实 COP 装不了配件）
- 第一人称动画借用默认枪包的 `pistol` 默认动画

## 怎么改

所有改动都只需要在 `docs/gunpack_tools/cop357_gunpack/` 里改完，再运行
`python3 docs/gunpack_tools/gen_cop357.py` 重新打包（脚本每次会覆盖生成）。

| 想改什么 | 改哪里 |
|---|---|
| 模型形状/方块 | `gen_cop357.py` 的 `CUBES`（方块坐标，0.125 倍数）和 `BONES`（骨骼/定位组） |
| 贴图颜色 | `gen_cop357.py` 的 `palette`（材质配色）；更精细的画法：用 Blockbench 打开 `geo_models/gun/cop357_geo.json` 直接编辑并重绘贴图 |
| 枪太大/太小 | `cop357_display.json` 的 `transform.scale`（thirdperson/ground/fixed） |
| 伤害/射速/后坐 | `data/cop357/data/guns/cop357_data.json` |
| 音效（现在是借默认枪包 M1911 的） | 自己做 `assets/cop357/sounds/gun/cop357/*.ogg`，改 `cop357_display.json` 的 `sounds` 字段 |
| 中文名/描述 | `assets/cop357/lang/zh_cn.json` |
| 合成配方 | `data/cop357/recipe/gun/cop357.json` |

## 推荐下一步（学会自己建模）

用 Blockbench 打开 `cop357_geo.json`，你会看到这个"积木枪"的全部结构：
`root > cop357 > barrels/receiver/frame/grip/...` 加 `views`/`positioning`/`refit` 定位组。
对照它和默认枪包的 `ak47_geo.json` 的骨骼树，再按
[官方模型指南](https://tacwiki.mcma.club/zh/model_guide/model.html) 的方法
找一张目标枪的侧视图描图，就能做出你自己的第二把枪了。
做完后用 `docs/gunpack_tools/measure_proportions.py` 量一下你的模型，和
`docs/GUNPACK_MODEL_RESEARCH.md` 第 3 节的比例表对照，保证不跑偏。
