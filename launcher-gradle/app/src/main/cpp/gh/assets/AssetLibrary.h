// GHEngine — asset library: mounts the shipped game-data .bpc archives from the
// launcher's data root and resolves mesh/texture pairs for the character system.
#pragma once
#include "BpcArchive.h"
#include "BtxTexture.h"
#include "ModMesh.h"
#include <string>
#include <vector>
#include <memory>
#include <mutex>

namespace gh {

struct SkinEntry {
    std::string meshEntry;    // e.g. "4elik.mod"
    std::string texEntry;     // e.g. "4elik.btx" (may be empty)
    std::string baseName;     // e.g. "4elik"
};

class AssetLibrary {
public:
    static AssetLibrary& get();

    void setDataRoot(const std::string& root);
    const std::string& dataRoot() const { return root_; }

    // Scan known archives; returns skin count.
    int scanCharacterAssets();

    const std::vector<SkinEntry>& skins() const { return skins_; }
    bool astcSupported() const { return astcSupported_; }
    void setAstcSupported(bool v) { astcSupported_ = v; }

    // Load the mesh for skin i (parses + decrypts on demand).
    bool loadSkinMesh(int skinIndex, ModMeshData& out);
    // Load the texture for skin i.
    bool loadSkinTexture(int skinIndex, GlTexture& out, std::string* err = nullptr);

    // Generic access for future subsystems (world, props).
    BpcArchive* openArchive(const std::string& file);

private:
    AssetLibrary() = default;
    std::string root_;
    std::vector<SkinEntry> skins_;
    bool astcSupported_ = true;
    std::mutex mtx_;
    std::vector<std::unique_ptr<BpcArchive>> meshArchives_;
    std::unique_ptr<BpcArchive> charTexArchive_;
};

} // namespace gh
