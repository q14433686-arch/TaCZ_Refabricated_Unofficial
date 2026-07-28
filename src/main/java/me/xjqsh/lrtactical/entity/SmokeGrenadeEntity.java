package me.xjqsh.lrtactical.entity;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.init.ModParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * 烟雾弹 —— 落地后持续释放烟幕遮蔽视野。
 *
 * <h2>与爆炸雷的关键差异：不会「死」</h2>
 * 它<b>不覆写 {@code onDeath}</b>，因此寿命耗尽时只是安静消失（基类 {@code discard()}），
 * 不产生任何爆炸。烟幕效果完全由 {@link #tick()} 里持续生成的粒子构成，
 * 所以 {@code life_time} 直接决定了烟雾持续多久。
 *
 * <h2>26.2 移植要点</h2>
 * <ul>
 *   <li>{@code EntityType.Builder} 三处变更，沿用 {@link GrenadeEntity} 的既有写法；</li>
 *   <li>{@code Level#addParticle} 在 26.2 是<b>两个 boolean</b>
 *       {@code (options, overrideLimiter, alwaysShow, x,y,z, xd,yd,zd)}（字节码确认）。
 *       上游只有一个 boolean，直接照抄会编译失败 ——
 *       基类 {@code ThrowableItemEntity#renderTailParticle} 已踩过同一处。</li>
 *   <li>上游在 {@code tickCount == 40} 时播 {@code ModSounds} 的释放音效，
 *       属原作受限素材，本移植不打包，已略去。</li>
 * </ul>
 */
public class SmokeGrenadeEntity extends ThrowableItemEntity {
    public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "smoke_grenade"));

    public static final EntityType<SmokeGrenadeEntity> TYPE = EntityType.Builder
            .<SmokeGrenadeEntity>of(SmokeGrenadeEntity::new, MobCategory.MISC)
            .noSave().noSummon().fireImmune()
            .sized(0.3F, 0.3F)
            .clientTrackingRange(64).updateInterval(1)
            .build(KEY);

    /** 引信时间：落地后多久开始冒烟（tick）。与上游一致。 */
    private static final int RELEASE_DELAY = 40;
    /** 每 tick 生成多少粒子。 */
    private static final int PARTICLES_PER_TICK = 16;

    public SmokeGrenadeEntity(LivingEntity owner, Level level, int lifeTime) {
        super(TYPE, owner, level, lifeTime);
    }

    public SmokeGrenadeEntity(EntityType<? extends SmokeGrenadeEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return me.xjqsh.lrtactical.init.ModItems.THROWABLE;
    }

    @Override
    public void tick() {
        super.tick();
        // 粒子是纯客户端表现，服务端不需要做任何事
        if (!this.level().isClientSide() || this.tickCount < RELEASE_DELAY) {
            return;
        }
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            // triangle(中心, 扩散)：靠近中心更密，边缘稀疏，形成半球形烟团
            double offsetX = this.random.triangle(0, 5.5);
            double offsetY = this.random.triangle(0, 4.5);
            double offsetZ = this.random.triangle(0, 5.5);
            // 26.2: addParticle 需要两个 boolean —— overrideLimiter 与 alwaysShow。
            // 烟雾是战术道具，必须无视客户端粒子数量设置，故 overrideLimiter=true。
            this.level().addParticle(ModParticleTypes.SMOKE_CLOUD, true, false,
                    x + offsetX, y + offsetY, z + offsetZ, 0.0D, 0.0D, 0.0D);
        }
    }
}
