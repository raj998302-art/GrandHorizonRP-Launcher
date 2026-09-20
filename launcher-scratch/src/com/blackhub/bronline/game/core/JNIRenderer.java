package com.blackhub.bronline.game.core;

import android.opengl.GLSurfaceView;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.grandhorizonrp.launcher.GHRPLog;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Engine frame renderer: initRender on surface creation, resize on change,
 * step() per frame — exact original contract.
 */
public final class JNIRenderer implements GLSurfaceView.Renderer {
    @SuppressWarnings("unused")
    private static final String TAG = "JNIRenderer";

    private final GLSurfaceView mView;
    private boolean mRenderInitialized = false;

    public JNIRenderer(GLSurfaceView view) {
        mView = view;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            WindowManager wm = (WindowManager) mView.getContext()
                    .getSystemService(android.content.Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            boolean ok = JNILib.initRender(metrics.widthPixels, metrics.heightPixels,
                    metrics.densityDpi, metrics.density, 1529);
            mRenderInitialized = ok;
            GHRPLog.i("initRender(" + metrics.widthPixels + "x" + metrics.heightPixels
                    + " dpi=" + metrics.densityDpi + ") -> " + ok);
        } catch (Throwable t) {
            GHRPLog.e("initRender failed", t);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        try {
            JNILib.resize(width, height);
        } catch (Throwable t) {
            GHRPLog.e("resize failed", t);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        try {
            JNILib.step();
        } catch (Throwable t) {
            // Keep the loop alive but log once per second to avoid log flooding.
            long now = android.os.SystemClock.uptimeMillis();
            if (now - sLastError > 1000) {
                sLastError = now;
                GHRPLog.e("step() failed", t);
            }
        }
    }

    private static long sLastError = 0;

    @SuppressWarnings("unused")
    private boolean isRenderInitialized() {
        return mRenderInitialized;
    }
}
