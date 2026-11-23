#version 330

// Hand Glow: weichgezeichnete Hand-Maske (GlowSampler, Dual-Kawase) wird außerhalb der Hand
// als Leuchtschein aufgetragen. Keine Ringe/Abtast-Muster mehr -> keine Streifen an dünnen Kanten.

uniform sampler2D DepthSampler;
uniform sampler2D GlowSampler;

layout(std140) uniform ZeroGlow {
    vec4 GlowColor;   // rgb, a = Deckkraft
    vec4 GlowParams;  // x = Verstärkung, y = Innenschimmer
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float center = texture(DepthSampler, texCoord).r < 0.99999 ? 1.0 : 0.0;
    float b = texture(GlowSampler, texCoord).r;
    float g = clamp(b * GlowParams.x, 0.0, 1.0);
    g = g * g * (3.0 - 2.0 * g);
    float outer = g * (1.0 - center);
    float inner = center * GlowParams.y;
    float alpha = clamp((outer + inner) * GlowColor.a, 0.0, 1.0);
    if (alpha <= 0.002) discard;
    vec3 col = mix(GlowColor.rgb, vec3(1.0), clamp(outer * outer * 0.3, 0.0, 0.3));
    fragColor = vec4(col, alpha);
}
