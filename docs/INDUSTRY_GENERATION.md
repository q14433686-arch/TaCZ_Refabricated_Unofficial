# 工业资源生成器

从平台扩张第一批开始，重复的工业资源不再应该人工复制。仓库以少量**平台源定义**作为事实来源，生成普通、可审查、可被数据包覆盖的资源文件。

> 生成发生在作者工具阶段，不在游戏运行时发生。服务器和客户端最终读取的仍然是 `src/main/resources` 下的普通 JSON/PNG，因此 REI、JEI、专服同步和第三方数据包覆盖行为不改变。

## 命令

Linux/macOS：

```bash
python3 tools/generate_industry_content.py --write
python3 tools/generate_industry_content.py --check
```

Windows：

```bat
py tools\generate_industry_content.py --write
py tools\generate_industry_content.py --check
```

也可以使用 Gradle 的可选任务：

```bat
.\gradlew generateIndustryContent
.\gradlew verifyIndustryContent
```

这两个任务是**可选**的，不会被普通 `build` 自动调用；普通玩家构建已提交资源时不需要 Python。

## 平台源定义

已校准平台每个平台一份文件：

```text
tools/industry/platforms/<gun recipe slug>.json
```

默认枪包中未显式列出的枪会由 `tools/industry/default_gun_policy.json` 在生成时自动发现：读取其原始枪配方、枪索引和首个射击模式，分配唯一蓝图种子与独立 NBT 平台。这样默认枪包全部 53 把枪都获得高保真组件、模具和总装资源，而不是退回到运行时通用门槛。

一个源定义描述：

- `slug`：原 TACZ 成枪工作台配方路径；
- `platform`：NBT 平台身份；
- `gun_id` 与初始 `fire_mode`；
- 原始档案、生产工装模板和旧存档模板的显示名；
- `manufacturing_tier`、`action_profile`、`tooling_scope`；
- 五个中性毛坯类别，以及向真实动作部件的映射；
- 结构件到最终件的映射（例如手枪的 `receiver -> frame`、`bolt -> slide`）；
- 最终总装消耗的家具/外装材料；
- 中英名称与必要的模具名称覆盖。

生成器根据这些字段写入：

```text
data/tacz/recipe/create/industry/form_template_blank.json
data/tacz/recipe/create/industry/survey_dossier_<platform>.json
data/tacz/recipe/create/industry/blueprint_<platform>.json        # 原始档案 → 生产模板
data/tacz/recipe/create/industry/copy_template_<platform>.json
data/tacz/recipe/create/industry/restore_template_<platform>.json
data/tacz/recipe/create/industry/calibrate_component_die_<platform>_*.json
data/tacz/recipe/create/industry/form_component_<platform>_*.json
data/tacz/recipe/create/industry/form_furniture_blank_<material signature>.json
data/tacz/recipe/create/industry/calibrate_furniture_<platform>.json
data/tacz/recipe/create/industry/assemble_<slug>.json
data/tacz/industry/assembly/gun/<slug>.json
assets/tacz/lang/en_us.json
assets/tacz/lang/zh_cn.json
```

原始木料/皮革/黄铜/玻璃不会再逐件出现在终端枪线里。生成器按材料签名只生成一次真正的机械合成器“中性外装毛坯”工艺；同平台蓝图由部署器保留地把它校准为命名外装套件，最终总装只安装该套件并冲压压合。相同原料输入永远只产同一种中性毛坯，平台差异来自真实蓝图，避免“安装石头”式无语义步骤和相同输入产出不同平台件的碰撞。

终端成枪结果还会写入 `IndustryAssemblyPlatform` 与 `IndustryAssemblyRecipe` 来源标记；工业回收站只接受这种实际终端产物，避免把旧工作台/战利品枪的同名 `GunId` 变成工业零件捷径。每个 assembly 声明同时保存五个结构毛坯类别，供回收站按实际枪械重量返还 3–5 件可再次成型的毛坯，并按外装材料表返还部分材料。

