// GHEngine — top-level engine: scenes, camera, update/render, modes.
#pragma once
#include "../render/GHRenderer.h"
#include "../render/GLESGfx.h"
#include "../assets/AssetLibrary.h"
#include <memory>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>

namespace gh {

enum class SceneMode {
    None = 0,
    CharacterCreation = 1,   // 3D character preview with orbit/zoom
    World = 2,               // world scene (M2)
};

class Engine {
public:
    static Engine& get();

    // Lifecycle (called from JNI).
    bool start(void* nativeWindow);
    void stop();
    void resize(int w, int h);
    void setDataRoot(const std::string& root);
    void setScene(SceneMode mode);
    void setCharacter(int skinIndex);
    const char* characterName(int skinIndex);
    void touch(int action, float x, float y);
    int characterSkinCount() const;

    // Java-side callbacks (set from JNI).
    using JavaCallback = std::function<void(const char* method, const char* json)>;
    void setJavaCallback(JavaCallback cb) { javaCb_ = std::move(cb); }

private:
    Engine() = default;
    void ensurePrograms();
    void loadCurrentSkin();
    void buildFloor();
    void frame(int w, int h);

    Renderer renderer_;
    GlProgram meshProg_, skyProg_, flatProg_;
    bool progsReady_ = false;

    SceneMode mode_ = SceneMode::None;
    int skinIndex_ = 0;
    bool skinDirty_ = true;
    Gfx::GpuMesh characterMesh_;
    Gfx::GpuMesh floorMesh_;
    std::mutex sceneMtx_;   // guards meshes + camera during loads
    ModMeshData currentMesh_;

    // Orbit camera for character view.
    float camYaw_ = 0.5f, camPitch_ = 0.25f, camDist_ = 3.2f;
    float lastX_ = 0, lastY_ = 0;
    bool dragging_ = false;
    Vec3 target_{0, 0.9f, 0};

    std::atomic<bool> started_{false};
    JavaCallback javaCb_;
    std::string dataRoot_;
};

} // namespace gh
