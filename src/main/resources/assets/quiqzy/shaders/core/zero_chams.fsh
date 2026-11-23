#version 330

// ZERO Chams (eigener Shader): färbt den Skin selbst ein statt einer Box.
// - Skin-Helligkeit bleibt als Detail erhalten (Gesicht, Kleidung erkennbar), Farbe = ESP-Farbe
// - leuchtender Rand dort, wo die Fläche von der Kamera wegzeigt (Fresnel)
// vertexColor: rgb = ESP-Farbe, a = Deckkraft. Params über ColorModulator: x = Rand-Glow, y = Skin-Details (0/1).

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 viewNormal;
in vec3 viewPos;
flat in vec2 params;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) discard;                       // Löcher im Skin (2. Ebene) bleiben Löcher
    float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    vec3 base = vertexColor.rgb;
    vec3 col = mix(base * 0.85, base * (0.30 + 1.05 * luma), params.y);
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    float rim = pow(1.0 - clamp(abs(dot(n, v)), 0.0, 1.0), 2.2) * params.x;
    col += mix(base, vec3(1.0), 0.45) * rim * 1.2;
    float alpha = clamp(vertexColor.a + rim * 0.35, 0.0, 1.0);
    fragColor = vec4(col, alpha);
}
