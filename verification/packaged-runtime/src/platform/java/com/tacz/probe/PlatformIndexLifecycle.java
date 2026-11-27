package com.tacz.probe;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.model.papi.PapiManager;
import com.tacz.guns.client.model.papi.RangefinderReadout;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.resource.pojo.display.gun.Rangefinder;
import com.tacz.guns.init.ModCreativeTabs;
import com.tacz.guns.mixin.client.CreativeModeTabsAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import org.joml.Vector3f;

/** Real index reload must invalidate client measurements and vanilla's content-only creative cache. */
final class PlatformIndexLifecycle {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            var id=Identifier.parse("tacz:ak47");var common=TimelessAPI.getCommonGunIndex(id).orElseThrow();
            var type=PlatformAds.read(common,"type");
            var data=IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder();
            float progress=data.clientAimingProgress,old=LocalPlayerDataHolder.oldAimingProgress;
            boolean restored=false;
            try {
                // The fixture changes data, not features/permissions/registries: vanilla alone sees an identical cache key.
                PlatformAds.write(common,"type","port_hidden");
                CreativeModeTabsAccessor.tacz$setCachedParameters(null);
                CreativeModeTabs.tryRebuildTabContents(mc.getConnection().enabledFeatures(),false,mc.level.registryAccess());
                if(hasAk47())throw new AssertionError("Creative cache control did not exclude fixture type");
                if(CreativeModeTabs.tryRebuildTabContents(mc.getConnection().enabledFeatures(),false,mc.level.registryAccess()))
                    throw new AssertionError("Vanilla cache control unexpectedly rebuilt unchanged parameters");
                PlatformAds.write(common,"type",type);
                data.clientAimingProgress=LocalPlayerDataHolder.oldAimingProgress=1;
                RangefinderReadout.clear();
                var range=ClientAssetsManager.GSON.fromJson("{max_distance:0}",Rangefinder.class);
                PapiManager.getTextShow("%rangefinder%",mc.player.getMainHandItem(),range);
                var cache=RangefinderReadout.class.getDeclaredField("samples");cache.setAccessible(true);
                if(((java.util.Map<?,?>)cache.get(null)).isEmpty())throw new AssertionError("No real rangefinder sample to invalidate");
                ClientIndexManager.reload();restored=true;
                if(!((java.util.Map<?,?>)cache.get(null)).isEmpty())throw new AssertionError("Reload retained old rangefinder sample");
                if(!CreativeModeTabs.tryRebuildTabContents(mc.getConnection().enabledFeatures(),false,mc.level.registryAccess()) || !hasAk47())
                    throw new AssertionError("Index reload did not invalidate/repopulate native creative contents");
                if(CreativeModeTabs.tryRebuildTabContents(mc.getConnection().enabledFeatures(),false,mc.level.registryAccess()))
                    throw new AssertionError("Reload made creative caching rebuild perpetually");
                var shell=new ShellRender(null);
                var queue=(java.util.Deque<?>)PlatformAds.read(shell,"SHELL_QUEUE");
                Object evicted=null;
                for(int i=0;i<140;i++){shell.addShell(new Vector3f());if(i==0)evicted=queue.getFirst();if(queue.size()>128)throw new AssertionError("Shell queue exceeded donor 128 bound");}
                if(queue.size()!=128 || queue.contains(evicted))throw new AssertionError("Shell FIFO bound did not evict oldest");
            } catch(ReflectiveOperationException e){throw new AssertionError(e);}
            finally {
                PlatformAds.write(common,"type",type);
                if(!restored)ClientIndexManager.reload();
                data.clientAimingProgress=progress;LocalPlayerDataHolder.oldAimingProgress=old;RangefinderReadout.clear();
            }
        });
        System.out.println("PORT_PLATFORM_INDEX_LIFECYCLE: actual client index reload cleared real range sample and rebuilt native creative content with unchanged features/permissions/registries; subsequent caching retained; shell FIFO capped at 128/oldest evicted");
    }
    private static boolean hasAk47(){return ModCreativeTabs.GUN_RIFLE_TAB.getDisplayItems().stream().anyMatch(stack->stack.getItem() instanceof IGun gun && gun.getGunId(stack).equals(Identifier.parse("tacz:ak47")));}
    private PlatformIndexLifecycle(){}
}
