package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.config.sync.SyncConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@Environment(EnvType.CLIENT)
public class CrawlKey {
    public static final KeyMapping CRAWL_KEY = new KeyMapping("key.tacz.crawl.desc",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_C,
            TaCZKeyCategory.TACZ);

    public static void onCrawlPress(InputEvent.Key event) {
        if (CRAWL_KEY.matches(InputConstants.Type.KEYBOARD.getOrCreate(event.getKey()))) {
            if (event.getAction() == InputConstants.PRESS) onCrawlControllerPress(true);
            else if (event.getAction() == InputConstants.RELEASE) onCrawlControllerPress(false);
        }
    }

    public static boolean onCrawlControllerPress(boolean isPress) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!(player instanceof IClientPlayerGunOperator operator)) return false;
        boolean hold = KeyConfig.HOLD_TO_CRAWL.get();
        // Releasing a held input must not be blocked by a changed item/config/state.
        if (!isPress) {
            if (hold) operator.crawl(false);
            return hold;
        }
        if (!isInGame()) return false;
        if (operator.isCrawl() && !hold) {
            operator.crawl(false);
            return true;
        }
        if (!SyncConfig.ENABLE_CRAWL.get() || player.isSpectator() || player.isPassenger()) return false;
        IGun gun = IGun.getIGunOrNull(player.getMainHandItem());
        if (gun == null ? !SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.get() : !gun.isCanCrawl(player.getMainHandItem())) return false;
        operator.crawl(true);
        return true;
    }
}
