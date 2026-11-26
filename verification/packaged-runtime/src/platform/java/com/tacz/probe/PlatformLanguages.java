package com.tacz.probe;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.nio.file.Files;

/** Malformed test gun-pack input is loaded through the shipped aggregate and native language loader. */
final class PlatformLanguages {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            var id=Identifier.parse("tacz_lang_probe:lang/en_us.json");
            try {
                var original=Files.readString(mc.gameDirectory.toPath().resolve("tacz/lang_probe/assets/tacz_lang_probe/lang/en_us.json"));
                if(!original.contains("\"Recovered first\"\n  \"port.lang.second\""))throw new AssertionError("Malformed language fixture was rewritten or not installed");
                try(var input=mc.getResourceManager().getResource(id).orElseThrow().openAsReader()) {
                    var repaired=JsonParser.parseReader(input).getAsJsonObject();
                    if(repaired.size()!=3 || repaired.has("port.lang.bad") || repaired.has("port.lang.nested"))
                        throw new AssertionError("Aggregate lang resource did not repair/drop only invalid values");
                }
                for(String key:new String[]{"first","second","last"})
                    if(!I18n.get("port.lang."+key).equals("Recovered "+key))throw new AssertionError("Native language loader lost repaired text: "+key);
                if(I18n.exists("port.lang.nested") || I18n.exists("port.lang.bad"))throw new AssertionError("Invalid translation entries leaked");
            } catch(IOException e){throw new java.io.UncheckedIOException(e);}
        });
        System.out.println("PORT_PLATFORM_LANGUAGES: malformed gun-pack language repaired through real aggregate/native Language loader; valid entries recovered, nested entries dropped, original disk bytes unchanged");
    }
    private PlatformLanguages(){}
}
