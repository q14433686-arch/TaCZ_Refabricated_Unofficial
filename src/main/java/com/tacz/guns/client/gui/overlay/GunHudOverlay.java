package com.tacz.guns.client.gui.overlay;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.resource.pojo.display.gun.AmmoCountStyle;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.industry.maintenance.IndustryMaintenanceService;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.text.DecimalFormat;

/**
 * 26.2 HUD implementation using Fabric HudElementRegistry + GuiGraphicsExtractor.
 */
public class GunHudOverlay {
    private static final Identifier FIRE_MODE_SEMI =
            Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/hud/fire_mode_semi.png");
    private static final Identifier FIRE_MODE_AUTO =
            Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/hud/fire_mode_auto.png");
    private static final Identifier FIRE_MODE_BURST =
            Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/hud/fire_mode_burst.png");

    /** 版本串每次都拼是浪费，缓存一次即可。 */
    private static String cachedVersionText = null;

    private static final DecimalFormat CURRENT_AMMO_FORMAT = new DecimalFormat("000");
    private static final DecimalFormat CURRENT_AMMO_FORMAT_PERCENT = new DecimalFormat("000%");
    private static final DecimalFormat INVENTORY_AMMO_FORMAT = new DecimalFormat("0000");
    private static long checkAmmoTimestamp = -1L;
    private static int cacheMaxAmmoCount = 0;
    private static int cacheInventoryAmmoCount = 0;

