#include "GHRenderer.h"
#include "GlFuncs.h"
#include "../core/GHLog.h"
#include <cstring>
#include <vector>

namespace gh {

bool Renderer::initEgl(ANativeWindow* window) {
    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY) { GHERR("EGL: no display"); return false; }
    if (!eglInitialize(display_, nullptr, nullptr)) { GHERR("EGL: init failed"); return false; }

    const EGLint cfgAttr[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24, EGL_STENCIL_SIZE, 8,
        EGL_NONE
    };
    EGLint num = 0;
    EGLConfig fallback = nullptr;
    if (!eglChooseConfig(display_, cfgAttr, &config_, 1, &num) || num < 1) {
        GHERR("EGL: no ES3 config, trying ES2 fallback");
        const EGLint cfg2[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 24,
            EGL_NONE
        };
        if (!eglChooseConfig(display_, cfg2, &fallback, 1, &num) || num < 1) {
            GHERR("EGL: no config at all");
            return false;
        }
        config_ = fallback;
    }

    const EGLint ctxAttr[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, ctxAttr);
    if (context_ == EGL_NO_CONTEXT) {
        GHERR("EGL: ES3 context failed, trying ES2");
        const EGLint ctx2[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, ctx2);
        if (context_ == EGL_NO_CONTEXT) { GHERR("EGL: no context"); return false; }
    }

    surface_ = eglCreateWindowSurface(display_, config_, window, nullptr);
    if (surface_ == EGL_NO_SURFACE) { GHERR("EGL: no surface"); return false; }
    if (!eglMakeCurrent(display_, surface_, surface_, context_)) {
        GHERR("EGL: makeCurrent failed");
        return false;
    }

    if (!loadGlFunctions()) { GHERR("GL: function loading failed"); return false; }
    const char* ver = (const char*)gh_glGetString(GL_VERSION);
    glVersion_ = ver ? ver : "?";
    astcSupported_.store(checkAstcSupport());
    GHLOG("EGL/GLES ready: %s (ASTC %s)", glVersion_.c_str(), astcSupported_.load() ? "yes" : "no");
    return true;
}

bool Renderer::checkAstcSupport() {
    const char* exts = (const char*)gh_glGetString(GL_EXTENSIONS);
    if (!exts) return false;
    bool has = strstr(exts, "GL_KHR_texture_compression_astc_ldr") != nullptr ||
               strstr(exts, "GL_OES_texture_compression_astc") != nullptr;
    GLint n = 0;
    gh_glGetIntegerv(0x8DFB /*GL_NUM_COMPRESSED_TEXTURE_FORMATS*/, &n);
    return has || n > 0;
}

bool Renderer::start(ANativeWindow* window) {
    if (running_.load()) return true;
    window_ = window;
    ANativeWindow_acquire(window);
    running_.store(true);
    thread_ = std::thread([this] { threadMain(); });
    return true;
}

void Renderer::stop() {
    if (!running_.exchange(false)) return;
    if (thread_.joinable()) thread_.join();
    std::lock_guard<std::mutex> lk(mtx_);
    if (display_ != EGL_NO_DISPLAY) {
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surface_ != EGL_NO_SURFACE) eglDestroySurface(display_, surface_);
        if (context_ != EGL_NO_CONTEXT) eglDestroyContext(display_, context_);
        eglTerminate(display_);
    }
    surface_ = EGL_NO_SURFACE;
    context_ = EGL_NO_CONTEXT;
    display_ = EGL_NO_DISPLAY;
    if (window_) { ANativeWindow_release(window_); window_ = nullptr; }
    GHLOG("Renderer stopped");
}

void Renderer::resize(int w, int h) {
    width_.store(w);
    height_.store(h);
}

void Renderer::threadMain() {
    {
        std::lock_guard<std::mutex> lk(mtx_);
        if (!initEgl(window_)) {
            GHERR("Renderer: initEgl failed — thread exiting");
            running_.store(false);
            return;
        }
    }
    GLint w = 1, h = 1;
    eglQuerySurface(display_, surface_, EGL_WIDTH, &w);
    eglQuerySurface(display_, surface_, EGL_HEIGHT, &h);
    width_.store(w);
    height_.store(h);

    // Persistent GL defaults.
    gh_glEnable(GL_DEPTH_TEST);
    gh_glDepthFunc(GL_LEQUAL);
    // NOTE: cull face stays DISABLED for the character preview — reconstructed
    // winding from the .mod edge buffers is not guaranteed consistent per-triangle,
    // and a wrong winding would hide the mesh on some drivers. World scenes will
    // enable culling once their meshes are authored/verified for it.
    gh_glDisable(GL_CULL_FACE);
    gh_glClearColor(0.05f, 0.07f, 0.11f, 1.0f);

    int frame = 0;
    while (running_.load()) {
        int cw = width_.load(), ch = height_.load();
        if (cw > 0 && ch > 0) {
            gh_glViewport(0, 0, cw, ch);
            gh_glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            if (frameCb_) frameCb_(cw, ch);
            eglSwapBuffers(display_, surface_);
        }
        frame++;
    }
}

} // namespace gh
