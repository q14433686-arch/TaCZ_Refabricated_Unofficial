package com.tacz.guns.mixin.client.carryon;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.item.GunSmithTableItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * 修复 Carry On（1.21.11 线的 2.9.x）搬运 TACZ 工作台时手持模型的「紫黑缺失贴图」。
 *
 * <p>Carry On 渲染被搬运方块时用 {@code CarryRenderHelper#getRenderItemStack} 构造
 * {@code new ItemStack(block)}——不带任何 NBT。而 {@code tacz:workbench_a/b/c} 这类
 * {@link GunSmithTableItem} 的模型由 BlockId NBT 决定（枪包自定义工作台/配件工作台/
 * 弹药工作台各不同），没有 BlockId 就会落入 {@code GunSmithTableItemRenderer} 的
 * MissingTexture 占位分支。只有旧版 {@code tacz:gun_smith_table}
 * （{@code DefaultTableItem}，id 硬编码）不受影响——与玩家观察到的现象一致。</p>
 *
 * <p>这里在被搬运物品栈缺 BlockId 时，从玩家已同步的 Carry On 数据里反射读出被搬运
 * 方块实体的 BlockId 并补回物品栈。只通过字符串 target 与反射和 Carry On 交互，
 * 不要求编译期依赖；未安装 Carry On 时由 {@code CarryOnCompatMixinPlugin} 跳过。</p>
 *
 * <p>26.1.2 / 26.2 的 Carry On（2.10+/2.11+）中该方法的签名变成
 * {@code ItemStackTemplate getRenderItemStack(Player)}（渲染处再 {@code .create()}），
 * 移植到 26.x 时需把本 mixin 的返回值类型与回调改为 {@code ItemStackTemplate} 变体，
 * 见 docs/PORT_TO_26x.md。</p>
 */
@Mixin(targets = "tschipp.carryon.client.render.CarryRenderHelper", remap = false)
public abstract class CarryOnRenderHelperMixin {

    /** 反射句柄缓存：Carry On 未安装/类不存在时保持 null，本 mixin 不生效。 */
    private static volatile Method tacz$getCarryData = null;
    private static volatile Method tacz$getBlockEntity = null;
    private static volatile boolean tacz$reflectionResolved = false;

    @Inject(method = "getRenderItemStack", at = @At("RETURN"), remap = false, cancellable = true, require = 0)
    private static void tacz$injectWorkbenchBlockId(Player player, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = cir.getReturnValue();
        if (!(stack.getItem() instanceof GunSmithTableItem) || !(stack.getItem() instanceof BlockItemDataAccessor accessor)) {
            return;
        }
        // 已有 BlockId（例如 DefaultTableItem 的硬编码 id）就不需要补
        if (!DefaultAssets.EMPTY_BLOCK_ID.equals(accessor.getBlockId(stack))) {
            return;
        }
        Identifier carriedId = tacz$readCarriedBlockId(player);
        if (carriedId != null) {
            accessor.setBlockId(stack, carriedId);
            cir.setReturnValue(stack);
        }
    }

    @Nullable
    private static Identifier tacz$readCarriedBlockId(Player player) {
        try {
            if (!tacz$reflectionResolved) {
                Class<?> managerClass = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
                tacz$getCarryData = managerClass.getMethod("getCarryData", Player.class);
                Class<?> dataClass = Class.forName("tschipp.carryon.common.carry.CarryOnData");
                tacz$getBlockEntity = dataClass.getMethod("getBlockEntity", BlockPos.class, HolderLookup.Provider.class);
                tacz$reflectionResolved = true;
            }
            Object data = tacz$getCarryData.invoke(null, player);
            if (data == null) {
                return null;
            }
            // CarryOnData#getBlockEntity(BlockPos, HolderLookup.Provider)：按保存的 tile NBT
            // 重建被搬运的方块实体（未搬运方块时抛 IllegalStateException，走 catch）
            Object blockEntity = tacz$getBlockEntity.invoke(data, BlockPos.ZERO, player.level().registryAccess());
            if (blockEntity instanceof GunSmithTableBlockEntity table && table.getId() != null) {
                return table.getId();
            }
        } catch (Throwable ignored) {
            // 反射失败 / 未搬运方块 / 数据缺失：保持原渲染行为
        }
        return null;
    }
}
