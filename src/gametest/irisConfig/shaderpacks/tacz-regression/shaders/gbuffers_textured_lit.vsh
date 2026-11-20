#version 120
uniform float frameTimeCounter;
uniform float viewWidth;
uniform float viewHeight;
varying vec2 texcoord;
varying vec4 color;
void main() {
    gl_Position = ftransform();
    gl_Position.xy += vec2(sin(frameTimeCounter * 27.0), cos(frameTimeCounter * 31.0)) * 0.8 * gl_Position.w / vec2(viewWidth, viewHeight);
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    color = gl_Color;
}
