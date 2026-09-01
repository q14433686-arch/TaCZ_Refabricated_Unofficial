# `Item#getName` 双端公共方法修复 —— 同步到 `1.21.11` 与 `26.1.2`（2026-08-22）

> 写给负责 `1.21.11` / `26.1.2` 分支的移植 AI 或维护者。
> 源分支：`arena/01a0255f-tacz-refabricated-unofficial`（26.2 线，tip = `ff73024`；
> 代码修复提交 = `86e693e`，文档补丁 = `57c99e3` / `b8590f9` / `ff73024`）。
> 目标分支 tip（撰写时）：`1.21.11` = `3cc3338`、`26.1.2` = `b2238f0`。
> 本文已读取两个目标分支**实码**（非文档推定）；每个 target 的结论都经过
> `git archive` 全树解包 + 三方 blob 比对 + `patch --dry-run` / `git apply --check` 验证。
> 注意：本会话工作树固定为 `arena/01a0255f-...`，**没有直接向两个目标分支提交**；
> 交付物是两份可直接 `git apply` 的补丁，由你在目标分支落地。

---

## 0. 一句话结论

**两个目标分支的病灶与 26.2 修复前逐行等价；基础设施（`Common*Index#getPojo()`、
POJO `getName()`、`TimelessAPI#getCommon*Index`、`custom.tacz.error.no_name` 兜底键）
全部齐备，无需补 getter。新增代码补丁可原样直贴；发布文案补丁已按各分支现状
（含 `1.21.11` 文案模板仍是 26.1.2 的事实）做适配。**

交付文件：

| 文件 | 用途 |
| --- | --- |
| `docs/patch/2026-08-22-getname-common-index-1.21.11.patch` | `1.21.11` 分支：9 个文件（4 Java + 4 publish + `CHANGELOG_1_21_11.md`） |
| `docs/patch/2026-08-22-getname-common-index-26.1.2.patch` | `26.1.2` 分支：9 个文件（4 Java + 4 publish + `UPDATE_REPORT_26_1_2_R2.md`） |

两份补丁都已用 `git apply --check` 对各自 tip 全树验证通过；应用后的产物与
本仓库 `86e693e` 对应的 Java 语义逐字符等价（唯一差异是 `AbstractGunItem` 中
26.2 线独有的一段无关注释，不随补丁带入——见 §3）。

## 1. 病灶确认（两个分支都与 26.2 修复前相同）

| 文件 | 行号（1.21.11 = 26.1.2） | 修复前 |
| --- | --- | --- |
| `com/tacz/guns/api/item/gun/AbstractGunItem.java` | `getName` = 291–299 | `@Environment(CLIENT)` + `getClientGunIndex` |
| `com/tacz/guns/item/AmmoItem.java` | `getName` = 102–111 | 同上（`getClientAmmoIndex`） |
| `com/tacz/guns/item/AttachmentItem.java` | `getName` = 38–47 | 同上（`getClientAttachmentIndex`） |
| `com/tacz/guns/item/GunSmithTableItem.java` | `getName` = 47–56 | 同上（`getClientBlockIndex`） |

三方 blob 比对（26.2 基线 `8edac57` vs `1.21.11@3cc3338` vs `26.1.2@b2238f0`）：

| 文件 | 26.2 基线 vs 1.21.11 | 26.2 基线 vs 26.1.2 |
| --- | --- | --- |
| `AmmoItem.java` | **逐字节相同** | **逐字节相同** |
| `AttachmentItem.java` | **逐字节相同** | **逐字节相同** |
| `GunSmithTableItem.java` | **逐字节相同** | **逐字节相同** |
| `AbstractGunItem.java` | 仅差一段与本次无关的注释（26.2 基线在 ~L166 多 4 行） | 同左 |

→ 因此 Java 补丁在目标分支应用时只有 `AbstractGunItem` 出现 4 行偏移
（`patch` 报 `offset -4 lines`），逻辑零适配。

## 2. 基础设施核验（两分支全部已有，本次无需新增 getter）

