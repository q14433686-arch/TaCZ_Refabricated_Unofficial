package com.tacz.guns.compat.ysm;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Client-only reader and first-person ownership handshake for Yes Steve Model.
 * No YSM classes are linked. The optional renderer must actively claim the hand;
 * exposing this facade does not establish that a particular installed YSM calls it. */
@Environment(EnvType.CLIENT)
public final class YsmCompat {
    /** YSM 在 Fabric 上的 mod id（ModernYSM {@code fabric.mod.json}）。 */
    public static final String MOD_ID = "yes_steve_model";

    /** 1.20.1 YSM 的 molang 变量名，供文档/自检脚本引用，避免两边写错字符串。 */
    public static final String VAR_HOLD_GUN = "tac_hold_gun";
    public static final String VAR_GUN_TYPE = "tac_gun_type";
    public static final String VAR_GUN_ID = "tac_gun_id";
    public static final String VAR_IS_FIRE = "tac_is_fire";
    public static final String VAR_IS_AIM = "tac_is_aim";
    public static final String VAR_IS_RELOAD = "tac_is_reload";
    public static final String VAR_IS_MELEE = "tac_is_melee";
    public static final String VAR_IS_DRAW = "tac_is_draw";
    public static final String VAR_FIRE_MODE = "tac_fire_mode";

    private static final String EMPTY = "";

    private static volatile boolean externalGunRender;

    private YsmCompat() {
    }

    /** YSM 是否装了（只看 mod 是否存在，不看开关）。 */
    public static boolean isModPresent() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /**
     * YSM 适配是否生效：装了 YSM 且 {@code RenderConfig.YsmCompat} 为 true。
     * 配置项尚未初始化（极早期调用）时按「未启用」处理。
     */
    public static boolean isEnabled() {
        if (!isModPresent()) {
            return false;
        }
        return RenderConfig.YSM_COMPAT != null && RenderConfig.YSM_COMPAT.get();
    }

    // ------------------------------------------------------------------
    // 第一人称渲染归属握手
    // ------------------------------------------------------------------

    /**
     * 外部渲染器（YSM）声明「这一帧的枪我自己画」。
     *
     * <p>调用方应当成对使用（try/finally），或者在每帧开始时无条件重设一次；即便忘记清除，
     * 客户端 tick 结束时也会自动复位。</p>
     */
    public static void setExternalGunRender(boolean active) {
        externalGunRender = active;
    }

    /** TaCZ 视模是否可以继续提交；{@code true} 表示外部渲染器已接手本帧的枪。 */
    public static boolean isExternalGunRenderActive() {
        return externalGunRender && isEnabled();
    }

    /** 由客户端 tick 调用，保证握手标志不会跨 tick 残留。 */
    public static void clearExternalGunRender() {
        externalGunRender = false;
    }

    /**
     * YSM 在自己的第一人称 pass 里画本玩家的枪口火光/抛壳时，需要把 TaCZ 的
     * {@code isSelf} 打开（1.20.1 的 {@code TacAnimHandler#handleTaczSound} 即如此）。
     * 这里提供成对入口，避免直接写两个 public 静态字段时漏关。
     */
    public static void beginSelfFlashPass() {
        MuzzleFlashRender.isSelf = true;
        ShellRender.isSelf = true;
    }

    public static void endSelfFlashPass() {
        MuzzleFlashRender.isSelf = false;
        ShellRender.isSelf = false;
    }

    // ------------------------------------------------------------------
    // 枪械状态读取（对应 YSM molang 变量）
    // ------------------------------------------------------------------

    /**
     * 主手持枪：{@code tac_hold_gun}。
     *
     * <p>用非弃用的 {@code mainHandHoldGun}（上游同一实现的 {@code mainhandHoldGun} 在 26.x 里
     * 已标 {@code @Deprecated} 但保留，1.20.1 的 YSM 源码即调用旧名，迁移时不必改）。</p>
     */
    public static boolean isHoldingGun(@Nullable LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        try {
            return IGun.mainHandHoldGun(entity);
        } catch (RuntimeException | LinkageError exception) {
            logOnce("isHoldingGun", exception);
            return false;
        }
    }

    /** 主手物品是否为 TaCZ 枪械（{@code IGun}），供 YSM 过滤 {@code renderStatic}。 */
    public static boolean isGunItem(@Nullable ItemStack stack) {
        return IGun.getIGunOrNull(stack) != null;
    }

