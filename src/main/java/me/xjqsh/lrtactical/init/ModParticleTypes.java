package me.xjqsh.lrtactical.init;

import me.xjqsh.lrtactical.EquipmentMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * 粒子类型注册。
 *
 * <h2>与 NeoForge 版的差异</h2>
 * 上游用 {@code DeferredRegister}；Fabric 直接 {@code Registry.register}，
 * 写法沿用本仓库 {@code com.tacz.guns.init.ModParticles} 的既有模式。
 *
 * <h2>【26.2】不能直接 {@code new SimpleParticleType(true)}</h2>
 * 该构造器是 <b>protected</b>（字节码确认 access flags），模组代码无法调用。
 * 正确做法是用 Fabric 官方提供的工厂 {@code FabricParticleTypes.simple(boolean)}
 * —— 它内部就是 {@code new SimpleParticleType(alwaysSpawn) { }}（匿名子类绕开
 * protected 限制），是 Fabric 为此专门提供的公开入口，已核对 26.2 分支源码。
 *
 * <p>参数 {@code alwaysSpawn = true}：无视客户端「粒子数量」图形设置。
 * 烟雾弹是战术道具，遮蔽效果直接影响玩法平衡，
 * 不能因为对方把粒子调到「最少」就看得一清二楚，故与上游一致取 true。
 *
 * <p><b>{@code init()} 必须被显式调用</b>：Fabric 没有 DeferredRegister，
 * 注册写在静态字段里，而 Java 类加载是惰性的 —— 没有调用方就永远不会注册。
 * 这个坑本模块第 4 步已经踩过一次（物品注册了却找不到）。
 */
public final class ModParticleTypes {
    public static final SimpleParticleType SMOKE_CLOUD =
            register("smoke_cloud", FabricParticleTypes.simple(true));

    private ModParticleTypes() {
    }

    public static void init() {
        // 触发静态初始化，完成注册
    }

    private static SimpleParticleType register(String name, SimpleParticleType type) {
        // 注册表类型是 Registry<ParticleType<?>>，SimpleParticleType 是其子类型；
        // 用与 TACZ 侧 ModParticles#register 相同的写法，把返回值转回具体类型。
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, name), type);
        return type;
    }
}
