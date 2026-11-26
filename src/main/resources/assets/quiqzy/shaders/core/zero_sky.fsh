#version 330

// ZERO Custom Sky – komplett prozedural (keine Bilder, eigene Mathematik):
// 0 Planets, 1 Snowy, 2 Moon, 3 Aurora, 4 Sunset, 5 Nebula, 6 Galaxy, 7 Storm, 8 Theme
// Vollbild-Dreieck, gl_FragDepth = 1.0 + LEQUAL -> nur Himmels-Pixel werden bemalt.

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
    vec4 SkyParams;  // x = Zeit (s), y = Preset, z = Helligkeit, w = Sterne
    vec4 ThemeColor; // rgb
};

in vec2 texCoord;
out vec4 fragColor;

// ------------------------------------------------------------------ Noise

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float snoise3(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x),
                   mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
                   mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float snoise2(vec2 x) {
    vec2 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}

float fbm3(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * snoise3(p);
        p = p * 2.02 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return v;
}

float fbm2(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * snoise2(p);
        p = p * 2.03 + vec2(3.1, 7.7);
        a *= 0.5;
    }
    return v;
}

// ------------------------------------------------------------------ Bausteine

vec3 starLayer(vec3 d, float scale, float density, float t, float size) {
    vec3 c = d * scale;
    vec3 id = floor(c);
    float h = hash13(id);
    if (h < 1.0 - density) return vec3(0.0);
    vec3 off = vec3(hash13(id + 7.1), hash13(id + 3.3), hash13(id + 1.9)) - 0.5;
    vec3 f = fract(c) - 0.5 - off * 0.6;
    float dist = length(f);
    float core = smoothstep(size, 0.0, dist);
    float halo = smoothstep(size * 3.5, 0.0, dist) * 0.25;
    float tw = 0.7 + 0.3 * sin(t * (1.3 + h * 4.0) + h * 60.0);
    float temp = fract(h * 17.0);
    vec3 col = temp < 0.33 ? vec3(0.7, 0.8, 1.0) : temp < 0.66 ? vec3(1.0, 0.97, 0.92) : vec3(1.0, 0.82, 0.65);
    return col * (core + halo) * tw;
}

vec3 stars(vec3 d, float amt, float t) {
    if (amt <= 0.0) return vec3(0.0);
    vec3 s = starLayer(d, 90.0, 0.012 * amt, t, 0.10) * 1.8;
    s += starLayer(d, 180.0, 0.020 * amt, t, 0.13) * 1.0;
    s += starLayer(d, 330.0, 0.030 * amt, t, 0.16) * 0.6;
    return s;
}

// Strahl vom Ursprung o in Richtung d gegen Kugel (c, r). Rückgabe: Abstand oder -1.
float sphere(vec3 o, vec3 d, vec3 c, float r) {
    vec3 oc = o - c;
    float b = dot(oc, d);
    float h = b * b - dot(oc, oc) + r * r;
    if (h < 0.0) return -1.0;
    float t = -b - sqrt(h);
    return t > 0.0 ? t : -1.0;
}

// lokales Koordinatensystem eines Planeten (Achse a)
void frame(vec3 a, out vec3 b, out vec3 c) {
    b = normalize(cross(a, abs(a.y) < 0.9 ? vec3(0, 1, 0) : vec3(1, 0, 0)));
    c = cross(a, b);
}

// ------------------------------------------------------------------ Realistische Planeten

// Abstand des Sichtstrahls zur Kugelmitte (Kugel im Abstand 1) + weiche Kanten-Deckung (Anti-Aliasing)
float discCover(vec3 d, vec3 C, float R, out float m) {
    float b = dot(d, C);
    m = sqrt(max(0.0, 1.0 - b * b));
    if (b <= 0.0) return 0.0;
    float aa = max(fwidth(m), 1e-5) * 1.2;
    return clamp((R - m) / aa + 0.5, 0.0, 1.0);
}

// Oberflächen-Normale (am Rand geklemmt, damit AA-Pixel nicht leer sind)
vec3 discNormal(vec3 d, vec3 C, float R) {
    float b = dot(d, C);
    float h = max(b * b - 1.0 + R * R, 0.0);
    vec3 p = d * (b - sqrt(h));
    return normalize(p - C);
}

