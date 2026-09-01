package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code LevelRenderer} 的私有提交节点表，供 PIP 二次渲染在窄遍结束后清掉遗留提交。
 *
 * <p>背景：26.1.2 一帧只做一次 {@code GameRenderer.extract → LevelRenderer.extractLevel}
 * 状态提取，而 {@code LevelRenderer.renderLevel} 尾部会 {@code LevelRenderState.reset()}。
 * PIP 的窄 FOV 第二次 {@code renderLevel} 消费并清空了共享状态 —— vanilla 主遍拿到空状态
 * （镜外实体/太阳/雾全灭的根因）。修复 = 窄遍后清提交表 + 重跑 {@code extractLevel}；
 * 清表需要这个 accessor（{@code SubmitNodeStorage.clear()} 本身是 public，字段不是）。</p>
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("submitNodeStorage")
    SubmitNodeStorage tacz$getSubmitNodeStorage();
}
