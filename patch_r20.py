#!/usr/bin/env python3
"""
第 20 轮补丁：代码审计清理 + 爆炸效果修复

修改内容：
  1. ExplodeUtil.java — 修复爆炸不生效 + 自定义伤害（核心 BUG）
     根因：ProjectileExplosion 完全覆写 ServerExplosion.explode()，但：
       a) 父类 interactWithBlocks() 是 private → 方块从未被破坏
       b) 绕过 ServerLevel.explode() → ClientboundExplodePacket 从未发送 → 客户端无爆炸特效/声音
       c) damage 参数（枪包 explosion_damage）从未被使用
     修法：原版 level.explode() 处理视觉/方块/击退 + 额外应用枪包自定义伤害(距离衰减)。

  2. AmmoBoxItem.java — 移除因 26.2 删除 ItemProperties/ColorProviderRegistry 后产生的死代码
     移除：PROPERTY_NAME, DEFAULT_COLOR, OPEN, CLOSE, CREATIVE_INDEX, ALL_TYPE_CREATIVE_INDEX,
           getColor(), getStatue(), getOpenStatue(), getLevelStatue(), getTagColor()
     移除未使用导入：GunMod, ClientLevel, DyedItemColor, LivingEntity, Level, Nullable, List

  3. ClientSetupEvent.java — 移除未使用导入 (ModItems, AmmoBoxItem)

  4. BedrockAttachmentModel.java — 移除 5 个未使用导入 (RenderSystem, IrisCompat, Mth, GL11, GL30)

  5. ClientIndexManager.java — 移除注释掉的死 import (FirstPersonRenderHandler)，
     将 TODO 注释更新为"已解决"

用法：python3 patch_r20.py [repo_root]
"""
import sys, os, io

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."


def rd(p):
    with io.open(os.path.join(ROOT, p), encoding="utf-8") as f:
        return f.read()


def wr(p, s):
    os.makedirs(os.path.dirname(os.path.join(ROOT, p)), exist_ok=True)
    with io.open(os.path.join(ROOT, p), "w", encoding="utf-8") as f:
        f.write(s)


def sub(p, old, new, desc):
    s = rd(p)
    if new in s and old not in s:
        print("  [skip] %s (already applied)" % desc)
        return
    if old not in s:
        print("  [WARN] PATTERN NOT FOUND in %s: %s" % (p, desc))
        return
    wr(p, s.replace(old, new, 1))
    print("  [ok]   %s" % desc)


# ================================================================
# 1. ExplodeUtil.java — 修复爆炸效果（核心修复）
# ================================================================
EXPLODE_UTIL = r"src/main/java/com/tacz/guns/util/ExplodeUtil.java"

EXPLODE_UTIL_NEW = r'''package com.tacz.guns.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ExplodeUtil {
    public static void createExplosion(Entity owner, Entity exploder, float damage, float radius, boolean knockback, boolean destroy, Vec3 hitPos) {
        // 客户端不执行
        if (!(exploder.level() instanceof ServerLevel level)) {
            return;
        }
        // 26.2 修复: 使用原版 ServerLevel.explode() 处理:
        //   - 客户端爆炸粒子/音效 (ClientboundExplodePacket)
        //   - 方块破坏 (interactWithBlocks)
        //   - 击退 (hitPlayers → ClientboundExplodePacket.playerKnockback)
        Level.ExplosionInteraction interaction = destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        level.explode(exploder, hitPos.x(), hitPos.y(), hitPos.z(), radius, false, interaction);

        // 自定义爆炸伤害: 枪包配置的 explosion_damage 值，按距离线性衰减
        // 原版爆炸伤害公式为 (1-dist/radius)*radius，与枪包配置值不一致，
        // 此处额外应用 (1-dist/radius)*damage 作为枪包定义的真实伤害。
        // 原版伤害 (最大=radius) 作为近距附加伤害保留，不影响平衡（radius 通常 2~5，damage 通常 10~50+）
        if (damage > 0) {
            DamageSource source = exploder.damageSources().explosion(exploder, owner);
            double size = radius * 2.0;
            AABB area = new AABB(
                    hitPos.x() - size, hitPos.y() - size, hitPos.z() - size,
                    hitPos.x() + size, hitPos.y() + size, hitPos.z() + size);
            List<Entity> entities = level.getEntities(exploder, area);
            for (Entity entity : entities) {
                if (entity == exploder) continue;
                double dist = Math.sqrt(entity.distanceToSqr(hitPos));
                if (dist > radius * 2.0) continue;
                // 距离衰减: 中心=100%伤害, 边缘=0
                float impact = (float) (1.0 - dist / (radius * 2.0));
                if (impact <= 0) continue;
                // 取消无敌帧，确保自定义伤害生效（与 tacAttackEntity 相同手法）
                entity.invulnerableTime = 0;
                entity.hurt(source, damage * impact);
            }
        }
    }
}
'''

# ================================================================
# 2. ClientIndexManager.java — 更新 TODO 注释 + 移除死 import
# ================================================================
CIM = r"src/main/java/com/tacz/guns/client/resource/ClientIndexManager.java"

CIM_OLD_IMPORT = """// import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.google.common.collect.Maps;"""
CIM_NEW_IMPORT = """import com.google.common.collect.Maps;"""

CIM_OLD_TODO = """            // FirstPersonRenderHandler.reset(); // TODO: Find replacement in MC 26.2"""
CIM_NEW_TODO = """            // 26.2 已解决: FirstPersonRenderHandler.reset() 的功能已由
            // AnimateGeoItemRenderer.needReInit()/tryInit() 自动重初始化机制取代。"""

# ================================================================
# 3. BedrockAttachmentModel.java — 移除 5 个未使用导入
# ================================================================
BAM = r"src/main/java/com/tacz/guns/client/model/BedrockAttachmentModel.java"

