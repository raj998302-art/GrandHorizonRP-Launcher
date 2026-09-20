package com.blackhub.bronline.game.core;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.BatteryManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.grandhorizonrp.launcher.GHRPLog;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Engine -> Java callback hub. Every method below is part of the native
 * engine contract (exact names + signatures from the original launcher).
 * The engine resolves this class by name and invokes these statics from
 * native threads — all UI work is dispatched to the main thread.
 */
public final class JNIJSONTransport {
    private static final String NO_ERROR_MESSAGE = "No error message received";
    private static final String UNDEFINED_ERROR_MESSAGE = "Unknown error message";
    private static final String PATCH_INDEX_FILE = "patch_index.json";
    private static final String STATE_MONITOR_FILE = "state_monitor.sm";
    private static final String FAILURE_FLAG_FILE = "failure_flag";
    private static final int VIBRATION_DONT_REPEAT = -1;

    /** Latch used by the engine's synchronous dialog RPC. */
    private static volatile CountDownLatch sLatch;
    /** Active media players (video playback bridge — minimal support). */
    private static final Map<Long, MediaPlayer> sMediaPlayers = new HashMap<>();
    /** Battery level cached from sticky broadcast. */
    private static volatile float sBatteryPercent = 100f;
    /** Cached signal strengths. */
    private static volatile int sGsmDbm = -1;
    private static volatile int sWifiDbm = -1;

    private JNIJSONTransport() {
    }

    // ------------------------------------------------------------------
    // Preferences (engine-facing keys kept identical to original)
    // ------------------------------------------------------------------
    private static SharedPreferences prefs() {
        Context c = JNIActivity.getContext();
        return c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------
    // System info
    // ------------------------------------------------------------------
    public static float GetBatteryPercentage() {
        try {
            Context c = JNIActivity.getContext();
            Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra("level", -1);
                int scale = battery.getIntExtra("scale", -1);
                if (level >= 0 && scale > 0) {
                    sBatteryPercent = level * 100f / scale;
                }
            }
        } catch (Throwable t) {
            GHRPLog.e("GetBatteryPercentage failed", t);
        }
        return sBatteryPercent;
    }

