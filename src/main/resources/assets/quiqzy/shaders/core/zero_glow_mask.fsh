#version 330

// Hand-Maske aus dem Tiefenpuffer: 1 = Hand/Item, 0 = Rest.

uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float m = texture(DepthSampler, texCoord).r < 0.99999 ? 1.0 : 0.0;
    fragColor = vec4(m, m, m, 1.0);
}
