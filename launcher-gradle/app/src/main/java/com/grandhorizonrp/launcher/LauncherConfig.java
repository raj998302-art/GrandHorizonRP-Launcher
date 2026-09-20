package com.grandhorizonrp.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Remote configuration (own implementation — no original-engine classes).
 * Fetches url-config.json / update_manager_feature_flag.json / patch_index.json
 * from the pinned jsDelivr CDN, matching the gamedata release content.
 */
public final class LauncherConfig {
    /** Pinned jsDelivr commit — immutable, matches the gamedata release content. */
    public static final String JSDELIVR_PIN =
            "1ddc504b1698c37196cf8e54138e57cd3254caf1";
    public static final String CLIENT_API_BASE =
            "https://cdn.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@"
                    + JSDELIVR_PIN + "/client-api/";

    public static final String URL_CONFIG_URL = CLIENT_API_BASE + "url-config.json";
    public static final String APP_CONFIG_URL = CLIENT_API_BASE + "app-config.json";
    public static final String FEATURE_FLAG_URL = CLIENT_API_BASE + "update_manager_feature_flag.json";
    public static final String SERVERS_URL = CLIENT_API_BASE + "servers.json";
    public static final String PATCH_INDEX_URL = CLIENT_API_BASE + "patch_index.json";

    /** GitHub release that hosts the game data assets (public CDN). */
    public static final String RELEASE_OWNER = "raj998302-art";
    public static final String RELEASE_REPO = "GrandHorizonRP-Launcher";
    public static final String RELEASE_TAG = "gamedata";

    public static final int VERSION = 1529;
    public static final String LAUNCHER_NAME = "Grand Horizon RP";
    public static final String CLIENT_PACKAGE = "com.grandhorizonrp.launcher";

    // Game server (Grand Horizon RP | HORIZON CITY)
    public static final String SERVER_NAME = "Grand Horizon RP";
    public static final String SERVER_CITY = "HORIZON CITY";
    public static final String SERVER_HOST = "142.132.203.47";
    public static final int SERVER_PORT = 14448;

    // ------------------------------------------------------------------
    // Parsed url-config (region WORLD — English launcher)
    // ------------------------------------------------------------------
    public static volatile String sCdnUrl = "";
    public static volatile String sCdnBackupUrl = "";
    public static volatile String sCharacterService = "https://ghrp-auth.vercel.app/api";
    public static volatile String sRegistrationService = "https://ghrp-auth.vercel.app";
    public static volatile String sPolicyUrl = "";
    public static volatile String sDiscordUrl = "";

    // Feature flag values we actually use.
    public static volatile int sConnectionTimeout = 15000;
    public static volatile int sDownloadTimeout = 1200000;

    private LauncherConfig() {
    }

    public static boolean fetchUrlConfig() {
        try {
            String raw = Http.getString(URL_CONFIG_URL, 20000);
            JSONArray regions = new JSONArray(raw);
            JSONObject chosen = null;
            for (int i = 0; i < regions.length(); i++) {
                JSONObject r = regions.optJSONObject(i);
                if (r == null) continue;
                if ("WORLD".equals(r.optString("region"))) { chosen = r; break; }
            }
            if (chosen == null && regions.length() > 0) chosen = regions.optJSONObject(0);
            if (chosen == null) { GHRPLog.e("fetchUrlConfig: empty region list"); return false; }
            sCdnUrl = stripSlash(chosen.optString("cdnUrl"));
            sCdnBackupUrl = stripSlash(chosen.optString("cdnBackupUrl"));
            sCharacterService = chosen.optString("characterService", sCharacterService);
            sRegistrationService = chosen.optString("registrationService", sRegistrationService);
            sPolicyUrl = chosen.optString("policyUrl", "");
            sDiscordUrl = chosen.optString("discordUrl", "");
            GHRPLog.i("url-config loaded: cdn=" + sCdnUrl
                    + " characterService=" + sCharacterService);
            return true;
        } catch (Throwable t) {
            GHRPLog.e("fetchUrlConfig failed", t);
            return false;
        }
    }

    public static boolean fetchFeatureFlag() {
        try {
            String raw = Http.getString(FEATURE_FLAG_URL, 20000);
            JSONObject o = new JSONObject(raw);
            sConnectionTimeout = o.optInt("connection_timeout", 15000);
            sDownloadTimeout = o.optInt("download_timeout", 1200000);
            GHRPLog.i("feature flag loaded: ct=" + sConnectionTimeout
                    + " dt=" + sDownloadTimeout);
            return true;
        } catch (Throwable t) {
            GHRPLog.e("fetchFeatureFlag failed — using code defaults (15000/1200000)", t);
            return false;
        }
    }

    /** Direct asset URL on the public GitHub release CDN. */
    public static String assetUrl(String link) {
        return "https://github.com/" + RELEASE_OWNER + "/" + RELEASE_REPO
                + "/releases/download/" + RELEASE_TAG + "/" + link;
    }

    private static String stripSlash(String s) {
        if (s == null) return "";
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
