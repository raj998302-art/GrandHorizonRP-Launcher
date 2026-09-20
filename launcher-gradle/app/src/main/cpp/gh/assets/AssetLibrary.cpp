#include "AssetLibrary.h"
#include "../core/GHLog.h"
#include <cstdio>
#include <sys/stat.h>

namespace gh {

AssetLibrary& AssetLibrary::get() {
    static AssetLibrary inst;
    return inst;
}

void AssetLibrary::setDataRoot(const std::string& root) {
    std::lock_guard<std::mutex> lk(mtx_);
    if (root_ == root) return;
    root_ = root;
    skins_.clear();
    meshArchives_.clear();
    charTexArchive_.reset();
    GHLOG("AssetLibrary: data root = %s", root.c_str());
}

static bool fileExists(const std::string& p) {
    struct stat st;
    return ::stat(p.c_str(), &st) == 0 && st.st_size > 0;
}

int AssetLibrary::scanCharacterAssets() {
    std::lock_guard<std::mutex> lk(mtx_);
    skins_.clear();
    meshArchives_.clear();
    charTexArchive_.reset();
    lastScanReport_ = "{}";
    if (root_.empty()) {
        lastScanReport_ = "{\"error\":\"empty root\"}";
        return 0;
    }

    // Character meshes: files.mesh.br_skins_01.bpc (observed container name).
    auto meshArc = std::make_unique<BpcArchive>();
    std::string meshPath = root_ + "/mesh/br_skins_01.bpc";
    bool meshOpened = meshArc->open(meshPath);
    long meshSize = 0;
    { struct stat st; if (::stat(meshPath.c_str(), &st) == 0) meshSize = (long)st.st_size; }
    if (meshOpened) {
        for (auto& e : meshArc->entries()) {
            if (e.name.size() > 4 && e.name.compare(e.name.size() - 4, 4, ".mod") == 0 &&
                e.name.find('/') == std::string::npos) {
                SkinEntry s;
                s.meshEntry = e.name;
                s.baseName = e.name.substr(0, e.name.size() - 4);
                s.texEntry = s.baseName + ".btx";
                skins_.push_back(std::move(s));
            }
        }
        meshArchives_.push_back(std::move(meshArc));
    } else {
        GHLOG("AssetLibrary: mesh archive absent (%s)", meshPath.c_str());
    }

    // Character textures: files.textures.Characters.astc.bpc
    auto texArc = std::make_unique<BpcArchive>();
    std::string texPath = root_ + "/textures/Characters.astc.bpc";
    bool texOpened = texArc->open(texPath);
    long texSize = 0;
    { struct stat st; if (::stat(texPath.c_str(), &st) == 0) texSize = (long)st.st_size; }
    if (texOpened) {
        charTexArchive_ = std::move(texArc);
    } else {
        GHLOG("AssetLibrary: texture archive absent");
    }

    // Drop skins whose texture is missing only if the texture archive exists
    // (otherwise render untextured rather than losing the mesh).
    if (charTexArchive_) {
        std::vector<SkinEntry> filtered;
        for (auto& s : skins_) {
            if (charTexArchive_->find(s.texEntry)) filtered.push_back(s);
        }
        if (!filtered.empty()) skins_ = std::move(filtered);
    }

    GHLOG("AssetLibrary: %zu character skins", skins_.size());

    // Rich scan report surfaced through onAssetsScanned (device evidence).
    {
        char buf[320];
        std::snprintf(buf, sizeof(buf),
                      "{\"skins\":%zu,\"mesh\":{\"ok\":%s,\"bytes\":%ld,\"entries\":%zu},"
                      "\"tex\":{\"ok\":%s,\"bytes\":%ld},\"root\":\"%.120s\"}",
                      skins_.size(), meshOpened ? "true" : "false", meshSize,
                      meshArchives_.empty() ? 0 : meshArchives_.back()->entries().size(),
                      texOpened ? "true" : "false", texSize, root_.c_str());
        lastScanReport_ = buf;
    }
    return (int)skins_.size();
}

const std::string& AssetLibrary::lastScanReport() const {
    return lastScanReport_;
}

bool AssetLibrary::loadSkinMesh(int skinIndex, ModMeshData& out) {
    std::lock_guard<std::mutex> lk(mtx_);
    if (skinIndex < 0 || skinIndex >= (int)skins_.size()) return false;
    if (meshArchives_.empty()) return false;
    for (auto& arc : meshArchives_) {
        const BpcEntry* e = arc->find(skins_[skinIndex].meshEntry);
        if (!e) continue;
        std::vector<uint8_t> raw;
        if (!arc->read(*e, raw)) return false;
        return ModMesh::parse(raw.data(), raw.size(), out);
    }
    return false;
}

bool AssetLibrary::loadSkinTexture(int skinIndex, GlTexture& out, std::string* err) {
    std::lock_guard<std::mutex> lk(mtx_);
    if (skinIndex < 0 || skinIndex >= (int)skins_.size() || !charTexArchive_) {
        if (err) *err = "skin/texture unavailable";
        return false;
    }
    const BpcEntry* e = charTexArchive_->find(skins_[skinIndex].texEntry);
    if (!e) {
        // Some meshes share a generic texture; try a few observed fallbacks.
        static const char* fb[] = {"AO_GG_M.btx", "AK_Lck_M.btx", "IE_EvaK2.btx"};
        for (auto f : fb) {
            e = charTexArchive_->find(f);
            if (e) break;
        }
        if (!e) { if (err) *err = "texture entry missing"; return false; }
    }
    std::vector<uint8_t> raw;
    if (!charTexArchive_->read(*e, raw)) { if (err) *err = "read failed"; return false; }
    return BtxTexture::load(raw.data(), raw.size(), astcSupported_, out, err);
}

BpcArchive* AssetLibrary::openArchive(const std::string& file) {
    std::lock_guard<std::mutex> lk(mtx_);
    if (root_.empty()) return nullptr;
    auto arc = std::make_unique<BpcArchive>();
    std::string p = root_ + "/" + file;
    if (!arc->open(p)) return nullptr;
    meshArchives_.push_back(std::move(arc));
    return meshArchives_.back().get();
}

} // namespace gh
