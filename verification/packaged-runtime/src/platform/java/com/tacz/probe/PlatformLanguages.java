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
    static void verifyUiLabels(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            var keys=new java.util.LinkedHashSet<String>();
            int advancements=0;
            try(var jar=new java.util.zip.ZipFile(System.getProperty("tacz.probe.jar"))) {
                var entries=jar.entries();
                while(entries.hasMoreElements()) {
                    var entry=entries.nextElement();
                    if(!entry.getName().startsWith("assets/tacz/custom/tacz_default_gun/data/tacz/advancement/") || !entry.getName().endsWith(".json"))continue;
                    try(var reader=new java.io.InputStreamReader(jar.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)) {
                        var json=JsonParser.parseReader(reader).getAsJsonObject();
                        if(!json.has("display"))continue;
                        var display=json.getAsJsonObject("display");
                        for(String field:new String[]{"title","description"})keys.add(display.getAsJsonObject(field).get("translate").getAsString());
                        advancements++;
                    }
                }
            } catch(IOException e){throw new java.io.UncheckedIOException(e);}
            if(advancements!=23 || keys.size()!=46)throw new AssertionError("Default advancement labels not read from actual product: "+advancements+"/"+keys.size());
            for(String field:new String[]{"all","previous","next"})keys.add("gui.tacz.creative_pack_filter."+field);
            for(String key:keys)if(!I18n.exists(key) || I18n.get(key).isBlank() || I18n.get(key).equals(key))
                throw new AssertionError("Native UI/advancement translation missing: "+key);
        });
        System.out.println("PORT_PLATFORM_UI_LABELS: actual product 23 advancement display title/description components and three creative sidebar labels resolved through native I18n; 49 nonempty translated keys");
    }
    private PlatformLanguages(){}
}
