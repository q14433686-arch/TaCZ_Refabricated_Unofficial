package me.xjqsh.lrtactical.item.throwable.area;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.item.throwable.ThrowableData;
import me.xjqsh.lrtactical.util.PotionTooltipUtil;
import me.xjqsh.lrtactical.util.TooltipLine;
import me.xjqsh.lrtactical.util.TooltipUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 效果云投掷物的配置，对应数据包里 {@code "type": "lrtactical:effect_cloud"} 的 {@code data} 段。
 *
 * <p>支持两种形态，由 {@code area_cloud} 切换：
 * <ul>
 *   <li>{@code true}（默认）：生成一片<b>持续存在的效果云</b>（类似滞留药水）；</li>
 *   <li>{@code false}：<b>一次性喷溅</b>（类似喷溅药水），立即对范围内目标施加效果。</li>
 * </ul>
 *
 * <h2>26.2 移植要点</h2>
 * <ul>
 *   <li>{@code ResourceLocation} → {@link Identifier}；</li>
 *   <li><b>{@code Registry#getHolder(Identifier)} 已改名为 {@code get(Identifier)}</b>，
 *       返回 {@code Optional<Holder.Reference<T>>}（字节码确认）。</li>
 *   <li>{@code particles} 字段<b>不参与 JSON 反序列化</b> —— 与基类
 *       {@code EntityData#tailParticles} 的处理一致：Gson 没有
 *       {@code ParticleOptions} 的适配器，写在 JSON 里会解析失败；
 *       上游同样没有注册该适配器，实际行为就是「永远取默认值」。
 *       此处保持一致，标为 {@code transient} 让这个事实<b>在代码里显式可见</b>，
 *       而不是留一个看起来能配、实则静默失效的字段。</li>
 * </ul>
 */
public class EffectCloudThrowableData extends ThrowableData {
    @SerializedName("cloud")
    private CloudData cloud = new CloudData();

    @NotNull
    public CloudData getCloudData() {
        return cloud;
    }

    @Override
    public List<TooltipLine> getTooltipLines() {
        List<TooltipLine> lines = super.getTooltipLines();
        CloudData data = getCloudData();
        lines.add(TooltipLine.normal(Component.translatable(
                "tooltip.lrtactical.throwable.cloud.line",
                TooltipUtil.format(data.getRadius()), TooltipUtil.formatTicks(data.getDuration()))));
        if (data.isIgnite()) {
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.throwable.cloud.ignite",
                    TooltipUtil.formatTicks(data.getIgniteTime()))));
        }
        List<PotionTooltipUtil.EffectWithChance> effects = data.getEffectInstances().stream()
                .map(effect -> new PotionTooltipUtil.EffectWithChance(effect, 1.0F)).toList();
        List<Component> effectLines = new ArrayList<>();
        PotionTooltipUtil.addPotionTooltip(effects, effectLines);
        effectLines.forEach(line -> lines.add(TooltipLine.collapsible(line)));
        return lines;
    }

    public static class CloudData {
        /** true=持续效果云；false=一次性喷溅。 */
        @SerializedName("area_cloud")
        private boolean areaCloud = true;

        @SerializedName("radius")
        private float radius = 5.5f;

        /** 每 tick 的半径变化量（负数表示逐渐缩小）。 */
        @SerializedName("radius_per_tick")
        private float radiusPerTick = 0.01f;

        /** 生成后多久才开始生效（tick）。 */
        @SerializedName("wait_time")
        private int waitTime = 20;

        @SerializedName("duration")
        private int duration = 200;

        /**
         * 云的粒子外观。
         *
         * <p>见类注释：{@code transient} 是<b>有意为之</b> —— 无 Gson 适配器，
         * 配了也不会生效，故不让它出现在可配置字段里。
         *
         * <p><b>26.2 变更</b>：{@code ParticleTypes.EFFECT} 在 26.2 的类型是
         * {@code ParticleType<SpellParticleOption>}（字节码确认），
         * <b>它本身不是 {@code ParticleOptions}</b>，不能像 1.21.1 那样直接赋值 ——
         * 这与手雷那边 {@code ParticleTypes.FLASH} 踩过的是同一个坑。
         * 必须用 {@code SpellParticleOption.create(type, color, alpha)} 构造实例。
         * 取白色不透明，与原版药水云的默认观感一致。
         */
        private final transient ParticleOptions particles =
                SpellParticleOption.create(ParticleTypes.EFFECT, 0xFFFFFF, 1.0F);

        @SerializedName("ignite")
        private boolean ignite = false;

        @SerializedName("ignite_time")
        private int igniteTime = 2;

        /** 被烟雾弹扑灭（燃烧云 + 烟雾弹的组合玩法）。 */
        @SerializedName("extinguish_by_smoke")
        private boolean extinguishBySmoke = false;

        @SerializedName("effects")
        private List<EffectData> effects = new ArrayList<>();

        public boolean isAreaCloud() {
            return areaCloud;
        }

        public float getRadius() {
            return radius;
        }

        public float getRadiusPerTick() {
            return radiusPerTick;
        }

        public int getWaitTime() {
            return waitTime;
        }

        public int getDuration() {
            return duration;
        }

        public ParticleOptions getParticles() {
            return particles;
        }

        public boolean isIgnite() {
            return ignite;
        }

        public int getIgniteTime() {
            return igniteTime;
        }

        public boolean isExtinguishBySmoke() {
            return extinguishBySmoke;
        }

        public List<EffectData> getEffects() {
            return effects == null ? List.of() : effects;
        }

        public List<MobEffectInstance> getEffectInstances() {
            List<MobEffectInstance> instances = new ArrayList<>();
            for (EffectData effect : getEffects()) {
                instances.add(effect.toInstance());
            }
            return instances;
        }
    }

    public record EffectData(Holder<MobEffect> type, int duration, int amplifier,
                             boolean visible, boolean showIcon) {
        public MobEffectInstance toInstance() {
            // 26.2 六参构造确认存在：(Holder, duration, amplifier, ambient, visible, showIcon)
            return new MobEffectInstance(type, duration, amplifier, false, visible, showIcon);
        }
    }

    /**
     * 单条效果的反序列化。
     *
     * <p>未知效果 id 直接抛异常而<b>不是</b>静默跳过 ——
     * 否则内容包作者会以为配好了，实际那条效果根本没生效。
     */
    public static class EffectDataDeserializer implements JsonDeserializer<EffectData> {
        @Override
        public EffectData deserialize(JsonElement element, Type type, JsonDeserializationContext ctx)
                throws JsonParseException {
            if (!element.isJsonObject()) {
                throw new JsonParseException("Invalid EffectData JSON: " + element);
            }
            JsonObject obj = element.getAsJsonObject();
            String rawId = GsonHelper.getAsString(obj, "type");
            Identifier id = Identifier.tryParse(rawId);
            if (id == null) {
                throw new JsonParseException("Malformed effect id \"" + rawId + "\"");
            }
            // 26.2: Registry#getHolder(Identifier) 已改名为 get(Identifier)，
            // 返回 Optional<Holder.Reference<MobEffect>>（字节码确认）。
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
            if (effect == null) {
                throw new JsonParseException("Unknown effect type: " + id);
            }
            int duration = GsonHelper.getAsInt(obj, "duration", 200);
            if (duration < 0) {
                throw new JsonParseException("Duration must be non-negative: " + duration);
            }
            int amplifier = GsonHelper.getAsInt(obj, "amplifier", 0);
            if (amplifier < 0) {
                throw new JsonParseException("Amplifier must be non-negative: " + amplifier);
            }
            boolean visible = GsonHelper.getAsBoolean(obj, "visible", true);
            boolean showIcon = GsonHelper.getAsBoolean(obj, "show_icon", true);
            return new EffectData(effect, duration, amplifier, visible, showIcon);
        }
    }
}