    public static int GetFreeMemoryInMB() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    JNIActivity.getContext().getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            return (int) (info.availMem / (1024L * 1024L));
        } catch (Throwable t) {
            GHRPLog.e("GetFreeMemoryInMB failed", t);
            return 0;
        }
    }

    public static int GetGsmSignalStrengthDbm() {
        return sGsmDbm;
    }

    public static int GetWiFiSignalStrengthDbm() {
        try {
            Context c = JNIActivity.getContext();
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int rssi = wm.getConnectionInfo().getRssi();
                if (rssi != Integer.MIN_VALUE) sWifiDbm = rssi;
            }
        } catch (Throwable ignored) {
        }
        return sWifiDbm;
    }

    public static void updateGsmSignal(int dbm) {
        sGsmDbm = dbm;
    }

    public static long getFreeSpaceMemory(String path) {
        try {
            android.os.StatFs fs = new android.os.StatFs(path);
            return fs.getAvailableBytes();
        } catch (Throwable t) {
            GHRPLog.e("getFreeSpaceMemory failed for " + path, t);
            return 0;
        }
    }

    public static boolean checkFreeSpaceMemory(double requiredGb, long requiredBytes) {
        try {
            Context c = JNIActivity.getContext();
            long free = getFreeSpaceMemory(c.getExternalFilesDir(null) != null
                    ? c.getExternalFilesDir(null).getAbsolutePath() : c.getFilesDir().getAbsolutePath());
            boolean ok = free >= requiredBytes;
            if (!ok) {
                GHRPLog.w("checkFreeSpaceMemory: need " + requiredBytes + " have " + free);
            }
            return ok;
        } catch (Throwable t) {
            return true; // fail-open like the original
        }
    }

    public static byte[] getDeviceInfo() {
        try {
            JSONObject o = new JSONObject();
            o.put("model", Build.MODEL);
            o.put("manufacturer", Build.MANUFACTURER);
            o.put("brand", Build.BRAND);
            o.put("device", Build.DEVICE);
            o.put("product", Build.PRODUCT);
            o.put("board", Build.BOARD);
            o.put("hardware", Build.HARDWARE);
            o.put("android_version", Build.VERSION.RELEASE);
            o.put("sdk_int", Build.VERSION.SDK_INT);
            o.put("build_id", Build.ID);
            o.put("fingerprint", Build.FINGERPRINT);
            o.put("locale", Locale.getDefault().toString());
            o.put("timezone", java.util.TimeZone.getDefault().getID());
            o.put("cpu_abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown");
            o.put("total_ram", totalRam());
            return o.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            GHRPLog.e("getDeviceInfo failed", t);
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }

    private static long totalRam() {
        try {
            Activity activity = JNIActivity.getContext();
            android.app.ActivityManager am = (android.app.ActivityManager)
                    activity.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.totalMem;
        } catch (Throwable t) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Settings bridge (engine reads a serialized settings JSON)
    // ------------------------------------------------------------------
    public static byte[] getSerializedSettings() {
        try {
            return com.blackhub.bronline.launcher.Settings.buildSerializedSettings(prefs());
        } catch (Throwable t) {
            GHRPLog.e("getSerializedSettings failed", t);
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }

    public static void onSettingsJsonDataUpdate(byte[] json) {
        try {
            String s = new String(json, StandardCharsets.UTF_8);
            GHRPLog.nativecb("onSettingsJsonDataUpdate", truncate(s));
            com.blackhub.bronline.launcher.Settings.applySerializedSettings(prefs(), s);
        } catch (Throwable t) {
            GHRPLog.e("onSettingsJsonDataUpdate failed", t);
        }
    }

    public static int getCompatibleClientVersion() {
        return prefs().getInt("COMPATIBLE_CLIENT_VERSION", 0);
    }

    public static boolean setCompatibleClientVersion(int version) {
        try {
            prefs().edit().putInt("COMPATIBLE_CLIENT_VERSION", version).apply();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String getVersionResources() {
        return prefs().getString("RESOURCES_VERSION", "");
    }

    public static boolean setVersionResources(String version) {
        try {
            prefs().edit().putString("RESOURCES_VERSION", version).apply();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Message pump (engine -> Java JSON messages)
    // ------------------------------------------------------------------
    public static void onJsonDataIncoming(int id, byte[] json) {
        try {
            String s = json == null ? "{}" : new String(json, StandardCharsets.UTF_8);
            GHRPLog.nativecb("onJsonDataIncoming", "id=0x" + Integer.toHexString(id) + " " + truncate(s));
            com.blackhub.bronline.game.GUIManager.onEngineMessage(id, s);
        } catch (Throwable t) {
            GHRPLog.e("onJsonDataIncoming failed id=" + id, t);
        }
    }

    public static void sendJsonData(int id, byte[] json) {
        try {
            JNILib.sendJsonData(id, json);
        } catch (Throwable t) {
            GHRPLog.e("sendJsonData failed id=" + id, t);
        }
    }

    public static void onTabEvent(int[] a, byte[] b, int[] c, int[] d, int e) {
        GHRPLog.nativecb("onTabEvent", "e=" + e);
    }

    public static void onSpawn() {
        GHRPLog.nativecb("onSpawn", null);
        com.blackhub.bronline.game.GUIManager.onEngineSpawn();
    }

    public static void onSplashScreenDestroyed() {
        GHRPLog.nativecb("onSplashScreenDestroyed", null);
        com.blackhub.bronline.game.GUIManager.onSplashScreenDestroyed();
    }

    // ------------------------------------------------------------------
    // Async asset callbacks (engine decoded -> Java)
    // ------------------------------------------------------------------
    public static void onAsyncBitmapRequestDone(String name, Bitmap bitmap) {
        GHRPLog.nativecb("onAsyncBitmapRequestDone", name);
        com.blackhub.bronline.game.GUIManager.onAsyncBitmap(name, bitmap);
    }

    public static void onAsyncFileRequestDone(String name, String content) {
        GHRPLog.nativecb("onAsyncFileRequestDone", name);
        com.blackhub.bronline.game.GUIManager.onAsyncFile(name, content);
    }

    public static void OnRequestPlayersCompleted(int id, int[] ids, String[] names) {
        GHRPLog.nativecb("OnRequestPlayersCompleted", "id=" + id);
    }

    // ------------------------------------------------------------------
    // Dialogs (synchronous RPC — engine blocks in awaitDialogClose)
    // ------------------------------------------------------------------
    public static void showErrorDialog(final String message) {
        final Activity activity = JNIActivity.getContext();
        if (activity == null) return;
        final String msg = message == null ? UNDEFINED_ERROR_MESSAGE : message;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                com.blackhub.bronline.game.GUIManager.showErrorDialog(activity, msg);
            }
        });
    }

    public static void awaitDialogClose() {
        awaitDialogClose(true);
    }

    public static void awaitDialogClose(boolean interruptible) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            sLatch = latch;
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            GHRPLog.e("awaitDialogClose interrupted");
        }
    }

    public static void releaseDialogLatch() {
        CountDownLatch latch = sLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    public static void onDialogRPCIncoming(int id, int type, byte[] titleBytes, byte[] contentBytes,
                                           byte[] leftBtnBytes, byte[] rightBtnBytes) {
        try {
            final String title = utf(titleBytes);
            final String content = utf(contentBytes);
            final String left = utf(leftBtnBytes);
            final String right = utf(rightBtnBytes);
            GHRPLog.nativecb("onDialogRPCIncoming", "id=" + id + " type=" + type + " t=" + truncate(title));
            final Activity activity = JNIActivity.getContext();
            if (activity == null) {
                releaseDialogLatch();
                return;
            }
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    com.blackhub.bronline.game.GUIManager.showEngineDialog(activity, title, content, left, right);
                }
            });
        } catch (Throwable t) {
            GHRPLog.e("onDialogRPCIncoming failed", t);
            releaseDialogLatch();
        }
    }

    // ------------------------------------------------------------------
    // Window management
    // ------------------------------------------------------------------
    public static void closeAllWindows() {
        final Activity activity = JNIActivity.getContext();
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                com.blackhub.bronline.game.GUIManager.closeOverlays(false);
            }
        });
    }

    public static void closeAllWindowsExSAMP() {
        final Activity activity = JNIActivity.getContext();
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                com.blackhub.bronline.game.GUIManager.closeOverlays(true);
            }
        });
    }

    public static void quitGame() {
        GHRPLog.nativecb("quitGame", null);
        final Activity activity = JNIActivity.getContext();
        if (activity == null) {
            System.exit(0);
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.finish();
            }
        });
    }

    public static void hideTimeStamp() {
        GHRPLog.nativecb("hideTimeStamp", null);
    }

    // ------------------------------------------------------------------
    // Software keyboard
    // ------------------------------------------------------------------
    public static void openSoftwareKeyboard(byte[] hintText, int a, int b, int c, boolean d,
                                            boolean multiline, int f, int g, int h, int i) {
        final String hint = utf(hintText);
        GHRPLog.nativecb("openSoftwareKeyboard", truncate(hint));
        final Activity activity = JNIActivity.getContext();
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                com.blackhub.bronline.game.GUIManager.openSoftwareKeyboard(activity, hint);
            }
        });
    }

    public static void closeSoftwareKeyboard() {
        GHRPLog.nativecb("closeSoftwareKeyboard", null);
        final Activity activity = JNIActivity.getContext();
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                com.blackhub.bronline.game.GUIManager.closeSoftwareKeyboard(activity);
            }
        });
    }

    public static void keyboardOpened(boolean opened) {
        GHRPLog.nativecb("keyboardOpened", String.valueOf(opened));
    }

    public static boolean isSoftwareKeyboardOpen() {
        Activity activity = JNIActivity.getContext();
        if (activity == null) return false;
        View root = activity.getWindow().getDecorView();
        android.graphics.Rect r = new android.graphics.Rect();
        root.getWindowVisibleDisplayFrame(r);
        int screenHeight = root.getHeight();
        return screenHeight - r.bottom > screenHeight / 4;
    }

    public static int getSoftwareKeyboardHeight() {
        Activity activity = JNIActivity.getContext();
        if (activity == null) return 0;
        View root = activity.getWindow().getDecorView();
        android.graphics.Rect r = new android.graphics.Rect();
        root.getWindowVisibleDisplayFrame(r);
        int h = root.getHeight() - r.bottom;
        return Math.max(0, h);
    }

    public static void setSoftwareKeyboardSelection(int start, int end) {
        GHRPLog.nativecb("setSoftwareKeyboardSelection", start + "," + end);
    }

    public static void setSoftwareKeyboardText(byte[] text, int start, int end) {
        GHRPLog.nativecb("setSoftwareKeyboardText", truncate(utf(text)));
        com.blackhub.bronline.game.GUIManager.setKeyboardText(utf(text));
    }

    public static void relocateInputField(int x, int y, int w, int h) {
        GHRPLog.nativecb("relocateInputField", x + "," + y + "," + w + "," + h);
    }

    // ------------------------------------------------------------------
    // Clipboard
    // ------------------------------------------------------------------
    public static byte[] getClipboardString() {
        try {
            ClipboardManager cm = (ClipboardManager)
                    JNIActivity.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData data = cm.getPrimaryClip();
            if (data != null && data.getItemCount() > 0) {
                CharSequence text = data.getItemAt(0).coerceToText(JNIActivity.getContext());
                if (text != null) return text.toString().getBytes(StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            GHRPLog.e("getClipboardString failed", t);
        }
        return new byte[0];
    }

    public static void setClipboardString(String text) {
        try {
            ClipboardManager cm = (ClipboardManager)
                    JNIActivity.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("GHRP", text == null ? "" : text));
        } catch (Throwable t) {
            GHRPLog.e("setClipboardString failed", t);
        }
    }

    // ------------------------------------------------------------------
    // Permissions / biometrics
    // ------------------------------------------------------------------
    public static boolean doFingerPrintSupport() {
        try {
            android.hardware.fingerprint.FingerprintManager fm =
                    JNIActivity.getContext().getSystemService(android.hardware.fingerprint.FingerprintManager.class);
            return fm != null && fm.isHardwareDetected();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean doRecordAudioPermissionGranted() {
        try {
            return JNIActivity.getContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Vibration
    // ------------------------------------------------------------------
    public static void playVibration(int durationMs, float strength) {
        try {
            Vibrator v = (Vibrator) JNIActivity.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            int d = Math.max(1, durationMs);
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //noinspection deprecation
                v.vibrate(d);
            }
        } catch (Throwable t) {
            GHRPLog.e("playVibration failed", t);
        }
    }

    public static void playVibrationSequence(int[] durations, float[] strengths) {
        try {
            Vibrator v = (Vibrator) JNIActivity.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || durations == null || durations.length == 0) return;
            long[] pattern = new long[durations.length * 2];
            for (int i = 0; i < durations.length; i++) {
                pattern[i * 2] = 0;
                pattern[i * 2 + 1] = Math.max(1, durations[i]);
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, VIBRATION_DONT_REPEAT));
            } else {
                //noinspection deprecation
                v.vibrate(pattern, VIBRATION_DONT_REPEAT);
            }
        } catch (Throwable t) {
            GHRPLog.e("playVibrationSequence failed", t);
        }
    }

    // ------------------------------------------------------------------
    // Media (video) bridge — minimal; launcher videos were removed as dead weight
    // ------------------------------------------------------------------
    public static void initializePlatformMediaDecoder(long id, byte[] urlBytes, boolean looped) {
        try {
            String url = utf(urlBytes);
            GHRPLog.nativecb("initializePlatformMediaDecoder", id + " " + truncate(url));
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(url);
            mp.prepareAsync();
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer player) {
                    try {
                        JNILib.onMediaOpened(id, player.getVideoWidth(), player.getVideoHeight());
                    } catch (Throwable t) {
                        GHRPLog.e("onMediaOpened notify failed", t);
                    }
                }
            });
            sMediaPlayers.put(id, mp);
        } catch (Throwable t) {
            GHRPLog.e("initializePlatformMediaDecoder failed id=" + id, t);
            try {
                JNILib.onMediaOpened(id, 0, 0);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void destroyPlatformMediaDecoder(long id) {
        try {
            MediaPlayer mp = sMediaPlayers.remove(id);
            if (mp != null) {
                mp.stop();
                mp.release();
            }
        } catch (Throwable t) {
            GHRPLog.e("destroyPlatformMediaDecoder failed", t);
        }
    }

    public static void updateSurfaceTexture(long id) {
        // Video surface update — no launcher videos in GHRP build.
    }

    // ------------------------------------------------------------------
    // Persistent engine state (files in internal filesDir)
    // ------------------------------------------------------------------
    public static boolean isExistState() {
        return stateFile().exists();
    }

    public static byte[] loadStateBytes() {
        return readInternalFile(STATE_MONITOR_FILE);
    }

    public static boolean storeStateBytes(byte[] data) {
        return writeInternalFile(STATE_MONITOR_FILE, data);
    }

    public static boolean removeState() {
        return stateFile().delete();
    }

    public static boolean isExistFailureFlag() {
        return new File(filesDir(), FAILURE_FLAG_FILE).exists();
    }

    public static boolean storeFailureFlag() {
        try {
            return writeInternalFile(FAILURE_FLAG_FILE, new byte[]{1});
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean removeFailureFlag() {
        return new File(filesDir(), FAILURE_FLAG_FILE).delete();
    }

    // ------------------------------------------------------------------
    // Patch index cache (Java-controlled: enables pre-seeding from CDN)
    // ------------------------------------------------------------------
    public static byte[] loadPatchIndexBytes() {
        byte[] data = readInternalFile(PATCH_INDEX_FILE);
        if (data != null && data.length > 0) {
            GHRPLog.i("loadPatchIndexBytes: cached copy served (" + data.length + " bytes)");
        }
        return data;
    }

    public static boolean storePatchIndexBytes(byte[] data) {
        boolean ok = writeInternalFile(PATCH_INDEX_FILE, data);
        GHRPLog.i("storePatchIndexBytes: " + (data == null ? 0 : data.length) + " bytes -> " + ok);
        return ok;
    }

    public static boolean removePatchIndex() {
        boolean ok = new File(filesDir(), PATCH_INDEX_FILE).delete();
        GHRPLog.i("removePatchIndex: " + ok);
        return ok;
    }

    /** Java-side pre-seed of the patch index (used before tryGetPatchIndex). */
    public static boolean seedPatchIndex(byte[] data) {
        return writeInternalFile(PATCH_INDEX_FILE, data);
    }

    // ------------------------------------------------------------------
    // Analytics (no third-party SDKs in GHRP — log only)
    // ------------------------------------------------------------------
    public static void reportEvent(int provider, String event, String data) {
        GHRPLog.d("[analytics] " + event + " " + (data == null ? "" : truncate(data)));
    }

    public static void reportEventForAllProviders(String event, String data) {
        GHRPLog.d("[analytics-all] " + event + " " + (data == null ? "" : truncate(data)));
    }

    public static void sendErrorToFirebaseFirestore(String a, String b, String c) {
        GHRPLog.w("[error-report] " + truncate(a) + " " + truncate(b));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private static File filesDir() {
        Context c = JNIActivity.getContext();
        File f = c.getFilesDir();
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        return f;
    }

    private static File stateFile() {
        return new File(filesDir(), STATE_MONITOR_FILE);
    }

    private static byte[] readInternalFile(String name) {
        try {
            File f = new File(filesDir(), name);
            if (!f.exists()) return null;
            FileInputStream fis = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
            fis.close();
            return bos.toByteArray();
        } catch (Throwable t) {
            GHRPLog.e("readInternalFile " + name + " failed", t);
            return null;
        }
    }

    private static boolean writeInternalFile(String name, byte[] data) {
        try {
            FileOutputStream fos = new FileOutputStream(new File(filesDir(), name));
            fos.write(data == null ? new byte[0] : data);
            fos.flush();
            fos.getFD().sync();
            fos.close();
            return true;
        } catch (Throwable t) {
            GHRPLog.e("writeInternalFile " + name + " failed", t);
            return false;
        }
    }

    private static String utf(byte[] bytes) {
        if (bytes == null) return "";
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 180 ? s.substring(0, 180) + "..." : s;
    }
}
