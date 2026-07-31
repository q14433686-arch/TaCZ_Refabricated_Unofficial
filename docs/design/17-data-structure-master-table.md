# 第 17 章 · 数据结构总表（唯一权威源 SSoT）

> 用途：实现期防冲突。所有字段名以此表为准；任何新增字段必须先在此表登记。
> 载体优先级：**DataComponent（26.2 原生机制，本仓库已确认 ItemStack=components 布局）**，方块实体用 NBT，玩家态用 PersistentData/能力。

## 17.1 组件注册总表（DataComponentType，域 `taczind`）

| 组件 ID | 载体物品 | 章节 | 字段（名:类型） |
|---|---|---|---|
| `taczind:workpiece` | 工件/半成品 | A-2 | `heat:int`, `process_id:ResourceLocation`, `progress:float`, `quality_seed:long`, `material:ResourceLocation` |
| `taczind:mold` | 模具 | A-3 | `die_type:string`, `wear:int`, `max_wear:int`, `ts:int` |
| `taczind:barrel_part` | 枪管零件/备用枪管 | A-3/G | `caliber:string`, `length:int`, `twist:int`, `bore_finish:float`, `material:ResourceLocation`, `ts:int`, `wear:float`, `erosion:float`, `hot_until_tick:long` |
| `taczind:receiver_part` | 机匣零件 | A-6 | `route:enum(STAMPED,MILLED)`, `material:ResourceLocation`, `ts:int`, `rivet_quality:float?` |
| `taczind:gun_build` | 成品枪 | A-8 | `ts:int`, `parts_ts:Map<string,int>`, `build_serial:string`, `factory_tag:string?`, `ts_degraded:int`, `weight_g:int`, `length_class:int` |
| `taczind:cartridge` | 弹药/弹壳同源 | B | `case_material:enum`, `primer_type:enum(BOXER,BERDAN)`, `primer_quality:float`, `primer_corrosive:boolean`, `propellant_type:enum`, `burn_rate:enum(FAST,MID,SLOW)`, `powder_charge_pct:int`, `powder_charge_dev:float`, `projectile_type:enum`, `projectile_mass_class:enum`, `overpressure_score:float`, `squib_risk:float`, `handload:boolean`, `lot_id:long` |
| `taczind:shell_case` | 空弹壳 | B-3 | `material:enum`, `primer_type:enum`, `reload_count:int`, `crack_risk:float`, `state:enum(FIRED_SPENT,INSPECTED_OK,CRACKED,DEFORMED)`, `rusted:boolean` |
| `taczind:powder_lot` | 发射药堆 | A-5/B | `burn_rate:enum`, `lot_quality:float`, `lot_id:long` |
| `taczind:cycle` | 枪 | E | `state:int`, `pending_tick:long`, `squib:boolean`, `squib_known:boolean`, `mal_bp_cache:float[6]`, `jam_streak:int` |
| `taczind:thermal` | 枪 | G | `heat:float`, `last_tick:long`, `erosion:float`, `hot_barrel_inserted:boolean`, `qcb:boolean` |
| `taczind:maintenance` | 枪 | H | `fouling:float`, `rust:float`, `lube:float`, `corrosive_pending:boolean`, `last_clean_day:int`, `obstruction:boolean`, `barrel_obstruction:boolean` |
| `taczind:parts` | 枪 | I | `parts:Map<slot, PartState>` — `PartState{part_id:string, wear:float, max_wear:float, broken:boolean, service_count:int, spring_free:float, ts:int}` |
| `taczind:build_ext` | 枪 | J | `barrel_slot:{len_class:int, twist:int}`, `zero_m:int`, `suppressor:{baffle_wear:float, wetted:boolean}` |
| `taczind:gun_history` | 枪 | L/N-4 | `kill_count:int`, `shots_fired:long`, `crafted_by:string`, `factory_tag:string` |
| `taczind:rig` | 携行具 | L-2.2 | `slots_count:int`, `mags:List<ItemStack>`, `draw_mult:float` |
| `taczind:mag` | 独立弹匣物品 | N-1 | `feed_type:enum(FeedDeviceType)`, `capacity:int`, `loaded:List<taczind:cartridge摘要>`, `mag_spring:PartState`, `device_state:Map<string,string>`(转轮膛位等机构态) |
| `taczind:ammo_stack` | 枪（弹仓镜像） | B-9/N-1 | `queue:List<taczind:cartridge摘要>`, `device:enum(FeedDeviceType)` |
| `taczind:unlocks` | 玩家(PersistentData) | A-7 | `Set<ResourceLocation>` |

## 17.2 方块实体（BlockEntity）NBT 字段表

