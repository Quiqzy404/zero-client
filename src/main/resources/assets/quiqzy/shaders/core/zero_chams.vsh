#version 330

// ZERO Chams: Modell-Vertex -> Bildschirm, gibt Textur-UV, Farbe und Sicht-Normale weiter.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;
in ivec2 UV2;

out vec2 texCoord0;
out vec4 vertexColor;
out vec3 viewNormal;
out vec3 viewPos;
flat out vec2 params;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    viewPos = view.xyz;
    viewNormal = normalize(mat3(ModelViewMat) * Normal);
    texCoord0 = UV0;
    vertexColor = Color;
    params = vec2(UV2) / 240.0; // x = Rand-Glow, y = Skin-Details (vom Mod als "Licht" mitgegeben)
}
