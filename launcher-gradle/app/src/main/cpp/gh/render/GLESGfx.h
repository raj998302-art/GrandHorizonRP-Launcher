// GHEngine — GL pipeline helpers: program build, mesh GPU residency, draw calls.
#pragma once
#include "GlFuncs.h"
#include "../assets/ModMesh.h"
#include "../assets/BtxTexture.h"
#include "../core/GHMath.h"
#include <string>
#include <vector>
#include <cstdint>

namespace gh {

struct GlProgram {
    uint32_t id = 0;
    int uViewProj = -1, uWorld = -1, uTex = -1, uTint = -1, uEye = -1,
        uFogData = -1, uFogColor = -1, uHasTex = -1, uColor = -1, uTop = -1, uBottom = -1;
    bool valid = false;
};

class Gfx {
public:
    static bool buildProgram(const char* vs, const char* fs, GlProgram& out);
    static void destroyProgram(GlProgram& p);

    // Upload a mesh (pos/norm/uv + indices) to GPU buffers.
    struct GpuMesh {
        uint32_t vao = 0, vbo = 0, ibo = 0;
        size_t indexCount = 0;
        bool hasUv = false;
        GlTexture texture;
        void destroy();
    };
    static bool uploadMesh(const ModMeshData& m, GpuMesh& out);
    static void drawMesh(const GpuMesh& mesh, const GlProgram& prog,
                         const Mat4& viewProj, const Mat4& world,
                         const Vec3& tint, const Vec3& eye);
};

} // namespace gh
