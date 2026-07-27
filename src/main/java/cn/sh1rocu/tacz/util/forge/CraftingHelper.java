package cn.sh1rocu.tacz.util.forge;

import com.google.gson.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.Objects;

public class CraftingHelper {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LogManager.getLogger();
    @SuppressWarnings("unused")
    private static final Marker CRAFTHELPER = MarkerManager.getMarker("CRAFTHELPER");
    private static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static ItemStack getItemStack(JsonObject json, boolean readNBT) {
        return getItemStack(json, readNBT, false);
    }

    public static Item getItem(String itemName, boolean disallowsAirInRecipe) {
        Identifier itemKey = Identifier.parse(itemName);
        if (!BuiltInRegistries.ITEM.containsKey(itemKey))
            throw new JsonSyntaxException("Unknown item '" + itemName + "'");

        Item item = BuiltInRegistries.ITEM.getValue(itemKey);
        if (disallowsAirInRecipe && item == Items.AIR)
            throw new JsonSyntaxException("Invalid item: " + itemName);
        return Objects.requireNonNull(item);
    }

    public static CompoundTag getNBT(JsonElement element) {
        try {
            if (element.isJsonObject())
                return TagParser.parseCompoundFully(GSON.toJson(element));
            else
                return TagParser.parseCompoundFully(GsonHelper.convertToString(element, "nbt"));
        } catch (CommandSyntaxException e) {
            throw new JsonSyntaxException("Invalid NBT Entry: " + e);
        }
    }

    public static ItemStack getItemStack(JsonObject json, boolean readNBT, boolean disallowsAirInRecipe) {
        String itemName = GsonHelper.getAsString(json, "item");
        Item item = getItem(itemName, disallowsAirInRecipe);
        int count = GsonHelper.getAsInt(json, "count", 1);
        ItemStack stack = new ItemStack(item, count);
        if (readNBT && json.has("nbt")) {
            CompoundTag nbt = getNBT(json.get("nbt"));
            nbt.remove("ForgeCaps");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        }
        return stack;
    }
}
