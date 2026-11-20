#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
#ifdef MATCH_SHIFTED_FACE
    // Complementary's entity flicker fix is in clip space, NOT normalized/window depth.
    // This pipeline uses EQUAL on a private copy of the real scene depth, not LEQUAL.
    gl_Position.z -= 0.00015;
#endif
    texCoord0 = UV0;
}
