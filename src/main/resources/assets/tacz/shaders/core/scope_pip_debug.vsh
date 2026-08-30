#version 330

// Depth-based scope PIP diagnostic vertex shader.
// Draws one fullscreen triangle without any vertex buffer (vanilla core/screenquad idiom).
// The fragment shader derives the aperture test from the depth copies, so the only varying
// carried here is the normalized [0,1] screen coordinate.

out vec2 texCoord;

void main() {
    // Cheap fullscreen triangle from gl_VertexID (positions are the classic 3-vertex fan).
    float x = -1.0 + float((gl_VertexID & 1) << 2);
    float y = -1.0 + float((gl_VertexID & 2) << 1);
    gl_Position = vec4(x, y, 0.0, 1.0);
    texCoord = vec2((x + 1.0) * 0.5, (y + 1.0) * 0.5);
}
