package com.blackhub.bronline.game.core;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.blackhub.bronline.game.GUIManager;
import com.blackhub.bronline.launcher.Settings;
import com.grandhorizonrp.launcher.GHRPLog;
import com.grandhorizonrp.launcher.UpdateController;
import com.grandhorizonrp.launcher.UpdateView;

import java.io.File;

/**
 * GHRP launcher main activity — from-scratch implementation of the original
 * engine host contract:
 *  - hosts JNIGLSurfaceView (engine render) + overlay layer (auth webview,
 *    update progress, dialogs, keyboard host)
 *  - mounts storage (STORAGE_ROOT AppLocalValue) and calls JNILib.init with
 *    the exact original argument set (1529, Site, Release)
 *  - starts the config-sync + update flow (UpdateController)
 *  - feeds connectivity changes to the engine (networkConnectionChanged)
 */
public class JNIActivity extends Activity {
    private static final String TAG = "JNIActivity";

    private static volatile JNIActivity sInstance;

    private JNIGLSurfaceView mGlView;
    private FrameLayout mOverlay;
    private UpdateController mUpdateController;
    private BroadcastReceiver mConnectivityReceiver;

    // ------------------------------------------------------------------
    // Engine-facing static accessors (used by JNIJSONTransport / GUIManager)
    // ------------------------------------------------------------------
    public static JNIActivity getContext() {
        return sInstance;
    }

    public static FrameLayout getOverlay() {
        JNIActivity a = sInstance;
        return a == null ? null : a.mOverlay;
    }

    // ------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;
        GHRPLog.i("=== JNIActivity.onCreate (GHRP from-scratch launcher) ===");

        keepScreenOn();
        hideSystemUi();

        try {
            mountStorage();
        } catch (Throwable t) {
            GHRPLog.e("mountStorage failed", t);
        }

        // ---- view hierarchy: GL surface + overlay --------------------
        FrameLayout root = new FrameLayout(this);
        mGlView = new JNIGLSurfaceView(this);
        root.addView(mGlView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mOverlay = new FrameLayout(this);
        root.addView(mOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        GUIManager.attachKeyboardHost(this, mOverlay);

        UpdateView updateView = new UpdateView(this, mOverlay);
        mUpdateController = new UpdateController(this, updateView);
        updateView.setController(mUpdateController);

        setContentView(root);

        registerConnectivity();

        // ---- engine init (exact original contract) --------------------
        try {
            String pathToRes = pathToRes();
            String pathToSaves = pathToSaves();
            GHRPLog.i("JNILib.init res=" + pathToRes + " saves=" + pathToSaves
                    + " version=1529 dist=Site(0) build=Release(5)");
            JNILib.init(pathToRes, pathToSaves, Settings.VERSION,
                    Settings.DISTRIBUTION_TYPE, Settings.BUILD_TYPE);
        } catch (Throwable t) {
            GHRPLog.e("JNILib.init FAILED", t);
            updateView.showErrorWithRetry("The game engine failed to initialize: "
                    + t.getMessage() + "\nPlease restart the launcher.", new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
            return;
        }

        try {
            JNILib.initFileLogger();
        } catch (Throwable t) {
            GHRPLog.e("initFileLogger failed", t);
        }

        // ---- config sync + update flow --------------------------------
        mUpdateController.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (mGlView != null) mGlView.onResume();
        notifyNetwork();
    }

    @Override
    protected void onPause() {
        if (mGlView != null) mGlView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            if (mConnectivityReceiver != null) {
                unregisterReceiver(mConnectivityReceiver);
            }
        } catch (Throwable ignored) {
        }
        if (mUpdateController != null) {
            mUpdateController.cancel();
        }
        if (sInstance == this) sInstance = null;
        GHRPLog.i("JNIActivity.onDestroy");
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // The engine handles navigation (XAML GUI). Ignore accidental backs
        // during gameplay; allow exit only from dialogs the engine opens.
        GHRPLog.d("back pressed — forwarded to engine, ignored on Java side");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    // ------------------------------------------------------------------
    // Storage mounting (original TAG_MOUNT_SYSTEM behaviour)
    // ------------------------------------------------------------------
    private void mountStorage() {
        File external = getExternalFilesDir(null);
        if (external == null) {
            external = getFilesDir();
            GHRPLog.w("getExternalFilesDir(null) is null — falling back to internal storage");
        }
        //noinspection ResultOfMethodCallIgnored
        external.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        getFilesDir().mkdirs();

        String storageRoot = external.getAbsolutePath() + "/";
        com.blackhub.bronline.game.core.AppLocalValues.put("STORAGE_ROOT", storageRoot);
        GHRPLog.i("[TAG_MOUNT_SYSTEM] STORAGE_ROOT = " + storageRoot
                + " pathToRes = " + external.getAbsolutePath()
                + " pathToSaves = " + getFilesDir().getPath());
    }

    private String pathToRes() {
        File external = getExternalFilesDir(null);
        return external != null ? external.getAbsolutePath() : getFilesDir().getAbsolutePath();
    }

    private String pathToSaves() {
        return getFilesDir().getPath();
    }

    // ------------------------------------------------------------------
    // Connectivity
    // ------------------------------------------------------------------
    private void registerConnectivity() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                ConnectivityManager cm = (ConnectivityManager)
                        getSystemService(Context.CONNECTIVITY_SERVICE);
                cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        notifyNetwork();
                    }

                    @Override
                    public void onLost(Network network) {
                        notifyNetwork();
                    }
                });
            } else {
                mConnectivityReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        notifyNetwork();
                    }
                };
                registerReceiver(mConnectivityReceiver,
                        new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            }
        } catch (Throwable t) {
            GHRPLog.e("connectivity registration failed", t);
        }
        notifyNetwork();
    }

    private void notifyNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean connected = false;
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= 23) {
                    Network n = cm.getActiveNetwork();
                    NetworkCapabilities caps = n != null ? cm.getNetworkCapabilities(n) : null;
                    connected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                } else {
                    //noinspection deprecation
                    android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                    connected = info != null && info.isConnected();
                }
            }
            JNILib.networkConnectionChanged(connected);
        } catch (Throwable t) {
            GHRPLog.e("networkConnectionChanged failed", t);
        }
    }

    // ------------------------------------------------------------------
    private void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
}
