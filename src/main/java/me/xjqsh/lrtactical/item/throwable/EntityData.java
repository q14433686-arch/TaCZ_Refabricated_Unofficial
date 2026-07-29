package me.xjqsh.lrtactical.item.throwable;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.jetbrains.annotations.Nullable;

/**
 * 投掷物实体的基础属性，由数据包 {@code index/throwable/*.json} 的 {@code entity} 段驱动。
 *
 * <p>字段与上游逐一对应，仅 Gson 反序列化用，无逻辑。
 */
public class EntityData {
    @SerializedName("life_time")
    private int lifeTime = 100;

    @SerializedName("gravity")
    private float gravity = 0.07f;

    @SerializedName("should_bounce")
    private boolean shouldBounce = true;

    @SerializedName("broke_on_ground")
    private boolean brokeOnGround = false;

    @SerializedName("bounce_factor")
    private double bounceFactor = 0.75;

    @SerializedName("hit_damage")
    private float hitDamage = 1.0f;

    /**
     * 尾迹粒子。
     *
     * <p>默认值 {@link ParticleTypes#SMOKE} 是 {@code SimpleParticleType}
     * （字节码确认，其本身即实现 {@code ParticleOptions}），可直接作为默认值使用。
     * 注意<b>不能</b>换成 {@code ParticleTypes.FLASH} 之类 —— 那类在 26.2 是
     * {@code ParticleType<ColorParticleOption>}，并非现成的 options 实例。
     *
     * <p>该字段目前<b>不参与 JSON 反序列化</b>：Gson 没有 {@code ParticleOptions}
     * 的适配器，写在 JSON 里会解析失败。上游同样没有注册该适配器，
     * 因此实际行为是「永远取默认值」。此处保持一致，不擅自加解析逻辑。
     */
    private final transient ParticleOptions tailParticles = ParticleTypes.SMOKE;

    public int getLifeTime() {
        return lifeTime;
    }

    public float getGravity() {
        return gravity;
    }

    public boolean isShouldBounce() {
        return shouldBounce;
    }

    public boolean isBrokeOnGround() {
        return brokeOnGround;
    }

    public double getBounceFactor() {
        return bounceFactor;
    }

    public float getHitDamage() {
        return hitDamage;
    }

    @Nullable
    public ParticleOptions getTailParticles() {
        return tailParticles;
    }
}
