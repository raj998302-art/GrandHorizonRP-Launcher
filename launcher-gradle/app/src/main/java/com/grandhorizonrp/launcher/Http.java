package com.grandhorizonrp.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

/**
 * Small, robust HTTP fetcher used by the Java-side config flow
 * (url-config / app-config / feature flag / patch-index pre-seed).
 * Uses the platform's TLS stack (real system CA store — no native curl
 * involvement), generous timeouts and retry with backoff.
 */
public final class Http {
    private Http() {
    }

    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    public static byte[] get(String url, int timeoutMs) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return getOnce(url, timeoutMs);
            } catch (IOException e) {
                last = e;
                GHRPLog.w("Http.get attempt " + (attempt + 1) + " failed: " + e.getMessage());
                try {
                    Thread.sleep(1500L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    private static byte[] getOnce(String urlString, int timeoutMs) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "GHRP-Launcher/1529");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " for " + urlString);
            }
            InputStream in = conn.getInputStream();
            if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                in = new GZIPInputStream(in);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 16);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            return bos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    public static String getString(String url, int timeoutMs) throws IOException {
        return new String(get(url, timeoutMs), StandardCharsets.UTF_8);
    }

    /** Non-throwing fetch — returns null on failure (logged). */
    public static byte[] getSafe(String url) {
        try {
            return get(url, 20000);
        } catch (Throwable t) {
            GHRPLog.e("Http.getSafe failed for " + url + ": " + t.getMessage());
            return null;
        }
    }

    /**
     * JSON POST with optional Bearer auth. Returns the parsed status line:
     * { code, body } — body is the raw response text (may be "").
     * Used by the session/character API calls against the SSO backend.
     */
    public static class JsonResp {
        public final int code;
        public final String body;

        JsonResp(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public boolean isOk() {
            return code >= 200 && code < 300;
        }
    }

    public static JsonResp postJson(String urlString, String json, String bearerToken, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "GHRP-Launcher/1529");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (bearerToken != null && !bearerToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }
            conn.setDoOutput(true);
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = "";
            if (in != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 14);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                body = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            }
            return new JsonResp(code, body);
        } catch (Throwable t) {
            GHRPLog.e("Http.postJson failed: " + t.getMessage());
            return new JsonResp(-1, "");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** JSON GET with optional Bearer auth (same contract as postJson). */
    public static JsonResp getJson(String urlString, String bearerToken, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "GHRP-Launcher/1529");
            conn.setRequestProperty("Accept", "application/json");
            if (bearerToken != null && !bearerToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = "";
            if (in != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 14);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                body = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            }
            return new JsonResp(code, body);
        } catch (Throwable t) {
            GHRPLog.e("Http.getJson failed: " + t.getMessage());
            return new JsonResp(-1, "");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Download to a byte array with progress reporting (used for diagnostics). */
    public static byte[] download(String urlString, int timeoutMs, ProgressListener listener)
            throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(Math.max(timeoutMs, 60000));
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "GHRP-Launcher/1529");
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " for " + urlString);
            }
            long total = conn.getContentLengthLong();
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 16);
            byte[] buf = new byte[65536];
            int n;
            long done = 0;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                done += n;
                if (listener != null) listener.onProgress(done, total);
            }
            in.close();
            return bos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }
}
