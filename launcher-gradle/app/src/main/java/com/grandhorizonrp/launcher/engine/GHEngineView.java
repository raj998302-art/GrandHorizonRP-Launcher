package com.grandhorizonrp.launcher.engine;

import android.content.Context;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.grandhorizonrp.launcher.GHRPLog;

/**
 * Hosts the GHEngine render surface. Touch events drive the engine camera
 * (orbit/zoom in character creation; look control in world scenes).
 */
public final class GHEngineView extends SurfaceView implements SurfaceHolder.Callback2 {
    private boolean mEngineStarted = false;

    public GHEngineView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(false);
        setClickable(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Surface s = holder.getSurface();
        GHNative.nativeSetLogPath(GHRPLog.logFilePath());
        boolean ok = GHNative.nativeInit(s, getWidth(), getHeight());
        GHRPLog.i("GHEngineView: nativeInit -> " + ok);
        mEngineStarted = ok;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        GHNative.nativeResize(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Stop rendering before the surface goes away.
        GHNative.nativeStop();
        mEngineStarted = false;
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        // The engine renders continuously; nothing extra to do.
    }

    public boolean isEngineStarted() {
        return mEngineStarted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: action = 0; break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: action = 1; break;
            case MotionEvent.ACTION_MOVE: action = 2; break;
            default: action = 3; break;
        }
        GHNative.nativeTouch(action, event.getX(), event.getY());
        return true;
    }
}