| 项 | 1.21.11 | 26.1.2 |
| --- | --- | --- |
| `CommonGunIndex#getPojo()` | ✅ L134 | ✅ L134 |
| `CommonAmmoIndex#getPojo()` | ✅ L32 | ✅ L32 |
| `CommonAttachmentIndex#getPojo()` | ✅ L51 | ✅ L51 |
| `CommonBlockIndex#getPojo()` | ✅ L47 | ✅ L47 |
| 四个 `*IndexPOJO#getName()` | ✅ | ✅ |
| `TimelessAPI#getCommon{Gun,Ammo,Attachment,Block}Index` | ✅ L95–107 | ✅ L95–107 |
| `CommonAssetsManager.get()` 回退 `CommonNetworkCache.INSTANCE` | ✅ L268 | ✅ L268 |
| `Client*Index` 空白名兜底键 | ✅ 均为 `custom.tacz.error.no_name` | ✅ 同左 |
| `org.apache.commons.lang3.StringUtils` 可用（`Client*Index` 已依赖） | ✅ | ✅ |
| LR 内置框架 `me.xjqsh.lrtactical.item.*#getName` | ✅ 已走 common 索引（`get{Consumable,Melee,Throwable}Index` + `getDescriptionId`），无需修改 | ✅ 同左 |
| `AmmoBoxItem#getName` | ✅ 固定 `item.tacz.ammo_box.*` 键 + 等级颜色，无 client 依赖，无需修改 | ✅ 同左 |

## 3. 补丁内容与「与 26.2 修复的等价性」

### 3.1 代码（4 文件）

与 `86e693e` 完全一致的语义：

```java
@Override
@Nonnull
public Component getName(@Nonnull ItemStack stack) {
    Identifier id = this.getXxxId(stack);
    Optional<CommonXxxIndex> index = TimelessAPI.getCommonXxxIndex(id);
    if (index.isPresent() && index.get().getPojo() != null) {
        String name = index.get().getPojo().getName();
        return Component.translatable(StringUtils.isBlank(name) ? "custom.tacz.error.no_name" : name);
    }
    return super.getName(stack);
}
```

- 删除 `@Environment(EnvType.CLIENT)`；
- 删除不再使用的 `Client*Index` import（两分支 `AmmoItem` 的
  `ClientAssetsManager` / `PackInfo` 仍被 `appendHoverText` 使用，保留不动；
  其余 `@Environment` 用法（`getCustomRenderer` 等）不受影响）；
- 新增 `org.apache.commons.lang3.StringUtils` import；
- 26.2 的 `AbstractGunItem` Javadoc（fabric-loader 剥离语义长文）随补丁进入目标分支
  时**只带 getName 区域**；26.2 基线独有的另一处注释不随补丁带入（保持目标分支原样）。

### 3.2 发布文案（`docs/publish/` 4 文件）

| 文件 | 改动 |
| --- | --- |
| `Modrinth.md` | 英文 FAQ 章节：`## FAQ: REI/JEI cheating on a dedicated server ...`（插在 Arcana 节后、`## Reporting issues` 前） |
| `CurseForge.md` | 同上（插在 Arcana 节后、`## Licensing` 前） |
| `MCMOD.md` | 正文新增「八、常见问题：专用服务器上通过 REI/JEI 作弊拿取…」，原文八/九/十/十一节顺延为十/十一/十二 |
| `README.md` | 「三份文案都必须同时保留两项边界说明」追加第 3 条（REI/JEI FAQ）；引用 26.2 分支审计文档与本同步文档 |

> ⚠️ **已知事实（不属于本次改动）**：`1.21.11` 分支 `docs/publish/*` 目前仍是
> `26.1.2` 模板（版本号字段写 `1.1.8+fabric.26.1.2.R1`、Minecraft `26.1.2`）。
> 本次 FAQ 是版本无关内容，插入不受影响；但发布 `1.21.11` 前请自行把三站文案的
> 版本字段更改为 `1.21.11`（`26.1.2` 分支文案无需改动）。

### 3.3 各分支 changelog 尾部追加

