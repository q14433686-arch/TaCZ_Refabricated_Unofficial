package me.xjqsh.lrtactical.api.animation;

import cn.sh1rocu.tacz.api.extension.IMoveDistTracker;
import com.tacz.guns.client.animation.statemachine.ItemAnimationStateContext;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import me.xjqsh.lrtactical.init.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Function;

/**
 * LRTactical 动画状态机的<b>基础上下文</b>，由 Lua 脚本通过反射（{@code CoerceJavaToLua}）调用。
 *
 * <p>这些 public 方法就是内容包脚本的 API 表面 —— <b>方法名不可随意改动</b>，
 * 否则第三方内容包的 {@code .lua} 会静默失效（Lua 侧调用不存在的方法只会返回 nil）。
 *
 * <h2>26.2 移植要点（全部按字节码核对，且优先照抄本仓库既有解法）</h2>
 * <ol>
 *   <li><b>{@code player.input.up/down/left/right/jumping} 五个字段全部消失。</b>
 *       26.2 的 {@code ClientInput} 只有 {@code keyPresses}（{@code Input} record）
 *       与 {@code moveVector} 两个字段（字节码确认）。
 *       改用 {@code player.input.keyPresses.forward()/backward()/left()/right()/jump()}，
 *       与本仓库 {@code GunAnimationStateContext#isInputUp} 等的写法<b>逐字一致</b>。</li>
 *   <li><b>{@code Entity#walkDist} / {@code walkDistO} 双双消失。</b>
 *       上游用 {@code walkDist + (walkDist - walkDistO) * partialTicks} 驱动行走动画。
 *       本仓库已就同一问题做过完整调查（见 {@code GunAnimationStateContext#tacz$walkDistance}
 *       与 {@code IMoveDistTracker} 的注释），结论是：
 *       <ul>
 *         <li>玩家用 {@code AbstractClientPlayer#avatarState().getInterpolatedWalkDistance(pt)}
 *             —— 它是官方继任者，量纲同为 ×0.6，且<b>对远程玩家同样有效</b>；</li>
 *         <li>非玩家实体回退到 {@code moveDist} + {@code IMoveDistTracker} 重建的插值。</li>
 *       </ul>
 *       这里<b>直接复用该结论</b>，不另创方案 —— 若改用 {@code walkAnimation.position}
 *       会让动画快约 6.7 倍（上游踩过的坑，已记录在案）。</li>
 * </ol>
 */
@SuppressWarnings("unused")
public class BaseAnimationStateContext extends ItemAnimationStateContext {
    private ItemStack currentItem = ItemStack.EMPTY;
    private int prepareTime = 0;
    private float walkDistAnchor = 0f;

    public void setCurrentItem(ItemStack currentItem) {
        this.currentItem = currentItem;
        if (currentItem.getItem() instanceof IThrowable iThrowable) {
            this.prepareTime = iThrowable.getThrowableIndex(currentItem)
                    .map(index -> index.getData().getPrepareTime())
                    .orElse(0);
        }
    }

    public ItemStack getCurrentItem() {
        return currentItem;
    }

    public int getStackCount() {
        return currentItem.getCount();
    }

    public int getPrepareTime() {
        return prepareTime;
    }

    /**
     * 26.2：{@code Minecraft#cameraEntity} <b>字段已不可访问</b>，改用 {@code getCameraEntity()}
     * （字节码确认 {@code Minecraft} 上只有
     * {@code getCameraEntity()Lnet/minecraft/world/entity/Entity;} 与配套的 setter，
     * 公开字段里只剩 {@code crosshairPickEntity}）。
     *
     * <p>本仓库 {@code GunAnimationStateContext#processCameraEntity} 早已是这个写法 ——
     * 本方法是照抄上游 1.21.1 的字段访问才编译失败的，属于「没先 grep 仓库既有解法」。
     */
    protected <T> Optional<T> processCameraEntity(Function<Entity, T> processor) {
        Entity entity = Minecraft.getInstance().getCameraEntity();
        if (entity != null) {
            return Optional.ofNullable(processor.apply(entity));
        }
        return Optional.empty();
    }

    /**
     * 获取当前系统时间，单位毫秒。
     */
    public long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 玩家的按键输入是否为上（前进键，如 W）。
     */
    public boolean isInputUp() {
        return Optional.ofNullable(Minecraft.getInstance().player)
                .map(player -> player.input.keyPresses.forward()).orElse(false);
    }

