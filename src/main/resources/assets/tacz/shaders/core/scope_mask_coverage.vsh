#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
out vec4 maskColor;
void main() {
    // The aperture hull is already projected with the exact hand projection.
    // Its z=.5 is clip-space depth, not a view-space point to project a second time.
    gl_Position = Color.a < 0.5 ? vec4(Position, 1.0) : ProjMat * ModelViewMat * vec4(Position, 1.0);
    maskColor = vec4(Color.rgb * ColorModulator.rgb, 1.0);
}
