package com.tacz.guns.client.particle;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.particles.BulletHoleOption;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/**
 * Author: Forked from MrCrayfish, continued by Timeless devs
 * 26.2: TextureSheetParticle → SingleQuadParticle, render → extract
 */
public class BulletHoleParticle extends SingleQuadParticle {
    private final Direction direction;
    private final BlockPos pos;
    private int uOffset;
    private int vOffset;
    private float textureDensity;

    public BulletHoleParticle(ClientLevel world, double x, double y, double z, Direction direction, BlockPos pos, String ammoId, String gunId, String gunDisplayId) {
        super(world, x, y, z, getSpriteForPos(pos));
        this.direction = direction;
        this.pos = pos;
        this.lifetime = this.getLifetimeFromConfig(world);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.quadSize = 0.05F;

        BlockState state = world.getBlockState(pos);
        if (state.is(ModBlocks.TARGET) || shouldRemove()) {
            this.remove();
        }
        TimelessAPI.getGunDisplay(Identifier.parse(gunDisplayId), Identifier.parse(gunId)).ifPresent(gunIndex -> {
            float[] gunTracerColor = gunIndex.getTracerColor();
            if (gunTracerColor != null) {
                this.rCol = gunTracerColor[0];
                this.gCol = gunTracerColor[1];
                this.bCol = gunTracerColor[2];
            } else {
                TimelessAPI.getClientAmmoIndex(Identifier.parse(ammoId)).ifPresent(ammoIndex -> {
                    float[] ammoTracerColor = ammoIndex.getTracerColor();
                    this.rCol = ammoTracerColor[0];
                    this.gCol = ammoTracerColor[1];
                    this.bCol = ammoTracerColor[2];
                });
            }
        });
        this.alpha = 0.9F;
    }

    private static TextureAtlasSprite getSpriteForPos(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        Level world = minecraft.level;
        if (world != null) {
            BlockState state = world.getBlockState(pos);
            return minecraft.getModelManager().getBlockModelShaper().getParticleIcon(state);
        }
        // Fallback: should not normally happen
        return minecraft.getModelManager().getBlockModelShaper().getParticleIcon(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }

    private int getLifetimeFromConfig(ClientLevel world) {
        int configLife = RenderConfig.BULLET_HOLE_PARTICLE_LIFE.get();
        if (configLife <= 1) {
            return configLife;
        }
        return configLife + world.getRandom().nextInt(configLife / 2);
    }

    @Override
    protected void setSprite(TextureAtlasSprite sprite) {
        super.setSprite(sprite);
        this.uOffset = this.random.nextInt(16);
        this.vOffset = this.random.nextInt(16);
        // 材质应该都是方形
        this.textureDensity = (sprite.getU1() - sprite.getU0()) / 16.0F;
    }

    @Override
    protected float getU0() {
        return this.sprite.getU0() + this.uOffset * this.textureDensity;
    }

    @Override
    protected float getV0() {
        return this.sprite.getV0() + this.vOffset * this.textureDensity;
    }

    @Override
    protected float getU1() {
        return this.getU0() + this.textureDensity;
    }

    @Override
    protected float getV1() {
        return this.getV0() + this.textureDensity;
    }

    @Override
    public void tick() {
        super.tick();
        if (shouldRemove()) {
            this.remove();
        }
    }

    /**
     * <b>第 8 轮修复：击碎方块时天上出现会变大的怪异方片。</b>
     *
     * <p>原代码写的是：</p>
     * <pre>
     * this.extractRotatedQuad(state, quaternion, red, green, blue, alphaFade);
     * </pre>
     *
     * <p>看起来像是"传旋转 + 颜色"，但 26.2 的 {@code SingleQuadParticle} 里<b>没有</b>
     * 接收颜色的重载（反编译确认，只有三个）：</p>
     * <pre>
     * extractRotatedQuad(QuadParticleRenderState, Camera, Quaternionf, float partialTick)
     * extractRotatedQuad(QuadParticleRenderState, Quaternionf, float x, float y, float z, float partialTick)
     * </pre>
     *
     * <p>于是这次调用被静默绑定到了第二个重载 —— <b>把 r/g/b 当成了 x/y/z 坐标</b>，
     * 把 alpha 当成了 partialTick。颜色是 0~1 的浮点，所以四边形被画在
     * "相对摄像机 (r, g, b)"这个<b>固定偏移</b>处（+x 偏东、+z 偏南、+y 在上方），
     * 与实际弹孔位置完全无关 —— 这就是你看到的"固定出现在西-西南方向屏幕上方"。</p>
     *
     * <p>而颜色取自枪械/弹药的 <b>tracerColor</b>，所以<b>不同枪械出现在不同位置</b>；
     * 随着 {@code colorPercent} 衰减到 0，坐标也趋近摄像机原点，
     * 视觉上就是"逐渐变大后消失"，生命周期约 60 tick ≈ 3 秒 —— 与反馈逐条吻合。</p>
     *
     * <p>正确做法：用带 Camera 的重载，让父类自己算出相机相对坐标；
     * 颜色则通过 {@code rCol/gCol/bCol/alpha} 字段传递（父类 {@code extractRotatedQuad}
     * 内部用 {@code ARGB.colorFromFloat(this.alpha, this.rCol, this.gCol, this.bCol)} 取值）。</p>
     */
    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTicks) {
        // 0 - 30 tick 内，从 15 亮度到 0 亮度
        int light = Math.max(15 - this.age / 2, 0);

        // 颜色，逐渐渐变到 0 0 0，也就是黑色
        float colorPercent = light / 15.0f;

        // 透明度，逐渐变成 0，也就是透明
        double threshold = RenderConfig.BULLET_HOLE_PARTICLE_FADE_THRESHOLD.get() * this.lifetime;
        float fade = 1.0f - (float) (Math.max(this.age - threshold, 0) / (this.lifetime - threshold));

        // 备份基色，渲染时临时写入父类字段（父类从这些字段取色），渲染完再还原，
        // 避免把衰减后的颜色累积回基色。
        float baseR = this.rCol;
        float baseG = this.gCol;
        float baseB = this.bCol;
        float baseA = this.alpha;
        this.rCol = baseR * colorPercent;
        this.gCol = baseG * colorPercent;
        this.bCol = baseB * colorPercent;
        this.alpha = baseA * fade;
        try {
            // 使用方向四元数旋转四边形；位置交给带 Camera 的重载计算。
            Quaternionf quaternion = this.direction.getRotation();
            this.extractRotatedQuad(state, camera, quaternion, partialTicks);
        } finally {
            this.rCol = baseR;
            this.gCol = baseG;
            this.bCol = baseB;
            this.alpha = baseA;
        }
    }

