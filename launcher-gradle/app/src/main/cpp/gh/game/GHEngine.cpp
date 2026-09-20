#include "GHEngine.h"
#include "../render/GHShaderLib.h"
#include "../core/GHLog.h"
#include <cmath>
#include <algorithm>
#include <cstdio>

namespace gh {

Engine& Engine::get() {
    static Engine inst;
    return inst;
}

bool Engine::start(void* nativeWindow) {
    if (started_.exchange(true)) return true;
    if (!renderer_.start((ANativeWindow*)nativeWindow)) {
        started_.store(false);
        return false;
    }
    renderer_.setFrameCallback([this](int w, int h) { frame(w, h); });
    return true;
}

void Engine::stop() {
    if (!started_.exchange(false)) return;
    renderer_.stop();
    std::lock_guard<std::mutex> lk(sceneMtx_);
    characterMesh_.destroy();
    floorMesh_.destroy();
    Gfx::destroyProgram(meshProg_);
    Gfx::destroyProgram(skyProg_);
    Gfx::destroyProgram(flatProg_);
    progsReady_ = false;
    infoEmitted_ = false;
}

void Engine::resize(int w, int h) { renderer_.resize(w, h); }

void Engine::setDataRoot(const std::string& root) {
    dataRoot_ = root;
    AssetLibrary::get().setDataRoot(root);
    int n = AssetLibrary::get().scanCharacterAssets();
    GHLOG("Engine: data root set, %d skins", n);
    // Rich scan report (archive presence/sizes) for device-side evidence.
    if (javaCb_) javaCb_("onAssetsScanned", AssetLibrary::get().lastScanReport().c_str());
    skinDirty_ = true;
    // Surface may already be live — rescan feeds the live preview.
    if (renderer_.isRunning() && n > 0 && skinIndex_ >= n) {
        skinIndex_ = 0;
    }
}

void Engine::setScene(SceneMode mode) {
    mode_ = mode;
    GHLOG("Engine: scene -> %d", (int)mode);
    if (mode == SceneMode::CharacterCreation) skinDirty_ = true;
}

void Engine::setCharacter(int skinIndex) {
    skinIndex_ = skinIndex;
    skinDirty_ = true;
    GHLOG("Engine: character skin -> %d", skinIndex);
}

int Engine::characterSkinCount() const {
    return (int)AssetLibrary::get().skins().size();
}

const char* Engine::characterName(int skinIndex) {
    static std::string buf;
    auto& skins = AssetLibrary::get().skins();
    if (skinIndex < 0 || skinIndex >= (int)skins.size()) return "";
    buf = skins[skinIndex].baseName;
    return buf.c_str();
}

void Engine::ensurePrograms() {
    if (progsReady_) return;
    progsReady_ = Gfx::buildProgram(shader::MESH_VS, shader::MESH_FS, meshProg_) &&
                  Gfx::buildProgram(shader::SKY_VS, shader::SKY_FS, skyProg_) &&
                  Gfx::buildProgram(shader::FLAT_VS, shader::FLAT_FS, flatProg_);
    GHLOG("Engine: programs %s", progsReady_ ? "ready" : "FAILED");
    if (progsReady_ && !floorMesh_.indexCount) buildFloor();
}

void Engine::loadCurrentSkin() {
    std::lock_guard<std::mutex> lk(sceneMtx_);
    characterMesh_.destroy();
    currentMesh_ = ModMeshData{};

    // Robust load: if the requested skin fails to parse/upload, advance to the
    // next one (up to the catalogue size) so the preview never stays empty.
    int n = (int)AssetLibrary::get().skins().size();
    int tried = 0;
    ModMeshData m;
    bool loaded = false;
    while (tried < n && n > 0) {
        if (!AssetLibrary::get().loadSkinMesh(skinIndex_, m)) {
            GHERR("Engine: skin mesh %d ('%s') failed to load — trying next",
                  skinIndex_, AssetLibrary::get().skinName(skinIndex_));
        } else if (!Gfx::uploadMesh(m, characterMesh_)) {
            GHERR("Engine: GPU upload failed for skin %d — trying next", skinIndex_);
        } else {
            loaded = true;
            break;
        }
        skinIndex_ = (skinIndex_ + 1) % n;
        tried++;
    }

    if (!loaded) {
        GHERR("Engine: no loadable skin in the archive (%d entries)", n);
        if (javaCb_) javaCb_("onLoadError", "no loadable character mesh in the game data");
        skinDirty_ = false;
        return;
    }

    currentMesh_ = m;
    std::string texErr;
    bool texOk = AssetLibrary::get().loadSkinTexture(skinIndex_, characterMesh_.texture, &texErr);
    if (!texOk) GHLOG("Engine: skin texture unavailable (%s)", texErr.c_str());

    // Frame the camera on the mesh bounds.
    Vec3 size = m.boundsMax - m.boundsMin;
    target_ = (m.boundsMax + m.boundsMin) * 0.5f;
    camDist_ = std::max(1.2f, std::max(std::max(size.x, size.y), size.z) * 1.6f);
    GHLOG("Engine: character '%s' on GPU (tris=%zu, tex=%s)",
          AssetLibrary::get().skinName(skinIndex_), m.triangleCount(), texOk ? "ok" : "off");

    // Rich payload — the Java panel surfaces this as device-side evidence.
    char buf[256];
    std::snprintf(buf, sizeof(buf),
                  "{\"skin\":\"%s\",\"tris\":%zu,\"verts\":%zu,\"tex\":\"%s\"}",
                  AssetLibrary::get().skinName(skinIndex_),
                  m.triangleCount(), m.vertexCount(), texOk ? "ok" : "unavailable");
    if (javaCb_) javaCb_("onSceneReady", buf);
    skinDirty_ = false;
}

void Engine::buildFloor() {
    // Small grid disc under the character (flat program) — orientation aid.
    ModMeshData f;
    const int RINGS = 4, SEGS = 40;
    for (int r = 0; r <= RINGS; r++) {
        float rad = 0.4f + r * 0.35f;
        for (int s = 0; s < SEGS; s++) {
            float a = (float)s / SEGS * 2.f * (float)M_PI;
            f.positions.push_back(cosf(a) * rad);
            f.positions.push_back(0.f);
            f.positions.push_back(sinf(a) * rad);
        }
    }
    for (int r = 0; r < RINGS; r++) {
        for (int s = 0; s < SEGS; s++) {
            uint16_t a = (uint16_t)(r * SEGS + s);
            uint16_t b = (uint16_t)(r * SEGS + (s + 1) % SEGS);
            uint16_t c = (uint16_t)((r + 1) * SEGS + s);
            uint16_t d = (uint16_t)((r + 1) * SEGS + (s + 1) % SEGS);
            f.indices.insert(f.indices.end(), {a, c, d, a, d, b});
        }
    }
    f.valid = true;
    Gfx::uploadMesh(f, floorMesh_);
}

void Engine::touch(int action, float x, float y) {
    if (mode_ != SceneMode::CharacterCreation) return;
    if (action == 0) {              // down
        dragging_ = true;
        lastX_ = x; lastY_ = y;
    } else if (action == 1) {       // up
        dragging_ = false;
    } else if (action == 2) {       // move
        if (!dragging_) return;
        float dx = x - lastX_, dy = y - lastY_;
        lastX_ = x; lastY_ = y;
        camYaw_ -= dx * 0.008f;
        camPitch_ = std::min(1.35f, std::max(-1.35f, camPitch_ + dy * 0.006f));
    } else if (action == 3) {       // pinch handled Java-side -> zoom message
        dragging_ = false;
    }
}

void Engine::frame(int w, int h) {
    ensurePrograms();
    if (!progsReady_) return;

    // One-shot device diagnostics event after the first successful frame setup.
    if (!infoEmitted_) {
        infoEmitted_ = true;
        // The renderer's ASTC capability is only known AFTER EGL init (which may
        // happen after setDataRoot) — sync it now and reload the preview once so
        // textures are uploaded with the correct capability flag.
        if (AssetLibrary::get().astcSupported() != renderer_.astcSupported()) {
            AssetLibrary::get().setAstcSupported(renderer_.astcSupported());
            skinDirty_ = true;
        }
        char buf[320];
        std::snprintf(buf, sizeof(buf),
                      "{\"gl\":\"%.48s\",\"astc\":%s,\"skins\":%d,\"abi\":\"%s\"}",
                      renderer_.glVersion().c_str(),
                      renderer_.astcSupported() ? "true" : "false",
                      (int)AssetLibrary::get().skins().size(),
#if defined(__aarch64__) || defined(__x86_64__)
                      "64"
#else
                      "32"
#endif
        );
        if (javaCb_) javaCb_("onEngineInfo", buf);
        GHLOG("EngineInfo: %s", buf);
    }

    if (skinDirty_) loadCurrentSkin();

    // Sky gradient.
    gh_glDisable(GL_DEPTH_TEST);
    gh_glUseProgram(skyProg_.id);
    if (skyProg_.uTop >= 0) gh_glUniform3f(skyProg_.uTop, 0.10f, 0.13f, 0.20f);
    if (skyProg_.uBottom >= 0) gh_glUniform3f(skyProg_.uBottom, 0.02f, 0.03f, 0.05f);
    gh_glDrawArrays(GL_TRIANGLES, 0, 3);
    gh_glEnable(GL_DEPTH_TEST);

    // Camera.
    float aspect = (float)w / (float)(h ? h : 1);
    Vec3 eye(
        target_.x + camDist_ * cosf(camPitch_) * sinf(camYaw_),
        target_.y + camDist_ * sinf(camPitch_),
        target_.z + camDist_ * cosf(camPitch_) * cosf(camYaw_));
    Mat4 viewProj = Mat4::perspective(0.9f, aspect, 0.05f, 200.f) *
                    Mat4::lookAt(eye, target_, Vec3(0, 1, 0));

    std::lock_guard<std::mutex> lk(sceneMtx_);
    if (floorMesh_.indexCount) {
        gh_glUseProgram(flatProg_.id);
        if (flatProg_.uViewProj >= 0) gh_glUniformMatrix4fv(flatProg_.uViewProj, 1, GL_FALSE, viewProj.m);
        if (flatProg_.uWorld >= 0) gh_glUniformMatrix4fv(flatProg_.uWorld, 1, GL_FALSE, Mat4().m);
        if (flatProg_.uColor >= 0) gh_glUniform3f(flatProg_.uColor, 0.16f, 0.19f, 0.24f);
        gh_glBindVertexArray(floorMesh_.vao);
        gh_glDrawElements(GL_TRIANGLES, (GLsizei)floorMesh_.indexCount, GL_UNSIGNED_SHORT, nullptr);
        gh_glBindVertexArray(0);
    }
    if (characterMesh_.indexCount) {
        Mat4 world = Mat4();  // identity: model space already in world units
        Gfx::drawMesh(characterMesh_, meshProg_, viewProj, world, Vec3(1, 1, 1), eye);
    }
}

} // namespace gh
