# TACZ 26.2 修复日志 - 阶段1（关键崩溃修复）

## 修复时间
2026-07-25

## 修复的BUG

### 1. ✅ 打开工作方块时的网络协议错误（Critical - 被踢出服务器）

**问题描述：**
- 玩家右键点击枪械工作台（Gun Smith Table）时立即被踢出服务器
- 错误信息：网络协议错误

**根本原因：**
- `GunSmithTableBlockEntity` 实现了 `ExtendedMenuProvider<Identifier>`，需要传递额外的 `Identifier` 数据给客户端
- `AbstractGunSmithTableBlock.java:46-47` 错误地将其转换为普通 `MenuProvider` 类型后调用 `openMenu()`
- 这导致 Fabric 网络层无法正确序列化/反序列化额外数据，触发协议不匹配

**修复方案：**
```java
// 修复前（错误）：
if (gunSmithTable instanceof MenuProvider menuProvider) {
    serverPlayer.openMenu(menuProvider);
}

// 修复后（正确）：
// 直接传递 GunSmithTableBlockEntity，保留 ExtendedMenuProvider<Identifier> 类型
serverPlayer.openMenu(gunSmithTable);
```

**文件修改：**
- `src/main/java/com/tacz/guns/block/AbstractGunSmithTableBlock.java`
  - 行46-48：移除错误的类型转换，直接传递 `gunSmithTable`
  - 行12：移除不必要的 `MenuProvider` import

---

### 2. ✅ 激光配件数值调节导致游戏崩溃（Critical）

**问题描述：**
- 在枪械改装界面中调节激光瞄准器的颜色/数值时游戏崩溃
- 崩溃发生在网络包解析阶段

**根本原因：**
- `ClientMessageLaserColor.java:36` 的 lambda 表达式参数引用错误
- `buf.readMap(buf1 -> buf.readEnum(...))` 中，lambda 参数名为 `buf1`，但函数体内却引用了外层作用域的 `buf`
- 这导致在解析 Map 的键时读取了错误的缓冲区位置，造成数据错位和崩溃

**修复方案：**
```java
// 修复前（错误）：
this.colorMap.putAll(buf.readMap(buf1 -> buf.readEnum(AttachmentType.class), FriendlyByteBuf::readInt));
//                                  ↑参数名     ↑错误引用外层buf

// 修复后（正确）：
this.colorMap.putAll(buf.readMap(buf1 -> buf1.readEnum(AttachmentType.class), FriendlyByteBuf::readInt));
//                                  ↑参数名     ↑正确引用lambda参数buf1
```

**文件修改：**
- `src/main/java/com/tacz/guns/network/message/ClientMessageLaserColor.java`
  - 行36：修正lambda参数引用，从 `buf.readEnum()` 改为 `buf1.readEnum()`

---

## 验证结果

### 构建测试
- ✅ `gradlew clean compileJava` - **通过**
- ✅ `gradlew build` - **通过**
- 生成的JAR文件：
  - `TACZ-Refabricated-26.2-0.0.0-26.2-audit.jar` (57,207,099 字节)
  - `TACZ-Refabricated-26.2-0.0.0-26.2-audit-sources.jar` (53,128,202 字节)

### 代码审查
- ✅ 激光颜色包的序列化/反序列化逻辑现在一致
- ✅ 工作台菜单现在使用正确的 Fabric API 扩展接口
- ✅ 没有引入新的编译错误或警告

---

## 下一步（阶段2 - 渲染问题）

根据用户报告的BUG列表，接下来需要修复：

### 高优先级（影响基础功能）
1. **第一人称看不见枪械模型**
   - 只在第三人称可见
   - 可能涉及 `TaczDynamicItemModel`、`AnimateGeoItemRenderer`

2. **持枪时手臂消失**
   - 第三人称观察发现手臂抬升有问题
   - 可能涉及 `ItemInHandLayer`、`HumanoidOffhandRender`

3. **配件无功能、无贴图、无模型**
   - 物品存在但是空白
   - 可能涉及 `AttachmentRender`、资源索引

### 中优先级（视觉效果）
4. 工作方块虽然能打开但无实际合成功能（需实机测试协议修复是否解决）
5. 枪械模型和配件物品栏贴图不可见（空白而非紫黑块）

---

## 技术笔记

### 26.2 网络包系统变化
- Fabric API 在 26.2 中使用 `StreamCodec` 和 `CustomPacketPayload`
- `ExtendedMenuProvider<T>` 需要配合 `ExtendedMenuType<M, T>` 使用
- 不能将 `ExtendedMenuProvider` 降级为普通 `MenuProvider`，否则额外数据会丢失

### Lambda 作用域陷阱
- Java lambda 可以捕获外层作用域的变量
- 当 lambda 参数与外层变量同名或相似时，容易误用
- 本次BUG 就是典型的"参数名与逻辑不符"导致的运行时错误

---

## 待验证项（需实机测试）

以下项目在当前修复后需要在真实客户端-服务器环境中验证：

- [ ] 打开枪械工作台不再踢出玩家
- [ ] 工作台GUI能正常显示配方列表
- [ ] 工作台合成功能正常（消耗材料、产出物品）
- [ ] 调节激光瞄准器颜色/数值不再崩溃
- [ ] 激光颜色更改能正确同步到服务器和其他玩家

**测试环境要求：**
- Minecraft 26.2 + Fabric Loader 0.19.3 + Fabric API 0.155.2+26.2
- 真实GPU环境（非软件渲染）
- 双端联机测试（服务器+客户端）
