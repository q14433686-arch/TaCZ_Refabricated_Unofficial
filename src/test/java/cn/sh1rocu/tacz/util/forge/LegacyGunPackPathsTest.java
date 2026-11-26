package cn.sh1rocu.tacz.util.forge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyGunPackPathsTest {
    @Test void exactRootsAndNestedFilesRoundTripWithoutPrefixCollisions() {
        for(String root:new String[]{"loot_table","advancement","tags/block","tags/item","tags/entity_type","tags/fluid","tags/game_event"}) {
            for(String suffix:new String[]{"","/","/nested/example.json"}) {
                String current=root+suffix,legacy=LegacyGunPackPaths.legacy(current);
                assertNotNull(legacy,current);assertEquals(current,LegacyGunPackPaths.modern(legacy));
            }
            assertNull(LegacyGunPackPaths.legacy(root+"_other/file.json"));
        }
        assertNull(LegacyGunPackPaths.legacy("recipe/example.json"));
        assertNull(LegacyGunPackPaths.legacy("tacz_tags/attachments/scope.json"));
        assertNull(LegacyGunPackPaths.legacy("lang/en_us.json"));
        assertNull(LegacyGunPackPaths.legacy("tags"));
    }
}
