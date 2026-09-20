package com.grandhorizonrp.launcher.engine;

import android.view.Surface;

/**
 * GHEngine JNI surface — our own native engine (libghengine.so).
 * The original engine's bridge classes are not part of this build.
 */
public final class GHNative {
    static {
        System.loadLibrary("ghengine");
    }

    public interface Callback {
        /** Engine -> Java events: onAssetsScanned, onSceneReady, onLoadError. */
        void onEngineEvent(String method, String json);
    }

    public static native void nativeSetCallback(Callback cb);

    public static native boolean nativeInit(Surface surface, int w, int h);
    public static native void nativeResize(int w, int h);
    public static native void nativeStart();
    public static native void nativeStop();

    public static native void nativeSetLogPath(String path);
    public static native void nativeSetDataRoot(String path);

    /** Scene modes: 1 = character creation, 2 = world (see gh::SceneMode). */
    public static native void nativeLoadScene(int mode);

    public static native void nativeSetCharacter(int skinIndex);
    public static native int nativeCharacterCount();
    public static native String nativeCharacterName(int skinIndex);

    /** Touch: action 0=down 1=up 2=move 3=cancel (screen coords). */
    public static native void nativeTouch(int action, float x, float y);

    private GHNative() {}
}
