package com.tacz.probe;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.init.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Compiled with just one viewer in each dedicated lane; the no-viewer baseline links neither. */
interface ViewerProbe {
    boolean hasGun(Identifier id);
    boolean hasWorkbenchRecipe(Identifier id);
    boolean hasOrdinaryCrafting();
    void checkInstalled(ClientGameTestContext ctx, Identifier live);
    boolean select(GunSmithTableScreen screen, Identifier id, boolean commit);

    /** Exercise the installed viewer comparator, not our string extractor in isolation. The
     * ordinary smith table has a fixed identity; only the three pack-backed items vary by id. */
    static void checkTableKeys(Function<ItemStack, ?> key) {
        for (var item : List.of(ModItems.WORKBENCH_111, ModItems.WORKBENCH_121, ModItems.WORKBENCH_211)) {
            var a = BlockItemBuilder.create(item).setId(Identifier.parse("net_probe:bench_a")).build();
            var b = BlockItemBuilder.create(item).setId(Identifier.parse("net_probe:bench_b")).build();
            var renamed = a.copy(); renamed.set(DataComponents.CUSTOM_NAME, Component.literal("renamed bench"));
            var identity = key.apply(a);
            if (identity == null || identity.equals(key.apply(b)) || !identity.equals(key.apply(renamed)))
                throw new AssertionError("Workstation key ignored BlockId or used the custom name: " + item);
        }
    }

    default String detail() { return getClass().getSimpleName(); }

    static ViewerProbe load(String name) {
        if (name.equals("none")) return null;
        String type = switch (name) { case "jei" -> "JeiProbe"; case "rei" -> "ReiProbe"; default -> throw new AssertionError(name); };
        try { return (ViewerProbe) Class.forName("com.tacz.probe." + type).getConstructor().newInstance(); }
        catch (ReflectiveOperationException e) { throw new AssertionError("Viewer not built for this run", e); }
    }
    static boolean gun(ItemStack stack, Identifier id) { return stack.getItem() instanceof IGun g && id.equals(g.getGunId(stack)); }
}