// Ringprofil wie bei einem echten Ringsystem (C-Ring schwach, B-Ring dicht, Cassini-Teilung, A-Ring, Encke-Lücke)
float ringDensity(float rr, float fw) {
    if (rr < 1.22 || rr > 2.30) return 0.0;
    float dens = 0.0;
    dens += smoothstep(1.22, 1.26, rr) * (1.0 - smoothstep(1.50, 1.53, rr)) * 0.22;   // C-Ring
    dens += smoothstep(1.51, 1.55, rr) * (1.0 - smoothstep(1.93, 1.95, rr)) * 0.92;   // B-Ring
    dens += smoothstep(1.95, 2.03, rr) * 0.0;                                           // Cassini
    dens += smoothstep(2.02, 2.04, rr) * (1.0 - smoothstep(2.26, 2.30, rr)) * 0.62;   // A-Ring
    dens *= 1.0 - (smoothstep(2.195, 2.205, rr) - smoothstep(2.215, 2.225, rr)) * 0.9; // Encke-Lücke
    // feine Ringlets – je nach Bildschirm-Auflösung ausgeblendet (kein Flimmern)
    float fine = 0.0, amp = 0.0;
    for (int i = 0; i < 4; i++) {
        float f = 40.0 * pow(2.6, float(i));
        float vis = clamp(1.0 - fw * f * 1.5, 0.0, 1.0);
        fine += (snoise2(vec2(rr * f, float(i) * 7.0)) - 0.5) * vis * (0.55 / (1.0 + float(i) * 0.6));
        amp += vis;
    }
    return clamp(dens * (1.0 + fine), 0.0, 1.0);
}

// Gasriese (Saturn/Jupiter-artig): dezente, verwirbelte Bänder, Randverdunkelung, weiche Tag/Nacht-Grenze.
// Gibt rgb + Deckung zurück, inkl. optionaler Ringe (vor/hinter dem Planeten korrekt sortiert).
vec4 gasGiant(vec3 d, vec3 C, float angR, vec3 axis, vec3 L, vec3 cA, vec3 cB, vec3 ringC, float ringAmt, float t) {
    float R = sin(angR);
    float m;
    float cover = discCover(d, C, R, m);
    vec3 B, Cx;
    frame(axis, B, Cx);

    vec3 planet = vec3(0.0);
    float tp = -1.0;
    if (cover > 0.0) {
        vec3 n = discNormal(d, C, R);
        float b = dot(d, C);
        tp = b - sqrt(max(b * b - 1.0 + R * R, 0.0));
        float lat = dot(n, axis);
        // Domain-Warp entlang der Breite -> Wirbel und Strömungen
        vec3 q = vec3(dot(n, B) * 1.6, lat * 9.0, dot(n, Cx) * 1.6);
        float w1 = fbm3(q * 1.3 + vec3(t * 0.003, 0.0, 0.0));
        float w2 = fbm3(q * 2.7 + vec3(0.0, w1 * 1.4, 0.0));
        float bands = fbm3(vec3(q.x * 0.35, lat * 26.0 + w2 * 2.2, q.z * 0.35));
        float streak = fbm3(vec3(q.x * 4.0, lat * 120.0 + w1 * 6.0, q.z * 4.0));
        vec3 surf = mix(cA, cB, smoothstep(0.30, 0.72, bands));
        surf *= 0.92 + 0.16 * streak;
        surf = mix(surf, surf * vec3(0.92, 0.95, 1.05), smoothstep(0.75, 0.95, abs(lat))); // Pole kühler
        float mu = max(dot(n, -d), 0.0);
        float ndl = dot(n, L);
        float diff = smoothstep(-0.12, 0.45, ndl) * (0.35 + 0.65 * max(ndl, 0.0));
        planet = surf * diff * pow(mu, 0.35);                 // Randverdunkelung
        planet += cB * 0.012;                                  // Restlicht Nachtseite
        // Ring-Schatten auf dem Planeten
        if (ringAmt > 0.0) {
            vec3 p = d * tp;
            float den = dot(L, axis);
            if (abs(den) > 1e-3) {
                float ts = dot(C - p, axis) / den;
                if (ts > 0.0) {
                    float rr2 = length(p + L * ts - C) / R;
                    planet *= 1.0 - ringDensity(rr2, 0.0) * 0.75 * ringAmt;
                }
            }
        }
    }

    // Ringe
    vec3 ringRgb = vec3(0.0);
    float ringA = 0.0, tr = -1.0;
    if (ringAmt > 0.0) {
        float den = dot(d, axis);
        if (abs(den) > 1e-4) {
            float tt = dot(C, axis) / den;
            if (tt > 0.0) {
                vec3 hp = d * tt;
                float rr = length(hp - C) / R;
                float fw = fwidth(rr);
                float dens = ringDensity(rr, fw);
                if (dens > 0.0) {
                    tr = tt;
                    float sunSide = sign(dot(L, axis)) == sign(dot(-d, axis)) ? 1.0 : 0.35; // Rückseite nur durchscheinend
                    float lit = (0.35 + 0.65 * abs(dot(L, axis))) * sunSide;
                    float shadow = sphere(hp, L, C, R) > 0.0 ? 0.12 : 1.0;
                    vec3 rc = ringC * (0.85 + 0.3 * snoise2(vec2(rr * 25.0, 3.0)));
                    ringRgb = rc * lit * shadow;
                    ringA = dens * ringAmt;
                }
            }
        }
    }

    // vormultipliziertes Alpha
    vec4 o;
    vec4 P = vec4(planet * cover, cover);
    vec4 Rg = vec4(ringRgb * ringA, ringA);
    if (cover > 0.0 && (tr < 0.0 || tp < tr)) {
        o = P + Rg * (1.0 - P.a);                     // Planet vorne
    } else {
        o = Rg + P * (1.0 - Rg.a);                    // Ring vorne
    }
    // ganz feiner Atmosphären-Saum an der beleuchteten Kante
    float b2 = dot(d, C);
    if (b2 > 0.0 && m > R * 0.97) {
        float edge = exp(-abs(m - R) / (R * 0.015));
        float litEdge = max(dot(normalize(d - C * b2), L), 0.0);
        o.rgb += cB * edge * litEdge * 0.25;
    }
    return o;
}

