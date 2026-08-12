package me.xjqsh.lrtactical.item;

import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.capability.CustomItemCoolDowns;
import me.xjqsh.lrtactical.entity.ThrowableItemEntity;
import me.xjqsh.lrtactical.init.ModCapabilities;
import me.xjqsh.lrtactical.init.ModItems;
import me.xjqsh.lrtactical.item.index.ThrowableIndex;
import me.xjqsh.lrtactical.item.throwable.explode.ExplodeThrowableData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 可投掷物品（手雷等）。一个物品承载所有手雷，具体种类由 NBT 决定。
 *
 * <h2>行为</h2>
 * 右键<b>按住</b>进入准备（拔销）→ 松手投出。若配置了 {@code cookable}，
 * 按住期间会持续「预燃」，超过安全时长会在手上炸掉。
 *
 * <h2>26.2 移植要点（每条均经字节码核对完整签名）</h2>
 * <ol>
 *   <li><b>{@code use} 的返回类型变了</b>：
 *       {@code InteractionResultHolder<ItemStack>} → {@code InteractionResult}。
 *       对应地 {@code InteractionResultHolder.fail(stack)} → {@code InteractionResult.FAIL}，
 *       {@code consume(stack)} → {@code InteractionResult.CONSUME}。</li>
 *   <li><b>{@code UseAnim} 改名为 {@code ItemUseAnimation}</b>（枚举值 {@code BOW} 不变）。</li>
 *   <li><b>{@code releaseUsing} 现在返回 {@code boolean}</b>（原为 {@code void}）。
 *       返回值语义为「是否消耗掉这次释放」，此处按是否真的投出来返回。</li>
 *   <li><b>{@code Item#getMaxStackSize(ItemStack)} 已不存在</b>。26.2 的堆叠上限由
 *       {@code DataComponents.MAX_STACK_SIZE} 组件决定 —— 本仓库
 *       {@code AmmoItemDataAccessor} 已就同一问题写过完整分析。
 *       现由 {@link IThrowable#applyMaxStackSize} 在 {@code setId} 时写入组件，
 *       并由下面的 {@link #inventoryTick} 为老物品补写。</li>
 *   <li><b>{@code onEntitySwing} 是 Forge/NeoForge 扩展</b>，Fabric 无对应，已移除。</li>
 *   <li><b>{@code Item.Properties} 必须带注册键</b>（{@code setId}），
 *       否则注册期报错 —— 沿用本仓库 {@code ModItems#itemProps} 的做法，
 *       故构造器改为接收 {@code Properties} 而非自己 new。</li>
 *   <li>上游 tooltip 走 {@code getTooltipImage}；现已按 26.2 的
 *       {@code ClientTooltipComponent#extractText(GuiGraphicsExtractor,...)} 接回，
 *       不再尝试旧 {@code GuiGraphics/MultiBufferSource} 即时绘制。</li>
 * </ol>
 *
 * <h2>当前能力边界（2026-08-12 复核）</h2>
 * <ul>
 *   <li><b>已完成</b>：五种投掷类型、索引联机同步、Bedrock/Lua 动画渲染、
 *       遥控起爆器与 C4 所有权判定；旧注释把它们写成“尚未移植”已经过时。</li>
 *   <li><b>反馈层已完成</b>：物品 tooltip、拔销/预燃进度 HUD，以及自定义分类
 *       冷却遮罩均走 26.2 extracted-GUI 路径。</li>
 * </ul>
 */
public class ThrowableItem extends Item implements IThrowable, com.tacz.guns.api.item.IAnimationItem, cn.sh1rocu.tacz.api.extension.IItem {
    public ThrowableItem(Properties properties) {
        // 【本轮修复】不再 stacksTo(1)。
        //
        // stacksTo(n) 的实现就是 component(MAX_STACK_SIZE, n)（字节码确认），
        // 写的是物品的<b>默认组件集(prototype)</b>。它带来两个独立的坏处：
        //
        // 1. 每种手雷的真实上限来自数据包（ThrowableIndex#getMaxStackSize），
        //    在物品注册期根本查不到，写死 1 就永远是 1；
        // 2. <b>patch 参与相等性判断</b>——PatchedDataComponentMap#equals 同时比较
        //    prototype 与 patch（字节码确认）。prototype=1 时，写过组件的手雷带
        //    patch{MAX_STACK_SIZE=16}、没写过的 patch 为空，
        //    isSameItemSameComponents 直接 false，两堆<b>看起来一样</b>的手雷永不合并。
        //
        // 因此把物品级默认上限抬到 99（= Item.ABSOLUTE_MAX_STACK_SIZE，
        // 也是 max_stack_size 组件 codec 的上界），
        // 每种手雷的精确上限再由 IThrowable#applyMaxStackSize 逐个写入。
        // 这与本仓库 TACZ 侧 AmmoItem 第 34 轮的修法完全一致 —— 同一个坑，同一套解法。
        super(properties.stacksTo(Item.ABSOLUTE_MAX_STACK_SIZE));
    }

    /**
     * 老物品的堆叠上限自愈。
     *
     * <p>{@link IThrowable#setId} 只在<b>生成</b>手雷时调用，已经躺在玩家背包/箱子里的
     * 旧物品不会再经过它，因此永远缺少正确的 {@code MAX_STACK_SIZE} 组件。
     * 这里在物品 tick 时补写一次，代价极低（组件值相同时 {@code set} 不产生变化）。
     *
     * <p>26.2 签名为 {@code (ItemStack, ServerLevel, Entity, EquipmentSlot)}（字节码确认），
     * <b>只在服务端</b>调用，组件变更会随物品同步到客户端。
     * 与 TACZ 侧 {@code AmmoItem#inventoryTick} 同构。
     */
    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull ServerLevel level,
                              @NotNull Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        IThrowable.applyMaxStackSize(stack);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        // 与上游一致：给一个足够大的值，实际何时结束由 releaseUsing / onUseTick 决定
        return 72000;
    }

    @Override
    public @NotNull ItemUseAnimation getUseAnimation(@NotNull ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    /**
     * 右键开始「拔销」。
     *
     * <h2>【本轮修复】为什么两端必须做出<b>相同</b>的决定</h2>
     * 这里曾是「投出一颗后就再也投不出、必须等它炸掉」的根因。
     *
     * <p>客户端 {@code MultiPlayerGameMode#useItem} 会<b>本地预测</b>执行一次
     * {@code ItemStack#use}（字节码确认走 {@code startPrediction}），
     * 服务端 {@code ServerPlayerGameMode#useItem} 再真正执行一次。
     * 两端<b>各自独立</b>地决定要不要 {@code startUsingItem}。
     *
     * <p>冷却的<b>判定</b>仍是纯服务端权威；客户端表现在由
     * {@code ServerMessageCustomCooldown} 同步，仅用于图标遮罩。这里仍不能用客户端
     * 预测结果拒绝 {@code startUsingItem}，否则网络延迟会让两端分叉。
     * 因此旧写法必然分叉：
     * <ul>
     *   <li><b>服务端</b>：冷却中 → 不 {@code startUsingItem}；</li>
     *   <li><b>客户端</b>：显示表可能因包延迟仍为空/已过期 → 照常
     *       {@code startUsingItem}，进入“使用中”。</li>
     * </ul>
     *
     * <p>客户端一旦进入「使用中」，{@code LivingEntity#startUsingItem} 开头那句
     * {@code if (isUsingItem()) return;}（字节码 offset 14-20 确认）
     * 就会<b>吞掉之后所有的右键</b>；而松手时服务端没有 {@code useItem} 可释放，
     * 状态再也回不到一致，表现为「右键彻底没反应」。
     *
     * <p>之所以看起来像「世界上只允许存在一颗手雷」，是因为示例配置里
     * {@code life_time}=60 tick(3秒) <b>远大于</b> {@code cooldown}=20 tick(1秒)：
     * 玩家总是在手雷还在空中时重试，而那时冷却其实早就结束了。
     * 真正的约束是<b>状态分叉</b>，与实体数量无关 —— 多人游戏同样会复现。
     *
     * <h2>修法</h2>
     * <b>只在服务端做冷却判定</b>，客户端一律放行。
     * 这样两端都会 {@code startUsingItem}（动画一致），
     * 而是否真的投出由服务端的 {@link #releaseUsing} 说了算 ——
     * 那里本来就有同一份冷却校验，不会漏判。
     *
     * <p>这也是原版的通行做法：客户端只做乐观预测，权威判定始终在服务端。
     * 当前客户端冷却表严格限定为 HUD 数据源，绝不进入本方法的行为门禁。
     */
    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        // 客户端同步表只供 HUD；网络延迟下不能作为行为门禁。
        // 故客户端一律乐观放行，权威判定交给服务端。
        boolean onCooldown = false;
        if (!level.isClientSide()) {
            CustomItemCoolDowns coolDowns = ModCapabilities.coolDowns(player);
            onCooldown = getThrowableIndex(stack)
                    .map(index -> index.getData().getCooldownCategory())
                    .map(coolDowns::isOnCooldown)
                    .orElse(false);
        }
        if (!onCooldown) {
            player.startUsingItem(hand);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 真正把手雷丢出去。
     *
     * @param ticksUsingItem 本次已按住的 tick 数。
     *                       <b>必须由调用方显式传入</b>，不能在这里现取 ——
     *                       {@code LivingEntity#getTicksUsingItem()} 开头就是
     *                       {@code if (!isUsingItem()) return 0;}（字节码 offset 1-4 确认），
     *                       而「预燃超时在手上炸」那条路径为了避免递归<b>必须先
     *                       {@code stopUsingItem()} 再投</b>，那之后现取只会得到 0，
     *                       预燃时长会被静默算成 0（手雷变回满引信）。
     * @return 是否成功投出
     */
    public boolean onThrow(Level level, LivingEntity entity, ItemStack stack,
                           ThrowableIndex<?, ?> index, int ticksUsingItem) {
        ThrowableItemEntity throwable = index.createEntity(stack, entity);
        if (throwable == null) {
            return false;
        }

        // 预燃（cook）：按住越久，飞出去后剩余引信越短
        if (index.getData().isCookable()) {
            int cooked = ticksUsingItem - index.getData().getPrepareTime();
            throwable.setLife(Math.max(throwable.getLife() - cooked, 0));
        }
        level.addFreshEntity(throwable);

        Identifier cooldownId = index.getData().getCooldownCategory();
        if (cooldownId != null && entity instanceof Player player) {
            ModCapabilities.coolDowns(player).addCooldown(cooldownId, index.getData().getCooldown());
        }

        boolean remoteDetonation = index.getData() instanceof ExplodeThrowableData explodeData
                && explodeData.getExplode().isRemoteDetonation();

        // 创造模式不消耗
        Player player = entity instanceof Player p ? p : null;
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        // 遥控起爆：投出 C4/遥控雷后确保玩家拥有一个“归属型” detonator。
        //
        // 与上游/早期移植不同，这里不再为每颗 C4 生成一个绑定单实体的一次性起爆器。
        // DetonatorItem 会按投掷者 UUID 引爆该玩家所有 remote_detonation=true 的 C4：
        //   - 不会误爆其他玩家的 C4；
        //   - 一个起爆器可重复引爆自己投出的多颗 C4；
        //   - 起爆器使用后不消失，避免廉价的一次性物品体验。
        if (remoteDetonation && player != null && !level.isClientSide() && !hasDetonator(player)) {
            ItemStack detonator = new ItemStack(ModItems.DETONATOR);
            if (stack.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, detonator);
            } else if (!player.getInventory().add(detonator)) {
                player.drop(detonator, false);
            }
        }
        return true;
    }

    /**
     * 预燃超时 —— 在手上炸。
     *
     * <h2>【本轮修复】这里绝对不能调 {@code releaseUsingItem()}</h2>
     * 上一轮把 {@code stopUsingItem()} 改成 {@code releaseUsingItem()}，
     * 造成<b>无限递归 StackOverflow</b>。递归环路（字节码逐帧确认）：
     * <pre>
     * onUseTick
     *   -> entity.releaseUsingItem()
     *        -> ItemStack#releaseUsing      (offset 48)
     *        -> entity.updatingUsingItem()  (offset 62)  ← 祸根
     *             -> updateUsingItem(stack)
     *                  -> ItemStack#onUseTick
     *                       -> 又回到本方法（此时 useItem 尚未清空，
     *                          isUsingItem() 仍为 true，条件依然成立）
     * </pre>
     * {@code releaseUsingItem()} 在真正 {@code stopUsingItem()} 之前
     * <b>先调了一次 {@code updatingUsingItem()}</b>（offset 62，而 stopUsingItem 在 66），
     * 且它自身<b>没有任何防重入门禁</b>（offset 0 起直接取 useItem，无 isUsingItem 检查）。
     * 于是「投出 → 再次 tick → 条件仍满足 → 再投出」无限套娃，
     * 每层还会真的生成一颗手雷实体。
     *
     * <p><b>教训</b>：换 API 时只比对了「两者都会清空 useItem/useItemRemaining」，
     * 却没有查<b>完整调用链</b> —— 而问题恰恰出在中间那一步回调上。
     * 光看方法签名和字段效果不够，<b>会回调用户代码的方法必须查它的内部调用序列</b>。
     *
     * <h2>正确写法：先停，再投</h2>
     * 调换顺序即可根除递归 —— {@code stopUsingItem()} 会把 {@code useItem} 置空
     * 并清 {@code LivingEntityFlag}，此后 {@code isUsingItem()} 为 false，
     * 引擎当刻的 {@code updatingUsingItem} 不会再进入 {@code updateUsingItem}
     * （其 offset 1 处就是 {@code isUsingItem} 门禁），
     * 也就不可能重入本方法。
     *
     * <p>至于上一轮想解决的「服务端悄悄退出、客户端不知情」：
     * 那个担忧本身不成立 —— {@code stopUsingItem} 会写
     * {@code DATA_LIVING_ENTITY_FLAGS}（字节码确认走 {@code SynchedEntityData#set}），
     * 而这是<b>会自动同步给客户端</b>的实体数据，客户端据此自行结束使用动画。
     * 原版 {@code Player#stopUsingItem} 之外的所有提前中止走的也都是这条路。
     */
    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack, int remainingUseDuration) {
        this.getThrowableIndex(stack).ifPresent(index -> {
            var data = index.getData();
            if (!data.isCookable()) {
                return;
            }
            // 留 10% 余量：手里预燃太久就直接在手上炸
            int maxCookTime = (int) (data.getEntityData().getLifeTime() * 0.9);
            int ticksUsingItem = entity.getTicksUsingItem();
            if (ticksUsingItem >= data.getPrepareTime() + maxCookTime && !level.isClientSide()) {
                // 顺序至关重要：必须先 stopUsingItem 再 onThrow，理由见方法注释。
                // 反过来（或改用 releaseUsingItem）会导致无限递归 + 无限生成手雷。
                //
                // 也正因为先停了，getTicksUsingItem() 此后会返回 0
                // （其开头就是 if (!isUsingItem()) return 0），
                // 所以蓄力时长必须在停之前取好、显式传给 onThrow。
                entity.stopUsingItem();
                onThrow(level, entity, stack, index, ticksUsingItem);
            }
        });
    }

    private static boolean hasDetonator(Player player) {
        if (player.getMainHandItem().is(ModItems.DETONATOR) || player.getOffhandItem().is(ModItems.DETONATOR)) {
            return true;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack.is(ModItems.DETONATOR)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 松手时投出。
     *
     * <p>26.2 该方法返回 {@code boolean}（见类注释第 3 点）。
     *
     * <p>客户端<b>无条件返回 true</b>。索引现已通过
     * {@code ServerMessageSyncLrPack} 同步，通常两端都有数据；但客户端本来就不生成
     * 权威实体，返回值只用于告诉引擎“这次释放已处理”。即使数据包重载/登录时序使
     * 客户端短暂查不到索引，也不应让两端对释放结果分叉；真正投不投出由服务端决定。
     */
    @Override
    public boolean releaseUsing(@NotNull ItemStack stack, @NotNull Level level,
                                @NotNull LivingEntity entity, int timeLeft) {
        if (level.isClientSide()) {
            return true;
        }
        // 此处 useItem 尚未被清空（releaseUsingItem 是先回调本方法、后 stopUsingItem），
        // 因此 getTicksUsingItem() 仍然有效；取一次并显式传下去，
        // 与 onUseTick 那条路径保持同一约定，避免以后有人在 onThrow 内部现取而踩坑。
        int ticksUsingItem = entity.getTicksUsingItem();
        return this.getThrowableIndex(stack).map(index -> {
            // 准备时间没到就松手 -> 不投出（避免刚点一下就飞出去）
            if (ticksUsingItem < index.getData().getPrepareTime()) {
                return false;
            }
            return onThrow(level, entity, stack, index, ticksUsingItem);
        }).orElse(false);
    }

    @Override
    public boolean useOnRelease(@NotNull ItemStack stack) {
        return true;
    }

    /**
     * 让每种手雷显示各自的名字。
     *
     * <h2>26.2 变更</h2>
     * 上游覆写的是 {@code getDescriptionId(ItemStack)}，但 26.2 的 {@code Item}
     * <b>只剩无参的 {@code getDescriptionId()}</b>（字节码确认），
     * 无法按物品堆返回不同翻译键。
     *
     * <p>正确做法是覆写 {@code getName(ItemStack)} —— 本仓库
     * {@code AbstractGunItem} 为「每把枪不同名字」用的正是这个方法。
     * 不这么做的话，所有手雷都会显示成同一个通用名「投掷物」。
     */
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return this.getThrowableIndex(stack)
                .<Component>map(index -> Component.translatable(index.getDescriptionId()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public boolean isSame(ItemStack stack1, ItemStack stack2) {
        return IThrowable.super.isSame(stack1, stack2);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return this.getThrowableIndex(stack).isPresent()
                ? Optional.of(new me.xjqsh.lrtactical.inventory.tooltip.ThrowableTooltip(stack))
                : Optional.empty();
    }

    /**
     * 自定义渲染器：接入 TACZ 的 Bedrock 模型 + Lua 动画状态机管线。
     *
     * <p>说明与注意事项见 {@link MeleeItem#getCustomRenderer()}。
     */
    @Override
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer() {
        return me.xjqsh.lrtactical.client.renderer.item.ThrowableItemRendererWrapper.INSTANCE.get();
    }

    /**
     * 阻止玩家手臂挥动 —— 拔销/投掷动作由 Lua 动画状态机负责。
     */
    @Override
    public boolean tacz$onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }
}
