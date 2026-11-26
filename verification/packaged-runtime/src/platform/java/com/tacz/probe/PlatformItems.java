package com.tacz.probe;

import me.xjqsh.lrtactical.api.item.ICustomItem;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/** Test-mod-only custom items exercise the actual LRT GUI contract without a third-party content pack. */
public final class PlatformItems implements ModInitializer {
    public static Item first,second;
    @Override public void onInitialize() { first=register("cooldown_a");second=register("cooldown_b"); }
    private static Item register(String name) {
        var id=Identifier.parse("tacz_packaged_probe:"+name);
        return Registry.register(BuiltInRegistries.ITEM,id,new ProbeItem(id));
    }
    private static final class ProbeItem extends Item implements ICustomItem {
        private final Identifier id;
        ProbeItem(Identifier id) { super(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id)));this.id=id; }
        @Override public Identifier getId(ItemStack stack) { return id; }
        @Override public void setId(ItemStack stack,Identifier id) { throw new UnsupportedOperationException(); }
        @Override public boolean isSame(ItemStack a,ItemStack b) { return a.getItem()==b.getItem(); }
        @Override public Optional<Identifier> getCoolDownId(ItemStack stack) { return Optional.of(id); }
    }
}
