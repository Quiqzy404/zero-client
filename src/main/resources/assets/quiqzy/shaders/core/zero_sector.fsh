#version 330

// Ring-Segment aus Glas (Radial-GUI): weichgezeichneter Hintergrund mit Vibrancy, Tönung,
// weiches Licht nach außen, runde Ecken, Lücke zum Nachbarsegment. Kein Rand.
// color1 = Tönung (rgb + Stärke), color2 = Licht (rgb + Stärke)
// shape: x = Eckenradius, z = Deckkraft, w = halbe Lücke (Pixel)
// extra: x = Innenradius, y = Startwinkel (rad), z = Winkelbreite (rad), w = Außenradius

uniform sampler2D Sampler0;

in vec4 color1;
in vec4 color2;
in vec2 local;
flat in vec2 halfSize;
flat in vec4 shape;
flat in vec4 extra;

out vec4 fragColor;

const float PI = 3.14159265;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float innerR = extra.x, outerR = extra.w;
    float start = extra.y, sweep = extra.z;
    float c = shape.x, gap = shape.w;

    float r = length(local);
    float th = atan(local.y, local.x);
    float mid = start + sweep * 0.5;
    float rel = th - mid;
    rel = mod(rel + PI, 2.0 * PI) - PI;
    float h = sweep * 0.5;
    vec2 q = vec2(r * cos(rel), abs(r * sin(rel)));
    float da = dot(q, vec2(-sin(h), cos(h))) + gap;   // > 0 = außerhalb des Keils
    float dr = max(innerR - r, r - outerR);           // > 0 = außerhalb des Rings
    vec2 dd = vec2(dr + c, da + c);
    float d = length(max(dd, 0.0)) + min(max(dd.x, dd.y), 0.0) - c;

    float aa = max(length(vec2(dFdx(d), dFdy(d))), 0.0001);
    float coverage = clamp(0.5 - d / aa, 0.0, 1.0);
    if (coverage <= 0.0) discard;

    vec2 uv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));
    vec3 bg = texture(Sampler0, uv).rgb;
    float l = dot(bg, vec3(0.299, 0.587, 0.114));
    bg = clamp(mix(vec3(l), bg, 1.45), 0.0, 1.0);
    vec3 col = mix(bg, color1.rgb, color1.a);
    float t = clamp((r - innerR) / max(outerR - innerR, 1.0), 0.0, 1.0);
    col += color2.rgb * color2.a * t * t;
    col += (hash(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(col, coverage * shape.z);
}
