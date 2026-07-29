package cn.sh1rocu.tacz.mixin.common;

import cn.sh1rocu.tacz.api.event.EntityRemoveEvent;
import cn.sh1rocu.tacz.api.extension.IEntityPersistentData;
import cn.sh1rocu.tacz.api.extension.IMoveDistTracker;
import com.tacz.guns.entity.sync.core.DataHolderCapabilityProvider;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin implements IEntityPersistentData, IMoveDistTracker {
    @Shadow
    private Level level;

    /**
     * 重建 26.2 已移除的 {@code walkDistO}。
     *
     * <p>上游 1.21.1 用 {@code walkDist} / {@code walkDistO} 做插值来驱动持枪行走动画；
     * 26.2 把 {@code walkDist} 更名 {@code moveDist} 且<b>未</b>保留 {@code walkDistO}。
     * 若直接取 {@code moveDist}，驱动量每游戏刻（20Hz）才跳变一次，
     * 渲染按帧跑（60~144Hz）就会出现阶梯感 —— 观感就是"掉帧/被抽帧"。</p>
     *
     * <p>这里在每个 tick 的 HEAD 记录<b>上一 tick 结束时</b>的 moveDist，
     * 供 {@code GunAnimationStateContext#getWalkDist()} 做与上游等价的线性插值。</p>
     */
    @Unique
    private float tacz$moveDistO;

    @Unique
    private boolean tacz$moveDistInit;

    @Unique
    @Override
    public float tacz$getMoveDistO() {
        // 未初始化时返回当前值，使增量为 0，避免第一帧出现跳变。
        return this.tacz$moveDistInit ? this.tacz$moveDistO : ((Entity) (Object) this).moveDist;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tacz$captureMoveDistO(CallbackInfo ci) {
        this.tacz$moveDistO = ((Entity) (Object) this).moveDist;
        this.tacz$moveDistInit = true;
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void remove(Entity.RemovalReason reason, CallbackInfo ci) {
        // The data-holder lifecycle exists on both logical sides. Restricting this event to the
        // client leaked server-side providers for every removed non-player entity.
        EntityRemoveEvent event = new EntityRemoveEvent((Entity) (Object) this);
        EntityRemoveEvent.EVENT.invoker().onEntityRemove(event);
    }

    @Unique
    private CompoundTag tacz$persistentData;

    @Unique
    @Override
    public CompoundTag tacz$getPersistentData() {
        if (this.tacz$persistentData == null) {
            this.tacz$persistentData = new CompoundTag();
        }
        return tacz$persistentData;
    }

    @Inject(method = "saveWithoutId", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V"))
    private void tacz$savePersistentData(ValueOutput output, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        DataHolderCapabilityProvider.maybeGet(self)
                .ifPresent(provider -> provider.writeToNbt(this.tacz$getPersistentData()));
        if (this.tacz$persistentData != null) {
            output.store("ForgeData", CompoundTag.CODEC, this.tacz$persistentData.copy());
        }
    }

    /**
     * <h2>刷怪笼极度掉帧修复（第 27 轮）</h2>
     *
     * <p><b>症状</b>：装本 mod 后看向刷怪笼 → 帧数暴跌、GPU 占用居高不下，
     * 且笼内旋转实体与烟雾/火焰粒子<b>全部不渲染</b>。</p>
     *
     * <p><b>vanilla 侧的放大器</b>（26.2 字节码确认）：
     * {@code BaseSpawner#getOrCreateDisplayEntity} 只在 {@code displayEntity == null}
     * 时才创建；而 {@code EntityType.loadEntityRecursive} 失败会返回 null，
     * 于是 {@code displayEntity} 一直为 null → {@code SpawnerRenderer#extractRenderState}
     * <b>每帧重试一次完整的实体反序列化</b>。
     * 并且 {@code clientTick} 的粒子发射也在同一个 {@code displayEntity != null}
     * 分支里（偏移 20-24），null 就<b>一个粒子都不发</b> —— 这正是「不渲染特效」与
     * 掉帧同源的原因。</p>
     *
     * <p><b>我们这边的成本来源</b>：每次 {@code Entity#load()} 都无条件调用
     * {@code DataHolderCapabilityProvider.get()}，那是
     * {@code Collections.synchronizedMap(WeakHashMap)} 上的 {@code computeIfAbsent} ——
     * 抢<b>全局锁</b> + 每次访问都要扫 {@code ReferenceQueue} 清理失效弱引用。
     * 配合上面「每帧重试」，就变成每帧一次全局锁争用。</p>
     *
     * <p>更糟的是：display entity <b>从不触发</b> {@code Entity#remove}
     * （它只是渲染用的临时对象，不会进入世界），所以
     * {@code CapabilityRegistry} 里注册的 {@code EntityRemoveEvent} 清理逻辑
     * <b>永远不会执行</b> —— 条目只能等 GC 回收弱引用，进一步加重 map 负担。</p>
     *
     * <p><b>修复</b>：改为<b>惰性创建</b> —— 只有当存档里确实存过 DataHolder 数据时
     * 才建 provider。绝大多数实体（包括每帧重建的 display entity）根本没有这段 NBT，
     * 于是完全不碰那张全局 map，锁争用归零。</p>
     *
     * <p>注意 {@code hasSyncedDataKey} 判定<b>保留</b>：它是纯 {@code HashMap} 缓存查询，
     * 开销可忽略，且能挡掉绝大多数无关实体。真正贵的是它后面的 {@code get()}。</p>
     */
    @Inject(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V"))
    private void tacz$loadPersistentData(ValueInput input, CallbackInfo ci) {
        input.read("ForgeData", CompoundTag.CODEC).ifPresent(tag -> this.tacz$persistentData = tag);
        Entity self = (Entity) (Object) this;
        if (!SyncedEntityData.instance().hasSyncedDataKey(self.getClass())) {
            return;
        }
        // 惰性：没有已持久化的 DataHolder 就不要创建 provider。
        // tacz$persistentData 为 null 表示这个实体压根没有 ForgeData 段。
        CompoundTag persisted = this.tacz$persistentData;
        if (persisted == null || persisted.getListOrEmpty("DataHolder").isEmpty()) {
            return;
        }
        DataHolderCapabilityProvider.get(self).readFromNbt(persisted);
    }
}