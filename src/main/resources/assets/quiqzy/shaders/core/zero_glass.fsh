#version 330

// Liquid Glass (Apple-Stil): Refraktion an den Raendern mit chromatischer Aberration,
// helles, leicht entsaettigtes Spielbild, weicher Specular-Sheen oben und duennes Randlicht.
// Shape: x = Radius, y = Weichheit (0), z = Gesamt-Deckkraft (0..1), w = Verlaufswinkel (Radiant)

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

float noise(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    float d = roundedBox(local, halfSize, shape.x);
    float aa = max(length(vec2(dFdx(d), dFdy(d))), 0.0001);
    float coverage = clamp(0.5 - d / aa, 0.0, 1.0);
    if (coverage <= 0.0) discard;

    vec2 res = vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy / res;

    // ---- Randrefraktion: Normale an der Kante, Staerke faellt nach innen quadratisch ab
    vec2 grad = vec2(dFdx(d), dFdy(d));
    float gradLen = length(grad);
    vec2 normal = gradLen > 1e-6 ? grad / gradLen : vec2(0.0);
    float radius = max(shape.x, 1.0);
    float edge = 1.0 - clamp(-d / radius, 0.0, 1.0);
    edge *= edge;

    // chromatische Aberration: pro Kanal leicht andere Verschiebung
    vec2 off = normal * edge * (11.0 / res);
    vec3 backdrop;
    backdrop.r = texture(Sampler0, uv + off * 1.30).r;
    backdrop.g = texture(Sampler0, uv + off * 1.00).g;
    backdrop.b = texture(Sampler0, uv + off * 0.70).b;

    // milchig hell und leicht entsaettigt
    backdrop = mix(backdrop, vec3(luma(backdrop)), 0.30);
    backdrop = backdrop * 1.10 + 0.05;

    // ---- getoenter Verlauf wie gehabt
    vec2 dir = vec2(cos(shape.w), sin(shape.w));
    float extent = abs(dir.x) * halfSize.x + abs(dir.y) * halfSize.y;
    float t = clamp(dot(local, dir) / (2.0 * extent) + 0.5, 0.0, 1.0);
    vec4 tint = mix(color1, color2, t);

    vec3 color = mix(backdrop, tint.rgb, tint.a);

    // ---- Specular-Sheen: breiter weicher Lichtstreifen oben, nach links heller
    float topFrac = clamp(local.y / max(halfSize.y, 0.001) * 0.5 + 0.5, 0.0, 1.0);
    float sideBias = clamp(0.5 - local.x / max(halfSize.x, 0.001) * 0.5, 0.0, 1.0);
    float sheen = pow(smoothstep(0.45, 1.0, topFrac), 2.0) * (0.45 + 0.55 * sideBias);
    color += vec3(1.0) * sheen * 0.14;

    // ---- duennes helles Randlicht direkt an der Kante
    float rim = pow(1.0 - clamp(-d / (2.5 * aa), 0.0, 1.0), 1.5);
    color += vec3(1.0) * rim * 0.30;

    color += (noise(gl_FragCoord.xy) - 0.5) * 1.5 / 255.0;
    fragColor = vec4(color, coverage * shape.z);
}
