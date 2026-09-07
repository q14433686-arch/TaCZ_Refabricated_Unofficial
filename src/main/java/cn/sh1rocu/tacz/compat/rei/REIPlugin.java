package cn.sh1rocu.tacz.compat.rei;

import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.init.ModItems;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;

public class REIPlugin implements me.shedaniel.rei.api.common.plugins.REICommonPlugin {
    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        registry.register(REISubtype.getAmmoSubtype(), ModItems.AMMO);
        registry.register(REISubtype.getAttachmentSubtype(), ModItems.ATTACHMENT);
        registry.register(REISubtype.getAmmoBoxSubtype(), ModItems.AMMO_BOX);
        registry.register(REISubtype.getTableSubType(), ModItems.GUN_SMITH_TABLE);
        registry.register(REISubtype.getTableSubType(), ModItems.WORKBENCH_111);
        registry.register(REISubtype.getTableSubType(), ModItems.WORKBENCH_121);
        registry.register(REISubtype.getTableSubType(), ModItems.WORKBENCH_211);
        GunItemManager.getAllGunItems().forEach(item ->
                registry.register(REISubtype.getGunSubtype(), item));
    }


    @Override
    public Class<me.shedaniel.rei.api.common.plugins.REICommonPlugin> getPluginProviderClass() {
        return me.shedaniel.rei.api.common.plugins.REICommonPlugin.class;
    }
}
