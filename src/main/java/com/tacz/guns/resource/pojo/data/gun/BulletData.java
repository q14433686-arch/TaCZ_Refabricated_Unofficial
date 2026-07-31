package com.tacz.guns.resource.pojo.data.gun;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

public class BulletData {
    @SerializedName("life")
    private float lifeSecond = 10f;

    @SerializedName("bullet_amount")
    private int bulletAmount = 1;

    @SerializedName("damage")
    private float damageAmount = 5;

    @SerializedName("extra_damage")
    private @Nullable ExtraDamage extraDamage = null;

    @SerializedName("speed")
    private float speed = 5;

    @SerializedName("gravity")
    private float gravity = 0;

    @SerializedName("knockback")
    private float knockback = 0;

    @SerializedName("friction")
    private float friction = 0.01f;

    @SerializedName("pierce")
    private int pierce = 1;

    @SerializedName("ignite")
    private Ignite ignite = new Ignite(false);

    @SerializedName("ignite_entity_time")
    private int igniteEntityTime = 2;

    @SerializedName("tracer_count_interval")
    private int tracerCountInterval = -1;

    @SerializedName("explosion")
    private @Nullable ExplosionData explosionData;

    // ====== P0扩展：弹道系统字段 ======

    /**
     * 弹头长度（游戏单位，用于缠距匹配计算）
     * 默认值0表示使用旧逻辑（不参与缠距匹配）
     */
    @SerializedName("bullet_length")
    private float bulletLength = 0;

    /**
     * 弹头直径/口径（游戏单位）
     * 默认值0表示使用旧逻辑
     */
    @SerializedName("bullet_diameter")
    private float bulletDiameter = 0;

    /**
     * 最佳枪管长度（mm）
     * 影响初速计算：实际枪管长度偏离最佳值时初速按比例降低
     */
    @SerializedName("optimal_barrel_length")
    private int optimalBarrelLength = 0;

    /**
     * 弹道系数（综合弹头形状/质量/截面积）
     * 影响空气阻力计算，值越大阻力越小
     */
    @SerializedName("ballistic_coefficient")
    private float ballisticCoefficient = 0.3f;

    public float getLifeSecond() {
        return lifeSecond;
    }

    public int getBulletAmount() {
        return bulletAmount;
    }

    public float getDamageAmount() {
        return damageAmount;
    }

    @Nullable
    public ExtraDamage getExtraDamage() {
        return extraDamage;
    }

    public float getSpeed() {
        return speed;
    }

    public float getGravity() {
        return gravity;
    }

    public float getKnockback() {
        return knockback;
    }

    public float getFriction() {
        return friction;
    }

    public int getPierce() {
        return pierce;
    }

    public Ignite getIgnite() {
        return ignite;
    }

    public int getIgniteEntityTime() {
        return igniteEntityTime;
    }

    public boolean hasTracerAmmo() {
        return this.tracerCountInterval >= 0;
    }

    public int getTracerCountInterval() {
        return tracerCountInterval;
    }

    @Nullable
    public ExplosionData getExplosionData() {
        return explosionData;
    }

    // P0扩展：弹道系统getter

    public float getBulletLength() {
        return bulletLength;
    }

    public float getBulletDiameter() {
        return bulletDiameter;
    }

    public int getOptimalBarrelLength() {
        return optimalBarrelLength;
    }

    public float getBallisticCoefficient() {
        return ballisticCoefficient;
    }
}
