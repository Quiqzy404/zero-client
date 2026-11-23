#version 330

// Glossy Floor – Vollbild-SSR: wirkt auf JEDE nach oben zeigende Fläche (Boden/Gras/Stufen).
// Normale aus der Tiefe rekonstruiert; nur fast waagrechte Flächen bekommen eine Spiegelung.
// Der Blickstrahl wird an der Boden-Ebene gespiegelt und im Sichtraum marschiert (Tiefentest gegen Szenenkopie).

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};
layout(std140) uniform ZeroGloss {
    vec4 GParams; // x = Intensität, y = Reflexionslänge (m)
};

uniform sampler2D Sampler0; // Szenenfarbe
uniform sampler2D Sampler1; // Szenentiefe

in vec2 texCoord;
out vec4 fragColor;

const int STEPS = 28;
const int REFINE = 4;

vec3 viewFromUV(vec2 uv, float d) {
    vec4 clip = vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec4 vp = inverse(ProjMat) * clip;
    return vp.xyz / vp.w;
}

vec3 project(vec3 p) {
    vec4 c = ProjMat * vec4(p, 1.0);
    return vec3(c.xy / c.w * 0.5 + 0.5, c.w);
}

float sceneZ(vec2 uv) {
    float d = texture(Sampler1, uv).r * 2.0 - 1.0;
    return -ProjMat[3][2] / (d + ProjMat[2][2]);
}

void main() {
    float d = texture(Sampler1, texCoord).r;
    if (d >= 1.0) discard; // Himmel

    vec3 P = viewFromUV(texCoord, d);
    vec3 v = normalize(P); // Kamera im Ursprung -> Blickstrahl = P
    // Flächennormale aus der Tiefe – ALLE Flächen (Boden UND Wände) werden gespiegelt.
    vec3 n = normalize(cross(dFdx(P), dFdy(P)));
    if (dot(n, -v) < 0.0) n = -n; // zur Kamera drehen

    vec3 refN = n; // Spiegelung an der echten Oberfläche -> alles glossy
    float ndv = dot(refN, -v);
    if (ndv <= 0.02) discard;

    vec3 r = normalize(reflect(v, refN));
    float maxLen = GParams.y;
    vec3 start = P + refN * 0.02;

    vec3 hitCol = vec3(0.0);
    float found = 0.0;
    float prevT = 0.0;
    vec2 lastUv = vec2(-1.0);
    for (int i = 1; i <= STEPS; i++) {
        float f = float(i) / float(STEPS);
        float t = maxLen * f * f;
        vec3 p = start + r * t;
        if (p.z > -0.05) break;
        vec3 s = project(p);
        if (s.x < 0.0 || s.x > 1.0 || s.y < 0.0 || s.y > 1.0) break;
        lastUv = s.xy;
        float sz = sceneZ(s.xy);
        float diff = sz - p.z;
        if (diff > 0.0 && diff < 0.6 + t * 0.12) {
            float a = prevT, b = t;
            for (int k = 0; k < REFINE; k++) {
                float m = (a + b) * 0.5;
                vec3 pm = start + r * m;
                vec3 sm = project(pm);
                if (sceneZ(sm.xy) - pm.z > 0.0) b = m; else a = m;
            }
            vec3 sh = project(start + r * b);
            hitCol = texture(Sampler0, sh.xy).rgb;
            vec2 e = min(sh.xy, 1.0 - sh.xy);
            found = clamp(min(e.x, e.y) * 10.0, 0.0, 1.0) * (1.0 - f * 0.6);
            break;
        }
        prevT = t;
    }
    if (found <= 0.001) {
        if (lastUv.x < 0.0) discard;
        hitCol = texture(Sampler0, lastUv).rgb;
        found = 0.45;
    }
    float fres = 0.30 + 0.70 * pow(1.0 - ndv, 3.0);
    fragColor = vec4(hitCol, GParams.x * fres * found);
}
