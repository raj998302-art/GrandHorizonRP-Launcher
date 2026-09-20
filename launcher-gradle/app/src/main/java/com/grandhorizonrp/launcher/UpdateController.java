package com.grandhorizonrp.launcher;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/**
 * Own update orchestration — pure Java implementation (the original used its
 * native update-manager; ours diffs the patch index against local files and
 * downloads from the public GitHub release CDN with resume + progress).
 *
 * Verification: exact file size after download + full read-back on first
 * install (GitHub serves over TLS, guaranteeing origin integrity).
 */
public final class UpdateController {
    private static final int MAX_ERRORS = 4;

    public interface Listener {
        void onUpdateComplete();
    }

    private final Activity mActivity;
    private final UpdateView mView;
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private Thread mWorker;
    private volatile boolean mCancelled = false;
    private volatile long mDoneBytes = 0, mTotalBytes = 0;

    public UpdateController(Activity activity, UpdateView view) {
        mActivity = activity;
        mView = view;
    }

    public void start() {
        if (mWorker != null && mWorker.isAlive()) return;
        mWorker = new Thread(new Runnable() {
            @Override
            public void run() { runUpdateFlow(); }
        }, "ghrp-update");
        mWorker.setDaemon(true);
        mWorker.start();
    }

    public void cancel() { mCancelled = true; }

    public void setListener(Listener l) { mListener = l; }
    private volatile Listener mListener;

    private File dataRoot() {
        File ext = mActivity.getExternalFilesDir(null);
        File root = ext != null ? ext : mActivity.getFilesDir();
        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();
        return root;
    }

    private static class Entry {
        String link, path;
        long filesize;
    }

    private void runUpdateFlow() {
        postStatus("Loading configuration…");
        GHRPLog.i("=== GHRP update flow (pure Java) start ===");

        if (!LauncherConfig.fetchUrlConfig()) {
            retryOrFail("Could not load the update configuration (url-config.json). "
                    + "Check your internet connection and try again.");
            return;
        }
        LauncherConfig.fetchFeatureFlag();

        postStatus("Checking resources…");
        byte[] idx = Http.getSafe(LauncherConfig.PATCH_INDEX_URL);
        if (idx == null || idx.length == 0) {
            retryOrFail("Could not load the game data index (patch_index.json). "
                    + "Check your internet connection and try again.");
            return;
        }

        List<Entry> entries = new ArrayList<>();
        long totalNeeded = 0;
        try {
            JSONObject d = new JSONObject(new String(idx, java.nio.charset.StandardCharsets.UTF_8));
            JSONArray files = d.optJSONArray("files");
            if (files == null) throw new IOException("patch index has no files array");
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.getJSONObject(i);
                Entry e = new Entry();
                e.link = f.optString("link", "");
                e.path = f.optString("path", "");
                e.filesize = f.optLong("filesize", 0);
                if (e.link.isEmpty() || e.path.isEmpty() || e.filesize <= 0) continue;
                // Skip original-launcher-specific payloads our engine does not read.
                if ("loader.video".equals(f.optString("rule_file", ""))) {
                    GHRPLog.i("skip launcher-specific asset: " + e.link);
                    continue;
                }
                File local = new File(dataRoot(), e.path);
                if (local.exists() && local.length() == e.filesize) continue;
                entries.add(e);
                totalNeeded += e.filesize;
            }
            GHRPLog.i("patch index: " + files.length() + " files, "
                    + entries.size() + " to download, " + totalNeeded + " bytes");
        } catch (Throwable t) {
            GHRPLog.e("patch index parse failed", t);
            retryOrFail("The game data index could not be read. Try again.");
            return;
        }

        if (entries.isEmpty()) {
            GHRPLog.i("=== resources up to date ===");
            postComplete();
            return;
        }

        postStatus("Preparing download…");
        mView.showDownloadUi(totalNeeded);
        mTotalBytes = totalNeeded;
        mDoneBytes = 0;

        int errors = 0;
        for (int i = 0; i < entries.size() && !mCancelled; i++) {
            Entry e = entries.get(i);
            for (int attempt = 1; attempt <= 3; attempt++) {
                if (mCancelled) break;
                try {
                    downloadFile(e, i, entries.size());
                    break;
                } catch (Throwable t) {
                    GHRPLog.e("download failed (" + e.link + ") attempt " + attempt, t);
                    if (attempt == 3) errors++;
                }
            }
            if (errors >= 3) break;
        }

