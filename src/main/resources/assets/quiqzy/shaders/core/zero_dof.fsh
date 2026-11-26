#version 330

// Background Blur (Tiefenunschärfe): weit entfernte Pixel bekommen die weichgezeichnete Version,
// nahe bleiben scharf. Übergang weich zwischen Start- und End-Distanz.

layout(std140) uniform Projection {
    mat4 ProjMat;
};
layout(std140) uniform ZeroDof {
    vec4 DofParams; // x = Start, y = Ende (Blöcke), z = Stärke 0..1, w = Himmel mitblurren
};

uniform sampler2D Sampler0; // weichgezeichnetes Bild
uniform sampler2D Sampler1; // Tiefe (Kopie)

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float d = texture(Sampler1, texCoord).r;
    float ndc = d * 2.0 - 1.0;
    float dist = d >= 1.0 ? (DofParams.w > 0.5 ? 100000.0 : 0.0) : ProjMat[3][2] / (ndc + ProjMat[2][2]);
    dist = abs(dist);
    float f = smoothstep(DofParams.x, max(DofParams.y, DofParams.x + 0.01), dist) * DofParams.z;
    if (f <= 0.002) discard;
    fragColor = vec4(texture(Sampler0, texCoord).rgb, f);
}
