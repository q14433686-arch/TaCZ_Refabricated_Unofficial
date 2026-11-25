package com.tacz.probe;

import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Compiled with just one viewer in each dedicated lane; the no-viewer baseline links neither. */
interface ViewerProbe {
    boolean hasGun(Identifier id);
    boolean hasWorkbenchRecipe(Identifier id);
    boolean hasOrdinaryCrafting();

    static ViewerProbe load(String name) {
        if (name.equals("none")) return null;
        String type = switch (name) { case "jei" -> "JeiProbe"; case "rei" -> "ReiProbe"; default -> throw new AssertionError(name); };
        try { return (ViewerProbe) Class.forName("com.tacz.probe." + type).getConstructor().newInstance(); }
        catch (ReflectiveOperationException e) { throw new AssertionError("Viewer not built for this run", e); }
    }
    static boolean gun(ItemStack stack, Identifier id) { return stack.getItem() instanceof IGun g && id.equals(g.getGunId(stack)); }
}
