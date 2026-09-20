#include "GLESGfx.h"
#include "GHShaderLib.h"
#include "../core/GHLog.h"
#include <cstring>

namespace gh {

void Gfx::GpuMesh::destroy() {
    if (gh_glDeleteVertexArrays && vao) gh_glDeleteVertexArrays(1, &vao);
    if (gh_glDeleteBuffers && vbo) gh_glDeleteBuffers(1, &vbo);
    if (gh_glDeleteBuffers && ibo) gh_glDeleteBuffers(1, &ibo);
    texture.destroy();
    vao = vbo = ibo = 0;
    indexCount = 0;
}

bool Gfx::buildProgram(const char* vsSrc, const char* fsSrc, GlProgram& out) {
    GLuint vs = gh_glCreateShader(GL_VERTEX_SHADER);
    gh_glShaderSource(vs, 1, &vsSrc, nullptr);
    gh_glCompileShader(vs);
    GLint ok = 0;
    gh_glGetShaderiv(vs, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        GLsizei n = 0;
        gh_glGetShaderInfoLog(vs, sizeof(log), &n, log);
        GHERR("Gfx: VS compile failed: %.*s", n, log);
        gh_glDeleteShader(vs);
        return false;
    }
    GLuint fs = gh_glCreateShader(GL_FRAGMENT_SHADER);
    gh_glShaderSource(fs, 1, &fsSrc, nullptr);
    gh_glCompileShader(fs);
    gh_glGetShaderiv(fs, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        GLsizei n = 0;
        gh_glGetShaderInfoLog(fs, sizeof(log), &n, log);
        GHERR("Gfx: FS compile failed: %.*s", n, log);
        gh_glDeleteShader(vs);
        gh_glDeleteShader(fs);
        return false;
    }
    GLuint prog = gh_glCreateProgram();
    gh_glAttachShader(prog, vs);
    gh_glAttachShader(prog, fs);
    gh_glLinkProgram(prog);
    gh_glDeleteShader(vs);
    gh_glDeleteShader(fs);
    gh_glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[1024];
        GLsizei n = 0;
        gh_glGetProgramInfoLog(prog, sizeof(log), &n, log);
        GHERR("Gfx: link failed: %.*s", n, log);
        gh_glDeleteProgram(prog);
        return false;
    }
    out.id = prog;
    out.uViewProj = gh_glGetUniformLocation(prog, "u_viewProj");
    out.uWorld = gh_glGetUniformLocation(prog, "u_world");
    out.uTex = gh_glGetUniformLocation(prog, "u_tex");
    out.uTint = gh_glGetUniformLocation(prog, "u_tint");
    out.uEye = gh_glGetUniformLocation(prog, "u_eye");
    out.uFogData = gh_glGetUniformLocation(prog, "u_fogData");
    out.uFogColor = gh_glGetUniformLocation(prog, "u_fogColor");
    out.uHasTex = gh_glGetUniformLocation(prog, "u_hasTex");
    out.uColor = gh_glGetUniformLocation(prog, "u_color");
    out.uTop = gh_glGetUniformLocation(prog, "u_top");
    out.uBottom = gh_glGetUniformLocation(prog, "u_bottom");
    out.valid = true;
    return true;
}

void Gfx::destroyProgram(GlProgram& p) {
    if (p.id) gh_glDeleteProgram(p.id);
    p.id = 0;
    p.valid = false;
}

bool Gfx::uploadMesh(const ModMeshData& m, GpuMesh& out) {
    out.destroy();
    if (!m.valid || m.positions.empty() || m.indices.empty()) return false;

    // Interleave: pos(3f) normal(3f) uv(2f) -> stride 32 bytes.
    size_t vc = m.vertexCount();
    std::vector<float> verts(vc * 8);
    for (size_t v = 0; v < vc; v++) {
        verts[v * 8 + 0] = m.positions[v * 3 + 0];
        verts[v * 8 + 1] = m.positions[v * 3 + 1];
        verts[v * 8 + 2] = m.positions[v * 3 + 2];
        verts[v * 8 + 3] = m.normals.size() == vc * 3 ? m.normals[v * 3 + 0] : 0.f;
        verts[v * 8 + 4] = m.normals.size() == vc * 3 ? m.normals[v * 3 + 1] : 1.f;
        verts[v * 8 + 5] = m.normals.size() == vc * 3 ? m.normals[v * 3 + 2] : 0.f;
        verts[v * 8 + 6] = m.uvs.size() == vc * 2 ? m.uvs[v * 2 + 0] : 0.5f;
        verts[v * 8 + 7] = m.uvs.size() == vc * 2 ? m.uvs[v * 2 + 1] : 0.5f;
    }

    gh_glGenVertexArrays(1, &out.vao);
    gh_glGenBuffers(1, &out.vbo);
    gh_glGenBuffers(1, &out.ibo);
    gh_glBindVertexArray(out.vao);

    gh_glBindBuffer(GL_ARRAY_BUFFER, out.vbo);
    gh_glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr)(verts.size() * 4), verts.data(), GL_STATIC_DRAW);
    gh_glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, out.ibo);
    gh_glBufferData(GL_ELEMENT_ARRAY_BUFFER, (GLsizeiptr)(m.indices.size() * 2), m.indices.data(), GL_STATIC_DRAW);

    gh_glEnableVertexAttribArray(0);
    gh_glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 32, (const void*)0);
    gh_glEnableVertexAttribArray(1);
    gh_glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 32, (const void*)12);
    gh_glEnableVertexAttribArray(2);
    gh_glVertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, 32, (const void*)24);

    gh_glBindVertexArray(0);
    out.indexCount = m.indices.size();
    out.hasUv = !m.uvs.empty();
    return true;
}

void Gfx::drawMesh(const GpuMesh& mesh, const GlProgram& prog,
                   const Mat4& viewProj, const Mat4& world,
                   const Vec3& tint, const Vec3& eye) {
    if (!mesh.vao || !prog.valid) return;
    gh_glUseProgram(prog.id);
    if (prog.uViewProj >= 0) gh_glUniformMatrix4fv(prog.uViewProj, 1, GL_FALSE, viewProj.m);
    if (prog.uWorld >= 0) gh_glUniformMatrix4fv(prog.uWorld, 1, GL_FALSE, world.m);
    if (prog.uTint >= 0) gh_glUniform3f(prog.uTint, tint.x, tint.y, tint.z);
    if (prog.uEye >= 0) gh_glUniform3f(prog.uEye, eye.x, eye.y, eye.z);
    if (prog.uFogData >= 0) gh_glUniform1f(prog.uFogData, 100000.0f); // far fog off
    if (prog.uHasTex >= 0) gh_glUniform1f(prog.uHasTex, mesh.texture.valid ? 1.f : 0.f);
    if (mesh.texture.valid) {
        gh_glActiveTexture(GL_TEXTURE0);
        gh_glBindTexture(GL_TEXTURE_2D, mesh.texture.id);
        if (prog.uTex >= 0) gh_glUniform1i(prog.uTex, 0);
    }
    gh_glBindVertexArray(mesh.vao);
    gh_glDrawElements(GL_TRIANGLES, (GLsizei)mesh.indexCount, GL_UNSIGNED_SHORT, nullptr);
    gh_glBindVertexArray(0);
}

} // namespace gh
