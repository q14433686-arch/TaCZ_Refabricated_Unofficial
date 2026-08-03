package com.tacz.guns.client.gui.overlay;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.client.input.InteractKey;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.InteractKeyConfigRead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.apache.commons.lang3.StringUtils;

/**
 * 持枪时的交互提示。
 *
 * <h2>本轮修复：此前是一个被简化过头的重写</h2>
 *
 * <p>对照上游 1.21.1，旧实现丢了 4 处行为，玩家看到的提示与原版观感差别明显：</p>
 *
 * <ol>
 *   <li><b>整条「工作台按手中物品过滤」提示不见了。</b> 上游在瞄准工作台且
 *       {@code AUTO_SELECT_GUN_SMITH_TABLE_FILTER} 开启时，会在主提示下方多画一行灰色
 *       {@code gui.tacz.interact_key.text.gun_smith_table_filter}。
 *       该 lang key 我们<b>全部 20 个语言文件里都有</b>，却没有任何代码引用它 ——
 *       等于翻译白翻了，功能提示也丢了。</li>
 *
 *   <li><b>颜色错了。</b> 上游主提示是 {@code ChatFormatting.YELLOW}（0xFFFF55），
 *       旧实现写死白色。黄色是「可交互」的视觉信号，白色削弱了这层含义。</li>
 *
 *   <li><b>位置错了。</b> 上游画在 {@code height/2 - 25}（准星<b>上方</b>），
 *       旧实现画在 {@code height/2 + 44}（准星下方 44px），
 *       那个位置在 16:9 下会和物品栏/弹药 HUD 打架。</li>
 *
 *   <li><b>空手时不再提示。</b> 上游有一条专门的分支：瞄准工作台、<b>手里拿着</b>
 *       枪/配件/弹药但<b>不是</b>持枪状态时，用<b>原版使用键</b>
 *       （{@code options.keyUse}）而不是 TACZ 交互键来提示 ——
 *       因为这种情况下玩家是用右键开工作台的。旧实现在开头就
 *       {@code !IGun.mainHandHoldGun(player) return}，把这条路整个堵死了。</li>
 * </ol>
 *
 * <p>另外上游会对键名做 {@code StringUtils.capitalize}（"r" → "R"），
 * 旧实现直接塞 {@code Component}，小写键名看起来像 bug。这里一并对齐。</p>
 *
 * <h2>26.2 侧的两处改写</h2>
 * <ul>
 *   <li>{@code GuiGraphics#drawString} → {@code GuiGraphicsExtractor#text}。</li>
 *   <li><b>颜色取值按 26.1.2 的 API 写</b>：26.1.2 的 {@code ChatFormatting#getColor()}
 *       依然存在（已对 26.1.2 jar 字节码确认，返回 {@code Integer}），
 *       与上游 1.21.1 写法一致；而 26.2 补丁用的 {@code TextColor.YELLOW/GRAY}
 *       具名常量是<b>26.2 新增</b>的（26.1.2 的 {@code TextColor} 只有
 *       {@code CODEC/NAMED_COLORS} 等几个字段，没有任何具名颜色常量），直接套用会编译失败。
 *       同理，「{@code ChatFormatting#getColor()} 已删除」是 26.2 的变更，不适用于本版。
 *       必须补 alpha —— {@code ChatFormatting#getColor()} 给的是六位色，
 *       {@code text()} 见 alpha=0 会整段短路丢弃（见 docs/PORTING_NOTES.md §1）。</li>
 * </ul>
 */
public class InteractKeyTextOverlay {
    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (RenderConfig.DISABLE_INTERACT_HUD_TEXT.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) {
            return;
        }
        HitResult hitResult = mc.hitResult;
        if (hitResult == null) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        if (hitResult instanceof BlockHitResult blockHitResult) {
            renderBlockText(graphics, width, height, blockHitResult, player, mc);
            return;
        }
        if (hitResult instanceof EntityHitResult entityHitResult) {
            renderEntityText(graphics, width, height, entityHitResult, mc);
        }
    }

    private static void renderBlockText(GuiGraphicsExtractor graphics, int width, int height,
                                        BlockHitResult blockHitResult, LocalPlayer player, Minecraft mc) {
        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState block = player.level().getBlockState(blockPos);
        if (!InteractKeyConfigRead.canInteractBlock(block)) {
            return;
        }
        boolean mainHandHoldGun = IGun.mainHandHoldGun(player);
        boolean hasGunSmithTableFilterItem = hasGunSmithTableFilterItem(block, player);
        boolean willFilterByHand = RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.get() && hasGunSmithTableFilterItem;
        if (mainHandHoldGun) {
            // 持枪：用 TACZ 的交互键
            renderText(graphics, width, height, mc.font,
                    InteractKey.INTERACT_KEY.getTranslatedKeyMessage().getString(), willFilterByHand);
        } else if (hasGunSmithTableFilterItem) {
            // 未持枪但手里有枪/配件/弹药：走原版使用键（右键开工作台）
            renderText(graphics, width, height, mc.font,
                    mc.options.keyUse.getTranslatedKeyMessage().getString(), willFilterByHand);
        }
    }

    private static void renderEntityText(GuiGraphicsExtractor graphics, int width, int height,
                                         EntityHitResult entityHitResult, Minecraft mc) {
        if (mc.player == null || !IGun.mainHandHoldGun(mc.player)) {
            return;
        }
        Entity entity = entityHitResult.getEntity();
        if (InteractKeyConfigRead.canInteractEntity(entity)) {
            renderText(graphics, width, height, mc.font,
                    InteractKey.INTERACT_KEY.getTranslatedKeyMessage().getString(), false);
        }
    }

    private static boolean hasGunSmithTableFilterItem(BlockState block, LocalPlayer player) {
        if (!(block.getBlock() instanceof AbstractGunSmithTableBlock)) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        Item item = stack.getItem();
        return item instanceof IGun || item instanceof IAttachment || item instanceof IAmmo;
    }

    private static void renderText(GuiGraphicsExtractor graphics, int width, int height,
                                   Font font, String keyName, boolean willFilterByHand) {
        Component title = Component.translatable("gui.tacz.interact_key.text.desc", StringUtils.capitalize(keyName));
        // 颜色补 alpha：ChatFormatting#getColor 给的是六位色，text() 会把 alpha=0 的整段丢弃。
        // （26.1.2 用上游同款 ChatFormatting；TextColor.YELLOW/GRAY 具名常量是 26.2 才有的。）
        graphics.text(font, title,
                (int) ((width - font.width(title)) / 2.0f), (int) (height / 2.0f - 25),
                0xFF000000 | ChatFormatting.YELLOW.getColor(), false);
        if (willFilterByHand) {
            Component filter = Component.translatable("gui.tacz.interact_key.text.gun_smith_table_filter");
            graphics.text(font, filter,
                    (int) ((width - font.width(filter)) / 2.0f), (int) (height / 2.0f - 14),
                    0xFF000000 | ChatFormatting.GRAY.getColor(), false);
        }
    }
}
