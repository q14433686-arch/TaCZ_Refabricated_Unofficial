package cn.sh1rocu.tacz.util.forge;

import org.jetbrains.annotations.Nullable;
import java.util.Map;

/** Non-recipe data paths renamed by vanilla; recipes keep their separately tested format migrator. */
public final class LegacyGunPackPaths {
    private static final Map<String,String> OLD_TO_CURRENT=Map.of(
            "loot_tables","loot_table",
            "advancements","advancement",
            "tags/blocks","tags/block",
            "tags/items","tags/item",
            "tags/entity_types","tags/entity_type",
            "tags/fluids","tags/fluid",
            "tags/game_events","tags/game_event");
    private LegacyGunPackPaths(){}
    public static @Nullable String legacy(String current) {
        for(var entry:OLD_TO_CURRENT.entrySet())if(matches(current,entry.getValue()))return entry.getKey()+current.substring(entry.getValue().length());
        return null;
    }
    public static @Nullable String modern(String legacy) {
        for(var entry:OLD_TO_CURRENT.entrySet())if(matches(legacy,entry.getKey()))return entry.getValue()+legacy.substring(entry.getKey().length());
        return null;
    }
    private static boolean matches(String path,String root){return path.equals(root)||path.startsWith(root+"/");}
}
