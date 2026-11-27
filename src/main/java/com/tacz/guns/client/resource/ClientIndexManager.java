package com.tacz.guns.client.resource;

import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.client.resource.index.ClientSlotAdapterIndex;
import com.google.common.collect.Maps;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.AmmoIndexPOJO;
import com.tacz.guns.resource.pojo.AttachmentIndexPOJO;
import com.tacz.guns.resource.pojo.BlockIndexPOJO;
import com.tacz.guns.resource.pojo.GunIndexPOJO;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class ClientIndexManager {
    private static final int HOTBAR_SLOT_COUNT = 9;

    public static final Map<Identifier, GunDisplayInstance> GUN_DISPLAY = Maps.newHashMap();
    public static final Map<Identifier, ClientGunIndex> GUN_INDEX = Maps.newHashMap();
    public static final Map<Identifier, ClientAmmoIndex> AMMO_INDEX = Maps.newHashMap();
    public static final Map<Identifier, ClientAttachmentIndex> ATTACHMENT_INDEX = Maps.newHashMap();
    public static final Map<Identifier, ClientAttachmentVariantIndex> ATTACHMENT_VARIANT_INDEX = Maps.newHashMap();
    public static final Map<Identifier, ClientSlotAdapterIndex> SLOT_ADAPTER_INDEX = Maps.newHashMap();
    public static final Map<Identifier, ClientBlockIndex> BLOCK_INDEX = Maps.newHashMap();

    private static volatile long revision;
    static long revision() { return revision; }
    /** Upstream 1.2.0: warn once per missing display per reload instead of on every lookup. */
    private static final java.util.Set<Identifier> WARNED_MISSING_GUN_DISPLAYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void clear() {
        com.tacz.guns.client.model.papi.RangefinderReadout.clear();
        GUN_DISPLAY.values().forEach(GunDisplayInstance::retireModels);
        ++revision;
        GUN_DISPLAY.clear();
        GUN_INDEX.clear();
        AMMO_INDEX.clear();
        ATTACHMENT_INDEX.clear();
        ATTACHMENT_VARIANT_INDEX.clear();
        SLOT_ADAPTER_INDEX.clear();
        BLOCK_INDEX.clear();
        WARNED_MISSING_GUN_DISPLAYS.clear();
    }

    public static void reload() {
        clear();
        loadGunDisplay();
        loadGunIndex();
        loadAmmoIndex();
        loadAttachmentIndex();
        loadBlockIndex();
        TimelessAPI.getAllCommonAttachmentVariantIndex().forEach(entry -> {
            try { ATTACHMENT_VARIANT_INDEX.put(entry.getKey(), new ClientAttachmentVariantIndex(entry.getValue().getPojo())); }
            catch (IllegalArgumentException e) { GunMod.LOGGER.warn("Invalid variant display for {}", entry.getKey(), e); }
        });
        TimelessAPI.getAllCommonSlotAdapterIndex().forEach(entry -> {
            try { SLOT_ADAPTER_INDEX.put(entry.getKey(), new ClientSlotAdapterIndex(entry.getValue().getPojo())); }
            catch (IllegalArgumentException e) { GunMod.LOGGER.warn("Invalid slot adapter display for {}", entry.getKey(), e); }
        });
        // Native cache parameters include features/permissions/registries, but not gun-pack data.
        // Invalidate on the client thread so the open/next creative screen also rebuilds search tabs.
        com.tacz.guns.mixin.client.CreativeModeTabsAccessor.tacz$setCachedParameters(null);
        warmUpInventoryModels();

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && IGun.mainHandHoldGun(player)) {
            AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());

            // 自动切一次枪，以便刷新状态机
            IClientPlayerGunOperator.fromLocalPlayer(player).draw(ItemStack.EMPTY);
            // 26.2 已解决: FirstPersonRenderHandler.reset() 的功能已由
            // AnimateGeoItemRenderer.needReInit()/tryInit() 自动重初始化机制取代。
        }
    }

    public static void loadGunIndex() {
        TimelessAPI.getAllCommonGunIndex().forEach(index -> {
            Identifier id = index.getKey();
            GunIndexPOJO pojo = index.getValue().getPojo();
            try {
                GUN_INDEX.put(id, ClientGunIndex.getInstance(pojo));
            } catch (IllegalArgumentException exception) {
                GunMod.LOGGER.warn("{} index file read fail!", id, exception);
            }
        });
    }

    public static void loadGunDisplay() {
        ClientAssetsManager.INSTANCE.getGunDisplays().forEach(entry -> {
            Identifier displayId = entry.getKey();
            try {
                GUN_DISPLAY.put(displayId, GunDisplayInstance.create(displayId, entry.getValue()));
            } catch (IllegalArgumentException exception) {
                GunMod.LOGGER.warn("{} display file read fail!", displayId, exception);
            }
        });
    }

    public static GunDisplayInstance getOrCreateGunDisplay(Identifier displayId) {
        GunDisplayInstance instance = GUN_DISPLAY.get(displayId);
        if (instance != null) {
            return instance;
        }
        if (WARNED_MISSING_GUN_DISPLAYS.add(displayId)) {
            GunMod.LOGGER.warn("Gun display {} is unavailable (assets/{}/display/guns/{}.json);",
                    displayId, displayId.getNamespace(), displayId.getPath());
        }
        return null;
    }

    public static void loadAmmoIndex() {
        TimelessAPI.getAllCommonAmmoIndex().forEach(index -> {
            Identifier id = index.getKey();
            AmmoIndexPOJO pojo = index.getValue().getPojo();
            try {
                AMMO_INDEX.put(id, ClientAmmoIndex.getInstance(pojo));
            } catch (IllegalArgumentException exception) {
                GunMod.LOGGER.warn("{} index file read fail!", id, exception);
            }
        });
    }

    public static void loadAttachmentIndex() {
        TimelessAPI.getAllCommonAttachmentIndex().forEach(index -> {
            Identifier id = index.getKey();
            AttachmentIndexPOJO pojo = index.getValue().getPojo();
            try {
                ATTACHMENT_INDEX.put(id, ClientAttachmentIndex.getInstance(id, pojo));
            } catch (IllegalArgumentException exception) {
                GunMod.LOGGER.warn("{} index file read fail!", id, exception);
            }
        });
    }

    public static void loadBlockIndex() {
        TimelessAPI.getAllCommonBlockIndex().forEach(index -> {
            Identifier id = index.getKey();
            BlockIndexPOJO pojo = index.getValue().getPojo();
            try {
                BLOCK_INDEX.put(id, ClientBlockIndex.getInstance(pojo));
            } catch (IllegalArgumentException exception) {
                GunMod.LOGGER.warn("{} index file read fail!", id, exception);
            }
        });
    }

    public static Set<Map.Entry<Identifier, ClientGunIndex>> getAllGuns() {
        return GUN_INDEX.entrySet();
    }

    public static Set<Map.Entry<Identifier, ClientAmmoIndex>> getAllAmmo() {
        return AMMO_INDEX.entrySet();
    }

    public static Set<Map.Entry<Identifier, ClientAttachmentIndex>> getAllAttachments() {
        return ATTACHMENT_INDEX.entrySet();
    }

    public static Set<Map.Entry<Identifier, ClientBlockIndex>> getAllBlocks() {
        return BLOCK_INDEX.entrySet();
    }

    public static void warmUpInventoryModels() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        warmUpItemForUse(player.getMainHandItem());
        warmUpItemForUse(player.getOffhandItem());
        warmUpHotbarModels(player);
        warmUpBackpackModels(player);
    }

    public static void warmUpEquippedAndHotbarModels() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        warmUpItemForUse(player.getMainHandItem());
        warmUpItemForUse(player.getOffhandItem());
        warmUpHotbarModels(player);
    }

    public static void warmUpBackpackModels() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        warmUpBackpackModels(player);
    }

    private static void warmUpHotbarModels(LocalPlayer player) {
        var items = player.getInventory().getNonEquipmentItems();
        int hotbarSize = Math.min(HOTBAR_SLOT_COUNT, items.size());
        for (int i = 0; i < hotbarSize; i++) {
            warmUpItemModel(items.get(i));
        }
    }

    private static void warmUpBackpackModels(LocalPlayer player) {
        var items = player.getInventory().getNonEquipmentItems();
        for (int i = Math.min(HOTBAR_SLOT_COUNT, items.size()); i < items.size(); i++) {
            warmUpItemModel(items.get(i));
        }
    }

    public static void warmUpItem(ItemStack stack) {
        warmUpItemForUse(stack);
    }

    public static void warmUpItemModel(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof IGun) {
            TimelessAPI.getGunDisplay(stack).ifPresent(display -> {
                display.warmUpLod();
                display.warmUpModel();
                display.warmUpRuntime();
            });
            return;
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment != null) {
            TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(stack)).ifPresent(ClientAttachmentIndex::warmUp);
            TimelessAPI.getClientAttachmentVariantIndex(stack).ifPresent(ClientAttachmentVariantIndex::warmUp);
            return;
        }
        IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
        if (ammo != null) {
            TimelessAPI.getClientAmmoIndex(ammo.getAmmoId(stack)).ifPresent(ClientAmmoIndex::warmUp);
        }
    }

    public static void warmUpItemForUse(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof IGun) {
            TimelessAPI.getGunDisplay(stack).ifPresent(display -> {
                display.warmUpLod();
                display.warmUpModel();
                display.warmUpRuntime();
            });
            return;
        }
        warmUpItemModel(stack);
    }
}