// Erdähnlicher Planet: Ozean, Kontinente, Eis, Wolken, Sonnenreflex, blaue Atmosphäre, Stadtlichter.
vec4 earthPlanet(vec3 d, vec3 C, float angR, vec3 axis, vec3 L, float t) {
    float R = sin(angR);
    float m;
    float cover = discCover(d, C, R, m);
    vec4 o = vec4(0.0);
    float b = dot(d, C);
    // Atmosphäre außerhalb der Scheibe (dünn, blau, nur Tagseite)
    if (b > 0.0 && m > R * 0.9) {
        vec3 side = normalize(d - C * b);
        float litS = smoothstep(-0.25, 0.5, dot(side, L));
        float halo = exp(-max(m - R, 0.0) / (R * 0.035));
        o.rgb += vec3(0.30, 0.55, 1.0) * halo * litS * 0.75 * step(R, m);
    }
    if (cover > 0.0) {
        vec3 n = discNormal(d, C, R);
        vec3 B, Cx;
        frame(axis, B, Cx);
        float lat = dot(n, axis);
        // Rotation um die Achse
        float ang = t * 0.004;
        vec3 nr = axis * lat + (B * cos(ang) + Cx * sin(ang)) * dot(n, B) + (Cx * cos(ang) - B * sin(ang)) * dot(n, Cx);
        float cont = fbm3(nr * 2.2 + 11.0) + 0.12 * fbm3(nr * 9.0);
        float land = smoothstep(0.52, 0.56, cont);
        float height = smoothstep(0.56, 0.78, cont);
        float dry = fbm3(nr * 3.5 + 4.0);
        vec3 ocean = mix(vec3(0.010, 0.040, 0.10), vec3(0.02, 0.10, 0.20), smoothstep(0.40, 0.52, cont));
        vec3 green = vec3(0.07, 0.15, 0.05), desert = vec3(0.42, 0.33, 0.20), rock = vec3(0.30, 0.27, 0.24);
        vec3 ground = mix(green, desert, smoothstep(0.45, 0.65, dry) * (1.0 - smoothstep(0.6, 0.85, abs(lat))));
        ground = mix(ground, rock, height * 0.6);
        vec3 surf = mix(ocean, ground, land);
        float ice = smoothstep(0.78, 0.86, abs(lat) + 0.06 * fbm3(nr * 6.0));
        surf = mix(surf, vec3(0.85, 0.88, 0.92), ice);
        // Wolken
        float cl = fbm3(nr * 3.0 + vec3(t * 0.006, 0.0, t * 0.004));
        float clouds = smoothstep(0.50, 0.72, cl + 0.08 * fbm3(nr * 12.0));
        float ndl = dot(n, L);
        float day = smoothstep(-0.10, 0.25, ndl);
        float mu = max(dot(n, -d), 0.0);
        vec3 col = surf * max(ndl, 0.0) * 1.3;
        // Sonnenreflex auf dem Wasser
        vec3 h = normalize(L - d);
        col += vec3(1.0, 0.9, 0.75) * pow(max(dot(n, h), 0.0), 60.0) * (1.0 - land) * (1.0 - clouds) * 0.9 * day;
        col = mix(col, vec3(0.95) * max(ndl, 0.0) * 1.1 + vec3(0.02), clouds * 0.9);
        // Stadtlichter auf der Nachtseite
        float city = smoothstep(0.62, 0.9, snoise3(nr * 60.0)) * smoothstep(0.55, 0.75, fbm3(nr * 8.0)) * land * (1.0 - ice);
        col += vec3(1.0, 0.65, 0.3) * city * (1.0 - day) * (1.0 - clouds * 0.7) * 0.8;
        // Atmosphäre am Rand der Scheibe (Rayleigh-artig)
        float rim = pow(1.0 - mu, 3.0);
        col = mix(col, vec3(0.35, 0.6, 1.0) * day, rim * 0.65 * day);
        col += vec3(0.25, 0.45, 0.9) * pow(1.0 - mu, 1.5) * 0.10 * day;
        o.rgb = o.rgb * (1.0 - cover) + col * cover;
        o.a = cover;
    }
    return o;
}

