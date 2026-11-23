#version 330

// Snowy Sky: fallender Schnee als Overlay in 5 Tiefen-Schichten (nah = groß, unscharf, schnell).
// An die Blickrichtung gebunden -> dreht sich korrekt mit der Kamera.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};
layout(std140) uniform ZeroSky {
    vec4 SkyParams;  // x = Zeit, y = Preset, z = Helligkeit, w = Sterne
    vec4 ThemeColor; // a = Schnee-Menge
};

in vec2 texCoord;
out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    float t = SkyParams.x;
    float amount = ThemeColor.a;

    // Bildschirm-fix: Schnee haengt am Bildschirm, faellt gerade und wandert NICHT beim Umschauen.
    // Seitenverhaeltnis aus ProjMat, damit die Flocken rund bleiben.
    float aspect = ProjMat[1][1] / ProjMat[0][0];
    vec2 uv = texCoord;
    uv.x *= aspect;
    float alpha = 0.0;
    for (int L = 0; L < 5; L++) {
        float fl = float(L);
        float scale = 7.0 + fl * 8.0;
        float fall = 0.16 / (1.0 + fl * 0.35);
        vec2 q = vec2(uv.x * scale + sin(t * 0.5 + fl * 1.7) * 0.15, uv.y * scale - t * fall * scale);
        vec2 id = floor(q);
        float h = hash12(id + fl * 37.0);
        if (h > 0.30 + 0.45 * (1.0 - amount)) continue;
        vec2 off = vec2(hash12(id + 5.3), hash12(id + 9.1)) - 0.5;
        vec2 f = fract(q) - 0.5 - off * 0.7;
        float r = (0.07 + 0.06 * hash12(id + 2.2)) * (1.0 - fl * 0.12);
        float soft = 0.25 + (4.0 - fl) * 0.12;    // nahe Flocken weicher
        float flake = smoothstep(r, r * (1.0 - soft), length(f));
        alpha += flake * (0.85 - fl * 0.12);
    }
    alpha = clamp(alpha, 0.0, 0.9);
    if (alpha < 0.01) discard;
    fragColor = vec4(vec3(0.97, 0.98, 1.0), alpha);
}
