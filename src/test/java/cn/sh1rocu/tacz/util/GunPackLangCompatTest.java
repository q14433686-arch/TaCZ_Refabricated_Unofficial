package cn.sh1rocu.tacz.util;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class GunPackLangCompatTest {
    private static GunPackLangCompat.Result sanitize(String value){return GunPackLangCompat.sanitize(value.getBytes(StandardCharsets.UTF_8));}
    @Test void validAndLenientPrimitiveObjectsPassUnchanged() {
        assertTrue(sanitize("{\"hello\":\"world\",\"count\":3,\"toggle\":true}").passThrough());
        assertTrue(sanitize("\ufeff{/* comment */ 'hello':'world'}").passThrough());
    }
    @Test void missingCommaAndInvalidValuesAreRepairedWithoutNestedKeyLeak() {
        var repaired=sanitize("{\"first\":\"one\"\n\"second\":\"two\",\"bad\":{\"nested\":\"never\"},\"nil\":null,\"array\":[1],\"last\":\"three\"}");
        assertFalse(repaired.passThrough());
        var object=JsonParser.parseString(repaired.json()).getAsJsonObject();
        assertEquals(3,object.size());assertEquals("one",object.get("first").getAsString());
        assertEquals("two",object.get("second").getAsString());assertEquals("three",object.get("last").getAsString());
        assertFalse(object.has("nested"));assertEquals(3,repaired.dropped());
    }
    @Test void malformedRootsAlwaysYieldUsableObjects() {
        for(String text:new String[]{"", "[]", "null", "{} trailing", "{\"key\":null}"}) {
            var result=sanitize(text);assertFalse(result.passThrough(),text);
            assertTrue(JsonParser.parseString(result.json()).isJsonObject(),text);
        }
    }
}
