package com.blackhub.bronline.game.core;

import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * EGL config chooser for GLES3 (5/6/5/0 RGB + 16 depth, 0 stencil —
 * matches the engine's requirements; falls back to any compatible config).
 */
public class JNIConfigChooser implements GLSurfaceView.EGLConfigChooser {
    private static final String TAG = "JNIConfigChooser";

    @Override
    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
        int[] attribs = new int[]{
                EGL10.EGL_RED_SIZE, 5,
                EGL10.EGL_GREEN_SIZE, 6,
                EGL10.EGL_BLUE_SIZE, 5,
                EGL10.EGL_ALPHA_SIZE, 0,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_RENDERABLE_TYPE, 4 /* EGL_OPENGL_ES3_BIT_KHR on EGL14 */,
                EGL10.EGL_NONE
        };
        int[] numConfigs = new int[1];
        if (!egl.eglChooseConfig(display, attribs, null, 0, numConfigs) || numConfigs[0] == 0) {
            Log.w(TAG, "preferred config unavailable — falling back to generic");
            attribs = new int[]{
                    EGL10.EGL_RED_SIZE, 5,
                    EGL10.EGL_GREEN_SIZE, 6,
                    EGL10.EGL_BLUE_SIZE, 5,
                    EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_NONE
            };
            if (!egl.eglChooseConfig(display, attribs, null, 0, numConfigs) || numConfigs[0] == 0) {
                Log.e(TAG, "no EGL config found — using system default");
                attribs = new int[]{EGL10.EGL_NONE};
                if (!egl.eglChooseConfig(display, attribs, null, 0, numConfigs)) {
                    throw new RuntimeException("eglChooseConfig failed");
                }
            }
        }
        EGLConfig[] configs = new EGLConfig[Math.max(1, numConfigs[0])];
        egl.eglChooseConfig(display, attribs, configs, configs.length, numConfigs);
        if (configs.length > 0 && configs[0] != null) {
            return configs[0];
        }
        throw new RuntimeException("no EGL config chosen");
    }
}
