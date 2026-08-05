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
- 蓝图显示名、蓝图压实材料；
- 五个结构件：`receiver`、`bolt`、`barrel`、`trigger`、`recoil`；
- 结构件到最终件的映射（例如手枪的 `receiver -> frame`、`bolt -> slide`）；
- 最终总装消耗的家具/外装材料；
- 中英名称与必要的模具名称覆盖。

生成器根据这些字段写入：

```text
data/tacz/recipe/create/industry/blueprint_<platform>.json
data/tacz/recipe/create/industry/calibrate_component_die_<platform>_*.json
data/tacz/recipe/create/industry/form_component_<platform>_*.json
data/tacz/recipe/create/industry/assemble_<slug>.json
data/tacz/industry/assembly/gun/<slug>.json
assets/tacz/lang/en_us.json
assets/tacz/lang/zh_cn.json
```

## 弹药与弹匣源定义

`tools/industry/cartridges.json` 是默认枪包全部 **24** 种散装弹药的口径源定义。每条口径声明 `AmmoId`、弹头类型、是否会抛出可回收弹壳、双语名称，以及一种真实的模具校准来源：

- 有对应默认枪的口径使用实际同口径完整枪，作为部署器中不消耗的膛室/口径量规；生成器会校验该枪的 `ammo` 字段真的等于该 `AmmoId`，不能拿不相干的枪伪造量规。
- 默认包虽然提供散装弹、但没有任何对应枪械数据的五种口径（4.6×30、5.45×39、6.8×51 Fury、7.62×25、7.62×54R）使用一条真正的机械合成器多槽配方制造专属淬硬口径量规；每条都有不同的物理 datum，不能靠同输入/改数量输出不同口径。
- 40 mm HE 与 RPG-7 HEAT 弹头在中性弹头坯进入模具前，还必须沿顺序装配由部署器装入 TNT 战斗部；RPG 火箭显式声明 `eject_case: false`，不会凭空掉出“空弹壳”。

生成器会输出：

```text
caliber_gauge_<caliber>.json                 # 仅无对应默认枪的口径
calibrate_case_die_<caliber>.json
calibrate_projectile_die_<caliber>.json
form_case_<caliber>.json
form_projectile_<caliber>.json
recondition_case_<caliber>.json              # 已击发弹壳 + 对应弹壳模具 -> 可装填弹壳
industry/cartridge_assembly/<caliber>.json
industry/ammo/<caliber>.json
```

`industry/ammo` 会移除同一 `AmmoId` 的旧工作台捷径；最终每发仍只由专用四槽弹药装配机装配。抛壳后的 `tacz:cartridge_case` 带 `IndustryPartKind: "spent_case"` 与精确 `CartridgeCaliber`，只能经对应 `recondition_case` 部署器工序整形，不能直接绕过底火/推进药重新装弹。

生成器还读取运行时的 `data/tacz/industry/gun_feed/*.json`，自动生成全部实体弹匣的“中性弹匣壳体 + 保留成枪量规”部署器配方；若某个供弹定义的 `ammo` 没有口径源定义，`--check` 会失败。

## 自动验证

`--check` 会拒绝：

- 重复的 `slug`、`platform`、`gun_id`、口径 ID、`AmmoId` 或无枪口径量规 datum；
- 相同的工作盆蓝图压实输入签名；
- 缺失/顺序错误的五个结构件；
- 默认枪包任何散装弹没有专用口径定义，或量规枪并非真正使用该弹种；
- 生成资源与已提交 JSON 不一致；
- 生成语言键与已提交语言文件不一致；
- 固定机器纹理或模型不是由其视觉源定义生成。

游戏运行时仍有 `IndustryProcessManager` 的 Basin 冲突诊断，专门防止数据包新增的压实配方发生同优先级匹配冲突。

## 纹理策略

平台差异不是靠复制 PNG 实现。`gun_component`、`press_die`、`cartridge_case`、`projectile_core`、`gun_blueprint` 都是 NBT 泛型物品，故意共用基础纹理；平台、口径、类型和显示名由 custom data 决定。

只有真正新增注册物品/方块才生成固定视觉资源。`tools/industry/machines.json` 当前描述弹药装配机的配色，生成器据此输出其 16×16 方块纹理和模型引用。生成器绝不触碰 `tacz_default_gun` 默认枪包中的受限许可美术资源。