| BE | 机器 | 字段 |
|---|---|---|
| `CrucibleBE` | 坩埚炉(A-2) | `heat:int`, `contents:ItemStackHandler`, `bellows_boost:boolean` |
| `ManualMachineBE` | 手动机床(A-3) | `progress:float`, `mold:ItemStack`, `output_ts_pending:int` |
| `KineticMachineBE`（Create 软依赖基类） | 动力机器(A-4/M) | `su_need:int`, `rpm_opt_min:int`, `rpm_opt_max:int`, `wear:int`, `energy_stability:float`, `queue:List<ItemStack>` |
| `ChemReactorBE` | 反应釜(A-5) | `recipe_id:ResourceLocation`, `progress:float`, `stirring_rpm:int`, `temp_game:int`, `risk_accum:float`, `tanks:FluidTank[]` |
| `MaintenanceBenchBE` | 保养台(H) | `tools:ItemStackHandler`(4槽), `session_progress:float`（非 tick，操作结算） |
| `StorageBE` | 弹药箱/枪架/携行 | `inv:ItemStackHandler`, `sealed:boolean` |

## 17.3 TACZ 原生字段互操作映射表（不可重定义）

| TACZ 字段（常量源） | 所在 | 本系统用法 |
|---|---|---|
| `GunId` / `GunFireMode` | GunItemDataAccessor | 直读不动 |
| `HasBulletInBarrel` / `GunCurrentAmmoCount` | 同上 | E 状态机旁路镜像的**显示源**；权威=`taczind:cycle`/`ammo_stack` |
| `Attachment` | 同上 | J 章配件挂载（新增 AttachmentType 值走 Q-04 决议路径） |
| `HeatAmount` / `OverHeated` | 同上 | G 章直接读写（复用原生锁射） |
| `DummyAmmo` / `MaxDummyAmmo` | 同上 | N-5 干火训练 |
| `heat{heatMax,heatPerShot,over_heat_time}` | GunData JSON | G 章冷却锚点 |
| `inaccuracy{stand/move/sneak/lie/aim}` | GunData JSON | C-2.4 基础散布查表 |
| `bolt(open/closed/manual)` | GunData JSON | E 状态机+G cook-off 免疫判定 |
| `FeedType(magazine/manual/fuel/inventory)` | GunData JSON | N-1 机构映射父类 |
| `fire_sound{fire_multiplier,silence_multiplier}` | GunData JSON | K-2.1 公式乘数 |
| `recoil`(GunRecoil 关键帧) | GunData JSON | D-2.1 生成器输出目标 |
| `movement_speed{base,aim,reload}` | GunData JSON | D-2.4 重量推导覆写 |

## 17.4 枪包 JSON 增量字段统一格式（嵌入 `GunData`/`AmmoData` 顶层）

```jsonc
{
  // ……TACZ 原生字段原样不动……
  "taczind": {
    "action_type": "GAS_LONG_PISTON",        // D/E
    "feed_device": "BELT",                    // N-1
    "trigger": {"type": "SA", "pull": "STD"}, // N-2
    "ballistic": {"barrel_class": "STD", "gravity_scale": 1.0, "air_density_scale": 1.0}, // C
    "barrel": {"twist": 10, "qcb": true},     // C/G
    "mount_systems": ["picatinny", "m-lok"],  // J
    "noise": {"base_radius": 48, "crack_signature": true}, // K
    "manufacture": {"required_blueprint": "taczind:bp_ak_series", "route": "STAMPED"}, // A
    "maintain": {"fouling_rate": 0.2}         // H
  }
}
```
缺省即旧枪包按上文各章默认值运行——**零迁移承诺**。

## 17.5 状态枚举登记（Java 枚举，`taczind` 包内唯一定义）

| 枚举 | 值 | 章节 |
|---|---|---|
| `GunCycleState` | SAFE, EMPTY_READY, FEEDING, CHAMBERED, FIRING, UNLOCKING, EXTRACTING, EJECTING, COOKING_OFF_RISK, JAM_FTF, JAM_FTEXTRACT, JAM_DOUBLE_FEED, JAM_STOVEPIPE, HANGFIRE_PENDING, DUD_IDENTIFIED, SQUIB_OBSTRUCTED, SLAMFIRE_RUNAWAY, MAINTENANCE_OPEN, BURST_DAMAGED | E-2.1 |
| `BurstSeverity` | MINOR, MODERATE, CATASTROPHIC | F |
| `ActionType` | MANUAL, REVOLVER, BLOWBACK, ROLLER_DELAYED, GAS_DI, GAS_SHORT_PISTON, GAS_LONG_PISTON, RECOIL_SHORT, RECOIL_LONG | D/E/N |
| `FeedDeviceType` | INTERNAL_CLIP, ENBLOC, TUBE, CYLINDER, BOX_MAG, DRUM, PAN, BELT | N-1 |
| `CaseMaterial / PrimerType / PropellantType / BurnRate / ProjectileType / MassClass` | 见 03-B | B |
| `TriggerType(SA/DA/DASA) , PullWeight(LIGHT/STD/HEAVY)` | 见 15-N | N-2 |
| `BarrelClass(SHORT/STD/LONG)` , `TwistClass` | 见 04-C | C |
| `BreathState(CALM/HOLDING/RECOVERING)` | 见 05-D | D |

