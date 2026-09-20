package com.grandhorizonrp.launcher;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.blackhub.bronline.game.core.JNIJSONTransport;
import com.blackhub.bronline.game.core.JNILib;
import com.blackhub.bronline.launcher.Settings;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Update orchestration — mirrors the original flow:
 *  1. config sync: url-config.json + app-config.json -> engine (onUrlConfigReceived/onAppConfigReceived)
 *  2. feature flag fetch (timeouts/recovery/etc.)
 *  3. patch index PRE-SEED: fetched through the Java HTTP stack (system CA store)
 *     into filesDir/patch_index.json so the native update manager can use the
 *     Java-provided cache (belt-and-braces against the CDN 2.0 reconnect loop)
 *  4. JNILib.tryGetPatchIndex(httpData, fileRules, ...) — native plan computation
 *  5. JNILib.tryDownloadResources(...) — native download+verify+extract
 *  6. progress polling via getAdditionDownloadPatchData()
 * On completion the engine continues on its own (files are on disk).
 */
public final class UpdateController {
    private static final int MAX_ERRORS = 4;

    private final Activity mActivity;
    private final UpdateView mView;
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private Thread mWorker;
    private volatile boolean mCancelled = false;
    private int mCountOfErrors = 0;

    public UpdateController(Activity activity, UpdateView view) {
        mActivity = activity;
        mView = view;
    }

