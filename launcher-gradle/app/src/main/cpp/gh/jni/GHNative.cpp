// GHEngine — JNI surface (com.grandhorizonrp.launcher.engine.GHNative).
// Own bridge: clean, minimal, ours. No original engine classes involved.
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include "../game/GHEngine.h"
#include "../core/GHLog.h"
#include <string>
#include <cstring>

static JavaVM* g_vm = nullptr;
static jobject g_cbObj = nullptr;
static jmethodID g_cbMethod = nullptr;

static void jniCallback(const char* method, const char* json) {
    if (!g_vm || !g_cbObj || !g_cbMethod) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) attached = true;
        else return;
    }
    jstring m = env->NewStringUTF(method);
    jstring j = env->NewStringUTF(json ? json : "");
    env->CallVoidMethod(g_cbObj, g_cbMethod, m, j);
    env->DeleteLocalRef(m);
    env->DeleteLocalRef(j);
    if (attached) g_vm->DetachCurrentThread();
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeSetCallback(JNIEnv* env, jobject, jobject cb) {
    if (g_cbObj) { env->DeleteGlobalRef(g_cbObj); g_cbObj = nullptr; }
    if (cb) {
        g_cbObj = env->NewGlobalRef(cb);
        jclass cls = env->GetObjectClass(cb);
        g_cbMethod = env->GetMethodID(cls, "onEngineEvent", "(Ljava/lang/String;Ljava/lang/String;)V");
    }
    gh::Engine::get().setJavaCallback(jniCallback);
}

JNIEXPORT jboolean JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeInit(JNIEnv* env, jobject, jobject surface,
                                                            jint w, jint h) {
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);
    bool ok = gh::Engine::get().start(win);
    gh::Engine::get().resize(w, h);
    ANativeWindow_release(win); // Engine holds its own reference via Renderer
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeResize(JNIEnv*, jobject, jint w, jint h) {
    gh::Engine::get().resize(w, h);
}

JNIEXPORT void JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeStart(JNIEnv*, jobject) {
    // Engine loop runs via the renderer thread started in nativeInit.
}

JNIEXPORT void JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeStop(JNIEnv*, jobject) {
    gh::Engine::get().stop();
}

JNIEXPORT void JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeSetLogPath(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    gh::Log::setFile(p);
    GHLOG("GHEngine native log attached");
    env->ReleaseStringUTFChars(path, p);
}

JNIEXPORT void JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeSetDataRoot(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    gh::Engine::get().setDataRoot(std::string(p));
    env->ReleaseStringUTFChars(path, p);
}

JNIEXPORT void JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeLoadScene(JNIEnv*, jobject, jint mode) {
    gh::Engine::get().setScene((gh::SceneMode)mode);
}

JNIEXPORT void JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeSetCharacter(JNIEnv*, jobject, jint skin) {
    gh::Engine::get().setCharacter(skin);
}

JNIEXPORT jint JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeCharacterCount(JNIEnv*, jobject) {
    return gh::Engine::get().characterSkinCount();
}

JNIEXPORT jstring JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeCharacterName(JNIEnv* env, jobject, jint skin) {
    return env->NewStringUTF(gh::Engine::get().characterName(skin));
}

JNIEXPORT void JNICALL
Java_com_grandhorizonrp_launcher_engine_GHNative_nativeTouch(JNIEnv*, jobject, jint action,
                                                             jfloat x, jfloat y) {
    gh::Engine::get().touch(action, x, y);
}

} // extern "C"
