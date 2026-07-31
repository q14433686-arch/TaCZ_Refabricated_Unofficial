package com.tacz.guns.resource.pojo.data.gun;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.enums.ActionType;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.RpmModifier;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class GunData {
    @SerializedName("ammo")
    private Identifier ammoId = null;

    @SerializedName("ammo_amount")
    private int ammoAmount = 30;

    @SerializedName("extended_mag_ammo_amount")
    private int @Nullable [] extendedMagAmmoAmount = null;

    @SerializedName("can_crawl")
    private boolean canCrawl = true;

    @SerializedName("can_slide")
    private boolean canSlide = true;

    @SerializedName("bolt")
    private Bolt bolt = Bolt.OPEN_BOLT;

    /**
     * P0扩展：自动原理类型。
     * 如果JSON中指定了action_type，则使用此字段替代bolt字段。
     * 向后兼容：如果未指定action_type，则从bolt字段自动映射。
     */
    @SerializedName("action_type")
    @Nullable
    private com.tacz.guns.api.item.enums.ActionType actionType = null;

    /**
     * P0补充：枪膛口径规格。
     * <p>
     * 引用 {@link com.tacz.guns.api.item.cartridge.CartridgeTypeManager} 中注册的口径类型 ID。
     * 决定此枪可以接受哪些口径的弹药。
     * <p>
     * 例如：tacz:9mm、tacz:556_nato、tacz:762x39 等。
     * 如果为 null，则使用旧逻辑（通过 ammoId 匹配）。
     */
    @SerializedName("chambered_cartridge")
    @Nullable
    private Identifier chamberedCartridge = null;

    /**
     * P0补充：兼容的供弹具型号标签。
     * <p>
     * 用于标识哪些供弹具（弹匣/弹链/弹鼓等）可以装在此枪上。
     * 供弹具物品通过此标签进行匹配。
     * <p>
     * 例如：tacz:ak47_compatible_feeds、tacz:ar15_compatible_feeds 等。
     * 如果为 null，则使用旧逻辑（通过 ammoId 匹配）。
     */
    @SerializedName("compatible_feed_device_tag")
    @Nullable
    private Identifier compatibleFeedDeviceTag = null;

    @SerializedName("rpm")
    private int roundsPerMinute = 300;

    @SerializedName("bullet")
    private BulletData bulletData = new BulletData();

    @SerializedName("draw_time")
    private float drawTime = 0.4f;

    @SerializedName("put_away_time")
    private float putAwayTime = 0.4f;

    @SerializedName("sprint_time")
    private float sprintTime = 0.2f;

    @SerializedName("aim_time")
    private float aimTime = 0.2f;

    @SerializedName("bolt_action_time")
    private float boltActionTime = 0;

    @SerializedName("bolt_feed_time")
    private float boltFeedTime = -1;

    @SerializedName("fire_sound")
    private FireSound fireSound = new FireSound();

    @SerializedName("reload")
    private GunReloadData reloadData = new GunReloadData();

    @SerializedName("fire_mode")
    private List<FireMode> fireModeSet = Collections.singletonList(FireMode.UNKNOWN);

    @SerializedName("fire_mode_adjust")
    private EnumMap<FireMode, GunFireModeAdjustData> fireModeAdjust = Maps.newEnumMap(FireMode.class);

    @SerializedName("burst_data")
    private BurstData burstData = new BurstData();

    @SerializedName("crawl_recoil_multiplier")
    private float crawlRecoilMultiplier = 0.5f;

    @SerializedName("recoil")
    private GunRecoil recoil = new GunRecoil();

    @SerializedName("hurt_bob_tweak_multiplier")
    private float hurtBobTweakMultiplier = 0.05f;

    @SerializedName("inaccuracy")
    private Map<InaccuracyType, Float> inaccuracy = null;

    @SerializedName("movement_speed")
    private MoveSpeed moveSpeed = new MoveSpeed();

    @SerializedName("melee")
    private GunMeleeData gunMeleeData = new GunMeleeData();

    @SerializedName("heat")
    @Nullable
    private GunHeatData gunHeatData = null;

    @SerializedName("allow_attachment_types")
    private List<AttachmentType> allowAttachments = Lists.newArrayList();

    @SerializedName("exclusive_attachments")
    private Map<Identifier, AttachmentData> exclusiveAttachments = Maps.newHashMap();

    @SerializedName("weight")
    private float weight = 0f;

    @SerializedName("builtin_attachments")
    private Map<AttachmentType, Identifier> builtInAttachments = Maps.newHashMap();

    @SerializedName("script")
    private Identifier script = null;

    @SerializedName("script_param")
    private Map<String, Object> scriptParam = null;

    @SerializedName("charging")
    private EnumMap<FireMode, ChargeData> chargeData = null;

    public ChargeData getChargeData(FireMode fireMode) {
        if (chargeData != null) {
            return chargeData.get(fireMode);
        }
        return null;
    }

    public Identifier getAmmoId() {
        return ammoId;
    }

    public int getAmmoAmount() {
        return ammoAmount;
    }

    public int @Nullable [] getExtendedMagAmmoAmount() {
        return extendedMagAmmoAmount;
    }

    public boolean isCanCrawl() {
        return canCrawl;
    }

    public boolean canSlide() {
        return canSlide;
    }

    public Bolt getBolt() {
        return bolt;
    }

    /**
     * 获取自动原理类型。
     * 如果JSON中指定了action_type，则使用它；否则从bolt字段自动映射。
     */
    public ActionType getActionType() {
        if (actionType != null) {
            return actionType;
        }
        return ActionType.fromBolt(bolt);
    }

    /**
     * 获取枪膛口径规格。
     * <p>
     * P0补充：用于口径兼容性判定。
     * 如果为 null，则使用旧逻辑（通过 ammoId 匹配）。
     */
    @Nullable
    public Identifier getChamberedCartridge() {
        return chamberedCartridge;
    }

    /**
     * 获取兼容的供弹具型号标签。
     * <p>
     * P0补充：用于供弹具兼容性判定。
     * 如果为 null，则使用旧逻辑（通过 ammoId 匹配）。
     */
    @Nullable
    public Identifier getCompatibleFeedDeviceTag() {
        return compatibleFeedDeviceTag;
    }

    /**
     * 判断指定口径是否与枪膛兼容。
     * <p>
     * P0补充：口径/型号兼容性判定函数。
     * <ol>
     *   <li>如果枪未指定 chamberedCartridge，使用旧逻辑（直接返回 true）</li>
     *   <li>如果枪指定了 chamberedCartridge，使用 {@link com.tacz.guns.api.item.cartridge.CartridgeTypeManager#isCompatible}
     *       判断口径兼容性</li>
     * </ol>
     *
     * @param cartridgeType 弹药口径标识符
     * @return 是否兼容
     */
    public boolean isCartridgeCompatible(@Nullable Identifier cartridgeType) {
        if (cartridgeType == null) return false;
        if (chamberedCartridge == null) return true;  // 旧逻辑兼容
        return com.tacz.guns.api.item.cartridge.CartridgeTypeManager.isCompatible(chamberedCartridge, cartridgeType);
    }

    /**
     * 判断指定供弹具是否与枪兼容。
     * <p>
     * P0补充：供弹具兼容性判定函数。
     * <ol>
     *   <li>如果枪未指定 compatibleFeedDeviceTag，使用旧逻辑（直接返回 true）</li>
     *   <li>如果枪指定了 compatibleFeedDeviceTag，需要供弹具的标签匹配</li>
     * </ol>
     * <p>
     * 注意：此方法仅检查标签匹配，还需配合 {@link #isCartridgeCompatible(Identifier)}
     * 检查口径匹配。
     *
     * @param feedDeviceTag 供弹具的型号标签
     * @return 是否兼容
     */
    public boolean isFeedDeviceCompatible(@Nullable Identifier feedDeviceTag) {
        if (compatibleFeedDeviceTag == null) return true;  // 旧逻辑兼容
        if (feedDeviceTag == null) return false;
        return compatibleFeedDeviceTag.equals(feedDeviceTag);
    }

    /**
     * 综合判定：弹药口径 + 供弹具型号是否都与枪兼容。
     * <p>
     * P0补充：作为后续"装弹"交互逻辑的判定基础。
     *
     * @param cartridgeType 弹药口径标识符
     * @param feedDeviceTag 供弹具型号标签
     * @return 是否全部兼容
     */
    public boolean isFullyCompatible(@Nullable Identifier cartridgeType, @Nullable Identifier feedDeviceTag) {
        return isCartridgeCompatible(cartridgeType) && isFeedDeviceCompatible(feedDeviceTag);
    }

    @ApiStatus.Internal
    public int getRoundsPerMinute() {
        return roundsPerMinute;
    }

    public int getRoundsPerMinute(FireMode fireMode) {
        int rpm = roundsPerMinute;
        GunFireModeAdjustData fireModeAdjustData = getFireModeAdjustData(fireMode);
        if (fireModeAdjustData != null) {
            rpm += fireModeAdjustData.getRoundsPerMinute();
        }
        // 为避免非法运算，随意返回一个默认值。
        if (rpm <= 0) {
            return 300;
        }
        return rpm;
    }

    public BulletData getBulletData() {
        return bulletData;
    }

    public float getDrawTime() {
        return drawTime;
    }

    public float getPutAwayTime() {
        return putAwayTime;
    }

    public float getAimTime() {
        return aimTime;
    }

    public float getSprintTime() {
        return sprintTime;
    }

    public float getBoltActionTime() {
        return boltActionTime;
    }

    public float getBoltFeedTime() {
        return boltFeedTime;
    }

    public FireSound getFireSound() {
        return fireSound;
    }

    public GunReloadData getReloadData() {
        return reloadData;
    }

    public List<FireMode> getFireModeSet() {
        return fireModeSet;
    }

    public BurstData getBurstData() {
        return burstData;
    }

    public float getWeight() {
        return weight;
    }

    @Nullable
    public GunFireModeAdjustData getFireModeAdjustData(FireMode fireMode) {
        if (fireModeAdjust != null && fireModeAdjust.containsKey(fireMode)) {
            return fireModeAdjust.get(fireMode);
        }
        return null;
    }

    public float getCrawlRecoilMultiplier() {
        return crawlRecoilMultiplier;
    }

    public GunRecoil getRecoil() {
        return recoil;
    }

    public float getHurtBobTweakMultiplier() {
        return hurtBobTweakMultiplier;
    }

    public Map<InaccuracyType, Float> getInaccuracy() {
        return inaccuracy;
    }

    public void setInaccuracy(Map<InaccuracyType, Float> inaccuracy) {
        this.inaccuracy = inaccuracy;
    }

    public float getInaccuracy(InaccuracyType type) {
        return Math.max(inaccuracy.get(type), 0F);
    }

    public float getInaccuracy(InaccuracyType type, float addend) {
        return Math.max(inaccuracy.get(type) + addend, 0F);
    }

    public MoveSpeed getMoveSpeed() {
        return moveSpeed;
    }

    public GunMeleeData getMeleeData() {
        return gunMeleeData;
    }

    @Nullable
    public GunHeatData getHeatData() {
        return gunHeatData;
    }

    public boolean hasHeatData() {
        return getHeatData() != null;
    }

    @Nullable
    public List<AttachmentType> getAllowAttachments() {
        return allowAttachments;
    }

    public Map<AttachmentType, Identifier> getBuiltInAttachments() {
        return builtInAttachments;
    }

    public Map<Identifier, AttachmentData> getExclusiveAttachments() {
        return exclusiveAttachments;
    }

    @Nullable
    public Identifier getScript() {
        return script;
    }

    @Nullable
    public Map<String, Object> getScriptParam() {
        return scriptParam;
    }

    /**
     * @return 枪械开火的间隔，单位为 ms 。
     */
    public long getShootInterval(LivingEntity shooter, FireMode fireMode, ItemStack gunStack) {
        int rpm = this.getRoundsPerMinute(fireMode);
        AttachmentCacheProperty cacheProperty = IGunOperator.fromLivingEntity(shooter).getCacheProperty();
        if (cacheProperty != null) {
            rpm = Mth.clamp(cacheProperty.<Integer>getCache(RpmModifier.ID), 1, 1200);
        }
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (hasHeatData())
            rpm = (int) (rpm * iGun.lerpRPM(gunStack));

        return 60_000L / rpm;
    }

    /**
     * @return 枪械开火的间隔，单位为 ms 。
     */
    public long getBurstShootInterval() {
        // 为避免非法运算，随意返回一个默认值。
        if (burstData == null || burstData.getBpm() <= 0) {
            return 300;
        }
        return 60_000L / burstData.getBpm();
    }
}
