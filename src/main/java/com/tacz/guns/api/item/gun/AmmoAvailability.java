package com.tacz.guns.api.item.gun;

/**
 * 一次「是否有可射击弹药」判定的不可变结果快照。
 *
 * <p>该类是 {@link AbstractGunItem#checkAmmoAvailability} 的返回值，把原先分散在
 * 服务端射击（{@code LivingEntityShoot}）、客户端射击（{@code LocalPlayerShoot}）与
 * 服务端拉栓（{@code LivingEntityBolt}）三处的「弹药可用性五连判定」收敛为一份纯数据，
 * 供下游模组稳定地读取/替换，而不必绑三处易漂移的内联逻辑。</p>
 *
 * <p>字段均为只读原始事实，不含任何副作用；最终的「无弹」结论由下方两个具名方法给出，
 * 分别对应<b>射击路径</b>与<b>拉栓路径</b>的两种既有语义（两者本就不同，勿混用）。</p>
 */
public final class AmmoAvailability {
    /** 枪械是否为「弹药直读」（FeedType.INVENTORY）。 */
    public final boolean useInventoryAmmo;
    /** 枪膛内是否有子弹（开膛待击恒为 false）。 */
    public final boolean hasAmmoInBarrel;
    /** 背包/直读容器中是否有备弹（原始值，未并入枪膛子弹）。 */
    public final boolean hasInventoryAmmo;
    /** 弹匣内备弹数（不计算枪膛内的一发）。 */
    public final int magazineAmmoCount;

    public AmmoAvailability(boolean useInventoryAmmo, boolean hasAmmoInBarrel, boolean hasInventoryAmmo, int magazineAmmoCount) {
        this.useInventoryAmmo = useInventoryAmmo;
        this.hasAmmoInBarrel = hasAmmoInBarrel;
        this.hasInventoryAmmo = hasInventoryAmmo;
        this.magazineAmmoCount = magazineAmmoCount;
    }

    /**
     * 射击路径的「无弹」判定，逐字节等价于 {@code LivingEntityShoot} / {@code LocalPlayerShoot}
     * 中内联的五连判定：
     * <pre>
     * hasAmmo = hasInventoryAmmo || hasAmmoInBarrel
     * ammoCount = magazineAmmoCount + (hasAmmoInBarrel ? 1 : 0)
     * noAmmo = useInventoryAmmo &amp;&amp; !hasAmmo || !useInventoryAmmo &amp;&amp; ammoCount &lt; 1
     * </pre>
     */
    public boolean isNoAmmoToShoot() {
        boolean hasAmmo = hasInventoryAmmo || hasAmmoInBarrel;
        int ammoCount = magazineAmmoCount + (hasAmmoInBarrel ? 1 : 0);
        return useInventoryAmmo && !hasAmmo || !useInventoryAmmo && ammoCount < 1;
    }

    /**
     * 拉栓路径的「无弹」判定，逐字节等价于 {@code LivingEntityBolt} 中内联的判定。
     * 与射击路径的区别：枪膛子弹在拉栓里是<b>单独</b>判断的，因此这里只看弹匣/直读容器，
     * 不并入枪膛子弹。
     */
    public boolean isNoAmmoToBolt() {
        return useInventoryAmmo && !hasInventoryAmmo || !useInventoryAmmo && magazineAmmoCount < 1;
    }
}
