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

每个平台一份文件：

```text
tools/industry/platforms/<gun recipe slug>.json
```

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

`tools/industry/cartridges.json` 是口径源定义。每条口径只写：口径 ID、`AmmoId`、不消耗的量规枪、弹头类型，以及中英名称。生成器会输出：

```text
calibrate_case_die_<caliber>.json
calibrate_projectile_die_<caliber>.json
form_case_<caliber>.json
form_projectile_<caliber>.json
industry/cartridge_assembly/<caliber>.json
industry/ammo/<caliber>.json
```

生成器还读取运行时的 `data/tacz/industry/gun_feed/*.json`，自动生成全部实体弹匣的“中性弹匣壳体 + 保留成枪量规”部署器配方；若某个供弹定义的 `ammo` 没有口径源定义，`--check` 会失败。

## 自动验证

`--check` 会拒绝：

- 重复的 `slug`、`platform` 或 `gun_id`；
- 相同的工作盆蓝图压实输入签名；
- 缺失/顺序错误的五个结构件；
- 生成资源与已提交 JSON 不一致；
- 生成语言键与已提交语言文件不一致；
- 固定机器纹理或模型不是由其视觉源定义生成。

游戏运行时仍有 `IndustryProcessManager` 的 Basin 冲突诊断，专门防止数据包新增的压实配方发生同优先级匹配冲突。

## 纹理策略

平台差异不是靠复制 PNG 实现。`gun_component`、`press_die`、`cartridge_case`、`projectile_core`、`gun_blueprint` 都是 NBT 泛型物品，故意共用基础纹理；平台、口径、类型和显示名由 custom data 决定。

只有真正新增注册物品/方块才生成固定视觉资源。`tools/industry/machines.json` 当前描述弹药装配机的配色，生成器据此输出其 16×16 方块纹理和模型引用。生成器绝不触碰 `tacz_default_gun` 默认枪包中的受限许可美术资源。
