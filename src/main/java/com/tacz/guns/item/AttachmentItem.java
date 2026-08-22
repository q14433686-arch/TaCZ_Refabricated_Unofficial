package com.tacz.guns.item;

import cn.sh1rocu.tacz.api.extension.IItem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.renderer.item.AttachmentItemRenderer;
import com.tacz.guns.inventory.tooltip.AttachmentItemTooltip;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import static com.tacz.guns.util.datafixer.AttachmentIdFix.updateAttachmentIdInTag;

public class AttachmentItem extends Item implements AttachmentItemDataAccessor, IItem {
    public AttachmentItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * 获取配件的显示名称。
     *
     * <p>{@code Item#getName(ItemStack)} 是双端公共方法（{@code /give} 回执、容器标题、
     * 铁砧改名等服务端路径都会调用），因此<b>不能</b>读只在客户端存在的
     * {@code ClientAttachmentIndex}：挂 {@code @Environment(CLIENT)} 会被 fabric-loader 在专服上
     * 把整个方法剥离（枪包名字静默退化成原版兜底名），只删注解不改实现则会
     * {@code NoClassDefFoundError}。完整推导见
     * {@link com.tacz.guns.api.item.gun.AbstractGunItem#getName(net.minecraft.world.item.ItemStack)}。
     *
     * <p>common/client 两侧索引取的是同一份 index json 的同一个 {@code name} 键，
     * 客户端显示结果不变。
     */
    @Override
    @Nonnull
    public Component getName(@Nonnull ItemStack stack) {
        Identifier attachmentId = this.getAttachmentId(stack);
        Optional<CommonAttachmentIndex> attachmentIndex = TimelessAPI.getCommonAttachmentIndex(attachmentId);
        if (attachmentIndex.isPresent() && attachmentIndex.get().getPojo() != null) {
            String name = attachmentIndex.get().getPojo().getName();
            return Component.translatable(StringUtils.isBlank(name) ? "custom.tacz.error.no_name" : name);
        }
        return super.getName(stack);
    }

    private static Comparator<Map.Entry<Identifier, CommonAttachmentIndex>> idNameSort() {
        return Comparator.comparingInt(m -> m.getValue().getSort());
    }

    public static NonNullList<ItemStack> fillItemCategory(AttachmentType type) {
        NonNullList<ItemStack> stacks = NonNullList.create();
        TimelessAPI.getAllCommonAttachmentIndex().stream().sorted(idNameSort()).forEach(entry -> {
            if (entry.getValue().getPojo().isHidden()) {
                return;
            }
            if (type.equals(entry.getValue().getType())) {
                ItemStack itemStack = AttachmentItemBuilder.create().setId(entry.getKey()).build();
                stacks.add(itemStack);
            }
        });
        return stacks;
    }

    @Environment(EnvType.CLIENT)
    @Override
    public BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer() {
        return AttachmentItemRenderer.INSTANCE.get();
    }

    @Override
    @Nonnull
    public AttachmentType getType(ItemStack attachmentStack) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        if (iAttachment != null) {
            Identifier id = iAttachment.getAttachmentId(attachmentStack);
            return TimelessAPI.getCommonAttachmentIndex(id).map(CommonAttachmentIndex::getType).orElse(AttachmentType.NONE);
        } else {
            return AttachmentType.NONE;
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new AttachmentItemTooltip(this.getAttachmentId(stack), this.getType(stack), stack));
    }

    public void verifyTagAfterLoad(@NotNull CompoundTag tag) {
        updateAttachmentIdInTag(tag);
    }
}