每个平台生成的是**原始工艺档案**来源，源策略在 `tools/industry/blueprint_acquisition.json`：legacy 档案进入 2 级武器匠与早期箱子，service 档案进入 5 级武器匠，advanced/precision 档案主要来自高阶探索箱。档案不是直接成枪模板；它要转印到 Basin 形成的空白工装页上，才得到可复制的生产工装模板。实际样枪也可经 `survey_dossier_<platform>` 测绘为同一份档案。旧存档蓝图则经 `restore_template_<platform>` 转换，不能报废。

同时，生成器为全部默认平台写出 `data/tacz/industry/reference/guns/<gun>.json`。这不是额外配方，而是运行时 `IndustryReferenceProfileManager` 使用的动作、供弹、弹药类别、制造 tier 对照表；它会把档案与实际默认 `gun_feed` / GunData 交叉校验。第三方包通过相同 schema 或显式 `industry/id_aliases` 接入，详见 `docs/INDUSTRY_REFERENCE_PROFILE.md`。

## 弹药与弹匣源定义

`tools/industry/cartridges.json` 是默认枪包全部 **24** 种散装弹药的口径源定义。每条口径声明 `AmmoId`、弹头类型、是否会抛出可回收弹壳、双语名称，以及一种真实的模具校准来源：

- 有对应默认枪的口径先让部署器持实际同口径完整枪，把一个中性弹药基准量规毛坯校准成可复用的**口径基准量规**；该量规再分别校准弹壳模具和弹头模具。生成器会校验样枪的 `ammo` 字段真的等于该 `AmmoId`，不能拿不相干的枪伪造量规，也不会让同一把枪重复直接“印”两枚不同模具。
- 默认包虽然提供散装弹、但没有任何对应枪械数据的五种口径（4.6×30、5.45×39、6.8×51 Fury、7.62×25、7.62×54R）使用一条真正的机械合成器多槽配方制造专属淬硬口径量规；每条都有不同的物理 datum，不能靠同输入/改数量输出不同口径。
- 40 mm HE 与 RPG-7 HEAT 弹头在中性弹头坯进入模具前，还必须沿顺序装配由部署器装入 TNT 战斗部；RPG 火箭显式声明 `eject_case: false`，不会凭空掉出“空弹壳”。

生成器会输出：

```text
press_die_cartridge_gauge_blank.json         # Basin 形成中性弹药基准量规毛坯
calibrate_cartridge_gauge_<caliber>.json     # 有对应默认枪：样枪 → 可复用口径基准量规
caliber_gauge_<caliber>.json                 # 无对应默认枪：机械合成器 datum → 口径基准量规
calibrate_case_die_<caliber>.json             # 口径基准量规 → 弹壳模具
calibrate_projectile_die_<caliber>.json       # 口径基准量规 → 弹头模具
form_case_<caliber>.json
form_projectile_<caliber>.json
recondition_case_<caliber>.json              # 已击发弹壳 + 对应弹壳模具 -> 可装填弹壳
reverse_case_gauge_<caliber>.json             # 新弹壳样本 -> 仅弹壳尺寸量规
reverse_spent_case_gauge_<caliber>.json       # 已击发壳样本 -> 仅弹壳尺寸量规（无抛壳口径除外）
reverse_projectile_gauge_<caliber>.json       # 弹头芯样本 -> 仅弹头外形量规
reverse_cartridge_gauge_<caliber>.json        # 一发完整散装弹样本 -> 完整口径量规
calibrate_case_die_from_case_gauge_<caliber>.json
calibrate_projectile_die_from_projectile_gauge_<caliber>.json
industry/cartridge_assembly/<caliber>.json
industry/ammo/<caliber>.json
```

