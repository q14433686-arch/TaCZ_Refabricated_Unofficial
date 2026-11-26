#!/usr/bin/env python3
"""Target-specific porting diagnostic: javap the named 1.21.11 API, never a donor JAR."""
import subprocess
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'docs'))
from target_12111_jar import named_jar

jar = named_jar()
print('PORT_RENDER_API:', jar, flush=True)
for name in [
    'net.minecraft.client.renderer.SubmitNodeStorage$ModelSubmit',
    'net.minecraft.client.renderer.feature.ModelFeatureRenderer$Storage',
    'net.minecraft.client.renderer.rendertype.RenderType',
    'net.minecraft.client.renderer.rendertype.RenderSetup',
    'net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding',
    'net.minecraft.client.renderer.state.CameraRenderState',
    'net.minecraft.client.renderer.texture.AbstractTexture',
    'com.mojang.blaze3d.pipeline.RenderPipeline$Builder',
    'net.minecraft.client.renderer.PlayerSkinRenderCache',
    'net.minecraft.client.renderer.PlayerSkinRenderCache$RenderInfo',
    'net.minecraft.world.entity.player.PlayerSkin',
    'net.minecraft.client.sounds.SoundEngine',
    'net.minecraft.client.KeyboardHandler',
    'net.minecraft.client.MouseHandler',
    'net.minecraft.client.KeyMapping',
    'net.minecraft.client.renderer.LevelRenderer',
    'net.minecraft.client.renderer.state.LevelRenderState',
]:
    subprocess.run(['javap', '-classpath', jar, '-p', name], check=True)

# Keep selected method bytecode, not whole classes (the CI log is deliberately bounded).
for name, methods in {
    'net.minecraft.client.renderer.LevelRenderer': ['submitEntities(', 'submitBlockEntities(', 'method_62214(', 'lambda$addMainPass$'],
    'net.minecraft.client.renderer.PlayerSkinRenderCache': ['getOrDefault(', 'lookup('],
    'net.minecraft.client.sounds.SoundEngine': ['calculateVolume('],
    'net.minecraft.client.KeyMapping': ['matches(', 'click(', 'resetMapping('],
    'com.mojang.blaze3d.pipeline.RenderTarget': ['copyDepthFrom('],
    'net.minecraft.client.renderer.GameRenderer': ['renderItemInHand('],
    'net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer': ['getBuffer(', 'createProjectionMatrix('],
    'com.mojang.blaze3d.systems.RenderSystem': ['setProjectionMatrix(', 'getProjectionMatrixBuffer('],
}.items():
    print('PORT_RENDER_BYTECODE:', name, flush=True)
    text = subprocess.check_output(['javap', '-classpath', jar, '-p', '-c', name], text=True)
    selected = False
    for line in text.splitlines():
        if line.startswith('  ') and not line.startswith('    ') and '(' in line:
            selected = any(method in line for method in methods)
        if selected:
            print(line, flush=True)

# Keep the R5 compact API at the tail so CI's head/tail log cap cannot elide it.
for name in [
    'net.minecraft.client.renderer.PlayerSkinRenderCache$RenderInfo',
    'net.minecraft.client.sounds.SoundManager',
    'net.minecraft.client.resources.sounds.AbstractSoundInstance',
]:
    print('PORT_PLATFORM_API:', name, flush=True)
    subprocess.run(['javap', '-classpath', jar, '-p', name], check=True)