// Komposit: Planet über Hintergrund legen
vec3 over(vec3 bg, vec4 p) {
    return bg * (1.0 - p.a) + p.rgb;
}

// Felsiger Mond mit Kratern
vec4 moon(vec3 d, vec3 dir, float angR, vec3 sunDir, vec3 tint) {
    float R = sin(angR);
    float m;
    float cover = discCover(d, dir, R, m);
    vec4 o = vec4(0.0);
    float b = dot(d, dir);
    if (b > 0.0) o.rgb += tint * exp(-max(0.0, m - R) / (R * 0.6)) * 0.30 * step(R, m);
    if (cover > 0.0) {
        vec3 n = discNormal(d, dir, R);
        float base = 0.62 + 0.22 * fbm3(n * 5.0);
        float maria = smoothstep(0.52, 0.62, fbm3(n * 2.2 + 3.0));
        float crater = 0.0;
        for (int i = 0; i < 3; i++) {
            vec3 q = n * (6.0 + float(i) * 7.0);
            vec3 id = floor(q);
            vec3 f = fract(q) - 0.5;
            float h = hash13(id + float(i) * 13.0);
            if (h > 0.55) {
                float r = 0.18 + 0.2 * fract(h * 7.0);
                float dd = length(f);
                crater += (smoothstep(r, r * 0.6, dd) * -0.25 + smoothstep(r * 1.1, r, dd) * smoothstep(r * 0.9, r, dd) * 0.25) * (1.0 - float(i) * 0.25);
            }
        }
        float alb = base - maria * 0.22 + crater;
        float diff = smoothstep(-0.05, 0.35, dot(n, sunDir)) * (0.4 + 0.6 * max(dot(n, sunDir), 0.0));
        float mu = max(dot(n, -d), 0.0);
        vec3 surf = tint * alb * (0.03 + diff * 1.15) * (0.75 + 0.25 * mu);
        o.rgb = o.rgb * (1.0 - cover) + surf * cover;
        o.a = cover;
    }
    return o;
}

vec3 auroraCurtains(vec3 d, float t, vec3 lowC, vec3 highC) {
    if (d.y <= 0.0) return vec3(0.0);
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 26; i++) {
        float fi = float(i);
        float h = 0.9 + fi * 0.06;                     // Höhe der Schicht
        vec2 p = d.xz * (h / max(d.y, 0.06));
        p += vec2(t * 0.025, t * 0.012);
        float warp = fbm2(p * 0.18 + fi * 0.02) * 2.4;
        float curtain = abs(sin(p.x * 0.55 + warp + p.y * 0.25));
        curtain = pow(1.0 - curtain, 6.0);
        float rays = 0.6 + 0.4 * snoise2(vec2(p.x * 3.0 + warp * 4.0, fi * 0.3));
        float fade = 1.0 - fi / 26.0;
        vec3 c = mix(lowC, highC, fi / 26.0);
        acc += c * curtain * rays * fade * 0.075;
    }
    return acc * smoothstep(0.0, 0.18, d.y);
}

