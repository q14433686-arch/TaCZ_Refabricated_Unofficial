package me.xjqsh.lrtactical.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.xjqsh.lrtactical.item.index.ThrowableIndex;
import me.xjqsh.lrtactical.resource.manager.ThrowableIndexManager;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * LRTactical 的数据包资源管理器。
 *
 * <h2>与 TACZ 侧同名类的关系</h2>
 * 结构刻意对齐 {@code com.tacz.guns.resource.CommonAssetsManager}，但<b>相互独立</b>：
 * 各自持有自己的 Gson 与 manager，互不干扰。
 *
 * <h2>26.2 / Fabric 差异</h2>
 * <ul>
 *   <li>上游用 {@code ResourceLocationSerializer} 给 Gson 注册 {@code ResourceLocation}
 *       适配器。26.2 类名为 {@link Identifier}，故本类自带一个等价适配器
 *       —— <b>必须有</b>，否则 {@code ThrowableData#cooldownCategory}
 *       这类 Identifier 字段无法从 JSON 字符串反序列化。</li>
 *   <li>上游还注册了 {@code ParticleOptionsDeserializer}。本移植暂不支持
 *       从 JSON 配置尾迹粒子（见 {@code EntityData#tailParticles} 的说明），
 *       故不注册 —— <b>不写一个会静默出错的半成品适配器</b>。</li>
 * </ul>
 */
public final class CommonAssetsManager {
    /**
     * 本模块专用的 Gson。
     *
     * <p>{@link Identifier} 适配器是必需的：数据包里写的是
     * {@code "cooldown_category": "lrtactical:grenade"} 这样的字符串。
     */
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Identifier.class,
                    (com.google.gson.JsonDeserializer<Identifier>) (json, type, ctx) ->
                            Identifier.tryParse(json.getAsString()))
            .registerTypeAdapter(Identifier.class,
                    (com.google.gson.JsonSerializer<Identifier>) (src, type, ctx) ->
                            new com.google.gson.JsonPrimitive(src.toString()))
            // 近战 hitbox 是按 type 字段分派的多态类型（cone/ray/obb）。
            // 【必须注册】否则 Gson 会试图直接实例化 ITargetFilter 这个接口，
            // 报出的异常与真正的原因毫不相干，极难排查。
            .registerTypeAdapter(me.xjqsh.lrtactical.api.collision.ITargetFilter.class,
                    new me.xjqsh.lrtactical.api.collision.ITargetFilter.Deserializer())
            // 近战 attack 段：按 attack_left/attack_right 分派，且兼容「单对象」与「数组」两种写法
            .registerTypeAdapter(me.xjqsh.lrtactical.item.melee.CombatData.class,
                    new me.xjqsh.lrtactical.item.melee.CombatData.Deserializer())
            // 近战 attributes 段：key 是属性 id，value 可以是数字或对象
            .registerTypeAdapter(me.xjqsh.lrtactical.item.melee.AttributeData.class,
                    new me.xjqsh.lrtactical.item.melee.AttributeData.Deserializer())
            // 效果云的单条效果：需要把 "type" 字符串解析成 Holder<MobEffect>，
            // Gson 无法自动完成，必须注册。漏了会在解析 effects 数组时静默失败。
            .registerTypeAdapter(
                    me.xjqsh.lrtactical.item.throwable.area.EffectCloudThrowableData.EffectData.class,
                    new me.xjqsh.lrtactical.item.throwable.area.EffectCloudThrowableData.EffectDataDeserializer())
            // 消耗品 remove_effects 支持 "@harmful" / "@beneficial" / 单个效果 id
            .registerTypeAdapter(
                    me.xjqsh.lrtactical.item.consumable.ConsumableData.RemoveEffectSelector.class,
                    new me.xjqsh.lrtactical.item.consumable.ConsumableData.RemoveEffectSelector.Deserializer())
            .create();

    private static final CommonAssetsManager INSTANCE = new CommonAssetsManager();

    private final ThrowableIndexManager throwableIndex = new ThrowableIndexManager(GSON);
    private final me.xjqsh.lrtactical.resource.manager.MeleeIndexManager meleeIndex =
            new me.xjqsh.lrtactical.resource.manager.MeleeIndexManager(GSON);
    private final me.xjqsh.lrtactical.resource.manager.ConsumableIndexManager consumableIndex =
            new me.xjqsh.lrtactical.resource.manager.ConsumableIndexManager(GSON);

    private CommonAssetsManager() {
    }

    public static CommonAssetsManager get() {
        return INSTANCE;
    }

    public ThrowableIndexManager getThrowableIndexManager() {
        return throwableIndex;
    }

    @Nullable
    public ThrowableIndex<?, ?> getThrowableIndex(Identifier id) {
        return throwableIndex.getData(id);
    }

    public Collection<ThrowableIndex<?, ?>> getThrowableIndexes() {
        return Collections.unmodifiableCollection(throwableIndex.getAllData().values());
    }

    // ---------------- melee ----------------

    public me.xjqsh.lrtactical.resource.manager.MeleeIndexManager getMeleeIndexManager() {
        return meleeIndex;
    }

    @Nullable
    public me.xjqsh.lrtactical.item.index.MeleeWeaponIndex<?> getMeleeIndex(Identifier id) {
        return meleeIndex.getData(id);
    }

    public Collection<me.xjqsh.lrtactical.item.index.MeleeWeaponIndex<?>> getMeleeIndexes() {
        return Collections.unmodifiableCollection(meleeIndex.getAllData().values());
    }

    // ---------------- consumable ----------------

    public me.xjqsh.lrtactical.resource.manager.ConsumableIndexManager getConsumableIndexManager() {
        return consumableIndex;
    }

    @Nullable
    public me.xjqsh.lrtactical.item.index.ConsumableIndex getConsumableIndex(Identifier id) {
        return consumableIndex.getData(id);
    }

    public Collection<me.xjqsh.lrtactical.item.index.ConsumableIndex> getConsumableIndexes() {
        return Collections.unmodifiableCollection(consumableIndex.getAllData().values());
    }
}
