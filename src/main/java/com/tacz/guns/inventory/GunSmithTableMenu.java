package com.tacz.guns.inventory;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageCraft;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class GunSmithTableMenu extends AbstractContainerMenu {
    // 26.2: 使用 ExtendedMenuType 配合 StreamCodec 传递 Identifier 数据
    public static final ExtendedMenuType<GunSmithTableMenu, Identifier> TYPE = new ExtendedMenuType<>(
            (windowId, inv, data) -> new GunSmithTableMenu(windowId, inv, data),
            Identifier.STREAM_CODEC);

    private final Identifier blockId;
    private final RecipeFilter filter;

    public GunSmithTableMenu(int id, Inventory inventory, @Nullable Identifier resourceLocation) {
        super(TYPE, id);
        this.blockId = resourceLocation;
        this.filter = TimelessAPI.getCommonBlockIndex(getBlockId()).map(CommonBlockIndex::getFilter).orElse(null);
    }

    @Nullable
    public Identifier getBlockId() {
        return blockId;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int pIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    /**
     * 按 id 取出工作台配方并做权限校验。
     *
     * <h2>为什么改用 {@code CommonAssetsManager}，不再走原版 {@code RecipeManager}</h2>
     * 原先这里是 {@code recipeManager.byKey(...)}（照搬上游 1.21.1）。但本项目在第 12 轮
     * 已经把工作台配方整体迁到 mod 自建的 {@code DataType.RECIPES} 通道
     * （原因：26.2 客户端没有完整配方表，见 {@code ICommonResourceProvider#getTableRecipe}），
     * <b>界面、JEI、REI 三处早就统一从 {@code CommonAssetsManager} 取数了，
     * 唯独这个真正执行合成的服务端方法还留在原版通道上</b> —— 两套数据源就此分叉。
     *
     * <p>分叉的后果是「<b>看得见、点不动</b>」：只要一条配方存在于我们的通道、
     * 却不存在于原版 {@code RecipeManager}，界面就会正常列出它、材料数量也正常统计，
     * 但点合成时 {@code byKey} 返回空 → 本方法返回 {@code null} →
     * {@code doCraft} 直接 return，<b>不报错、不提示、不扣材料</b>。
     *
     * <p>实测触发场景：旧枪包把配方放在 {@code data/<ns>/recipes/}（复数）。
     * 我们的 {@code TableRecipeManager#prepare} 已特意兼容了这个旧目录，
     * 但原版 {@code RecipeManager} 的 {@code RECIPE_LISTER} 是
     * {@code FileToIdConverter.registry(Registries.RECIPE)}，字节码逐级确认其目录名取自
     * {@code registryDirPath} → {@code ResourceKey.identifier().getPath()} = {@code "recipe"}
     * （<b>单数，且是常量，无法扩展</b>）。于是这些配方对原版通道<b>永远不可见</b>，
     * 旧枪包的每一条配方都合不出来。
     *
     * <p>改成与界面同源后，两侧判据完全一致，旧目录/新目录、默认包/第三方包一视同仁。
     * 校验逻辑（过滤器 + 页签归属）原样保留，<b>不放宽任何限制</b> ——
     * 仍然只有「当前方块页签里真实存在」的配方才允许合成。
     */
    @Nullable
    private GunSmithTableRecipe getRecipe(Identifier recipeId) {
        if (!DefaultAssets.DEFAULT_BLOCK_ID.equals(getBlockId()) || SyncConfig.ENABLE_TABLE_FILTER.get()) {
            if (filter != null && !filter.contains(recipeId)) {
                return null;
            }
        }

        TableRecipe pojo = CommonAssetsManager.get().getTableRecipe(recipeId);
        if (pojo == null || pojo.getResult() == null) {
            return null;
        }
        GunSmithTableRecipe gunSmithTableRecipe = new GunSmithTableRecipe(recipeId, pojo);
        // 必须 init()：Gson 反序列化只填了 raw 数据，
        // 真正的 ItemStack 与 group(=页签) 要靠它解析出来。
        // 少了这一步 getTab() 恒为 null，下面的页签校验必然失败。
        gunSmithTableRecipe.init();

        // Keep this server-side whitelist in lockstep with the client screen:
        // declared data-pack tabs plus the finite code-owned TACZ pages (which
        // include the dedicated industry_* pages) are valid. RecipeFilter
        // remains the authority over which recipe IDs a specific table admits.
        boolean flag = TimelessAPI.getCommonBlockIndex(getBlockId()).map(blockIndex ->
                !blockIndex.getData().supportsTab(gunSmithTableRecipe.getTab())
        ).orElse(true);
        if (DefaultAssets.DEFAULT_BLOCK_ID.equals(getBlockId()) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
            flag = false;
        }
        if (flag) {
            return null;
        }
        return gunSmithTableRecipe;
    }

    public void doCraft(Identifier recipeId, Player player) {
        // 仍然要求服务端环境：合成必须由服务端权威执行（生成掉落物、扣材料）。
        // 配方数据本身已改从 CommonAssetsManager 取，不再依赖 level 的 RecipeManager，见 getRecipe。
        Level level = player.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        GunSmithTableRecipe recipe = getRecipe(recipeId);
        if (recipe == null) {
            return;
        }
        player.tacz$getItemHandler(null).ifPresent(handler -> {
            // 是创造模式，就不扣材料
            if (!player.isCreative()) {
                Int2IntArrayMap recordCount = new Int2IntArrayMap();
                List<GunSmithTableIngredient> ingredients = recipe.getInputs();

                for (GunSmithTableIngredient ingredient : ingredients) {
                    int count = 0;
                    // 第 14 轮：材料延迟解析。若解析不出来（tag 缺失等），
                    // 必须<b>拒绝合成</b>而不是跳过该材料 —— 否则玩家能白嫖成品。
                    net.minecraft.world.item.crafting.Ingredient resolved = ingredient.getIngredient();
                    if (resolved == null) {
                        return;
                    }
                    for (int slotIndex = 0; slotIndex < handler.getSlots(); slotIndex++) {
                        ItemStack stack = handler.getStackInSlot(slotIndex);
                        int stackCount = stack.getCount();
                        if (!stack.isEmpty() && resolved.test(stack)) {
                            count = count + stackCount;
                            // Blueprints/tooling still have to be present, but
                            // are deliberately excluded from the extraction
                            // record after validation succeeds.
                            if (ingredient.isConsumed()) {
                                // 记录扣除的 slot 和数量
                                if (count <= ingredient.getCount()) {
                                    // 如果数量不足，全扣
                                    recordCount.put(slotIndex, stackCount);
                                } else {
                                    // 数量够了，只扣需要的数量
                                    int remaining = count - ingredient.getCount();
                                    recordCount.put(slotIndex, stackCount - remaining);
                                    break;
                                }
                            } else if (count >= ingredient.getCount()) {
                                break;
                            }
                        }
                    }
                    // 数量不够，不执行后续逻辑，合成失败
                    if (count < ingredient.getCount()) {
                        return;
                    }
                }

                // 开始扣材料
                for (int slotIndex : recordCount.keySet()) {
                    handler.extractItem(slotIndex, recordCount.get(slotIndex), false);
                }
            }

            // 给玩家对应的物品
            if (!level.isClientSide()) {
                ItemEntity itemEntity = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), recipe.getOutput().copy());
                itemEntity.setPickUpDelay(0);
                level.addFreshEntity(itemEntity);
            }
            // 更新，否则客户端显示不正确
            player.inventoryMenu.broadcastFullState();
            if (player instanceof ServerPlayer serverPlayer)
                NetworkHandler.sendToClientPlayer(new ServerMessageCraft(this.containerId), serverPlayer);
        });
    }
}