    /**
     * 玩家的按键输入是否为下（后退键，如 S）。
     */
    public boolean isInputDown() {
        return Optional.ofNullable(Minecraft.getInstance().player)
                .map(player -> player.input.keyPresses.backward()).orElse(false);
    }

    /**
     * 玩家的按键输入是否为左（左移键，如 A）。
     */
    public boolean isInputLeft() {
        return Optional.ofNullable(Minecraft.getInstance().player)
                .map(player -> player.input.keyPresses.left()).orElse(false);
    }

    /**
     * 玩家的按键输入是否为右（右移键，如 D）。
     */
    public boolean isInputRight() {
        return Optional.ofNullable(Minecraft.getInstance().player)
                .map(player -> player.input.keyPresses.right()).orElse(false);
    }

    /**
     * 玩家的按键输入是否为跳跃（如 Space）。
     */
    public boolean isInputJumping() {
        return Optional.ofNullable(Minecraft.getInstance().player)
                .map(player -> player.input.keyPresses.jump()).orElse(false);
    }


    /**
     * 某个近战攻击动作已连续进行的次数。上游默认近战 Lua 用它在 attack_left_1/2/3
     * 等连击动画之间取模切换；不是近战动作或没有玩家上下文时返回 0。
     */
    public int getActionCount(String action) {
        MeleeAction meleeAction = switch (action) {
            case "attack_left" -> MeleeAction.LEFT;
            case "attack_right" -> MeleeAction.RIGHT;
            default -> null;
        };
        if (meleeAction == null) {
            return 0;
        }
        return processCameraEntity(entity -> {
            if (entity instanceof net.minecraft.world.entity.player.Player player) {
                return ModCapabilities.combatProperties(player).getActionCount(meleeAction);
            }
            return 0;
        }).orElse(0);
    }

    /**
     * 玩家是否接触地面。
     */
    public boolean isOnGround() {
        return processCameraEntity(Entity::onGround).orElse(false);
    }

    /**
     * 玩家是否蹲伏。
     */
    public boolean isCrouching() {
        return processCameraEntity(Entity::isCrouching).orElse(false);
    }

    /**
     * 在玩家当前的行走距离打上锚点。此后 {@link #getWalkDist()} 返回与此锚点的相对值。
     */
    public void anchorWalkDist() {
        processCameraEntity(entity -> {
            if (entity instanceof LivingEntity livingEntity) {
                walkDistAnchor = lrtactical$walkDistance(livingEntity);
            }
            return null;
        });
    }

    /**
     * 与锚点相对的行走距离（未打锚点时即为绝对行走距离）。
     *
     * @see #lrtactical$walkDistance(LivingEntity)
     */
    public float getWalkDist() {
        return processCameraEntity(entity -> {
            if (entity instanceof LivingEntity livingEntity) {
                return lrtactical$walkDistance(livingEntity) - walkDistAnchor;
            }
            return 0f;
        }).orElse(0f);
    }

    /**
     * 取与上游 1.21.1 {@code walkDist} <b>语义等价</b>的行走距离（已按 partialTick 插值）。
     *
     * <p>实现与本仓库 {@code GunAnimationStateContext#tacz$walkDistance} 完全一致 ——
     * 那里有对「为什么不能用 {@code walkAnimation.position}」「为什么 {@code moveDist}
     * 对远程玩家恒为 0」的完整字节码论证，此处不重复，只强调<b>必须保持同一实现</b>：
     * 内容包脚本用 {@code (getWalkDist() % 2.0) / 2.0} 驱动一个行走周期，
     * 量纲一旦不同，动画速率就会整体偏掉。
     */
    private float lrtactical$walkDistance(LivingEntity livingEntity) {
        // 首选：26.2 官方的 walkDist/walkDistO 继任者，对本机与远程玩家均有效
        if (livingEntity instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer.avatarState().getInterpolatedWalkDistance(this.partialTicks);
        }
        // 回退：非玩家实体（持械生物）没有 ClientAvatarState，用 moveDist + 重建的 moveDistO
        float moveDist = livingEntity.moveDist;
        if (livingEntity instanceof IMoveDistTracker tracker) {
            float moveDistO = tracker.tacz$getMoveDistO();
            return moveDist + (moveDist - moveDistO) * this.partialTicks;
        }
        return moveDist;
    }
}