    private static final int MAX_AMMO_COUNT = 9999;

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (!RenderConfig.GUN_HUD_ENABLE.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!(player instanceof IClientPlayerGunOperator)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        Identifier gunId = iGun.getGunId(stack);

        GunData gunData = TimelessAPI.getClientGunIndex(gunId).map(ClientGunIndex::getGunData).orElse(null);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (gunData == null) {
            return;
        }

        boolean useInventoryAmmo = iGun.useInventoryAmmo(stack);
        boolean useDummyAmmo = iGun.useDummyAmmo(stack);
        boolean overheatLocked = gunData.hasHeatData() && iGun.isOverheatLocked(stack);
        handleCacheCount(player, stack, gunData, iGun, useInventoryAmmo);

        int ammoCount = useInventoryAmmo ? cacheInventoryAmmoCount + (iGun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0) :
                iGun.getCurrentAmmoCount(stack) + (iGun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0);
        ammoCount = Math.min(ammoCount, MAX_AMMO_COUNT);

        // 【顺序】先算缓存, 再画 —— 上游是先画后算, 导致首帧用的是上一帧的
        // cacheMaxAmmoCount, 百分比模式下切枪瞬间会闪一下错误数字。这里修正。
        handleCacheCount(player, stack, gunData, iGun, useInventoryAmmo);

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // ===== 以下布局逐项对照上游 1.21.1 GunHudOverlay =====

        // 弹药数颜色: 余弹告急 / 过热 -> 红; 背包直读+虚拟备弹 -> 青;
        // 仅背包直读 -> 黄; 其余 -> 白
        int ammoCountColor;
        if (ammoCount < (cacheMaxAmmoCount * 0.25) && ammoCount < 10 || overheatLocked) {
            ammoCountColor = 0xFFFF5555;
        } else {
            ammoCountColor = useInventoryAmmo && useDummyAmmo ? 0xFF55FFFF
                    : useInventoryAmmo ? 0xFFFFFF55 : 0xFFFFFFFF;
        }
        // 备弹颜色
        int inventoryAmmoCountColor = (!useInventoryAmmo && useDummyAmmo) ? 0xFF55FFFF : 0xFFAAAAAA;

        // 当前弹药数文本
        String currentAmmoCountText;
        if (display != null && display.getAmmoCountStyle() == AmmoCountStyle.PERCENT) {
            currentAmmoCountText = CURRENT_AMMO_FORMAT_PERCENT.format(
                    (float) ammoCount / (cacheMaxAmmoCount == 0 ? 1f : cacheMaxAmmoCount));
        } else {
            currentAmmoCountText = CURRENT_AMMO_FORMAT.format(ammoCount);
        }

        // 备弹文本: 背包直读模式不显示备弹; 无限备弹显示 ∞
        String inventoryAmmoCountText = useInventoryAmmo ? ""
                : INVENTORY_AMMO_FORMAT.format(Math.min(cacheInventoryAmmoCount, MAX_AMMO_COUNT));
        if (!useInventoryAmmo && gunData.getReloadData().isInfinite()) {
            inventoryAmmoCountText = "\u221e";
        }

        Font font = mc.font;
        Matrix3x2fStack poseStack = graphics.pose();

        // 竖线分隔符
        graphics.fill(width - 75, height - 43, width - 74, height - 25, 0xFFFFFFFF);

        // 当前弹药数 (1.5 倍字号)
        poseStack.pushMatrix();
        poseStack.scale(1.5f, 1.5f);
        graphics.text(font, currentAmmoCountText,
                (int) ((width - 70) / 1.5f), (int) ((height - 43) / 1.5f), ammoCountColor, false);
        poseStack.popMatrix();

        // 备弹数 (0.8 倍字号, 紧跟在当前弹药数右侧)
        poseStack.pushMatrix();
        poseStack.scale(0.8f, 0.8f);
        graphics.text(font, inventoryAmmoCountText,
                (int) ((width - 68 + font.width(currentAmmoCountText) * 1.5f) / 0.8f),
                (int) ((height - 43) / 0.8f), inventoryAmmoCountColor, false);
        poseStack.popMatrix();

        // 版本信息。
        //
        // 【本轮修复：防溢出】上游写死 0.5 倍字号并从 x = width-70 起画，
        // 因为它的串是 "1.21.1-1.1.8"（≈56 字体像素 → 屏上 28px），70px 的余量绰绰有余。
        //
        // 本移植的版本号带构建元数据，形如 "26.2-1.1.8+fabric.26.2.Beta-2"
        // ≈146 字体像素 → 0.5 倍下仍有 73px，比 70px 的可用宽度还长 ——
        // 直接画会顶出屏幕右边缘（且枪包/附属自带的更长版本号会更糟）。
        //
        // 这里不截断（版本号被截掉反而不利于反馈问题），改为按可用宽度自适应缩小字号：
        // 以 0.5 为上限，必要时等比缩小，下限 0.25 以免小到看不清。
        // 可用宽度取 70 - 2 的安全边距，与竖线/弹药数那一列的左边界对齐。
        String versionLine = versionText();
        final int versionAvailable = 70 - 2;
        int versionRawWidth = font.width(versionLine);
        float versionScale = 0.5f;
        if (versionRawWidth * versionScale > versionAvailable) {
            versionScale = Math.max(0.25f, versionAvailable / (float) versionRawWidth);
            // 缩到下限仍放不下（枪包/附属自定义了超长版本号）时才截断，
            // 保证无论如何都不会画出屏幕。
            if (versionRawWidth * versionScale > versionAvailable) {
                int budget = (int) (versionAvailable / versionScale);
                versionLine = font.plainSubstrByWidth(versionLine, budget - font.width("...")) + "...";
            }
        }
        poseStack.pushMatrix();
        poseStack.scale(versionScale, versionScale);
        graphics.text(font, versionLine,
                (int) ((width - 70) / versionScale), (int) ((height - 29f) / versionScale), 0xFFAAAAAA, false);
        poseStack.popMatrix();

        // 枪械图标。弹尽/过热时若有专用空仓图标就换图, 否则染红。
        if (display != null) {
            Identifier hudTexture = display.getHUDTexture();
            Identifier hudEmptyTexture = display.getHudEmptyTexture();
            int hudTint = 0xFFFFFFFF;
            if (ammoCount <= 0 || overheatLocked) {
                if (hudEmptyTexture == null) {
                    // 上游用 RenderSystem.setShaderColor(1,0.3,0.3,1); 26.2 该 API 已移除,
                    // 改用 blit 的 tint 参数 —— 等价且不依赖全局状态。
                    hudTint = 0xFFFF4D4D;
                } else {
                    hudTexture = hudEmptyTexture;
                }
            }
            if (hudTexture != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, hudTexture,
                        width - 117, height - 44, 0.0F, 0.0F, 39, 13, 39, 13, hudTint);
            }
        }

