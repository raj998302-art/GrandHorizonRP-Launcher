// GHEngine — MOD mesh loader.
// Decrypts the container (ModCrypto) and structurally extracts the vertex/index
// arrays from the plaintext body (format contract: ENGINE_ARCHITECTURE.md §1.2).
// The extraction is validated against known reference geometry (cylinder test).
#pragma once
#include <vector>
#include <string>
#include <cstdint>
#include "../core/GHMath.h"

namespace gh {

struct ModMeshData {
    std::string name;
    std::vector<float> positions;   // xyz per vertex
    std::vector<float> normals;     // xyz per vertex (computed from faces if absent)
    std::vector<float> uvs;         // uv per vertex (optional)
    std::vector<uint16_t> indices;  // triangle list
    bool hasSkin = false;
    Vec3 boundsMin{0, 0, 0}, boundsMax{0, 0, 0};
    bool valid = false;

    size_t vertexCount() const { return positions.size() / 3; }
    size_t triangleCount() const { return indices.size() / 3; }
};

class ModMesh {
public:
    static bool parse(const uint8_t* data, size_t size, ModMeshData& out);

    // Build flat normals from the index list (fallback when no normal array exists).
    static void computeNormals(ModMeshData& m);
    // Fill boundsMin/boundsMax from the position array.
    static void computeBounds(ModMeshData& m);
};

} // namespace gh