// ------------------------------------------------------------------ Szenen

vec3 spaceBase(vec3 d, float t, float starAmt, vec3 nebA, vec3 nebB, float nebAmt) {
    vec3 col = mix(vec3(0.010, 0.012, 0.030), vec3(0.025, 0.020, 0.055), smoothstep(-0.3, 0.8, d.y));
    float n1 = fbm3(d * 1.8 + vec3(3.0, 1.0, 0.0));
    float n2 = fbm3(d * 4.0 + vec3(0.0, t * 0.004, 0.0));
    col += mix(nebA, nebB, n2) * pow(smoothstep(0.42, 0.9, n1), 2.0) * nebAmt;
    col += stars(d, starAmt, t);
    return col;
}


// ------------------------------------------------------------------ Sky Shaders (9-14)

// Flüssiges Metall: doppelt verwirbeltes Rauschen, dunkler Grund, glänzende Schlieren an den Kämmen.
float fbmSoft(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * snoise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return v / 0.875;
}

vec3 liquidMetal(vec3 d, float t, float scale, vec3 dark, vec3 mid, vec3 hi) {
    // große, weich fließende Formen (wenig Oktaven) + starke Verwirbelung
    vec3 p = d * 0.95 * scale;
    vec3 q = vec3(fbmSoft(p + vec3(0.0, 0.0, t * 0.30)),
                  fbmSoft(p + vec3(5.2, 1.3, 2.8) - vec3(t * 0.24, 0.0, 0.0)),
                  fbmSoft(p + vec3(1.7, 9.2, 3.3) + vec3(0.0, t * 0.20, 0.0)));
    vec3 r = vec3(fbmSoft(p + 2.4 * q + vec3(1.7, 9.2, 0.0) + t * 0.12),
                  fbmSoft(p + 2.4 * q + vec3(8.3, 2.8, 4.1) - t * 0.10),
                  fbmSoft(p + 2.4 * q + vec3(3.1, 6.4, 7.7)));
    float f = fbmSoft(p + 2.2 * r);
    // Kämme -> glänzende Schlieren (breiter Glanz + heller Kern)
    float w = f * 6.5 + q.x * 2.4 + r.y * 1.5;
    float ridge = 1.0 - abs(sin(w * 3.14159));
    float core = pow(ridge, 10.0);
    float glow = pow(ridge, 3.0);
    float body = smoothstep(0.35, 0.75, f);
    vec3 col = mix(dark, mid * 0.55, body);
    col += mid * glow * 0.55;
    col += hi * core * (0.8 + 0.6 * body);
    return col;
}

// Glass Rain: Tropfen, die auf einer Glasscheibe vor dem Himmel herunterlaufen.
vec3 glassRain(vec3 d, float t, float scale) {
    float up = d.y;
    vec3 col = mix(vec3(0.70, 0.78, 0.88), vec3(0.36, 0.50, 0.70), smoothstep(-0.05, 0.75, up));
    vec2 uv = vec2(atan(d.z, d.x) * 3.0, up * 6.0) * scale;
    float drops = 0.0, light = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec2 g = uv * (1.0 + fi * 0.7) + vec2(fi * 7.3, 0.0);
        vec2 cell = floor(g);
        float h = hash12(cell + fi * 13.1);
        float speed = 0.15 + h * 0.35;
        vec2 f = fract(g + vec2(0.0, t * speed)) - 0.5;
        f.x += (hash12(cell + 3.7) - 0.5) * 0.6;
        float r = 0.06 + 0.10 * hash12(cell + 9.1);
        float dd = length(f * vec2(1.0, 0.8));
        float drop = smoothstep(r, r * 0.55, dd) * step(0.45, h);
        // Spur über dem Tropfen
        float trail = smoothstep(0.02, 0.0, abs(f.x)) * smoothstep(0.0, 0.45, f.y) * step(0.45, h) * 0.35;
        drops += drop + trail;
        light += drop * smoothstep(0.0, r, -f.y + r * 0.3);
    }
    col = mix(col, col * 0.82 + vec3(0.05, 0.07, 0.10), clamp(drops, 0.0, 1.0));
    col += vec3(1.0) * clamp(light, 0.0, 1.0) * 0.35;
    // weiche Wolken dahinter
    vec2 p = d.xz / (max(up, 0.0) + 0.15);
    col = mix(col, vec3(0.86, 0.89, 0.94), smoothstep(0.45, 0.85, fbm2(p * 0.6 + vec2(t * 0.01, 0.0))) * 0.5 * smoothstep(0.0, 0.2, up));
    return col;
}

