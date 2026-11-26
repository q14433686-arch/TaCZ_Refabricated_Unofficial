package com.tacz.probe;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.api.event.common.GunFireEvent;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import static cn.sh1rocu.tacz.TaCZFabric.*;

public final class EventPhaseSmoke {
    private static BiConsumer<String,GunFireEvent> observe=(phase,event)->{};
    static {
        // Intentionally register backwards. Array-backed registration order is not phase order.
        GunFireEvent.CALLBACK.register(LOWEST,event->observe.accept("lowest",event));
        GunFireEvent.CALLBACK.register(LOW,event->observe.accept("low",event));
        GunFireEvent.CALLBACK.register(Event.DEFAULT_PHASE,event->observe.accept("normal",event));
        GunFireEvent.CALLBACK.register(HIGH,event->observe.accept("high",event));
        GunFireEvent.CALLBACK.register(HIGHEST,event->observe.accept("highest",event));
    }
    public static void fireCallbackPhases(GameTestHelper h) {
        var shooter=h.spawn(EntityType.ZOMBIE,BlockPos.ZERO);
        var event=new GunFireEvent(shooter,ItemStack.EMPTY,LogicalSide.SERVER);
        var phases=new ArrayList<String>();
        observe=(phase,e)->{if(e==event) phases.add(phase);};
        try {
            GunFireEvent.CALLBACK.invoker().post(event);
            h.assertTrue(phases.equals(List.of("highest","high","normal","low","lowest")),"GunFireEvent priority order: "+phases);
        } finally {observe=(phase,e)->{};shooter.discard();}
        System.out.println("PORT_EVENT_PHASES: actual Fabric GunFireEvent invoker honored highest/high/default/low/lowest despite reverse registration");
        h.succeed();
    }
}
