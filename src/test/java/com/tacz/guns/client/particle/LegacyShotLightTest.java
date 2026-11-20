package com.tacz.guns.client.particle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyShotLightTest {
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Identifier.class,
            (JsonDeserializer<Identifier>)(json,type,ctx) -> Identifier.parse(json.getAsString())).create();
    private static final MuzzleAttachment NONE = new MuzzleAttachment(null,0);
    private static int sample(String body, String type, MuzzleAttachment part, boolean first) {
        var display = GSON.fromJson(body,GunDisplay.class);
        var flash = display.getMuzzleFlash();
        return LegacyShotLight.level(flash,display.omitsMuzzleFlash(),type,true,part,first);
    }
    @Test void onlyMissingExplosiveLaunchersGetTheLocatorlessFallback() {
        assertEquals(12,sample("{}","rpg",NONE,true));
        assertEquals(0,LegacyShotLight.level(null,true,"rpg",false,NONE,true));
        for (String body:new String[]{"{\"muzzle_flash\":null}","{\"muzzle_flash\":{}}"}) assertEquals(0,sample(body,"rpg",NONE,true));
        assertEquals(0,sample("{}","rifle",NONE,true));
    }
    @Test void authoredGatesPrecedeHeuristicsAndAttachmentReduction() {
        String base="{\"muzzle_flash\":{\"texture\":\"demo:old\",\"scale\":0.25%s}}";
        assertEquals(3,sample(base.formatted(""),"pistol",NONE,true));
        assertEquals(4,sample(base.formatted(""),"custom_type",NONE,true));
        assertEquals(0,sample(base.formatted(",\"type\":\"renew\""),"rpg",NONE,true));
        assertEquals(0,sample(base.formatted(",\"flash_light_level\":0"),"rpg",NONE,true));
        assertEquals(0,sample(base.replace("0.25","0").formatted(""),"rpg",NONE,true));
        assertEquals(0,sample(base.formatted(",\"first_person_flash\":null"),"rpg",NONE,true));
        assertEquals(6,sample(base.formatted(",\"first_person_flash\":null"),"rpg",NONE,false));
        var hider=new MuzzleAttachment(GSON.fromJson("{\"flash_hider\":0.2}",AttachmentData.class),0);
        assertEquals(2,sample(base.formatted(",\"flash_light_level\":10"),"pistol",hider,true));
        assertEquals(15,GSON.fromJson("{\"type\":\"renew\"}",MuzzleFlash.class).getFlashLightLevel());
    }
}
