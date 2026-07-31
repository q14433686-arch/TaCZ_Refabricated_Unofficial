package com.tacz.guns.api.item.builder;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.component.AmmoData;
import com.tacz.guns.api.item.enums.BulletType;
import com.tacz.guns.api.item.enums.CaseCondition;
import com.tacz.guns.api.item.enums.CaseMaterial;
import com.tacz.guns.api.item.enums.PowderType;
import com.tacz.guns.api.item.enums.PrimerType;
import com.tacz.guns.init.ModItems;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class AmmoItemBuilder {
    private int count = 1;
    private Identifier ammoId = DefaultAssets.DEFAULT_AMMO_ID;
    // P0扩展：弹药属性
    private Identifier cartridgeType = null; // P0补充：口径类型
    private CaseMaterial caseMaterial = CaseMaterial.BRASS;
    private PrimerType primerType = PrimerType.BOXER;
    private PowderType powderType = PowderType.SMOKELESS;
    private float powderCharge = 1.0f;
    private BulletType bulletType = BulletType.FMJ;
    private int reloadCount = 0;
    private CaseCondition caseCondition = CaseCondition.PRISTINE;
    private boolean useCustomAmmoData = false;

    private AmmoItemBuilder() {
    }

    public static AmmoItemBuilder create() {
        return new AmmoItemBuilder();
    }

    public AmmoItemBuilder setCount(int count) {
        this.count = Math.max(count, 1);
        return this;
    }

    public AmmoItemBuilder setId(Identifier id) {
        this.ammoId = id;
        return this;
    }

    /**
     * 设置口径类型标识符。
     * <p>
     * P0补充：引用 CartridgeTypeManager 中注册的口径类型。
     */
    public AmmoItemBuilder setCartridgeType(Identifier cartridgeType) {
        this.cartridgeType = cartridgeType;
        this.useCustomAmmoData = true;
        return this;
    }

    public AmmoItemBuilder setCaseMaterial(CaseMaterial material) {
        this.caseMaterial = material;
        this.useCustomAmmoData = true;
        return this;
    }

    public AmmoItemBuilder setPrimerType(PrimerType type) {
        this.primerType = type;
        this.useCustomAmmoData = true;
        return this;
    }

    public AmmoItemBuilder setPowderType(PowderType type) {
        this.powderType = type;
        this.useCustomAmmoData = true;
        return this;
    }

    public AmmoItemBuilder setPowderCharge(float charge) {
        this.powderCharge = Math.clamp(charge, 0.5f, 1.5f);
        this.useCustomAmmoData = true;
        return this;
    }

    public AmmoItemBuilder setBulletType(BulletType type) {
        this.bulletType = type;
        this.useCustomAmmoData = true;
        return this;
    }

    public AmmoItemBuilder setReloadCount(int count) {
        this.reloadCount = Math.max(count, 0);
        this.useCustomAmmoData = true;
        return this;
    }

    public AmmoItemBuilder setCaseCondition(CaseCondition condition) {
        this.caseCondition = condition;
        this.useCustomAmmoData = true;
        return this;
    }

    /**
     * 设置完整的弹药数据
     */
    public AmmoItemBuilder setAmmoData(AmmoData data) {
        this.cartridgeType = data.cartridgeType();
        this.caseMaterial = data.caseMaterial();
        this.primerType = data.primerType();
        this.powderType = data.powderType();
        this.powderCharge = data.powderCharge();
        this.bulletType = data.bulletType();
        this.reloadCount = data.reloadCount();
        this.caseCondition = data.caseCondition();
        this.useCustomAmmoData = true;
        return this;
    }

    public ItemStack build() {
        ItemStack ammo = new ItemStack(ModItems.AMMO, this.count);
        if (ammo.getItem() instanceof IAmmo iAmmo) {
            iAmmo.setAmmoId(ammo, this.ammoId);
            // P0扩展：设置弹药数据组件
            if (useCustomAmmoData) {
                iAmmo.setAmmoData(ammo, new AmmoData(
                        cartridgeType, caseMaterial, primerType, powderType, powderCharge,
                        bulletType, reloadCount, caseCondition
                ));
            }
        }
        return ammo;
    }
}
