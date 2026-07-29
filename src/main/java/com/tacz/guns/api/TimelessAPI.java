package com.tacz.guns.api;

import com.tacz.guns.api.client.other.IThirdPersonAnimation;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TimelessAPI {
    @Environment(EnvType.CLIENT)
    public static Optional<GunDisplayInstance> getGunDisplay(ItemStack stack) {
        if (stack.getItem() instanceof IGun iGun) {
            Identifier gunId = iGun.getGunId(stack);
            if (getCommonGunIndex(gunId).isEmpty()) {
                return Optional.empty();
            }
            Identifier displayId = iGun.getGunDisplayId(stack);
            if (displayId.equals(DefaultAssets.DEFAULT_GUN_DISPLAY_ID)) {
                return getClientGunIndex(gunId).map(ClientGunIndex::getDefaultDisplay);
            } else {
                return getGunDisplay(displayId, gunId);
            }
        }
        return Optional.empty();
    }

    @Environment(EnvType.CLIENT)
    public static Optional<ClientGunIndex> getClientGunIndex(Identifier gunId) {
        return Optional.ofNullable(ClientIndexManager.GUN_INDEX.get(gunId));
    }

    @Environment(EnvType.CLIENT)
    public static Optional<GunDisplayInstance> getGunDisplay(Identifier displayId, Identifier fallbackGunId) {
        if (displayId == null || displayId.equals(DefaultAssets.DEFAULT_GUN_DISPLAY_ID)) {
            return getClientGunIndex(fallbackGunId).map(ClientGunIndex::getDefaultDisplay);
        }

        GunDisplayInstance instance = ClientIndexManager.getOrCreateGunDisplay(displayId);
        if (instance == null) {
            return getClientGunIndex(fallbackGunId).map(ClientGunIndex::getDefaultDisplay);
        }
        return Optional.of(instance);
    }

    @Environment(EnvType.CLIENT)
    public static Optional<ClientAttachmentIndex> getClientAttachmentIndex(Identifier attachmentId) {
        return Optional.ofNullable(ClientIndexManager.ATTACHMENT_INDEX.get(attachmentId));
    }

    @Environment(EnvType.CLIENT)
    public static Optional<ClientAmmoIndex> getClientAmmoIndex(Identifier ammoId) {
        return Optional.ofNullable(ClientIndexManager.AMMO_INDEX.get(ammoId));
    }

    @Environment(EnvType.CLIENT)
    public static Optional<ClientBlockIndex> getClientBlockIndex(Identifier blockId) {
        return Optional.ofNullable(ClientIndexManager.BLOCK_INDEX.get(blockId));
    }

    @Environment(EnvType.CLIENT)
    public static Set<Map.Entry<Identifier, ClientGunIndex>> getAllClientGunIndex() {
        return ClientIndexManager.getAllGuns();
    }

    @Environment(EnvType.CLIENT)
    public static Set<Map.Entry<Identifier, ClientAmmoIndex>> getAllClientAmmoIndex() {
        return ClientIndexManager.getAllAmmo();
    }

    @Environment(EnvType.CLIENT)
    public static Set<Map.Entry<Identifier, ClientAttachmentIndex>> getAllClientAttachmentIndex() {
        return ClientIndexManager.getAllAttachments();
    }

    public static Optional<CommonBlockIndex> getCommonBlockIndex(Identifier blockId) {
        return Optional.ofNullable(CommonAssetsManager.get().getBlockIndex(blockId));
    }

    public static Optional<CommonGunIndex> getCommonGunIndex(Identifier gunId) {
        return Optional.ofNullable(CommonAssetsManager.get().getGunIndex(gunId));
    }

    public static Optional<CommonAttachmentIndex> getCommonAttachmentIndex(Identifier attachmentId) {
        return Optional.ofNullable(CommonAssetsManager.get().getAttachmentIndex(attachmentId));
    }

    public static Optional<CommonAmmoIndex> getCommonAmmoIndex(Identifier ammoId) {
        return Optional.ofNullable(CommonAssetsManager.get().getAmmoIndex(ammoId));
    }

    /**
     * 按 id 取出工作台配方。
     *
     * <p>此前这里的注释写着「请用原版 RecipeManager 获取配方」，在 26.2 上<b>已经过时且会误导</b>：
     * 本项目第 12 轮起工作台配方走 mod 自建的 {@code DataType.RECIPES} 通道
     * （26.2 客户端没有完整配方表），原版 {@code RecipeManager} 既拿不到旧枪包
     * {@code recipes/}（复数）目录里的配方，客户端上更是整个为空。
     * 保留一个恒返回 {@code empty()} 的空壳只会让调用方以为「没有这条配方」。
     *
     * <p>现改为与界面/JEI/REI/合成校验<b>同源</b>，返回真实数据。
     */
    public static Optional<GunSmithTableRecipe> getRecipe(Identifier recipeId) {
        TableRecipe pojo = CommonAssetsManager.get().getTableRecipe(recipeId);
        if (pojo == null || pojo.getResult() == null) {
            return Optional.empty();
        }
        GunSmithTableRecipe recipe = new GunSmithTableRecipe(recipeId, pojo);
        recipe.init();
        return Optional.of(recipe);
    }

    public static Set<Map.Entry<Identifier, CommonBlockIndex>> getAllCommonBlockIndex() {
        return CommonAssetsManager.get().getAllBlocks();
    }

    public static Set<Map.Entry<Identifier, CommonGunIndex>> getAllCommonGunIndex() {
        return CommonAssetsManager.get().getAllGuns();
    }

    public static Set<Map.Entry<Identifier, CommonAmmoIndex>> getAllCommonAmmoIndex() {
        return CommonAssetsManager.get().getAllAmmos();
    }

    public static Set<Map.Entry<Identifier, CommonAttachmentIndex>> getAllCommonAttachmentIndex() {
        return CommonAssetsManager.get().getAllAttachments();
    }

    /**
     * 全部工作台配方。与 {@link #getRecipe(Identifier)} 同源，理由见该方法注释。
     */
    public static Map<Identifier, GunSmithTableRecipe> getAllRecipes() {
        Map<Identifier, GunSmithTableRecipe> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<Identifier, TableRecipe> entry : CommonAssetsManager.get().getAllTableRecipes()) {
            TableRecipe pojo = entry.getValue();
            if (pojo == null || pojo.getResult() == null) {
                continue;
            }
            GunSmithTableRecipe recipe = new GunSmithTableRecipe(entry.getKey(), pojo);
            recipe.init();
            result.put(entry.getKey(), recipe);
        }
        return result;
    }

    public static void registerThirdPersonAnimation(String name, IThirdPersonAnimation animation) {
        ThirdPersonManager.register(name, animation);
    }
}