    public void start() {
        if (mWorker != null && mWorker.isAlive()) return;
        mWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                runUpdateFlow();
            }
        }, "ghrp-update");
        mWorker.setDaemon(true);
        mWorker.start();
    }

    public void cancel() {
        mCancelled = true;
        try {
            JNILib.cancelDownloadResources();
        } catch (Throwable t) {
            GHRPLog.e("cancelDownloadResources failed", t);
        }
    }

    // ------------------------------------------------------------------
    private void runUpdateFlow() {
        postStatus("Loading configuration…");
        GHRPLog.i("=== GHRP update flow start ===");

        // ---- 1. config sync ------------------------------------------
        boolean urlConfigOk = Settings.fetchUrlConfig();
        if (!urlConfigOk) {
            retryOrFail("Could not load the update configuration (url-config.json). "
                    + "Check your internet connection and try again.");
            return;
        }
        deliverConfigToEngine("url-config.json", com.grandhorizonrp.launcher.Http.getSafe(
                Settings.URL_CONFIG_URL));
        deliverConfigToEngine("app-config.json", com.grandhorizonrp.launcher.Http.getSafe(
                Settings.APP_CONFIG_URL));

        // ---- 2. feature flag ------------------------------------------
        Settings.fetchFeatureFlag(); // defaults are sane on failure (15000/1200000)

        // ---- 3. patch index pre-seed (Java-side fetch) ----------------
        postStatus("Checking resources…");
        byte[] patchIndex = com.grandhorizonrp.launcher.Http.getSafe(Settings.PATCH_INDEX_URL);
        if (patchIndex != null && patchIndex.length > 0) {
            JNIJSONTransport.seedPatchIndex(patchIndex);
            GHRPLog.i("patch_index.json pre-seeded from jsDelivr (" + patchIndex.length + " bytes)");
        } else {
            GHRPLog.w("patch index pre-seed unavailable — relying on native fetch");
        }

        // ---- 4. native patch index phase ------------------------------
        while (mCountOfErrors < MAX_ERRORS && !mCancelled) {
            String result = null;
            try {
                String httpData = Settings.buildHttpData();
                GHRPLog.i("tryGetPatchIndex httpData=" + httpData + " fileRules=" + Settings.FILE_RULES);
                result = JNILib.tryGetPatchIndex(
                        httpData,
                        Settings.FILE_RULES,
                        Settings.sIsEnabledCheckResources,
                        Settings.VERSION,
                        Settings.sCandidateVersion,
                        Settings.sDownloadTimeout,
                        Settings.sConnectionTimeout,
                        Settings.DISTRIBUTION_TYPE,
                        false, /* useBackupCdn */
                        false, /* isDevModUpdateManager */
                        Settings.sForceCheckResources || mCountOfErrors > 0);
            } catch (Throwable t) {
                GHRPLog.e("tryGetPatchIndex threw", t);
            }

            if (result == null || result.isEmpty()) {
                GHRPLog.e("tryGetPatchIndex returned empty json (attempt " + (mCountOfErrors + 1) + ")");
                mCountOfErrors++;
                continue;
            }

            try {
                JSONObject r = new JSONObject(result);
                long status = r.optLong("patch_index_status_key", -1);
                long additionSize = r.optLong("patch_index_addition_size_after_apply", -1);
                String errorKey = r.optString("patch_index_error_key", "");
                GHRPLog.i("patch index result: status=" + status + " addition=" + additionSize
                        + " error=" + errorKey);

                if (status == 1 && (additionSize == 0 || errorKey.equalsIgnoreCase("0x00000000") && additionSize == 0)) {
                    // Up to date
                    GHRPLog.i("=== resources up to date — engine continues ===");
                    postComplete();
                    return;
                }
                if (status == 1 && additionSize > 0) {
                    // Update required -> native download phase
                    runDownload(additionSize);
                    return;
                }
                // status != 1 -> CDN error, retry with countOfErrors++
                GHRPLog.w("patch index phase failed (error=" + errorKey + ") attempt "
                        + (mCountOfErrors + 1) + " of " + MAX_ERRORS);
                postStatus("Reconnecting to CDN… attempt " + (mCountOfErrors + 1) + " of " + MAX_ERRORS);
            } catch (Throwable t) {
                GHRPLog.e("patch index result parse failed", t);
            }
            mCountOfErrors++;
            sleep(2000);
        }

        if (mCancelled) return;
        retryOrFail("Could not reach the game data CDN (patch index phase failed after "
                + MAX_ERRORS + " attempts). Check your connection and try again.");
    }

    // ------------------------------------------------------------------
    private void runDownload(long additionSize) {
        postStatus("Preparing download…");
        mView.showDownloadUi(additionSize);
        GHRPLog.i("=== download phase start: " + additionSize + " bytes ===");

        final long start = android.os.SystemClock.elapsedRealtime();
        Boolean ok = null;
        try {
            ok = JNILib.tryDownloadResources(
                    Settings.sIsEnabledRecovery,
                    Settings.sDownloadSpeedLimit,
                    Settings.sIsEnabledCheckResources,
                    Settings.sDownloadTimeout,
                    Settings.sConnectionTimeout,
                    Settings.sIsEnabledSendingOfCDNMetric,
                    Settings.sForceCheckResources);
        } catch (Throwable t) {
            GHRPLog.e("tryDownloadResources threw", t);
        }

        GHRPLog.i("tryDownloadResources -> " + ok + " in "
                + ((android.os.SystemClock.elapsedRealtime() - start) / 1000) + "s");

        if (mCancelled) return;

        if (ok != null && ok) {
            GHRPLog.i("=== download complete — engine continues ===");
            postComplete();
            return;
        }

        // Download failed -> retry the whole cycle (fresh patch index state)
        mCountOfErrors++;
        if (mCountOfErrors < MAX_ERRORS) {
            GHRPLog.w("download failed — restarting update cycle (attempt " + mCountOfErrors + ")");
            mView.hideDownloadUi();
            runUpdateFlow();
            return;
        }
        retryOrFail("The game data download did not complete. Check your connection "
                + "and free storage, then try again.");
    }

    // ------------------------------------------------------------------
    // progress polling (driven by the view timer)
    // ------------------------------------------------------------------
    public void pollProgress() {
        try {
            String data = JNILib.getAdditionDownloadPatchData();
            if (data == null || data.isEmpty()) return;
            JSONObject o = new JSONObject(data);
            // Common shapes seen in the update manager state: try the known keys.
            long total = o.optLong("patch_index_addition_size_after_apply",
                    o.optLong("total_size", o.optLong("size", 0)));
            long done = o.optLong("downloaded", o.optLong("bytes_downloaded", 0));
            String file = o.optString("current_file", o.optString("file", ""));
            if (total > 0 || done > 0 || !file.isEmpty()) {
                mView.updateProgress(done, total, file);
            }
        } catch (Throwable t) {
            // progress polling must never break the flow
        }
    }

    // ------------------------------------------------------------------
    private void deliverConfigToEngine(String name, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            GHRPLog.w(name + " unavailable — engine will use defaults");
            return;
        }
        try {
            if (name.equals("url-config.json")) {
                JNILib.onUrlConfigReceived(bytes);
            } else {
                JNILib.onAppConfigReceived(bytes);
            }
            GHRPLog.i(name + " delivered to engine (" + bytes.length + " bytes)");
        } catch (Throwable t) {
            GHRPLog.e("deliverConfigToEngine " + name + " failed", t);
        }
    }

    private void retryOrFail(final String message) {
        mUi.post(new Runnable() {
            @Override
            public void run() {
                mView.showErrorWithRetry(message, new Runnable() {
                    @Override
                    public void run() {
                        mCountOfErrors = 0;
                        start();
                    }
                });
            }
        });
    }

    private void postStatus(final String s) {
        mUi.post(new Runnable() {
            @Override
            public void run() {
                mView.setStatus(s);
            }
        });
    }

    private void postComplete() {
        mUi.post(new Runnable() {
            @Override
            public void run() {
                mView.onUpdateComplete();
            }
        });
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
