package com.blackhub.bronline.game.core;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

import com.grandhorizonrp.launcher.GHRPLog;

/**
 * Engine render surface. Forwards multi-touch to the engine exactly like the
 * original (up to 3 pointers packed into 8 ints, dispatched on the GL thread).
 */
public class JNIGLSurfaceView extends GLSurfaceView {
    @SuppressWarnings("unused")
    private static final String TAG = "JNIGLSurfaceView";

    private final Activity mActivity;

    public JNIGLSurfaceView(Activity activity) {
        super(activity);
        mActivity = activity;
        setEGLContextClientVersion(3);
        setEGLConfigChooser(new JNIConfigChooser());
        setRenderer(new JNIRenderer(this));
        setPreserveEGLContextOnPause(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        GHRPLog.d("JNIGLSurfaceView attached");
    }

    @Override
    public void onResume() {
        super.onResume();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                try {
                    JNILib.resumeEvent();
                } catch (Throwable t) {
                    GHRPLog.e("resumeEvent failed", t);
                }
            }
        });
    }

    @Override
    public void onPause() {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                try {
                    JNILib.pauseEvent();
                } catch (Throwable t) {
                    GHRPLog.e("pauseEvent failed", t);
                }
            }
        });
        super.onPause();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int pointerCount = Math.min(event.getPointerCount(), 3);

        final int x0, y0, x1, y1, x2, y2;
        if (pointerCount > 0) {
            x0 = (int) event.getX(0);
            y0 = (int) event.getY(0);
        } else {
            x0 = 0;
            y0 = 0;
        }
        if (pointerCount > 1) {
            x1 = (int) event.getX(1);
            y1 = (int) event.getY(1);
        } else {
            x1 = x0;
            y1 = y0;
        }
        if (pointerCount > 2) {
            x2 = (int) event.getX(2);
            y2 = (int) event.getY(2);
        } else {
            x2 = x1;
            y2 = y1;
        }

        final int actionIndex = event.getActionIndex();
        final int actionPointerId = event.getPointerId(Math.min(actionIndex, pointerCount - 1 < 0 ? 0 : pointerCount - 1));
        final int actionMasked = event.getActionMasked();

        queueEvent(new Runnable() {
            @Override
            public void run() {
                try {
                    JNILib.multiTouchEvent(x0, y0, x1, y1, x2, y2, actionPointerId, actionMasked);
                } catch (Throwable t) {
                    GHRPLog.e("multiTouchEvent failed", t);
                }
            }
        });
        return true;
    }

    @SuppressWarnings("unused")
    private Activity getActivity() {
        return mActivity;
    }
}