## 17.6 网络包登记（S2C/C2S，事件驱动禁轮询）

| 包 | 方向 | 载荷 | 触发 |
|---|---|---|---|
| `CycleStateSync` | S2C | gun uuid+state+pending | 状态转移 |
| `MalfunctionAlert` | S2C | 故障类型+提示级别 | ★掷骰失败 |
| `NoiseEventPacket` | S2C | pos+radius+signature | 击发 |
| `ZeroAdjustC2S` | C2S | zero_m | 调零键 |
| `ClearJamC2S / InspectBarrelC2S / ChangeBarrelC2S` | C2S | 动作+手持槽 | E/G 交互键 |

---

# 17.7 【2026-08-01 落地修订】P0 供弹具数据系统（已实现部分以本节为准）

> 对应实现记录：`docs/impl-log/P0-feed-device-data-system.md`。本节为已编码字段的**最终权威**；上方设计节如与本节冲突，以本节为准。

## 17.7.1 已注册 DataComponent 实况（`IndustryComponents`，命名空间 taczind）

| 组件 | 类型 | Codec 键/字段 |
|---|---|---|
| `taczind:feed_device_data` | `FeedDeviceData`（密封多态） | `"feed_system"` 分派六机构 |
| `taczind:gun_state_data` | `GunStateData` | `chambered_round:Optional<LoadedRound>`、`barrel_obstruction:bool`、`obstruction_known:bool` |
| `taczind:loaded_round` | `LoadedRound` | 单发个体完整数据（下详） |

## 17.7.2 LoadedRound 字段实况（record，Codec=RecordCodecBuilder）

| JSON 键 | Java 类型 | 默认 | 说明 |
|---|---|---|---|
| `cartridge` | Identifier | （必填） | →CartridgeRegistry |
| `bullet_type` | Identifier | `taczind:fmj` | →BulletRegistry |
| `case_material` | enum | `brass` | brass/steel/aluminum |
| `case_state` | enum | `factory_new` | factory_new/fired_spent/inspected_ok/cracked/deformed |
| `primer_type` | enum | `boxer` | boxer/berdan |
| `corrosive_primer` | boolean | false | 腐蚀性独立于结构 |
| `charge_deviation` | float | 0 | +超装/-欠装；≤-0.45=Squib 风险档 |

## 17.7.3 FeedSystemType → 数据形状实况（class:`cn.sh1rocu.tacz.industry.api.feed.device`）

| 枚举(序列化名) | record | 独有字段 |
|---|---|---|
| `box_magazine` | BoxMagazineData | rounds:LIFO(栈顶=末位)、spring_fatigue、feed_lip_damage |
| `tubular` | TubularMagazineData | rounds:严格FIFO、spring_fatigue |
| `cylinder` | CylinderData | slots:固定槽位数组【嵌套 CylinderSlot{state:empty/loaded/spent, round?}】、aligned_index |
| `belt` | BeltData | rounds:FIFO、link_type(disintegrating/non_disintegrating)、has_link_tail |
| `stripper_clip` | StripperClipData | rounds、consumed(一次性) |
| `en_bloc` | EnBlocClipData | rounds、ejected(强制整体弹出) |

公共字段（接口层）：`cartridge:Identifier`、`capacity:int`；公共行为：`peekNext/ejectNext/tryLoad`。
**物品规则：承载 feed_device_data 的物品必须 stacksTo(1)（FeedItemRules 断言）。**

## 17.7.4 GunData 增量字段实况（gson POJO，键名已定型）

| JSON 键 | 类型 | 缺省行为 |
|---|---|---|
| `taczind_chambered_cartridge` | Identifier? | null→回退 ammoId（FeedCompatibility.resolveChamberCartridge） |
| `taczind_compatible_feed_device_tag` | Identifier? | null→全兼容（旧枪包语义） |

> 注：设计稿曾计划 `taczind` 顶层嵌套对象；实现期改为**扁平双键**，理由：gson POJO 纯字段追加零侵入、与 TACZ 现有键风格一致、无需嵌套适配器。判定函数在规则层 `FeedCompatibility`（canChamber/acceptsFeedDeviceTag/canLoadFromDevice）。

## 17.7.5 数据驱动注册表（loader：IndustryDataLoader）

| 注册表 | JSON 路径 | 来源优先级 |
|---|---|---|
| CartridgeRegistry | `data/<ns>/cartridge/<name>.json` | 代码默认(12条) ← 数据包覆盖 |
| BulletRegistry | `data/<ns>/bullet/<name>.json` | 代码默认(6条) ← 数据包覆盖 |

## 17.7.6 镜像一致性写入规范（GunStateData ↔ TACZ HasBulletInBarrel）

权威=`taczind:gun_state_data.chambered_round`；`HasBulletInBarrel` 仅作显示/动画镜像。
**任何 chambered 变更必须双写**；读一律读组件。后续刘状枪膛状态（E 章）在此组件上扩展字段，不回头加布尔。
