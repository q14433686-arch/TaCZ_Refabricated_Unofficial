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
]:
    subprocess.run(['javap', '-classpath', jar, '-p', name], check=True)
