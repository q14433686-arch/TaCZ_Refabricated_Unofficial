package com.tacz.guns.client.model;

import com.google.gson.Gson;
import com.tacz.guns.client.resource.pojo.model.BonesItem;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OcularBakeTest {
    private static BonesItem bone(String name, String parent) {
        return new Gson().fromJson("{name:'" + name + "',parent:'" + parent + "'}", BonesItem.class);
    }
    @Test void exactNumberedOcularAndDescendantsOnly() {
        for(String name:new String[]{"ocular","ocular_sight","ocular_scope","ocular_2","ocular_sight_2","ocular_scope_13"}) {
            var ocular=bone(name,"root");var child=bone("glass",name);
            var index=Map.of(name,ocular,"glass",child);
            assertEquals(.0005f,BedrockAttachmentModel.ocularInflate(ocular,index,.0005f),name);
            assertEquals(.0005f,BedrockAttachmentModel.ocularInflate(child,index,.0005f),name);
        }
        for(String name:new String[]{"ocular_ring","ocular_scope_body","ocular_sight_2_illuminated","division","ocular_fake"}) {
            var other=bone(name,"root");
            assertEquals(0,BedrockAttachmentModel.ocularInflate(other,Map.of(name,other),1),name);
        }
    }
    @Test void malformedAncestryAndNonfiniteAmountsDoNotExpand() {
        var ocular=bone("ocular_scope_2","root");var ring=bone("ocular_ring","root");
        var cycle=bone("cycle","cycle");var lost=bone("glass","absent");
        var index=new HashMap<>(Map.of("ocular_scope_2",ocular,"ocular_ring",ring,"cycle",cycle,"glass",lost));
        assertEquals(0,BedrockAttachmentModel.ocularInflate(ring,index,1));
        assertEquals(0,BedrockAttachmentModel.ocularInflate(cycle,index,1));
        assertEquals(0,BedrockAttachmentModel.ocularInflate(lost,index,1));
        assertEquals(0,BedrockAttachmentModel.ocularInflate(null,index,1));
        for(float amount:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            assertEquals(0,BedrockAttachmentModel.ocularInflate(ocular,index,amount));
    }
}
