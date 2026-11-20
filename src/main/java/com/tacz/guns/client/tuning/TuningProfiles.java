package com.tacz.guns.client.tuning;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.UUID;

/** The only identity/held-item lookup. No networking, no server-side config or player NBT. */
public final class TuningProfiles {
    public static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("tacz/view-profiles");
    private static final TuningProfileStore STORE = new TuningProfileStore(DIRECTORY, ConfigValue::get);

    public static UUID account() { return Minecraft.getInstance().getUser().getProfileId(); }
    public static TuningProfile current() { return STORE.get(account()); }
    public static boolean save(UUID editorOwner, TuningProfile edited) {
        return editorOwner.equals(account()) && STORE.save(editorOwner, edited);
    }

    public static @Nullable String type(Identifier gun) {
        return TimelessAPI.getClientGunIndex(gun).map(ClientGunIndex::getType).orElse(null);
    }

    public static TuningValues forStack(ItemStack stack) {
        var profile = current();
        if (!(stack.getItem() instanceof IGun gun)) return profile.base();
        var id = gun.getGunId(stack);
        return profile.hud(type(id), id.toString());
    }

    private TuningProfiles() {}
}
