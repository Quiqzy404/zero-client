#version 330

// Panel-Glas (Panel-GUI): weichgezeichneter Hintergrund mit mehr Farbe (Vibrancy),
// dunkle Tönung, weiches Licht oben, KEIN Rand. Dither gegen Farbstreifen.
// color1 = Tönung (rgb + Stärke in a), color2 = Lichtfarbe oben (rgb + Stärke in a).
// Shape: x = Radius, z = Gesamt-Deckkraft.

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

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float d = roundedBox(local, halfSize, shape.x);
    float aa = max(length(vec2(dFdx(d), dFdy(d))), 0.0001);
    float coverage = clamp(0.5 - d / aa, 0.0, 1.0);
    if (coverage <= 0.0) discard;

    vec2 uv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));
    vec3 bg = texture(Sampler0, uv).rgb;

    // Vibrancy: Sättigung hoch, damit das Glas die Farben dahinter schön aufnimmt
    float l = dot(bg, vec3(0.299, 0.587, 0.114));
    bg = clamp(mix(vec3(l), bg, 1.45), 0.0, 1.0);

    // Tönung
    vec3 col = mix(bg, color1.rgb, color1.a);

    // weiches Licht von oben (0 = unten, 1 = oben), ohne Kante
    float t = clamp(0.5 - local.y / (2.0 * halfSize.y), 0.0, 1.0);
    col += color2.rgb * color2.a * t * t;

    // Dither gegen Banding
    col += (hash(gl_FragCoord.xy) - 0.5) / 255.0;

    fragColor = vec4(col, coverage * shape.z);
}