vec3 nightSky(vec3 d, float t, bool rain) {
    float up = d.y;
    vec3 col = mix(vec3(0.06, 0.08, 0.18), vec3(0.008, 0.012, 0.04), smoothstep(-0.05, 0.7, up));
    if (!rain) {
        col += stars(d, 1.3, t) * smoothstep(-0.02, 0.25, up);
        vec3 mdir = normalize(vec3(-0.35, 0.45, 0.82));
        float mb = max(dot(d, mdir), 0.0);
        col += vec3(0.30, 0.40, 0.70) * pow(mb, 25.0) * 0.35 + vec3(0.9, 0.92, 1.0) * smoothstep(0.9993, 0.9996, mb);
        return col;
    }
    vec2 p = d.xz / (max(up, 0.0) + 0.15);
    float c = fbm2(p * 0.7 + vec2(t * 0.04, t * 0.02));
    col = mix(col, vec3(0.10, 0.11, 0.15), smoothstep(0.35, 0.8, c) * 0.9);
    float flash = step(0.985, fract(sin(floor(t * 0.9) * 91.7) * 4375.5)) * (1.0 - fract(t * 0.9));
    col += vec3(0.45, 0.5, 0.75) * flash * smoothstep(0.4, 0.75, c);
    return col;
}

void main() {
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 v = inverse(ProjMat) * vec4(ndc, 1.0, 1.0);
    vec3 viewDir = normalize(v.xyz / v.w);
    vec3 d = normalize(transpose(mat3(ModelViewMat)) * viewDir);

    float t = SkyParams.x;
    int preset = int(SkyParams.y + 0.5);
    float bright = SkyParams.z;
    float starAmt = SkyParams.w;
    float up = d.y;
    vec3 col;

    if (preset == 0) { // ---------------- Planets
        col = spaceBase(d, t, starAmt, vec3(0.22, 0.10, 0.35), vec3(0.08, 0.20, 0.40), 0.35);
        vec3 sun = normalize(vec3(-0.45, 0.30, -0.85));
        // ferner Ringplanet (Saturn-artig)
        col = over(col, gasGiant(d, normalize(vec3(-0.55, 0.42, 0.72)), 0.085, normalize(vec3(0.28, 0.94, -0.20)), sun,
                vec3(0.78, 0.66, 0.48), vec3(0.92, 0.85, 0.68), vec3(0.86, 0.79, 0.67), 1.0, t));
        // Mond
        col = over(col, moon(d, normalize(vec3(0.62, 0.55, 0.56)), 0.028, sun, vec3(0.88, 0.87, 0.85)));
        // großer erdähnlicher Planet
        col = over(col, earthPlanet(d, normalize(vec3(0.15, 0.20, 0.97)), 0.25, normalize(vec3(0.25, 0.96, 0.12)), normalize(vec3(-0.85, 0.25, -0.45)), t));
        col *= mix(1.0, 0.5, smoothstep(0.0, -0.35, up));
    } else if (preset == 1) { // ---------------- Snowy
        vec3 top = vec3(0.56, 0.64, 0.76), hor = vec3(0.85, 0.88, 0.92);
        col = mix(hor, top, smoothstep(-0.05, 0.6, up));
        vec3 sunDir = normalize(vec3(0.4, 0.22, 0.9));
        float sd = max(dot(d, sunDir), 0.0);
        vec2 p = d.xz / (max(up, 0.0) + 0.12);
        float c = fbm2(p * 0.55 + vec2(t * 0.01, t * 0.004));
        float c2 = fbm2(p * 1.4 - vec2(t * 0.006, 0.0));
        float cover = smoothstep(0.30, 0.75, c * 0.7 + c2 * 0.45);
        vec3 cloud = mix(vec3(0.72, 0.75, 0.80), vec3(0.95, 0.96, 0.98), c2);
        col = mix(col, cloud, cover * smoothstep(-0.02, 0.12, up));
        col += vec3(1.0, 0.96, 0.9) * (pow(sd, 6.0) * 0.25 + pow(sd, 90.0) * 0.6) * (1.0 - cover * 0.7);
        // ferne Schneeflocken im Himmel
        col = mix(col, vec3(1.0), clamp(starLayer(d + vec3(0.0, t * 0.02, 0.0), 120.0, 0.05, t, 0.2).r * 0.5, 0.0, 0.6));
    } else if (preset == 2) { // ---------------- Moon
        col = mix(vec3(0.07, 0.10, 0.22), vec3(0.01, 0.015, 0.05), smoothstep(-0.05, 0.75, up));
        col += stars(d, starAmt, t) * smoothstep(-0.02, 0.25, up);
        vec3 mdir = normalize(vec3(0.25, 0.42, 0.87));
        vec3 sun = normalize(-mdir + vec3(0.35, 0.15, 0.0)); // Licht von vorne -> fast Vollmond
        float mb = max(dot(d, mdir), 0.0);
        col += vec3(0.35, 0.45, 0.75) * pow(mb, 30.0) * 0.5 + vec3(0.6, 0.7, 0.95) * pow(mb, 300.0) * 0.4;
        col = over(col, moon(d, mdir, 0.15, sun, vec3(0.95, 0.94, 0.9)));
        // dünne Wolken vor dem Mond
        vec2 p = d.xz / (max(up, 0.0) + 0.15);
        float cl = smoothstep(0.5, 0.85, fbm2(p * 0.8 + vec2(t * 0.02, 0.0)));
        col = mix(col, vec3(0.16, 0.19, 0.30) + vec3(0.5, 0.55, 0.7) * pow(mb, 12.0) * 0.5, cl * 0.75 * smoothstep(0.0, 0.2, up));
    } else if (preset == 3 || preset == 8) { // ---------------- Aurora / Theme
        bool theme = preset == 8;
        col = mix(vec3(0.03, 0.07, 0.11), vec3(0.005, 0.01, 0.03), smoothstep(-0.05, 0.6, up));
        col += stars(d, starAmt, t) * smoothstep(0.0, 0.3, up);
        vec3 lowC = theme ? ThemeColor.rgb : vec3(0.15, 1.0, 0.55);
        vec3 highC = theme ? mix(ThemeColor.rgb, vec3(1.0), 0.3) : vec3(0.75, 0.30, 1.0);
        vec3 a = auroraCurtains(d, t, lowC, highC);
        col += a * 1.6;
        col += lowC * 0.05 * smoothstep(0.35, 0.0, abs(up - 0.08)); // Schein am Horizont
    } else if (preset == 4) { // ---------------- Sunset
        vec3 sunDir = normalize(vec3(0.9, 0.05, -0.2));
        float sd = max(dot(d, sunDir), 0.0);
        vec3 top = vec3(0.12, 0.08, 0.28), mid = vec3(0.62, 0.25, 0.48), hor = vec3(1.0, 0.55, 0.32);
        col = mix(hor, mid, smoothstep(0.0, 0.22, up));
        col = mix(col, top, smoothstep(0.18, 0.75, up));
        col += vec3(1.0, 0.55, 0.25) * pow(sd, 8.0) * 0.45;
        col += vec3(1.0, 0.9, 0.7) * smoothstep(0.9975, 0.999, sd) * 3.0;
        vec2 p = d.xz / (max(up, 0.0) + 0.1);
        float c = fbm2(p * vec2(0.25, 0.9) + vec2(t * 0.01, 0.0));
        float cover = smoothstep(0.5, 0.78, c) * smoothstep(0.0, 0.1, up) * smoothstep(0.7, 0.2, up);
        vec3 lit = mix(vec3(0.95, 0.42, 0.55), vec3(1.0, 0.75, 0.45), pow(sd, 3.0));
        col = mix(col, lit * (0.7 + 0.5 * pow(sd, 2.0)), cover * 0.85);
        col += stars(d, starAmt * 0.5, t) * smoothstep(0.35, 0.8, up);
    } else if (preset == 5) { // ---------------- Nebula
        col = spaceBase(d, t, starAmt, vec3(0.60, 0.12, 0.85), vec3(0.95, 0.38, 0.80), 1.3);
        float n = fbm3(d * 6.0 + 2.0);
        col += vec3(0.9, 0.75, 1.0) * pow(smoothstep(0.6, 0.9, n * fbm3(d * 2.0)), 2.0) * 1.5;
        col = over(col, gasGiant(d, normalize(vec3(-0.4, 0.3, 0.86)), 0.10, normalize(vec3(0.2, 1.0, 0.3)), normalize(vec3(0.75, 0.15, -0.65)),
                vec3(0.42, 0.60, 0.70), vec3(0.62, 0.80, 0.86), vec3(0.70, 0.76, 0.82), 0.35, t));
    } else if (preset == 6) { // ---------------- Galaxy
        col = vec3(0.008, 0.01, 0.02);
        vec3 axis = normalize(vec3(0.92, 0.25, 0.18));
        float lat = dot(d, axis);
        float band = exp(-pow(lat * 5.5, 2.0));
        float dust = fbm3(d * 5.0);
        vec3 bandCol = mix(vec3(0.45, 0.38, 0.65), vec3(1.0, 0.88, 0.78), dust);
        col += bandCol * band * (0.25 + 0.75 * dust) * 0.9;
        col *= 1.0 - band * smoothstep(0.52, 0.72, fbm3(d * 11.0)) * 0.75;
        vec3 core = normalize(vec3(0.0, 0.25, 1.0));
        core = normalize(core - axis * dot(core, axis));
        col += vec3(1.0, 0.85, 0.65) * pow(max(dot(d, core), 0.0), 40.0) * band * 1.5;
        col += stars(d, starAmt * 1.6, t) + starLayer(d, 500.0, 0.25 * band * starAmt, t, 0.2) * 0.8;
    } else if (preset >= 9) { // ---------------- Sky Shaders
        float sc = max(SkyParams.w, 0.2);
        if (preset == 9) col = liquidMetal(d, t, sc, vec3(0.035, 0.020, 0.006), vec3(0.55, 0.36, 0.10), vec3(1.0, 0.84, 0.48));
        else if (preset == 10) col = liquidMetal(d, t, sc, vec3(0.012, 0.014, 0.020), vec3(0.42, 0.45, 0.52), vec3(1.0, 1.0, 1.0));
        else if (preset == 11) col = liquidMetal(d, t, sc, vec3(0.045, 0.010, 0.022), vec3(0.50, 0.24, 0.24), vec3(1.0, 0.80, 0.70));
        else if (preset == 12) { // Glass: helles, bläulich-weißes flüssiges Glas über hellem Himmel
            vec3 sky = mix(vec3(0.72, 0.80, 0.90), vec3(0.42, 0.56, 0.76), smoothstep(-0.05, 0.75, d.y));
            vec3 glass = liquidMetal(d, t, sc, vec3(0.30, 0.40, 0.56), vec3(0.70, 0.80, 0.92), vec3(1.0, 1.0, 1.0));
            col = mix(sky, glass, 0.62);
        }
        else col = nightSky(d, t, preset == 14);
    } else { // ---------------- Storm
        col = mix(vec3(0.20, 0.22, 0.27), vec3(0.06, 0.07, 0.10), smoothstep(-0.1, 0.7, up));
        vec2 p = d.xz / (max(up, 0.0) + 0.15);
        float c = fbm2(p * 0.7 + vec2(t * 0.05, t * 0.02));
        float c2 = fbm2(p * 1.8 - vec2(t * 0.03, 0.0));
        col = mix(col, vec3(0.30, 0.32, 0.38), smoothstep(0.4, 0.8, c * 0.7 + c2 * 0.4) * 0.8);
        float flash = step(0.97, fract(sin(floor(t * 1.4) * 91.7) * 4375.5)) * (1.0 - fract(t * 1.4));
        col += vec3(0.65, 0.7, 0.95) * flash * smoothstep(0.45, 0.75, c) * 1.2;
    }

    fragColor = vec4(max(col, 0.0) * bright, 1.0);
    gl_FragDepth = 1.0;
}