    @Override
    public ParticleRenderType getGroup() {
        return ParticleRenderType.SINGLE_QUADS;
    }

    /**
     * {@code SingleQuadParticle.Layer} 在 1.21.11 里的常量是
     * {@code TERRAIN / ITEMS / OPAQUE / TRANSLUCENT}（javap 确认），
     * 没有 26.1 的 {@code TRANSLUCENT_TERRAIN} 合并常量。
     * 弹孔贴在方块表面、取自方块图集，且需要半透明混合，
     * 因此对应到这一版就是 {@code TERRAIN}（其 record 的 translucent 标志为 true）。
     */
    @Override
    protected Layer getLayer() {
        return Layer.TERRAIN;
    }

    private boolean shouldRemove() {
        final BlockState blockState = this.level.getBlockState(this.pos);
        if (blockState.isAir()) {
            return true;
        } else {
            // 阻止弹孔在与方块不构成有效附着时继续渲染
            VoxelShape shape = blockState.getCollisionShape(this.level, this.pos);
            if (shape.isEmpty()) {
                return true;
            }
            AABB baseBlockBoundingBox = shape.bounds();
            AABB blockBoundingBox = baseBlockBoundingBox.move(this.pos);
            boolean intersects = blockBoundingBox.intersects(
                    this.x - 0.1, this.y - 0.1, this.z - 0.1,
                    this.x + 0.1, this.y + 0.1, this.z + 0.1);
            return !intersects;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class Provider implements ParticleProvider<BulletHoleOption> {
        public Provider() {
        }

        @Override
        public BulletHoleParticle createParticle(@NotNull BulletHoleOption option, @NotNull ClientLevel world, double x, double y, double z, double pXSpeed, double pYSpeed, double pZSpeed, RandomSource random) {
            return new BulletHoleParticle(world, x, y, z, option.getDirection(), option.getPos(), option.getAmmoId(), option.getGunId(), option.getGunDisplayId());
        }
    }
}
