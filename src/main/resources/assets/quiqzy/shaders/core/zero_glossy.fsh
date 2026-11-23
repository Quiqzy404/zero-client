#version 330

// Glossy Floor - echte Screen-Space-Reflexion mit Tiefentest:
// Der Blickstrahl wird an der Boden-Ebene gespiegelt und im Sichtraum vorwärts marschiert.
// Pro Schritt wird die Szenen-Tiefe (Sampler1) verglichen: liegt der Strahl hinter der Geometrie,
// ist das ein Treffer -> Binärsuche zur Verfeinerung -> Farbe aus der Szenen-Kopie (Sampler0).
// Verlässt der Strahl das Bild ohne Treffer, wird der Himmel/Rand an der Austrittsstelle genommen.

layout(std140) uniform Projection {
    mat4 ProjMat;
};

uniform sampler2D Sampler0; // Szenenfarbe
uniform sampler2D Sampler1; // Szenentiefe

in vec3 viewPos;
in vec3 upView;
in vec4 tint;
in vec2 params;

out vec4 fragColor;

const int STEPS = 40;
const int REFINE = 6;

vec3 project(vec3 p) {
    vec4 c = ProjMat * vec4(p, 1.0);
    return vec3(c.xy / c.w * 0.5 + 0.5, c.w);
}

float sceneZ(vec2 uv) {
    float d = texture(Sampler1, uv).r * 2.0 - 1.0;
    // Sichtraum-z (negativ) aus NDC-Tiefe
    return -ProjMat[3][2] / (d + ProjMat[2][2]);
}

void main() {
    vec3 n = normalize(upView);
    vec3 v = normalize(viewPos);
    float ndv = dot(n, -v);
    if (ndv <= 0.02) discard;

    vec3 r = normalize(reflect(v, n));
    float maxLen = params.y;
    vec3 start = viewPos + n * 0.02;

    vec3 hitCol = vec3(0.0);
    float found = 0.0;
    float prevT = 0.0;
    vec2 lastUv = vec2(-1.0);
    for (int i = 1; i <= STEPS; i++) {
        float f = float(i) / float(STEPS);
        float t = maxLen * f * f;
        vec3 p = start + r * t;
        if (p.z > -0.05) break;                   // hinter die Kamera
        vec3 s = project(p);
        if (s.x < 0.0 || s.x > 1.0 || s.y < 0.0 || s.y > 1.0) break;
        lastUv = s.xy;
        float sz = sceneZ(s.xy);
        float diff = sz - p.z;                    // > 0: Strahl liegt hinter der Oberfläche
        if (diff > 0.0 && diff < 0.6 + t * 0.12) {
            // verfeinern
            float a = prevT, b = t;
            for (int k = 0; k < REFINE; k++) {
                float m = (a + b) * 0.5;
                vec3 pm = start + r * m;
                vec3 sm = project(pm);
                if (sceneZ(sm.xy) - pm.z > 0.0) b = m; else a = m;
            }
            vec3 sh = project(start + r * b);
            hitCol = texture(Sampler0, sh.xy).rgb;
            // zum Bildrand hin ausblenden (dort fehlt Information)
            vec2 e = min(sh.xy, 1.0 - sh.xy);
            found = clamp(min(e.x, e.y) * 10.0, 0.0, 1.0) * (1.0 - f * 0.6);
            break;
        }
        prevT = t;
    }
    if (found <= 0.001) {
        // kein Treffer: Himmel/Rand an der Austrittsstelle, schwächer
        if (lastUv.x < 0.0) discard;
        hitCol = texture(Sampler0, lastUv).rgb;
        found = 0.45;
    }

    float fres = 0.30 + 0.70 * pow(1.0 - ndv, 3.0);
    fragColor = vec4(hitCol, params.x * fres * found * tint.a);
}
