# 雕像崩溃修复 → 26.1.2 回移植

`statue-fix-26.1.2.patch` 是提交 `a86a1a8`（详见 PR #10）的 format-patch 导出，
针对 `26.1.2` 分支的回移植。**无需任何改动**：已验证以下三文件中，与修复相关的两个
（`StatueBlock.java`、`StatueRenderer.java`）在 `26.1.2` 与 `26.2(main)` 修复前
逐字节一致，补丁已用 `git apply --check` 对 26.1.2 的实际文件内容干跑验证，干净应用、零冲突。

第三个文件 `StatueBlockEntity.java` 在两分支间有差异，但仅为 BlockEntityType 注册写法
（`new BlockEntityType<>(...)` vs Fabric `FabricBlockEntityTypeBuilder`），修复不涉及它。

## 应用方式（在本地 26.1.2 工作区任选其一）

```bash
git fetch origin
git checkout 26.1.2

# 方式一：直接 cherry-pick（推荐，保留完整提交信息）
git cherry-pick a86a1a8

# 方式二：应用补丁文件
git am < backport-26.1.2/statue-fix-26.1.2.patch
# 或：git apply backport-26.1.2/statue-fix-26.1.2.patch
```

应用后建议 `./gradlew build` + 实机验证：持枪对雕像按交互键 O 放枪 →
不再崩溃、枪正常悬浮显示 → 空手右键取回枪。