`industry/ammo` 会移除同一 `AmmoId` 的旧工作台捷径；最终每发仍只由专用四槽弹药装配机装配。生成器还从默认包 `index/ammo/<caliber>.json` 的 `stack_size` 读取最终散装弹上限（按 26.2 的 99 上限夹取），并把相同 `minecraft:max_stack_size` 写到该口径的弹壳、弹头、爆炸弹过渡件和整形弹壳输出中。抛壳后的 `tacz:cartridge_case` 带 `IndustryPartKind: "spent_case"`、精确 `CartridgeCaliber` 与 `CartridgeAmmoId`，只能经对应 `recondition_case` 部署器工序整形，不能直接绕过底火/推进药重新装弹。

每条清单还显式声明 `balance_tier`、`batch_count`、`propellant_count`、`case_blank_count` 与 `projectile_blank_count`。`balance_tier` 对应内置的弹道角色最低毛坯质量/推进药下限；最终定义将 `case_count`、`projectile_count`、`primer_count` 设为批量输出数，确保一枚物理件绝不复制成一批弹；推进药按弹种等级单独增加，而大口径壳/弹头会在成型顺序线上实际消耗额外中性毛坯。`--check` 同时核对批量不超过成品堆叠/旧配方批量，且推进药不少于弹道等级与旧枪包按每发折算的火药下限。

`tools/industry/magazine_carriers.json` 与运行时的 `data/tacz/industry/gun_feed/*.json` 双向校验全部默认可拆卸弹匣/弹链箱。生成器为每个族／弹种／容量身份输出：中性壳体、供弹组件和规格量规毛坯的 Basin 工艺；生产模板（保留）或空成品样本（消耗）→规格量规；规格量规→命名壳体与命名托弹/弹链组件；以及单工件最终总装。它明确删除旧的“中性壳体 + 完整成枪 → 成品弹匣”捷径。若某个可拆卸供弹定义没有清单身份、缺少对应平台、缺少弹药源，或任一顺序线超过 Create Fly 的 7 个原生 JEI 阶段，`--check` 会失败。

## 稳定工艺档案委托

`tools/industry/blueprint_acquisition.json` 的每个 tier 还声明一个 `stable_archive`。生成器把它写成 Basin 或机械合成器档案包配方，并为全部默认平台生成：

```text
data/tacz/recipe/create/industry/form_dossier_archive_<tier>.json
data/tacz/recipe/industry/dossier_commission_<platform>.json
```

后者是默认枪械工作台杂项页中的显式 GUI 委托：消耗对应层级档案包和空白工装页，检查但不消耗对应动作夹具，输出玩家选定的 `master` 原始档案。它不是多个同输入 Create 物理配方竞争不同输出；型号由工作台中玩家明确选择的委托条目决定。随机村民、箱子和样枪测绘仍是捷径。

## 自动验证

`--check` 会拒绝：

- 重复的 `slug`、`platform`、`gun_id`、口径 ID、`AmmoId` 或无枪口径量规 datum；
- 相同的工作盆蓝图压实输入签名；
- 缺失/顺序错误的五个结构件；
- 默认枪包任何散装弹没有专用口径定义，或量规枪并非真正使用该弹种；
- 任一弹药批量超过最终堆叠/旧配方批量，或推进药低于旧配方每发火药下限；
- 缺失任一默认平台的村民/世界箱模板来源；
- 生成资源与已提交 JSON 不一致；
- 生成语言键与已提交语言文件不一致；
- 固定机器纹理或模型不是由其视觉源定义生成。

游戏运行时仍有 `IndustryProcessManager` 的 Basin 冲突诊断，专门防止数据包新增的压实配方发生同优先级匹配冲突。

## 纹理策略

平台差异不是靠复制 PNG 实现。`gun_component`、`press_die`、`cartridge_case`、`projectile_core`、`gun_blueprint` 都是 NBT 泛型物品，故意共用基础纹理；平台、口径、类型和显示名由 custom data 决定。

只有真正新增注册物品/方块才生成固定视觉资源。`tools/industry/machines.json` 当前描述弹药装配机与工业回收站的配色，生成器据此输出其 16×16 方块纹理和模型引用。生成器绝不触碰 `tacz_default_gun` 默认枪包中的受限许可美术资源。