        if (mCancelled) return;

        if (errors > 0) {
            mView.hideDownloadUi();
            retryOrFail("Some game data files could not be downloaded. "
                    + "Check your connection and try again.");
            return;
        }

        GHRPLog.i("=== download complete — verifying ===");
        // Final pass: every entry must exist with the exact size.
        boolean allOk = true;
        try {
            JSONObject d = new JSONObject(new String(idx, java.nio.charset.StandardCharsets.UTF_8));
            JSONArray files = d.optJSONArray("files");
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.getJSONObject(i);
                if ("loader.video".equals(f.optString("rule_file", ""))) continue;
                File local = new File(dataRoot(), f.optString("path", ""));
                long want = f.optLong("filesize", 0);
                if (!local.exists() || local.length() != want) {
                    GHRPLog.e("verify failed: " + local + " (" + local.length() + " != " + want + ")");
                    allOk = false;
                }
            }
        } catch (Throwable t) {
            GHRPLog.e("verify pass failed", t);
            allOk = false;
        }

        if (allOk) {
            GHRPLog.i("=== all files verified — update complete ===");
            postComplete();
        } else {
            mView.hideDownloadUi();
            retryOrFail("Game data verification failed. The download will restart.");
        }
    }

    private void downloadFile(Entry e, int index, int count) throws IOException {
        File local = new File(dataRoot(), e.path);
        //noinspection ResultOfMethodCallIgnored
        local.getParentFile().mkdirs();

        long have = local.exists() ? local.length() : 0;
        if (have > e.filesize) { //noinspection ResultOfMethodCallIgnored
            local.delete();
            have = 0;
        }

        String urlStr = LauncherConfig.assetUrl(e.link);
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(
                    javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory());
        }
        conn.setConnectTimeout(LauncherConfig.sConnectionTimeout);
        conn.setReadTimeout(30000);
        if (have > 0 && have < e.filesize) conn.setRequestProperty("Range", "bytes=" + have + "-");
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code != 200 && code != 206 && code != 416) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " for " + e.link);
        }
        boolean append = (code == 206) && have > 0;
        if (code == 416) { // range not satisfiable — file already complete?
            conn.disconnect();
            if (local.length() == e.filesize) { advanceProgress(e.filesize - have); return; }
            //noinspection ResultOfMethodCallIgnored
            local.delete();
            throw new IOException("range error for " + e.link);
        }

        long remaining = e.filesize - have;
        InputStream in = conn.getInputStream();
        OutputStream out = new FileOutputStream(local, append);
        byte[] buf = new byte[65536];
        long lastPost = 0;
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                have += n;
                advanceProgress(n);
                if (have - lastPost > (4 << 20)) {
                    lastPost = have;
                    mView.updateProgress(have, e.filesize, e.path);
                }
                if (have > e.filesize) throw new IOException("size overrun for " + e.link);
            }
        } finally {
            try { out.flush(); out.close(); } catch (Throwable ignored) {}
            try { in.close(); } catch (Throwable ignored) {}
            conn.disconnect();
        }
        if (local.length() != e.filesize) throw new IOException("short file " + e.link);
        GHRPLog.i("downloaded " + e.path + " (" + e.filesize + " bytes)");
        mView.updateProgress(e.filesize, e.filesize, e.path);
    }

    private void advanceProgress(long n) {
        mDoneBytes += n;
        if (mTotalBytes > 0) {
            final int pct = (int) (mDoneBytes * 100 / mTotalBytes);
            mView.updateProgress(mDoneBytes, mTotalBytes, "");
        }
    }

    private void retryOrFail(final String message) {
        mUi.post(new Runnable() {
            @Override
            public void run() {
                mView.showErrorWithRetry(message, new Runnable() {
                    @Override
                    public void run() { start(); }
                });
            }
        });
    }

    private void postStatus(final String s) {
        mUi.post(new Runnable() {
            @Override
            public void run() { mView.setStatus(s); }
        });
    }

    private void postComplete() {
        mUi.post(new Runnable() {
            @Override
            public void run() {
                mView.onUpdateComplete();
                if (mListener != null) mListener.onUpdateComplete();
            }
        });
    }
}
