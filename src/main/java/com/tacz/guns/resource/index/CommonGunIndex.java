package com.tacz.guns.resource.index;

import com.google.common.base.Preconditions;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.GunIndexPOJO;
import com.tacz.guns.resource.pojo.data.gun.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.StringUtils;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Arrays;
import java.util.Map;

public class CommonGunIndex {
    private static final Marker MARKER = MarkerFactory.getMarker("CommonGunIndex");
    private GunData gunData;
    private String type;
    private GunIndexPOJO pojo;
    private int sort;
    private LuaTable script;
    private LuaTable scriptParam;

    private CommonGunIndex() {
    }

    public static CommonGunIndex getInstance(GunIndexPOJO gunIndexPOJO) throws IllegalArgumentException {
        CommonGunIndex index = new CommonGunIndex();
        index.pojo = gunIndexPOJO;
        checkIndex(gunIndexPOJO, index);
        checkData(gunIndexPOJO, index);
        return index;
    }

    private static void checkIndex(GunIndexPOJO gunIndexPOJO, CommonGunIndex index) {
        Preconditions.checkArgument(gunIndexPOJO != null, "index object file is empty");
        Preconditions.checkArgument(StringUtils.isNoneBlank(gunIndexPOJO.getType()), "index object missing type field");
        index.type = gunIndexPOJO.getType();
        index.sort = Mth.clamp(gunIndexPOJO.getSort(), 0, 65536);
    }

    private static void checkData(GunIndexPOJO gunIndexPOJO, CommonGunIndex index) {
        Identifier pojoData = gunIndexPOJO.getData();
        Preconditions.checkArgument(pojoData != null, "index object missing pojoData field");
        GunData data = CommonAssetsManager.get().getGunData(pojoData);
        Preconditions.checkArgument(data != null, "there is no corresponding data file");
        Preconditions.checkArgument(data.getAmmoId() != null, "ammo id is empty");
        Preconditions.checkArgument(data.getAmmoAmount() >= 1, "ammo count must >= 1");
        int[] extendedMagAmmoAmount = data.getExtendedMagAmmoAmount();
        Preconditions.checkArgument(extendedMagAmmoAmount == null || extendedMagAmmoAmount.length >= 3, "extended_mag_ammo_amount size must is 3");
        Preconditions.checkArgument(data.getRoundsPerMinute() >= 1, "rpm count must >= 1");
        Preconditions.checkArgument(data.getBolt() != null, "bolt type is error");
        Preconditions.checkArgument(data.getReloadData().getType() != null, "reload type is error");
        Preconditions.checkArgument(!data.getFireModeSet().isEmpty(), "fire mode is empty");
        Preconditions.checkArgument(!data.getFireModeSet().contains(null) && !data.getFireModeSet().contains(FireMode.UNKNOWN), "fire mode is error");
        checkInaccuracy(data);
        checkRecoil(data);
        checkScript(data, index);
        // P2弹道扩展：验证新增字段
        checkBallisticExtensionFields(data);
        index.gunData = data;
    }

    private static void checkInaccuracy(GunData data) {
        Map<InaccuracyType, Float> defaultInaccuracy = InaccuracyType.getDefaultInaccuracy();
        Map<InaccuracyType, Float> readInaccuracy = data.getInaccuracy();
        if (readInaccuracy == null || readInaccuracy.isEmpty()) {
            data.setInaccuracy(defaultInaccuracy);
        } else {
            defaultInaccuracy.forEach(readInaccuracy::putIfAbsent);
        }
    }

    private static void checkRecoil(GunData data) {
        GunRecoil recoil = data.getRecoil();
        GunRecoilKeyFrame[] pitch = recoil.getPitch();
        GunRecoilKeyFrame[] yaw = recoil.getYaw();
        if (pitch != null) {
            for (GunRecoilKeyFrame keyFrame : pitch) {
                float[] value = keyFrame.getValue();
                Preconditions.checkArgument(value.length == 2, "Recoil value's length must be 2");
                Preconditions.checkArgument(value[0] <= value[1], "Recoil value's left must be less than right");
                Preconditions.checkArgument(keyFrame.getTime() >= 0, "Recoil time must be more than 0");
            }
            Arrays.sort(pitch);
        }

        if (yaw != null) {
            for (GunRecoilKeyFrame keyFrame : yaw) {
                float[] value = keyFrame.getValue();
                Preconditions.checkArgument(value.length == 2, "Recoil value's length must be 2");
                Preconditions.checkArgument(value[0] <= value[1], "Recoil value's left must be less than right");
                Preconditions.checkArgument(keyFrame.getTime() >= 0, "Recoil time must be more than 0");
            }
            Arrays.sort(yaw);
        }
    }