        // 开火模式图标
        Identifier fireModeTexture = switch (iGun.getFireMode(stack)) {
            case AUTO -> FIRE_MODE_AUTO;
            case BURST -> FIRE_MODE_BURST;
            default -> FIRE_MODE_SEMI;
        };
        graphics.blit(RenderPipelines.GUI_TEXTURED, fireModeTexture,
                (int) (width - 68.5 + font.width(currentAmmoCountText) * 1.5), height - 38,
                0.0F, 0.0F, 10, 10, 10, 10);

        // Phase-A maintenance HUD: two compact server-NBT-backed bars above
        // the existing gun card. The upper bar is the worst component
        // condition; the lower bar is fouling. They are informational only:
        // no jam/lockout is enabled in this phase.
        IndustryMaintenanceService.Snapshot maintenance = IndustryMaintenanceService.getSnapshot(stack);
        if (maintenance.eligible()) {
            final int maintenanceLeft = width - 117;
            final int maintenanceTop = height - 52;
            final int maintenanceWidth = 39;
            int conditionWidth = Math.round(maintenanceWidth * maintenance.minimumCondition()
                    / (float) IndustryMaintenanceService.MAX_CONDITION);
            int foulingWidth = Math.round(maintenanceWidth * maintenance.fouling()
                    / (float) IndustryMaintenanceService.MAX_CONDITION);
            graphics.fill(maintenanceLeft, maintenanceTop, maintenanceLeft + maintenanceWidth,
                    maintenanceTop + 3, 0xAA202020);
            graphics.fill(maintenanceLeft, maintenanceTop, maintenanceLeft + conditionWidth,
                    maintenanceTop + 3, 0xFF000000 | maintenance.status().color());
            graphics.fill(maintenanceLeft, maintenanceTop + 4, maintenanceLeft + maintenanceWidth,
                    maintenanceTop + 6, 0xAA202020);
            graphics.fill(maintenanceLeft, maintenanceTop + 4, maintenanceLeft + foulingWidth,
                    maintenanceTop + 6, 0xFFC98647);
        }
    }

    /** 版本信息文本，格式与上游一致：{@code <MC版本>-<模组版本>}。 */
    private static String versionText() {
        if (cachedVersionText == null) {
            String mcVersion = SharedConstants.getCurrentVersion().name();
            String modVersion = FabricLoader.getInstance().getModContainer(GunMod.MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
            cachedVersionText = mcVersion + "-" + modVersion;
        }
        return cachedVersionText;
    }

    private static void handleCacheCount(LocalPlayer player, ItemStack stack, GunData gunData, IGun iGun, boolean useInventoryAmmo) {
        if ((System.currentTimeMillis() - checkAmmoTimestamp) > 50) {
            checkAmmoTimestamp = System.currentTimeMillis();
            cacheMaxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData);
            if (IGunOperator.fromLivingEntity(player).needCheckAmmo()) {
                if (iGun.useDummyAmmo(stack)) {
                    cacheInventoryAmmoCount = iGun.getDummyAmmoAmount(stack);
                } else {
                    handleInventoryAmmo(stack, player.getInventory());
                }
            } else {
                cacheInventoryAmmoCount = MAX_AMMO_COUNT;
            }
            if (useInventoryAmmo) {
                iGun.setCurrentAmmoCount(stack, cacheInventoryAmmoCount);
            }
        }
    }

    private static void handleInventoryAmmo(ItemStack stack, Inventory inventory) {
        cacheInventoryAmmoCount = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack inventoryItem = inventory.getItem(i);
            if (inventoryItem.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(stack, inventoryItem)) {
                cacheInventoryAmmoCount += inventoryItem.getCount();
            }
            if (inventoryItem.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(stack, inventoryItem)) {
                if (iAmmoBox.isAllTypeCreative(inventoryItem) || iAmmoBox.isCreative(inventoryItem)) {
                    cacheInventoryAmmoCount = MAX_AMMO_COUNT;
                    return;
                }
                cacheInventoryAmmoCount += iAmmoBox.getAmmoCount(inventoryItem);
            }
        }
    }
}
