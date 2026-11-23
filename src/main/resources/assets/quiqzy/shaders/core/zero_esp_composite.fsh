#version 330

// Shader-ESP: Eingabe = Entity-Outline-Puffer (Silhouette in ESP-Farbe, Alpha = Entity).
// Innen: Füllung. Außen: gleichmäßiger, harter Umriss (Breite w), danach optional weicher Glow.
// EspParams: x = Füll-Deckkraft, y = Umriss-Deckkraft, z = Umriss-Breite (px), w = Glow-Reichweite (px, 0 = aus)

uniform sampler2D MaskSampler;

layout(std140) uniform ZeroEsp {
    vec4 EspParams;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 px = 1.0 / vec2(textureSize(MaskSampler, 0));
    vec4 c = texture(MaskSampler, texCoord);
    if (c.a > 0.5) {
        if (EspParams.x <= 0.001) discard;
        fragColor = vec4(c.rgb, EspParams.x);
        return;
    }
    float w = EspParams.z;
    float glow = EspParams.w;

    // 1) harter Umriss: nächster Treffer im Kreis mit Radius w
    vec3 col = vec3(0.0);
    float best = 1e9;
    int steps = int(ceil(w));
    for (int y = -steps; y <= steps; y++) {
        for (int x = -steps; x <= steps; x++) {
            float dd = length(vec2(x, y));
            if (dd > w + 0.5 || dd >= best) continue;
            vec4 s = texture(MaskSampler, texCoord + vec2(x, y) * px);
            if (s.a > 0.5) {
                best = dd;
                col = s.rgb;
            }
        }
    }
    if (best < 1e8) {
        float edge = clamp(w + 0.5 - best, 0.0, 1.0);
        fragColor = vec4(col, EspParams.y * edge);
        return;
    }
    if (glow <= 0.0) discard;

    // schneller Ausschluss: weit weg von jeder Silhouette -> nichts tun
    bool near = false;
    for (int i = 0; i < 8 && !near; i++) {
        float a = float(i) * 0.785398;
        vec2 dir = vec2(cos(a), sin(a)) * px;
        near = texture(MaskSampler, texCoord + dir * (w + glow * 0.35)).a > 0.5
            || texture(MaskSampler, texCoord + dir * (w + glow * 0.75)).a > 0.5;
    }
    if (!near) discard;

    // 2) Glow: Ringe außerhalb des Umrisses, 16 Richtungen, Abfall nach außen
    float acc = 0.0, tot = 0.0;
    vec3 gcol = vec3(0.0);
    for (int r = 1; r <= 6; r++) {
        float rad = w + glow * float(r) / 6.0;
        float wt = 1.0 - float(r - 1) / 6.0;
        for (int i = 0; i < 16; i++) {
            float a = float(i) * 0.3926991 + float(r) * 0.2;
            vec4 s = texture(MaskSampler, texCoord + vec2(cos(a), sin(a)) * rad * px);
            if (s.a > 0.5) {
                acc += wt;
                gcol += s.rgb * wt;
            }
            tot += wt;
        }
    }
    if (acc <= 0.0) discard;
    gcol /= acc;
    float g = clamp(acc / tot * 2.2, 0.0, 1.0);
    fragColor = vec4(gcol, g * g * EspParams.y * 0.75);
}
