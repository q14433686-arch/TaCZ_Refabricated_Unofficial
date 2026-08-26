package me.xjqsh.lrtactical.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.NotNull;

/**
 * 烟雾弹的烟雾粒子 —— 大体积、不受重力、无碰撞，按环境光采样（保底 2）。
 *
 * <h2>26.2 移植要点：粒子系统整体重组（均字节码确认）</h2>
 * <table border="1">
 *   <tr><th>上游（1.21.1）</th><th>26.2</th></tr>
 *   <tr><td>{@code extends TextureSheetParticle}</td>
 *       <td><b>该类已不存在</b> → {@code extends SingleQuadParticle}</td></tr>
 *   <tr><td>{@code getRenderType()} 返回 {@code ParticleRenderType}</td>
 *       <td>拆成两个：{@code getGroup()} 返回 {@code ParticleRenderType}，
 *           {@code getLayer()} 返回 {@code SingleQuadParticle.Layer}</td></tr>
 *   <tr><td>{@code ParticleRenderType.PARTICLE_SHEET_LIT}</td>
 *       <td>该常量已无 → {@code ParticleRenderType.SINGLE_QUADS}
 *           + {@code Layer.TRANSLUCENT}</td></tr>
 *   <tr><td>{@code createParticle(..., double zSpeed)}</td>
 *       <td>末尾<b>新增 {@code RandomSource} 参数</b></td></tr>
 *   <tr><td>{@code render(VertexConsumer, Camera, float)}</td>
 *       <td>渲染改为 {@code extract(QuadParticleRenderState, Camera, float)}，
 *           本类无需自定义渲染，故<b>不再覆写</b></td></tr>
 * </table>
 *
 * <p>写法对照本仓库已适配好的 {@code com.tacz.guns.client.particle.BulletHoleParticle}
 * （同为 {@code SingleQuadParticle} 子类），以及原版 {@code PlayerCloudParticle}。
 *
 * <h2>贴图从哪来</h2>
 * 本移植<b>不打包原作美术资源</b>，但粒子必须有贴图才能渲染。
 * 解法是提供一份 {@code assets/lrtactical/particles/smoke_cloud.json}，
 * 内容指向<b>原版已有的烟雾贴图</b>。这样：
 * <ul>
 *   <li>不引入任何受限素材；</li>
 *   <li>烟雾功能（视野遮蔽）完全可用；</li>
 *   <li>内容包若想换成自己的贴图，覆盖同名 json 即可。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class SmokeCloudParticle extends SingleQuadParticle {
    private final SpriteSet spriteSet;

    protected SmokeCloudParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteSet spriteSet) {
        super(level, x, y, z, spriteSet.first());
        this.spriteSet = spriteSet;
        // 单个粒子铺得很大，少量粒子即可形成完整烟幕
        this.quadSize *= 5.5F;
        this.lifetime = 20;
        this.gravity = 0F;
        this.hasPhysics = false;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.setSpriteFromAge(spriteSet);
    }

    /**
     * 按<b>环境光</b>采样，天光/块光各自保底 2。
     *
     * <p>此前写死 15728880（= 0xF000F0，天光块光全满），理由是「烟雾不该在暗处变黑」。
     * 但那让烟幕在夜里/洞里变成一团自发光的白雾，比变黑更违和。
     * 官方 0.4.3 的做法是采环境光并加一个下限：两者都 ≤2 时再扫六个邻格取较大值
     * —— 烟雾体积很大，粒子中心常常落在墙体/自身遮挡里，只采中心格会偏暗。
     *
     * <h2>本分支方法名</h2>
     * 覆写的方法是 {@code getLightColor(float)} —— 1.21.1 上游同名，
     * 26.2 才改名为 {@code getLightCoords(float)}。此前这里的注释把前后两个名字
     * 写成了同一个（从 26.2 复制后改漏），已修正。
     *
     * <p>不走 {@code LevelRenderer.getLightColor}，改用
     * {@code BlockAndLightGetter#getBrightness(LightLayer, BlockPos)}
     * （{@code ClientLevel} 沿继承链实现它）自己打包成
     * {@code sky << 20 | block << 4}，与原版 {@code LightTexture} 的编码一致。
     *
     * <p>它在 {@code Particle} 上是 <b>protected</b>，这里保持同样的可见性 ——
     * 放宽成 public 虽然合法，但没有理由对外暴露。
     */
    @Override
    protected int getLightColor(float partialTick) {
        BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
        int sky = this.level.getBrightness(LightLayer.SKY, pos);
        int block = this.level.getBrightness(LightLayer.BLOCK, pos);
        if (sky <= 2 && block <= 2) {
            // 中心格几乎全黑：极可能是被自身/墙体遮住，扫一圈邻格取最亮的
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                sky = Math.max(sky, this.level.getBrightness(LightLayer.SKY, neighbor));
                block = Math.max(block, this.level.getBrightness(LightLayer.BLOCK, neighbor));
            }
        }
        // 下限 2：完全的漆黑会让烟幕彻底消失，玩家看不出自己被烟住了
        sky = Math.max(sky, 2);
        block = Math.max(block, 2);
        return sky << 20 | block << 4;
    }

    @Override
    public @NotNull ParticleRenderType getGroup() {
        return ParticleRenderType.SINGLE_QUADS;
    }

    @Override
    protected @NotNull Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.spriteSet);
    }

    /**
     * 26.2: {@code createParticle} 末尾新增 {@code RandomSource} 参数（字节码确认）。
     */
    @Environment(EnvType.CLIENT)
    public record Provider(SpriteSet spriteSet) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed,
                                       @NotNull RandomSource random) {
            return new SmokeCloudParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }
}
