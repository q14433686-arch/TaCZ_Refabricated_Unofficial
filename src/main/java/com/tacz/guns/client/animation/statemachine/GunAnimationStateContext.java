package com.tacz.guns.client.animation.statemachine;

import cn.sh1rocu.tacz.api.extension.IMoveDistTracker;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.util.LuaNbtAccessor;
import com.tacz.guns.client.industry.magazine.IndustryReloadRouteClientState;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.industry.ammo.SpentCartridgeService;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("unused")
public class GunAnimationStateContext extends ItemAnimationStateContext {
    private ItemStack currentGunItem;
    private IGun iGun;
    private GunDisplayInstance display;
    private GunData gunData;
    private float walkDistAnchor = 0f;
    private LuaNbtAccessor nbtUtil;

    private <T> Optional<T> processGunData(BiFunction<IGun, GunDisplayInstance, T> processor) {
        if (iGun != null && display != null) {
            return Optional.ofNullable(processor.apply(iGun, display));
        }
        return Optional.empty();
    }

    private <T> Optional<T> processGunOperator(Function<IClientPlayerGunOperator, T> processor) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            return Optional.ofNullable(processor.apply(IClientPlayerGunOperator.fromLocalPlayer(player)));
        }
        return Optional.empty();
    }

    private <T> Optional<T> processRemoteGunOperator(Function<IGunOperator, T> processor) {
        return processCameraEntity(entity -> {
            if (entity instanceof LivingEntity) {
                IGunOperator gunOperator = IGunOperator.fromLivingEntity((LivingEntity) entity);
                return processor.apply(gunOperator);
            }
            return null;
        });
    }

    private <T> Optional<T> processCameraEntity(Function<Entity, T> processor) {
        Entity entity = Minecraft.getInstance().getCameraEntity();
        if (entity != null) {
            return Optional.ofNullable(processor.apply(entity));
        }
        return Optional.empty();
    }

    /**
     * 获取枪膛中是否有子弹。
     *
     * @return 枪膛中是否有子弹。如果是开膛待击的枪械，则此方法返回 false。
     */
    public boolean hasBulletInBarrel() {
        return processGunData((iGun, gunIndex) -> {
            Bolt boltType = gunData.getBolt();
            return boltType != Bolt.OPEN_BOLT && iGun.hasBulletInBarrel(currentGunItem);
        }).orElse(false);
    }

    public boolean isOverHeat() {
        return gunData.getHeatData() != null && iGun.isOverheatLocked(currentGunItem);
    }

    public float getHeatProgress() {
        return gunData.getHeatData() != null ?
                Mth.clamp(iGun.getHeatAmount(currentGunItem) / gunData.getHeatData().getHeatMax(), 0f, 1f) : 0f;
    }

    /**
     * 获取枪械的射击间隔，单位毫秒
     *
     * @return 射击间隔
     */
    public long getShootInterval() {
        return processCameraEntity(entity -> {
            if (entity instanceof LivingEntity livingEntity) {
                FireMode fireMode = iGun.getFireMode(currentGunItem);
                if (fireMode == FireMode.BURST) {
                    long coolDown = (long) (gunData.getBurstData().getMinInterval() * 1000f);
                    return Math.max(coolDown, 0L);
                }
                long coolDown = gunData.getShootInterval(livingEntity, fireMode, currentGunItem);
                return Math.max(coolDown, 0L);
            }
            return 0L;
        }).orElse(0L);
    }

    /**
     * 返回上次射击的 timestamp(系统时间)，单位为毫秒。此值在切枪时会重置为 -1。
     *
     * @return 上次射击的 timestamp，在切枪时会重置为 -1。
     */
    public long getLastShootTimestamp() {
        return processGunOperator(operator -> operator.getDataHolder().clientLastShootTimestamp).orElse(-1L);
    }

    /**
     * 获取当前系统时间，单位毫秒。
     *
     * @return 当前系统时间
     */
    public long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 调整射击间隔。(仅在客户端表现)
     *
     * @param alpha 需要加上或减少的射击间隔，单位为毫秒。正数即增加射击间隔，负数则是减少。
     */
    public void adjustClientShootInterval(long alpha) {
        processGunOperator(operator -> {
            long timestamp = operator.getDataHolder().clientShootTimestamp;
            operator.getDataHolder().clientShootTimestamp = timestamp + alpha;
            return null;
        });
    }

    /**
     * 获取弹匣中的备弹数。
     *
     * @return 返回弹匣中的备弹数，不计算已在枪管中的弹药。
     */
    public int getAmmoCount() {
        return processGunData((iGun, gunIndex) -> iGun.getCurrentAmmoCount(currentGunItem)).orElse(0);
    }

    /**
     * 获取枪械弹匣的最大备弹数。
     *
     * @return 返回枪械弹匣的最大备弹数，不计算已在枪管中的弹药。
     */
    public int getMaxAmmoCount() {
        return processGunData(
                (iGun, gunIndex) ->
                        AttachmentDataUtils.getAmmoCountWithAttachment(currentGunItem, gunData)
        ).orElse(0);
    }

    /**
     * 检查玩家身上（或者虚拟备弹）是否有弹药可以消耗，通常用于循环换弹的打断。
     * 创造模式的玩家会直接返回 true
     *
     * @return 玩家身上（或者虚拟备弹）是否有弹药可以消耗
     */
    public boolean hasAmmoToConsume() {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            Boolean routeSource = IndustryReloadRouteClientState.hasAmmoToConsume(localPlayer, currentGunItem);
            if (routeSource != null) {
                return routeSource;
            }
        }
        if (!processRemoteGunOperator(IGunOperator::needCheckAmmo).orElse(true)) {
            return true;
        }
        if (iGun.useDummyAmmo(currentGunItem)) {
            return iGun.getDummyAmmoAmount(currentGunItem) > 0;
        }
        return processCameraEntity(entity -> {
                    if (entity instanceof LivingEntity livingEntity) {
                        return livingEntity.tacz$getItemHandler(null)
                                .map(cap -> {
                                    // 背包检查
                                    for (int i = 0; i < cap.getSlots(); i++) {
                                        ItemStack checkAmmoStack = cap.getStackInSlot(i);
                                        if (checkAmmoStack.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(currentGunItem, checkAmmoStack)) {
                                            return true;
                                        }
                                        if (checkAmmoStack.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(currentGunItem, checkAmmoStack)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                }).orElse(false);
                    }
                    return false;
                }
        ).orElse(false);
    }

    /**
     * 获取枪械扩容等级。
     *
     * @return 扩容等级，范围 0 ~ 3。0 表示没有安装扩容弹匣，1 ~ 3 表示安装了扩容等级 1 ~ 3 的扩容弹匣
     */
    public int getMagExtentLevel() {
        return processGunData(
                (iGun, gunIndex) ->
                        AttachmentDataUtils.getMagExtendLevel(currentGunItem, gunData)
        ).orElse(0);
    }

    /**
     * 获取枪械当前的开火模式。
     *
     * @return FireMode 枚举的 ordinal 值
     */
    public int getFireMode() {
        return processGunData((iGun, gunIndex) -> iGun.getFireMode(currentGunItem).ordinal()).orElse(0);
    }

    /**
     * 获取持枪玩家的瞄准进度。
     *
     * @return 持枪玩家的瞄准进度，取值范围：0 ~ 1。
     * 0 代表没有喵准，1 代表喵准完成。
     */
    public float getAimingProgress() {
        return processGunOperator(operator -> operator.getClientAimingProgress(partialTicks)).orElse(0f);
    }

    /**
     * 获取玩家当前是否在瞄准。如果正在瞄准，aiming progress 会增加，否则减少。
     *
     * @return 玩家当前是否在瞄准
     */
    public boolean isAiming() {
        return processGunOperator(IClientPlayerGunOperator::isAim).orElse(false);
    }

    /**
     * 获取玩家的射击冷却。
     *
     * @return 玩家的射击冷却，单位为毫秒(ms)。
     */
    public long getShootCoolDown() {
        return processGunOperator(IClientPlayerGunOperator::getClientShootCoolDown).orElse(0L);
    }

    /**
     * 获取玩家的换弹状态
     *
     * @return 玩家的换弹状态
     */
    public int getReloadStateType() {
        return processCameraEntity(entity -> {
            if (entity instanceof LivingEntity livingEntity) {
                return IGunOperator.fromLivingEntity(livingEntity).getSynReloadState().getStateType().ordinal();
            }
            return ReloadState.StateType.NOT_RELOADING.ordinal();
        }).orElse(ReloadState.StateType.NOT_RELOADING.ordinal());
    }

    /**
     * 获取玩家的按键输入是否为上。
     *
     * @return 玩家的按键输入是否为上 (对应着移动中的前进按键，如 W)
     */
    public boolean isInputUp() {
        return Optional.ofNullable(Minecraft.getInstance().player).map(player -> player.input.keyPresses.forward()).orElse(false);
    }

    /**
     * 获取玩家的按键输入是否为下。
     *
     * @return 玩家的按键输入是否为下 (对应着移动中的后退按键，如 S)
     */
    public boolean isInputDown() {
        return Optional.ofNullable(Minecraft.getInstance().player).map(player -> player.input.keyPresses.backward()).orElse(false);
    }

    /**
     * 获取玩家的按键输入是否为左。
     *
     * @return 玩家的按键输入是否为左 (对应着移动中的左移按键，如 A)
     */
    public boolean isInputLeft() {
        return Optional.ofNullable(Minecraft.getInstance().player).map(player -> player.input.keyPresses.left()).orElse(false);
    }

    /**
     * 获取玩家的按键输入是否为右。
     *
     * @return 玩家的按键输入是否为右 (对应着移动中的右移按键，如 D)
     */
    public boolean isInputRight() {
        return Optional.ofNullable(Minecraft.getInstance().player).map(player -> player.input.keyPresses.right()).orElse(false);
    }

    /**
     * 获取玩家的按键输入是否为跳跃。
     *
     * @return 玩家的按键输入是否为跳跃 (对应着移动中的跳跃按键，如 Space)
     */
    public boolean isInputJumping() {
        return Optional.ofNullable(Minecraft.getInstance().player).map(player -> player.input.keyPresses.jump()).orElse(false);
    }

    /**
     * 获取玩家当前是否正在匍匐
     *
     * @return 玩家当前是否正在匍匐
     */
    public boolean isCrawl() {
        return processGunOperator(IClientPlayerGunOperator::isCrawl).orElse(false);
    }

    /**
     * 获取玩家是否接触地面
     *
     * @return 玩家是否接触地面
     */
    public boolean isOnGround() {
        return processCameraEntity(Entity::onGround).orElse(false);
    }

    /**
     * 获取 玩家是否蹲伏
     *
     * @return 玩家是否蹲伏
     */
    public boolean isCrouching() {
        return processCameraEntity(Entity::isCrouching).orElse(false);
    }

    /**
     * 获取 玩家当前是否应该斜握枪械
     * 需要同时满足蹲伏和枪械允许斜握
     *
     * @return 玩家当前是否应该斜握枪械
     */
    public boolean shouldSlide() {
        return processCameraEntity(e -> e.isCrouching() && gunData.canSlide()).orElse(false);
    }

    /**
     * 在玩家当前的行走距离打上锚点。此后，getWalkDist() 将返回与此锚点的相对值
     */
    public void anchorWalkDist() {
        processCameraEntity(entity -> {
            if (entity instanceof LivingEntity livingEntity) {
                walkDistAnchor = tacz$walkDistance(livingEntity);
            }
            return null;
        });
    }

    /**
     * 取<b>与上游 1.21.1 语义等价</b>的行走距离（已按 partialTick 插值）。
     *
     * <p><b>26.2 修复：持枪行走动画“抖动”的真正根因 —— 动画速率快了约 6.7 倍。</b></p>
     *
     * <p>上游 1.21.1 用的是 {@code Entity#walkDist}：</p>
     * <pre>
     * entity.walkDist + (entity.walkDist - entity.walkDistO) * partialTicks
     * </pre>
     *
     * <p>移植时误换成了 {@code livingEntity.walkAnimation.position(...)}。
     * <b>这两者不是同一个量</b>（反编译确认）：</p>
     *
     * <table border="1">
     *   <tr><th></th><th>每 tick 增量</th><th>走一个 2.0 周期</th></tr>
     *   <tr><td>{@code moveDist}（= 旧 walkDist）</td>
     *       <td>{@code += 水平位移 * 0.6}</td><td>约 33 tick ≈ 1.67s</td></tr>
     *   <tr><td>{@code walkAnimation.position}</td>
     *       <td>{@code += min(位移 * 4.0, 1.0)}（0.4 缓动）</td><td>约 5 tick ≈ 0.25s</td></tr>
     * </table>
     *
     * <p>以正常步行 0.1 格/tick 计算，后者约为前者的 <b>6.7 倍</b>。
     * 而枪包 Lua 的驱动写法是 {@code setAnimationProgress(track, (getWalkDist() % 2.0) / 2.0)}，
     * 即固定 2.0 为一个行走周期 —— 于是行走动画被以约 6.7 倍速播放。
     * 因为两者都与移动速度<b>线性相关</b>，所以表现为“走得越快抖得越快、
     * 喝缓慢药水后变慢”，本质上不是抖动，而是<b>被加速的持枪行走动画</b>。</p>
     *
     * <p>26.2 中 {@code Entity.walkDist} 已更名为 {@code Entity.moveDist}
     * （javap 确认：{@code public float moveDist}，且仍是 {@code += 水平位移 * 0.6}），
     * 语义与旧 {@code walkDist} 完全一致，因此改用它。</p>
     *
     * <p><b>关于插值（第 7 轮补充）</b>：{@code moveDist} 没有配套的 {@code moveDistO}。
     * 第 6 轮曾判断"增量很小、直接取值不会有可见阶梯感" —— <b>这个判断是错的</b>，
     * 实测动画明显发涩、像掉帧。现由 {@code EntityMixin} 在每 tick HEAD 记录上一 tick 的
     * {@code moveDist}（见 {@link IMoveDistTracker}）重建出 {@code walkDistO}，
     * 从而做与上游<b>完全一致</b>的线性插值，兼顾正确量纲与平滑度。</p>
     */
    private float tacz$walkDistance(LivingEntity livingEntity) {
        // ------------------------------------------------------------------
        // 【首选路径】26.2 官方的 walkDist/walkDistO 继任者：ClientAvatarState
        //
        // 第 20 轮修正：r6/r7 认定「26.2 把 walkDist 更名为 Entity.moveDist、
        // 语义完全一致」——这个判断只对了一半，且在多人环境下是错的。
        //
        // 字节码事实（Entity.move，偏移 601~648）：
        //     if (level.isClientSide() && !isLocalInstanceAuthoritative()) -> 整段跳过
        //     ...
        //     applyMovementEmissionAndPlaySound(...)   // moveDist 唯一的写入点
        // 而门禁逐级展开为：
        //     Entity.isLocalInstanceAuthoritative() -> Player.isLocalClientAuthoritative()
        //         -> Player.isLocalPlayer()      = iconst_0  (基类恒 false)
        //            LocalPlayer.isLocalPlayer() = iconst_1  (只有本机玩家)
        //
        // 即：客户端上 moveDist 只对 LocalPlayer 累加，RemotePlayer 恒为 0。
        // 后果是**其他玩家的持枪行走动画完全静止**（(0 % 2.0)/2.0 恒为 0），
        // 而本机自测发现不了——自己的动画是正常的。
        //
        // 26.2 为此提供了专门的载体（均经字节码确认）：
        //     ClientAvatarState.walkDist / walkDistO                （private 字段）
        //     ClientAvatarState.addWalkDistance(float)              （walkDist += v）
        //     ClientAvatarState.tick(Vec3,Vec3)                     （walkDistO = walkDist）
        //     ClientAvatarState.getInterpolatedWalkDistance(float)  （Mth.lerp(pt, walkDistO, walkDist)）
        // 驱动方 LocalPlayer.move 末尾：Mth.length(dx,dz) * 0.6F -> addWalkedDistance
        //     -> AbstractClientPlayer.addWalkedDistance -> clientAvatarState.addWalkDistance
        //
        // 关键点：量纲同为 **×0.6**，与上游 1.21.1 的 walkDist 完全一致；
        // 且 getInterpolatedWalkDistance 就是上游那句手写插值的官方实现，
        // 对本机与远程玩家**同样有效**（RemotePlayer 也是 AbstractClientPlayer）。
        // vanilla 自己在 AvatarRenderer.extractCapeState 里就是这么用的。
        if (livingEntity instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer.avatarState().getInterpolatedWalkDistance(this.partialTicks);
        }

        // ------------------------------------------------------------------
        // 【回退路径】非玩家实体（僵尸等持枪生物）走 moveDist。
        // 这类实体在客户端由服务端同步位置，moveDist 的门禁同样不通过，
        // 但它们没有 ClientAvatarState，也没有更好的量可用。
        // EntityMixin 重建的 moveDistO 在此仍有意义（服务端侧/单机场景）。
        float moveDist = livingEntity.moveDist;
        if (livingEntity instanceof IMoveDistTracker tracker) {
            float moveDistO = tracker.tacz$getMoveDistO();
            return moveDist + (moveDist - moveDistO) * this.partialTicks;
        }
        return moveDist;
    }

    /**
     * 获取与锚点相对的行走距离。如果没有打锚点，则直接获取行走距离。
     *
     * @return 与锚点相对的行走距离。如果没有打锚点，则直接返回行走距离。
     */

    public float getWalkDist() {
        return processCameraEntity(entity -> {
            if (entity instanceof LivingEntity livingEntity) {
                // 必须与上游同量纲（moveDist），否则动画速率会快约 6.7 倍。见 tacz$walkDistance。
                float currentWalkDist = tacz$walkDistance(livingEntity);
                return currentWalkDist - walkDistAnchor;
            }
            return 0f;
        }).orElse(0f);
    }

    /**
     * 从指定序号的抛壳窗弹出一枚弹壳
     *
     * @param index 抛壳窗序号
     */
    public void popShellFrom(int index) {
        // Industrial cartridges use a synchronised server ItemEntity instead
        // of a second, non-recoverable client-only shell.  If the server later
        // rejects the shot, no fake shell is shown either.
        if (gunData != null && SpentCartridgeService.hasRecoverableCase(gunData.getAmmoId())) {
            return;
        }
        if (display.getShellEjection() != null) {
            BedrockGunModel gunModel = display.getGunModel();
            if (gunModel != null) {
                ShellRender shellRender = gunModel.getShellRender(index);
                Vector3f velocity = display.getShellEjection().getRandomVelocity();
                if (shellRender != null) {
                    shellRender.addShell(velocity);
                }

                var lod = display.getLodModel();
                if (lod != null) {
                    ShellRender lodShell = lod.getLeft().getShellRender(index);
                    if (lodShell != null) {
                        lodShell.addShell(velocity);
                    }
                }
            }
        }
    }

    /**
     * 获取在枪械 display 中声明的状态机参数
     *
     * @return 状态机参数表
     */
    public LuaTable getStateMachineParams() {
        LuaTable param = display.getStateMachineParam();
        return param == null ? new LuaTable() : param;
    }

    /**
     * 获取当前枪械物品的 NBT 数据访问器。<br/>
     * 注意，你不应该在客户端侧修改 NBT 数据，这可能会导致与服务端的数据不一致。<br/>
     * 你应该确保在状态机脚本内仅进行读操作
     *
     * @return NBT 数据访问器
     */
    public LuaNbtAccessor getNbtAccessor() {
        return nbtUtil;
    }

    /**
     * 获取枪械的配件 ID
     *
     * @return 配件 ID, 如果类型错误或者对应的配件不存在则返回空配件 ID 'tacz:empty'
     */
    public String getAttachment(String type) {
        try {
            AttachmentType t = AttachmentType.valueOf(type);
            String actual = iGun.getAttachmentId(currentGunItem, t).toString();
            LocalPlayer player = Minecraft.getInstance().player;
            // Mirror the server's audited route selector. This is only a
            // state-machine predicate: no attachment is installed, rendered,
            // or granted any stat effect.
            if (DefaultAssets.EMPTY_ATTACHMENT_ID.toString().equals(actual) && player != null
                    && IndustryReloadRouteClientState.forcesAttachmentPresent(player, currentGunItem, type)) {
                return "tacz:industry_reload_route";
            }
            return actual;
        } catch (IllegalArgumentException e) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID.toString();
        }
    }

    /**
     * 获取当前的蓄力进度
     * @return 当前的蓄力进度
     */
    public float getChargeProgress() {
        return processGunOperator(IClientPlayerGunOperator::getChargeProgress).orElse(0f);
    }

    /**
     * 获取当前枪械的最大蓄力值
     * @return 当前枪械的最大蓄力值
     */
    public float getMaxCharge() {
        return processGunData((iGun, gunIndex) -> {
            var chargeData = gunData.getChargeData(iGun.getFireMode(currentGunItem));
            return chargeData != null ? chargeData.getMaxCharge() : 0f;
        }).orElse(0f);
    }

    /**
     * 获取当前枪械的蓄力触发阈值(仅hold模式有效)
     * @return 当前枪械的蓄力触发阈值
     */
    public float getChargeThreshold() {
        return processGunData((iGun, gunIndex) -> {
            var chargeData = gunData.getChargeData(iGun.getFireMode(currentGunItem));
            return chargeData != null ? chargeData.getFireThreshold() : 0f;
        }).orElse(0f);
    }

    /**
     * 获取当前是否正在蓄力
     * @return 当前是否正在蓄力
     */
    public boolean isCharging() {
        return processGunOperator(IClientPlayerGunOperator::isCharging).orElse(false);
    }

    /**
     * 状态机脚本请不要调用此方法。此方法用于状态机更新时设置当前的物品对象。
     */
    public void setCurrentGunItem(ItemStack currentGunItem) {
        this.currentGunItem = currentGunItem;
        this.iGun = IGun.getIGunOrNull(currentGunItem);
        if (iGun != null) {
            display = TimelessAPI.getGunDisplay(currentGunItem).orElse(null);
            gunData = TimelessAPI.getClientGunIndex(iGun.getGunId(currentGunItem))
                    .map(ClientGunIndex::getGunData).orElse(null);
        }
        if (currentGunItem.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA) != null) {
            nbtUtil = LuaNbtAccessor.from(currentGunItem);
        }
    }
}
