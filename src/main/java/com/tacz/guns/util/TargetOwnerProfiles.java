package com.tacz.guns.util;

import com.tacz.guns.GunMod;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

/**
 * 标靶 / 标靶矿车「头像」所用档案的唯一创建入口（common 侧，只做数据，不碰任何客户端类）。
 *
 * <h2>为什么不能再写 {@code new GameProfile(null, 名字)}</h2>
 *
 * 标靶（以及标靶矿车）被改过名之后，头像要显示「那个名字对应玩家」的皮肤，所以需要一个
 * <b>只有名字、没有 UUID</b> 的档案。上游 1.21.1 用的是 {@code ResolvableProfile}；
 * 26.x 移植时换成了 {@code GameProfile} + {@code null} UUID，而本线实际依赖的
 * {@code authlib-7.0.61} 里 {@code GameProfile} 是 record，构造器对空值直接抛异常
 * （2026-10-27 用 CI 的 compileJava 前置阶段对这个 jar 反射实测，记录在提交 {@code ba453eb8}：
 * {@code new GameProfile(null, "probe")} → {@code NullPointerException: Profile ID must not be null}）。
 * 后果是<b>运行期</b>必现、编译期完全看不出来：
 * <ul>
 *   <li>放置改过名的标靶：{@code TargetBlock#setPlacedBy} 在给方块实体设主人之前就抛出，
 *       名字与头像都设不上；{@code BlockItem#place} 里 {@code setPlacedBy} 排在 {@code consume(1)}
 *       之前（1.21.11 反编译源码核对），异常还会让物品不被消耗；</li>
 *   <li>改过名的标靶矿车：旧的 {@code TargetMinecart#getGameProfile}（现为 {@code getOwnerProfile}）在客户端
 *       {@code extractRenderState} 里每帧调用，会让渲染线程直接崩溃。</li>
 * </ul>
 *
 * <h2>现在的做法：和原版头颅同一套</h2>
 *
 * 只存「名字」，用原版为「只知道名字的档案」专门提供的 {@link ResolvableProfile#createUnresolved(String)}；
 * 真正的 UUID 与皮肤由<b>客户端</b>在渲染时通过原版 {@code PlayerSkinRenderCache} 异步解析
 * （原版 {@code SkullBlockEntity} 也是这样：服务端不解析，见 1.21.11 反编译源码）。
 * 解析失败 / 名字不是真实玩家 / 离线服时回退为默认皮肤，不会抛异常。
 *
 * <p>26.2 线对同一症状的修法（服务端经 {@code Services#nameToIdCache} 把名字解析成 UUID，
 * 客户端改用 {@code SkinManager#createLookup(profile, false)}）<b>没有照搬</b>：1.21.11 的
 * {@code SkinManager#get} 自身不发起任何「按名字 / 按 UUID 向会话服务查人」的调用，只处理档案
 * 已经带着的 {@code textures} 属性；而那条服务端解析只会补出 UUID（没有 {@code textures}，
 * 也不会被存档或同步给客户端），所以客户端拿到后仍是默认皮肤；它的整个前提
 * （档案里可以有 {@code null} UUID）在本线也不成立。详见
 * {@code docs/lineage/SYNC_REVIEW_262_TO_1211_20261027.md}。</p>
 */
public final class TargetOwnerProfiles {
    private TargetOwnerProfiles() {
    }

    /**
     * 用一个名字创建「未解析」档案；名字为空白时返回 {@code null}（即没有头像）。
     *
     * <p>{@link ResolvableProfile#createUnresolved(String)} 内部会构造一个带占位 UUID 的
     * {@code GameProfile}，而 {@code GameProfile} 的构造器不接受 {@code null}；这里额外兜住
     * 任何运行期异常，宁可不要头像，也不让放置标靶 / 读取区块 / 渲染矿车因为一个名字而失败。</p>
     */
    @Nullable
    public static ResolvableProfile fromName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return ResolvableProfile.createUnresolved(name);
        } catch (RuntimeException e) {
            GunMod.LOGGER.warn("[TaCZ] Cannot build a head profile from target name '{}', the head is skipped: {}",
                    name, e.toString());
            return null;
        }
    }
}