- `1.21.11` → `docs/CHANGELOG_1_21_11.md`：追加「FAQ：专服上 REI/JEI 作弊拿取…（2026-08-22 同步增补）」；
- `26.1.2` → `docs/UPDATE_REPORT_26_1_2_R2.md`：追加同一 FAQ 段。

## 4. 应用方法（目标分支上执行）

```bash
# 1.21.11
git checkout 1.21.11 && git pull origin 1.21.11
git apply docs/patch/2026-08-22-getname-common-index-1.21.11.patch
git add -A && git commit -m "fix(server): Item#getName 改读 common 索引（自 26.2 86e693e 同步）+ 发布 FAQ" && git push

# 26.1.2
git checkout 26.1.2 && git pull origin 26.1.2
git apply docs/patch/2026-08-22-getname-common-index-26.1.2.patch
git add -A && git commit -m "同上" && git push
```

补丁头部为标准 `a/ b/` 路径，`git apply`（含 `--check`）已在对应 tip 全树验证。
若目标分支在 `3cc3338` / `b2238f0` 后有新提交触及上述 9 个文件，`git apply` 可能
报 context miss；此时改用三路合并：

```bash
git checkout <目标分支>
git apply --3way docs/patch/2026-08-22-getname-common-index-<目标>.patch
```

## 5. 验证清单（有构建环境后执行）

代码层面（两分支同样）：

1. `./gradlew compileJava --no-daemon`（本会话环境无 JDK，**未执行**，不要宣称已编译）；
2. 四个文件 `getName` 方法体不再含 `@Environment(CLIENT)`，且全部走
   `TimelessAPI.getCommon*Index(...).getPojo().getName()`；
3. `grep -rn "getClient" src/main/java --include="*.java" | grep -v "/client/"` 确认
   `getName` 病灶清零（`getAimingZoom` / `LaserColorUtil` 为已知"安全但脆弱"项，
   本次不在修改范围，见 26.2 分支审计文档 §4）；
4. 服务端行为：`/give @p tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47"}]`
   （26.x 与 1.21.11 组件语法一致）后，**服务端回执显示 `tacz.gun.ak47.name`**
   （服务端无枪包 lang 时显示该键本身即算通过），而非 `item.tacz.modern_kinetic_gun`；
5. 客户端显示：单人/局域网/远程专服下，带 id 物品的 tooltip、GUI、聊天 hover 均正常。

FAQ 发布文案的验证点（与代码修复无耦合）：

- **REI/JEI 紫黑问题**：症状与原因见补丁内 FAQ 段落；零代码修复 =
  专服安装与客户端同版本的 REI（JEI 同理走 `PacketGiveItemStack`）。
  REI 未装服务端时的 `/give` 命令兜底会丢 `minecraft:custom_data`
  （`ClientHelperImpl#tryCheatingEntry` 的 `tagMessage = ""`，`TODO 24w09a`），
  单机/局域网因 `canUsePackets()` 为真而正常——该行为与 MC 版本无关，
  因此在两条目标分支同样成立。

## 6. 不作移植的内容

- 26.2 分支独有的 `docs/DEDICATED_SERVER_GETNAME_AUDIT_2026_08_21.md`
  （内部审计长文，含 26.2 源码细节）**不随补丁复制**；其 §8/§9 结论（裸物品回退、
  REI 兜底丢组件）在两分支同样成立，发布文案 FAQ 已覆盖玩家所需结论。
- `docs/CHANGELOG_26_2_R2.md` 的 26.2 专属条目不移植；两分支各自的使用
  `CHANGELOG_1_21_11.md` / `UPDATE_REPORT_26_1_2_R2.md` 追加通用 FAQ。

## 7. 来源提交（26.2 线，供回溯）

| 提交 | 内容 |
| --- | --- |
| `86e693e` | 四个 Java 文件：删 `@Environment(CLIENT)`、改读 common 索引 |
| `57c99e3` | 审计文档：裸物品回退与正确 `/give` 组件语法解读 |
| `b8590f9` | 审计文档第 9 节：REI/JEI 兜底路径源码实锤 |
| `ff73024` | 发布文案 FAQ（Modrinth/CurseForge/MCMOD/README）+ 26.2 changelog FAQ |
