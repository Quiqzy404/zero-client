#version 330

// Glossy Floor: Weltvertex -> Sichtraum. ViewPos ist die Kamera-Sicht (Kamera im Ursprung),
// upView die Welt-Hochrichtung in Sichtraum (fuer die Spiegelung an der Boden-Ebene).

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
in vec2 Params;

out vec3 viewPos;
out vec3 upView;
out vec4 tint;
out vec2 params;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    viewPos = view.xyz;
    upView = (ModelViewMat * vec4(0.0, 1.0, 0.0, 0.0)).xyz;
    tint = Color;
    params = Params;
}
