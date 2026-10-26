package com.tacz.guns.entity;

import cn.sh1rocu.tacz.api.LogicalSide;
import cn.sh1rocu.tacz.api.extension.IMinecart;
import com.tacz.guns.api.entity.ITargetEntity;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.init.ModSounds;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunHurt;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;

public class TargetMinecart extends AbstractMinecart implements ITargetEntity, IMinecart {
    public static EntityType<TargetMinecart> TYPE = EntityType.Builder.<TargetMinecart>of(TargetMinecart::new, MobCategory.MISC)
            .sized(0.75F, 2.4F)
            .clientTrackingRange(8)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("tacz", "target_minecart")));

    /**
     * 标靶矿车的「主人档案」，由自定义名派生（名字就是皮肤名）。
     *
     * <p>与原版头颅一致地使用 {@link ResolvableProfile}：允许「只有名字」，服务端不解析。
     * 缓存 + 记录派生的名字，避免每帧都重建（渲染线程每帧读一次）。</p>
     */
    private @Nullable ResolvableProfile ownerProfile = null;
    /** {@link #ownerProfile} 是由哪个自定义名算出来的；名字一变就重建。 */
    private @Nullable Component ownerProfileSource = null;

    public TargetMinecart(EntityType<TargetMinecart> type, Level world) {
        super(type, world);
    }

    public TargetMinecart(Level level, double x, double y, double z) {
        super(TYPE, level, x, y, z);
    }

    @Override
    public void onProjectileHit(Entity entity, EntityHitResult result, DamageSource source, float damage) {
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }
        if (source.isDirect()) {
            return;
        }
        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof Player player) {
            this.setHurtDir(-1);
            this.setHurtTime(10);
            this.markHurt();
            this.setDamage(10);
            double dis = this.position().distanceTo(sourceEntity.position());
            player.sendSystemMessage(Component.translatable("message.tacz.target_minecart.hit", String.format("%.1f", damage), String.format("%.2f", dis)));
            // 原版的声音传播距离由 volume 决定
            // 当声音大于 1 时，距离为 = 16 * volume
            float volume = OtherConfig.TARGET_SOUND_DISTANCE.get() / 16.0f;
            volume = Math.max(volume, 0);
            level().playSound(null, this, ModSounds.TARGET_HIT, SoundSource.BLOCKS, volume, this.level().getRandom().nextFloat() * 0.1F + 0.9F);

            if (entity instanceof EntityKineticBullet projectile) {
                boolean isHeadshot = false;
                float headshotMultiplier = 1;
                EntityHurtByGunEvent.Post event = new EntityHurtByGunEvent.Post(projectile, this, player, projectile.getGunId(), projectile.getGunDisplayId(), damage, Pair.of(source, source), isHeadshot, headshotMultiplier, LogicalSide.SERVER);
                EntityHurtByGunEvent.POST.invoker().post(event);
                NetworkHandler.sendToDimension(new ServerMessageGunHurt(projectile.getId(), this.getId(), player.getId(), projectile.getGunId(), projectile.getGunDisplayId(), damage, isHeadshot, headshotMultiplier), this);
            }
        }
    }

    @Override
    protected boolean shouldSourceDestroy(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        return super.shouldSourceDestroy(source);
    }

    @Override
    public boolean tacz$canBeRidden() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double size = this.getBoundingBox().getSize();
        if (Double.isNaN(size)) {
            size = 1.0;
        }
        size *= RenderConfig.TARGET_RENDER_DISTANCE.get() * getViewScale();
        return distance < size * size;
    }

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        this.remove(Entity.RemovalReason.KILLED);
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            ItemStack itemStack = new ItemStack(ModItems.TARGET_MINECART);
            if (this.hasCustomName()) {
                itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
            }
            this.spawnAtLocation(level, itemStack);
        }
    }

    @Override
    protected @NotNull Item getDropItem() {
        return ModItems.TARGET_MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        ItemStack itemStack = new ItemStack(ModItems.TARGET_MINECART);
        if (this.hasCustomName()) {
            itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        return itemStack;
    }

    /**
     * 取标靶矿车的主人档案（皮肤名）。没有自定义名时返回 {@code null}。
     *
     * <h2>26.2 必现崩渲染线程（2026-10-27 修复）</h2>
     * <p>这里是 {@code new GameProfile(null, 自定义名)}。authlib 9 的 {@code GameProfile}
     * 是 record，id/name/properties 全部 {@code Objects.requireNonNull} ⇒ 改名过的标靶矿车
     * 必抛 NPE。而本方法<b>在客户端每帧、在渲染线程上</b>被
     * {@code TargetMinecartRenderer#extractRenderState} 调用，于是不是「偶尔崩」，
     * 是一靠近就<b>把整个渲染线程崩掉</b>（游戏直接卡死/崩溃）。</p>
     * <p>现在返回原版 {@link ResolvableProfile}：构造它不需要 UUID，也不会校验/联网；
     * 真正的皮肤解析交给客户端的 {@code PlayerSkinRenderCache} 异步完成，
     * 解析不到就是默认皮肤 —— 与原版头颅同一套机制。</p>
     */
    @Nullable
    public ResolvableProfile getOwnerProfile() {
        Component customName = this.getCustomName();
        if (customName == null) {
            this.ownerProfile = null;
            this.ownerProfileSource = null;
            return null;
        }
        // 名字没变就复用缓存：本方法在渲染线程每帧调用，不能每次都新建对象。
        if (this.ownerProfile == null || !customName.equals(this.ownerProfileSource)) {
            String name = customName.getString();
            this.ownerProfile = name.isEmpty() ? null : ResolvableProfile.createUnresolved(name);
            this.ownerProfileSource = customName;
        }
        return this.ownerProfile;
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        // 自定义名变了，派生的档案必须作废，否则换名字后皮肤不跟着变。
        this.ownerProfile = null;
        this.ownerProfileSource = null;
        super.setCustomName(name);
    }

    @Override
    @NotNull
    public BlockState getDefaultDisplayBlockState() {
        return ModBlocks.TARGET.defaultBlockState();
    }


    @Override
    protected double getMaxSpeed(ServerLevel level) {
        return 0.2F;
    }
}
