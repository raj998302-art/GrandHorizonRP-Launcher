package com.grandhorizonrp.launcher;

import android.os.Build;
import android.util.DisplayMetrics;

import org.json.JSONObject;

/**
 * Automatic production diagnostics: device-side engine evidence is posted to
 * the account backend (/api/v2/diagnostics) so real-device issues (renderer
 * capabilities, asset scan results, scene load failures) can be diagnosed
 * without adb. Fire-and-forget, never blocks the UI, never crashes the app,
 * and never reports anything user-identifying beyond the device model.
 */
public final class Diagnostics {
    private Diagnostics() {
    }

    private static final int MAX_BACKLOG = 8;
    private static final JSONObject[] sBacklog = new JSONObject[MAX_BACKLOG];
    private static int sCount = 0;
    private static volatile boolean sSending = false;

    /** Device descriptor attached to every report. */
    public static JSONObject deviceInfo() {
        try {
            JSONObject d = new JSONObject();
            d.put("model", Build.MODEL == null ? "?" : Build.MODEL);
            d.put("manufacturer", Build.MANUFACTURER == null ? "?" : Build.MANUFACTURER);
            d.put("android", Build.VERSION.RELEASE == null ? "?" : Build.VERSION.RELEASE);
            d.put("sdk", Build.VERSION.SDK_INT);
            d.put("abi", Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                    ? Build.SUPPORTED_ABIS[0] : "?");
            d.put("app", LauncherConfig.VERSION);
            return d;
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    /** Record an engine event for the next flush (kept even offline). */
    public static void record(String kind, JSONObject payload) {
        try {
            JSONObject o = new JSONObject();
            o.put("kind", kind);
            o.put("device", deviceInfo());
            o.put("app_version", LauncherConfig.VERSION);
            o.put("payload", payload != null ? payload : new JSONObject());
            synchronized (sBacklog) {
                if (sCount < MAX_BACKLOG) {
                    sBacklog[sCount++] = o;
                } else {
                    // keep the newest
                    System.arraycopy(sBacklog, 1, sBacklog, 0, MAX_BACKLOG - 1);
                    sBacklog[MAX_BACKLOG - 1] = o;
                }
            }
            flush();
        } catch (Throwable t) {
            GHRPLog.w("diagnostics record failed: " + t.getMessage());
        }
    }

    public static void record(String kind, String payloadJson) {
        try {
            record(kind, payloadJson == null || payloadJson.isEmpty()
                    ? new JSONObject() : new JSONObject(payloadJson));
        } catch (Throwable t) {
            record(kind, (JSONObject) null);
        }
    }

    /** Send everything we have (async, single-flight). */
    public static void flush() {
        JSONObject[] toSend;
        synchronized (sBacklog) {
            if (sCount == 0) return;
            toSend = new JSONObject[sCount];
            System.arraycopy(sBacklog, 0, toSend, 0, sCount);
        }
        if (sSending) return;
        sSending = true;
        final JSONObject[] batch = toSend;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String base = (LauncherConfig.sRegistrationService == null
                            || LauncherConfig.sRegistrationService.isEmpty())
                            ? "https://ghrp-auth.vercel.app" : LauncherConfig.sRegistrationService;
                    for (JSONObject o : batch) {
                        Http.JsonResp r = Http.postJson(base + "/api/v2/diagnostics",
                                o.toString(), null, 15000);
                        if (!r.isOk()) {
                            GHRPLog.w("diagnostics upload HTTP " + r.code);
                            return; // keep the backlog for a later flush
                        }
                    }
                    synchronized (sBacklog) {
                        sCount = Math.max(0, sCount - batch.length);
                    }
                } catch (Throwable t) {
                    GHRPLog.w("diagnostics flush failed: " + t.getMessage());
                } finally {
                    sSending = false;
                }
            }
        }, "ghrp-diag").start();
    }
}