    private static void checkScript(GunData data, CommonGunIndex index) {
        // 加载脚本
        Identifier scriptId = data.getScript();
        CommonAssetsManager commonAssetsManager = CommonAssetsManager.getInstance();
        if (scriptId != null && commonAssetsManager != null) {
            index.script = commonAssetsManager.getScript(scriptId);
            if (index.script == null) {
                GunMod.LOGGER.warn(MARKER, "script '{}' not found", scriptId);
            }
        }
        // 加载脚本参数
        Map<String, Object> params = data.getScriptParam();
        if (params != null) {
            index.scriptParam = new LuaTable();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                index.scriptParam.set(entry.getKey(), CoerceJavaToLua.coerce(entry.getValue()));
            }
        }
    }

    /**
     * P2弹道扩展 + P3过热扩展：验证新增 GunData/BulletData/GunHeatData 字段的合法性。
     * <p>
     * 所有新增字段均有合理的默认值，因此验证以警告而非异常为主：
     * <ul>
     *   <li>barrel_length ≥ 0（0 表示不参与弹道修正）</li>
     *   <li>twist_rate ≥ 0（0 表示使用 Greenhill 推荐缠距）</li>
     *   <li>barrel_material 为有效值或 null</li>
     *   <li>bullet_length ≥ 0</li>
     *   <li>bullet_diameter ≥ 0</li>
     *   <li>ballistic_coefficient > 0</li>
     *   <li>optimal_barrel_length ≥ 0</li>
     *   <li>erosion_per_shot ≥ 0</li>
     *   <li>cookoff_threshold ∈ [0, 1]</li>
     *   <li>environment_cooling_modifier > 0</li>
     *   <li>caliber_heat_modifier > 0</li>
     *   <li>catastrophic_heat_threshold ∈ [0, 1]</li>
     * </ul>
     */
    private static void checkBallisticExtensionFields(GunData data) {
        // GunData 字段验证
        if (data.getBarrelLength() < 0) {
            GunMod.LOGGER.warn(MARKER, "GunData barrel_length must >= 0, got {}", data.getBarrelLength());
        }
        if (data.getTwistRate() < 0) {
            GunMod.LOGGER.warn(MARKER, "GunData twist_rate must >= 0, got {}", data.getTwistRate());
        }
        String barrelMaterial = data.getBarrelMaterial();
        if (barrelMaterial != null) {
            Preconditions.checkArgument(
                    barrelMaterial.equals("wrought_iron")
                    || barrelMaterial.equals("carbon_steel")
                    || barrelMaterial.equals("alloy_steel")
                    || barrelMaterial.equals("ordnance_steel"),
                    "GunData barrel_material must be one of: wrought_iron, carbon_steel, alloy_steel, ordnance_steel. Got: %s",
                    barrelMaterial
            );
        }

        // BulletData 字段验证
        BulletData bulletData = data.getBulletData();
        if (bulletData.getBulletLength() < 0) {
            GunMod.LOGGER.warn(MARKER, "BulletData bullet_length must >= 0, got {}", bulletData.getBulletLength());
        }
        if (bulletData.getBulletDiameter() < 0) {
            GunMod.LOGGER.warn(MARKER, "BulletData bullet_diameter must >= 0, got {}", bulletData.getBulletDiameter());
        }
        if (bulletData.getBallisticCoefficient() <= 0) {
            GunMod.LOGGER.warn(MARKER, "BulletData ballistic_coefficient must > 0, got {}", bulletData.getBallisticCoefficient());
        }
        if (bulletData.getOptimalBarrelLength() < 0) {
            GunMod.LOGGER.warn(MARKER, "BulletData optimal_barrel_length must >= 0, got {}", bulletData.getOptimalBarrelLength());
        }

        // GunHeatData P3扩展字段验证
        GunHeatData heatData = data.getHeatData();
        if (heatData != null) {
            if (heatData.getErosionPerShot() < 0) {
                GunMod.LOGGER.warn(MARKER, "GunHeatData erosion_per_shot must >= 0, got {}", heatData.getErosionPerShot());
            }
            if (heatData.getCookoffThreshold() < 0 || heatData.getCookoffThreshold() > 1) {
                GunMod.LOGGER.warn(MARKER, "GunHeatData cookoff_threshold must be in [0, 1], got {}", heatData.getCookoffThreshold());
            }
            if (heatData.getEnvironmentCoolingModifier() <= 0) {
                GunMod.LOGGER.warn(MARKER, "GunHeatData environment_cooling_modifier must > 0, got {}", heatData.getEnvironmentCoolingModifier());
            }
            if (heatData.getCaliberHeatModifier() <= 0) {
                GunMod.LOGGER.warn(MARKER, "GunHeatData caliber_heat_modifier must > 0, got {}", heatData.getCaliberHeatModifier());
            }
            if (heatData.getCatastrophicHeatThreshold() < 0 || heatData.getCatastrophicHeatThreshold() > 1) {
                GunMod.LOGGER.warn(MARKER, "GunHeatData catastrophic_heat_threshold must be in [0, 1], got {}", heatData.getCatastrophicHeatThreshold());
            }
            // 逻辑一致性：cookoff_threshold > catastrophic_heat_threshold
            if (heatData.getCookoffThreshold() < heatData.getCatastrophicHeatThreshold()) {
                GunMod.LOGGER.warn(MARKER, "GunHeatData cookoff_threshold ({}) should be >= catastrophic_heat_threshold ({})",
                        heatData.getCookoffThreshold(), heatData.getCatastrophicHeatThreshold());
            }
        }
    }

    public GunData getGunData() {
        return gunData;
    }

    public BulletData getBulletData() {
        return gunData.getBulletData();
    }

    public String getType() {
        return type;
    }

    public GunIndexPOJO getPojo() {
        return pojo;
    }

    public LuaTable getScript() {
        return script;
    }

    public LuaTable getScriptParam() {
        return scriptParam;
    }

    public int getSort() {
        return sort;
    }
}
