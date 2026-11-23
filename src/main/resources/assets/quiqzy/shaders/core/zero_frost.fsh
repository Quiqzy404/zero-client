#version 330

// Frost: schlichtes Milchglas. Nur weichgezeichnetes Spielbild + graue Tönung.
// Kein Rand, kein Glanz, keine Brechung. Shape: x = Radius, z = Gesamt-Deckkraft.

uniform sampler2D Sampler0;

in vec4 color1;
in vec4 color2;
in vec2 local;
flat in vec2 halfSize;
flat in vec4 shape;

out vec4 fragColor;

float roundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    float d = roundedBox(local, halfSize, shape.x);
    float aa = max(length(vec2(dFdx(d), dFdy(d))), 0.0001);
    float coverage = clamp(0.5 - d / aa, 0.0, 1.0);
    if (coverage <= 0.0) discard;
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));
    vec3 backdrop = texture(Sampler0, uv).rgb;
    vec3 color = mix(backdrop, color1.rgb, color1.a);
    fragColor = vec4(color, coverage * shape.z);
}
