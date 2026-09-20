// GHEngine — renderer: EGL/GLES3 context lifecycle on an ANativeWindow.
#pragma once
#include <EGL/egl.h>
#include <android/native_window.h>
#include <thread>
#include <atomic>
#include <mutex>
#include <functional>
#include <string>

namespace gh {

class Renderer {
public:
    bool start(ANativeWindow* window);
    void stop();
    void resize(int w, int h);
    bool isRunning() const { return running_.load(); }

    // Per-frame scene callback, executed on the render thread with the GL context current.
    void setFrameCallback(std::function<void(int, int)> cb) { frameCb_ = std::move(cb); }

    bool checkAstcSupport();
    void swap() { eglSwapBuffers(display_, surface_); }

private:
    void threadMain();
    bool initEgl(ANativeWindow* window);

    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface surface_ = EGL_NO_SURFACE;
    EGLConfig config_ = nullptr;
    ANativeWindow* window_ = nullptr;
    std::thread thread_;
    std::atomic<bool> running_{false};
    std::atomic<int> width_{0}, height_{0};
    std::function<void(int, int)> frameCb_;
    std::mutex mtx_;
    std::string glVersion_;
};

} // namespace gh
