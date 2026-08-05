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
        registry.register(REISubtype.getMagazineSubtype(), ModItems.MAGAZINE);
        registry.register(REISubtype.getIndustrySubtype(), ModItems.GUN_COMPONENT);
        registry.register(REISubtype.getIndustrySubtype(), ModItems.GUN_COMPONENT_BLANK);
        registry.register(REISubtype.getIndustrySubtype(), ModItems.GUN_BLUEPRINT);
        registry.register(REISubtype.getIndustrySubtype(), ModItems.CARTRIDGE_CASE_BLANK);
        registry.register(REISubtype.getIndustrySubtype(), ModItems.CARTRIDGE_CASE);
        registry.register(REISubtype.getIndustrySubtype(), ModItems.PROJECTILE_BLANK);
        registry.register(REISubtype.getIndustrySubtype(), ModItems.PROJECTILE_CORE);
        registry.register(REISubtype.getIndustrySubtype(), ModItems.PRESS_DIE);
        GunItemManager.getAllGunItems().forEach(item ->
                registry.register(REISubtype.getGunSubtype(), item));
    }


    @Override
    public Class<me.shedaniel.rei.api.common.plugins.REICommonPlugin> getPluginProviderClass() {
        return me.shedaniel.rei.api.common.plugins.REICommonPlugin.class;
    }
}