    /** 主手枪械 id，例如 {@code tacz:ak47}；未持枪返回空串：{@code tac_gun_id}。 */
    @NotNull
    public static String getGunId(@Nullable ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return EMPTY;
        }
        Identifier id = gun.getGunId(stack);
        return id == null ? EMPTY : id.toString();
    }

    /** 主手枪械 id（从实体取）：{@code tac_gun_id}。 */
    @NotNull
    public static String getGunId(@Nullable LivingEntity entity) {
        return entity == null ? EMPTY : getGunId(entity.getMainHandItem());
    }

    /**
     * 主手枪械分类（{@code CommonGunIndex#getType()}，即 {@code pistol/sniper/rifle/shotgun/smg/rpg/mg}
     * 之一，与 {@link com.tacz.guns.api.item.GunTabType} 的 {@code name().toLowerCase()} 对应）：
     * {@code tac_gun_type}。
     */
    @NotNull
    public static String getGunType(@Nullable ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return EMPTY;
        }
        try {
            Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gun.getGunId(stack));
            return index.map(CommonGunIndex::getType).orElse(EMPTY);
        } catch (RuntimeException | LinkageError exception) {
            logOnce("getGunType", exception);
            return EMPTY;
        }
    }

    /** 主手枪械分类（从实体取）：{@code tac_gun_type}。 */
    @NotNull
    public static String getGunType(@Nullable LivingEntity entity) {
        return entity == null ? EMPTY : getGunType(entity.getMainHandItem());
    }

    /** 当前开火模式名（{@code AUTO/SEMI/BURST}，未知返回空串）：{@code tac_fire_mode}。 */
    @NotNull
    public static String getFireMode(@Nullable LivingEntity entity) {
        if (entity == null) {
            return EMPTY;
        }
        try {
            FireMode mode = IGun.getMainHandFireMode(entity);
            return mode == null || mode == FireMode.UNKNOWN ? EMPTY : mode.name();
        } catch (RuntimeException | LinkageError exception) {
            logOnce("getFireMode", exception);
            return EMPTY;
        }
    }

    /**
     * 是否处于开火冷却：{@code tac_is_fire}。
     *
     * <p>与 1.20.1 YSM 的 {@code TacAnimHandler#handleTaczGunAction} 一致，本地玩家还要和
     * {@link IClientPlayerGunOperator#getClientShootCoolDown()} 取较大值 —— 服务端同步值有
     * 网络延迟，只读 {@code getSynShootCoolDown()} 会让连发动画在本地漏帧。</p>
     */
    public static boolean isFiring(@Nullable LivingEntity entity) {
        IGunOperator operator = operatorOf(entity);
        if (operator == null) {
            return false;
        }
        long coolDown = operator.getSynShootCoolDown();
        if (entity instanceof LocalPlayer localPlayer) {
            try {
                coolDown = Math.max(coolDown, IClientPlayerGunOperator.fromLocalPlayer(localPlayer).getClientShootCoolDown());
            } catch (RuntimeException | LinkageError exception) {
                logOnce("isFiring#clientCoolDown", exception);
            }
        }
        return coolDown > 0;
    }

    /** 瞄准进度 > 0：{@code tac_is_aim}。 */
    public static boolean isAiming(@Nullable LivingEntity entity) {
        IGunOperator operator = operatorOf(entity);
        return operator != null && operator.getSynAimingProgress() > 0.0F;
    }

    /** 换弹倒计时 > 0：{@code tac_is_reload}。 */
    public static boolean isReloading(@Nullable LivingEntity entity) {
        IGunOperator operator = operatorOf(entity);
        if (operator == null) {
            return false;
        }
        try {
            return operator.getSynReloadState().getCountDown() > 0;
        } catch (RuntimeException | LinkageError exception) {
            logOnce("isReloading", exception);
            return false;
        }
    }

    /** 近战（刺刀）冷却 > 0：{@code tac_is_melee}。 */
    public static boolean isMelee(@Nullable LivingEntity entity) {
        IGunOperator operator = operatorOf(entity);
        return operator != null && operator.getSynMeleeCoolDown() > 0;
    }

    /** 切枪冷却 > 0：{@code tac_is_draw}。 */
    public static boolean isDrawing(@Nullable LivingEntity entity) {
        IGunOperator operator = operatorOf(entity);
        return operator != null && operator.getSynDrawCoolDown() > 0;
    }

    /**
     * 安全取 {@link IGunOperator}：
     * 原版 {@code fromLivingEntity} 是无检查强制转型，对未挂 TaCZ mixin 的实体
     * （部分模组的 NPC、展示实体、雕刻出来的假玩家）会直接抛 {@link ClassCastException}。
     */
    @Nullable
    private static IGunOperator operatorOf(@Nullable LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        if (!(entity instanceof IGunOperator operator)) {
            return null;
        }
        return operator;
    }

    /** 同一异常只记一次，避免渲染线程每帧刷屏。 */
    private static void logOnce(String where, Throwable throwable) {
        if (logged.add(where)) {
            GunMod.LOGGER.warn("[TACZ X YSM] {} failed; YSM will see the neutral value from now on.", where, throwable);
        }
    }

    private static final java.util.Set<String> logged = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 仅供调试：当前客户端是否正在第一人称。 */
    public static boolean isFirstPerson(@NotNull Minecraft client) {
        return client.options.getCameraType().isFirstPerson();
    }
}
