// GHEngine — our own GLSL sources (written for GHEngine; the original engine's
// shaders are referenced only as behavioral documentation).
#pragma once

namespace gh::shader {

// Skinned/static mesh: Lambert + hemisphere ambient + fog.
inline constexpr const char* MESH_VS = R"(#version 300 es
precision highp float;
in vec3 in_pos;
in vec3 in_normal;
in vec2 in_uv;
uniform mat4 u_viewProj;
uniform mat4 u_world;
out vec3 v_normal;
out vec2 v_uv;
out float v_fog;
uniform vec3 u_eye;
uniform vec2 u_fogData; // x: start, y: 1/(end-start)
void main() {
    vec4 world = u_world * vec4(in_pos, 1.0);
    v_normal = normalize(mat3(u_world) * in_normal);
    v_uv = in_uv;
    float d = length(world.xyz - u_eye);
    v_fog = clamp((d - u_fogData.x) * u_fogData.y, 0.0, 1.0);
    gl_Position = u_viewProj * world;
}
)";

inline constexpr const char* MESH_FS = R"(#version 300 es
precision mediump float;
in vec3 v_normal;
in vec2 v_uv;
in float v_fog;
uniform sampler2D u_tex;
uniform vec3 u_tint;
uniform vec3 u_fogColor;
uniform float u_hasTex;
out vec4 out_color;
void main() {
    vec3 n = normalize(v_normal);
    vec3 lightDir = normalize(vec3(0.35, 0.9, 0.25));
    float ndl = max(dot(n, lightDir), 0.0);
    float hemi = 0.5 + 0.5 * n.y;
    vec3 albedo = u_tint;
    if (u_hasTex > 0.5) albedo *= texture(u_tex, v_uv).rgb;
    vec3 lit = albedo * (0.25 * hemi + 0.85 * ndl);
    lit = mix(lit, u_fogColor, v_fog);
    out_color = vec4(lit, 1.0);
}
)";

// Sky gradient backdrop (fullscreen triangle).
inline constexpr const char* SKY_VS = R"(#version 300 es
precision highp float;
out vec2 v_ndc;
void main() {
    vec2 p = vec2((gl_VertexID == 1) ? 3.0 : -1.0, (gl_VertexID == 2) ? 3.0 : -1.0);
    v_ndc = p;
    gl_Position = vec4(p, 0.9999, 1.0);
}
)";

inline constexpr const char* SKY_FS = R"(#version 300 es
precision mediump float;
in vec2 v_ndc;
uniform vec3 u_top;
uniform vec3 u_bottom;
out vec4 out_color;
void main() {
    float t = clamp(v_ndc.y * 0.5 + 0.5, 0.0, 1.0);
    out_color = vec4(mix(u_bottom, u_top, t), 1.0);
}
)";

// Flat color pass (grid floor / bounds / markers).
inline constexpr const char* FLAT_VS = R"(#version 300 es
precision highp float;
in vec3 in_pos;
uniform mat4 u_viewProj;
uniform mat4 u_world;
void main() { gl_Position = u_viewProj * (u_world * vec4(in_pos, 1.0)); }
)";

inline constexpr const char* FLAT_FS = R"(#version 300 es
precision mediump float;
uniform vec3 u_color;
out vec4 out_color;
void main() { out_color = vec4(u_color, 1.0); }
)";

} // namespace gh::shader