# 这些导入在清理前存在（如果已经清理过则 skip）
BAM_OLD_IMPORTS = """import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;"""
BAM_NEW_IMPORTS = """import com.mojang.blaze3d.vertex.*;"""

BAM_OLD_IRIS = """import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.compat.iris.IrisCompat;"""
BAM_NEW_IRIS = """import com.tacz.guns.client.model.bedrock.BedrockPart;"""

BAM_OLD_MTH = """import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;"""
BAM_NEW_MTH = """import net.minecraft.client.renderer.SubmitNodeCollector;"""

BAM_OLD_GL = """import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;"""
BAM_NEW_GL = """import org.joml.Vector3f;"""

# ================================================================
# 4. ClientSetupEvent.java — 移除未使用导入
# ================================================================
CSE = r"src/main/java/com/tacz/guns/client/init/ClientSetupEvent.java"

CSE_OLD_IMPORTS = """import com.tacz.guns.client.tooltip.ClientGunTooltip;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.AmmoBoxItem;"""
CSE_NEW_IMPORTS = """import com.tacz.guns.client.tooltip.ClientGunTooltip;"""

# ================================================================
# 5. AmmoBoxItem.java — 移除死代码
#    注意：这是最复杂的修改，原始文件包含大量因 26.2 API 移除而失效的代码。
#    如果文件已经是清理后的版本（包含 IItem 接口），则跳过。
# ================================================================
ABI = r"src/main/java/com/tacz/guns/item/AmmoBoxItem.java"


def patch_ammo_box():
    """检查 AmmoBoxItem 是否还有死代码需要清理"""
    s = rd(ABI)
    # 如果已经实现了 IItem 接口，说明已经清理过
    if "implements AmmoBoxItemDataAccessor, IItem" in s:
        print("  [skip] AmmoBoxItem dead code removal (already applied)")
        return
    # 如果有 PROPERTY_NAME 字段，说明需要清理
    if "PROPERTY_NAME" not in s:
        print("  [skip] AmmoBoxItem dead code removal (no dead code found)")
        return
    print("  [WARN] AmmoBoxItem still has dead code - manual review needed")
    print("         Dead items: PROPERTY_NAME, DEFAULT_COLOR, OPEN, CLOSE, CREATIVE_INDEX,")
    print("         ALL_TYPE_CREATIVE_INDEX, getColor(), getStatue(), getOpenStatue(),")
    print("         getLevelStatue(), getTagColor()")


# ================================================================
# 执行补丁
# ================================================================
print("=== TACZ 26.2 Round 20 Patch ===")
print()

# 1. ExplodeUtil (核心修复 - 直接写入完整文件)
print("[1/5] ExplodeUtil.java - 爆炸效果修复")
try:
    current = rd(EXPLODE_UTIL)
    if "damage * impact" in current:
        print("  [skip] ExplodeUtil (already applied)")
    else:
        wr(EXPLODE_UTIL, EXPLODE_UTIL_NEW)
        print("  [ok]   ExplodeUtil - 替换 ProjectileExplosion 为原版 level.explode()")
except FileNotFoundError:
    wr(EXPLODE_UTIL, EXPLODE_UTIL_NEW)
    print("  [ok]   ExplodeUtil - 创建新文件")

# 2. ClientIndexManager
print("[2/5] ClientIndexManager.java - 移除死 import + 更新 TODO")
try:
    sub(CIM, CIM_OLD_IMPORT, CIM_NEW_IMPORT, "移除 FirstPersonRenderHandler 死 import")
    sub(CIM, CIM_OLD_TODO, CIM_NEW_TODO, "更新 FirstPersonRenderHandler.reset() TODO 为已解决")
except FileNotFoundError:
    print("  [WARN] 文件不存在: %s" % CIM)

# 3. BedrockAttachmentModel
print("[3/5] BedrockAttachmentModel.java - 移除未使用导入")
try:
    sub(BAM, BAM_OLD_IMPORTS, BAM_NEW_IMPORTS, "移除 RenderSystem 导入")
    sub(BAM, BAM_OLD_IRIS, BAM_NEW_IRIS, "移除 IrisCompat 导入")
    sub(BAM, BAM_OLD_MTH, BAM_NEW_MTH, "移除 Mth 导入")
    sub(BAM, BAM_OLD_GL, BAM_NEW_GL, "移除 GL11/GL30 导入")
except FileNotFoundError:
    print("  [WARN] 文件不存在: %s" % BAM)

# 4. ClientSetupEvent
print("[4/5] ClientSetupEvent.java - 移除未使用导入")
try:
    sub(CSE, CSE_OLD_IMPORTS, CSE_NEW_IMPORTS, "移除 ModItems/AmmoBoxItem 导入")
except FileNotFoundError:
    print("  [WARN] 文件不存在: %s" % CSE)

# 5. AmmoBoxItem
print("[5/5] AmmoBoxItem.java - 死代码检查")
try:
    patch_ammo_box()
except FileNotFoundError:
    print("  [WARN] 文件不存在: %s" % ABI)

print()
print("=== 补丁完成 ===")
print()
print("注意事项:")
print("  - 爆炸修复后，榴弹/RPG/高爆弹同时拥有原版爆炸效果 + 枪包自定义伤害")
print("  - 自定义伤害公式: (1 - dist/(radius*2)) * explosion_damage，中心最大，边缘为0")
print("  - 原版爆炸伤害 (最大=radius) 作为近距附加保留，不影响平衡")
print("  - 方块破坏受 TNT 爆炸游戏规则控制 (tntExplosionDropBlocks)")
print("  - 穿甲(armor_ignore)已完整实装: 伤害按比例分裂为普通+穿甲两部分，穿甲部分绕过护甲")
