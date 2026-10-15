# 弹孔朝向：姊妹仓 26.3 → Fabric 26.2

移植日期：2026-10-15。纳入当前源码版本 `1.1.8+fabric.26.2.R3-hotfix3`，尚未发布。

## 来源与范围

来源为姊妹仓 `q14433686-arch/TaCZ_Renovated` 的 26.3 提交
[`9e2ad58f0a97f0540010a3a63dbe8893369f897f`](https://github.com/q14433686-arch/TaCZ_Renovated/commit/9e2ad58f0a97f0540010a3a63dbe8893369f897f)，
不是本次首创根因分析；该提交在姊妹仓记录为六面数值验证、游戏内待验。

仅移植 `BulletHoleParticle.extract()` 的旋转及位置偏移，不复制 NeoForge 专属代码、26.3 包名、
该提交中无关的 lang 验收状态或其它文档修改。保留本仓库原有颜色衰减、透明度恢复、碰撞移除和粒子分组。

## 本地 26.2 API 核验

直接检查仓库已有的 26.2 merged jar 字节码：

- `Direction.getRotation()` 以局部 +Y 为法线；UP 为单位四元数，DOWN 为 Rx(π)，
  NORTH 为 Rx(π/2)·Rz(π)，SOUTH 为 Rx(π/2)，WEST/EAST 为 Rx(π/2)·Rz(±π/2)。
- `QuadParticleRenderState.renderVertex()` 从 `Vector3f(x, y, 0)` 出发再做四元数旋转。
  `renderRotatedQuad()` 的四角为 `(1,-1,0)`、`(1,1,0)`、`(-1,1,0)`、`(-1,-1,0)`，
  UV 依次为 `(u1,v1)`、`(u1,v0)`、`(u0,v0)`、`(u0,v1)`。
- `SingleQuadParticle.extractRotatedQuad(state, Camera, quaternion, partialTick)` 自行插值位置、
  减去 `Camera.position()`，转为 float 后调用 `(state, quaternion, x, y, z, partialTick)` 重载。
- 六参数重载的末四个 float 是位置与 partialTick，不是 RGBA；颜色从粒子字段获取。
- `getQuadSize(float)` 存在；`Camera.position()` 返回 `Vec3`，其 `x()/y()/z()` 均存在。

另外对照了上游
[1.21.1 BulletHoleParticle.render](https://github.com/Sh1roCu/TACZ-Refabricated/blob/1.21.1/src/main/java/com/tacz/guns/client/particle/BulletHoleParticle.java)：
原 XZ 四角为 `(-1,.01,-1)`、`(-1,.01,1)`、`(1,.01,1)`、`(1,.01,-1)`，UV 顺序与上列对应。
此链接为上游分支链接；回移来源以姊妹仓固定提交为准。

## 实现

```text
rotation = Direction.getRotation() · Rx(-π/2) · Rz(π)
normalOffset = 0.01 × getQuadSize(partialTick)
position = lerp(previous, current, partialTick) − camera + hitNormal × normalOffset
```

右乘顺序不能反转。Rx(-π/2) 把 XY 四边形的 +Z 转为 +Y，Direction 随后将其转到命中面法线；
Rz(π) 保留旧 XZ 四边形四角的 UV 对应，而不是只保证法线正确。
位置计算沿用 Camera 重载的插值及相机相对算法，只额外加入法向偏移。
不改变粒子的真实世界坐标或碰撞检测位置，不引入朝向摄像机的 billboard。

## 验证与边界

```bash
python3 scripts/verify_bullet_hole_orientation.py
python3 scripts/verify_recipe_viewer_reload.py
bash scripts/check_release_consistency.sh --strict
```

弹孔脚本使用 Python 标准库模拟 JOML 四元数右乘，校验：

1. 六面旋转后的法线都等于命中面法线，旧未修正旋转在六面均不符合。
2. 三种尺寸下，每个 UV 对应的角点及法向偏移都与旧 XZ 实现一致。
3. 两个相机位置、三个 partialTick 下，带偏移的相机相对四角与旧实现一致。
4. Java 源码确实使用该旋转、逐轴插值和坐标重载，而非旧 Camera 调用或 RGBA 误传。
5. `finally` 仍恢复基色与 alpha，避免颜色累计衰减。

**这只是离线数学模型和源码结构检查，不是 Java/JOML 执行、编译或游戏渲染测试。**
本地没有可用 JDK，未完成本次 Java 编译；之前 JEI 修复的远端编译记录不覆盖本次弹孔修改。

待实机验收：关闭光影时分别射击六个方块面，移动/转动相机确认贴面且不跟随镜头；
再开启 Iris 光影检查墙、地面、天花板的朝向、可见性、z-fighting 和 UV；
检查远近距离、渐黑/淡出、击碎附着方块后移除。数值模型通过不能代替这些验收。
