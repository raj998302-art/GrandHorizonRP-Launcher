package com.blackhub.bronline.launcher;

import android.content.SharedPreferences;

import com.grandhorizonrp.launcher.GHRPLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Remote configuration: fetches url-config.json / app-config.json /
 * update_manager_feature_flag.json from the pinned jsDelivr CDN
 * (same pinned commit the game data release was generated from) and
 * builds the serialized-settings JSON the engine reads via
 * JNIJSONTransport.getSerializedSettings().
 */
public final class Settings {
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

    public static final int VERSION = 1529;
    /** DistributionType.Site code. */
    public static final int DISTRIBUTION_TYPE = 0;
    /** BuildType.Release code. */
    public static final int BUILD_TYPE = 5;

    public static final String LAUNCHER_NAME = "Grand Horizon RP";
    public static final String CLIENT_PACKAGE = "com.grandhorizonrp.launcher";

    // ------------------------------------------------------------------
    // Parsed url-config (region WORLD — English launcher)
    // ------------------------------------------------------------------
    public static volatile String sCdnUrl = "";
    public static volatile String sCdnBackupUrl = "";
    public static volatile String sApiUsername = "";
    public static volatile String sApiPassword = "";
    public static volatile String sCharacterService = "https://ghrp-auth.vercel.app/api";
    public static volatile String sRegistrationService = "https://ghrp-auth.vercel.app";
    public static volatile String sPolicyUrl = "";
    public static volatile String sDataPolicyUrl = "";
    public static volatile String sDiscordUrl = "";

    // ------------------------------------------------------------------
    // Parsed feature flag (defaults = original sane values)
    // ------------------------------------------------------------------
    public static volatile int sConnectionTimeout = 15000;
    public static volatile int sDownloadTimeout = 1200000;
    public static volatile int sCandidateVersion = 9999;
    public static volatile int sDownloadSpeedLimit = 0;
    public static volatile long sDownloadSizeWithoutConfirm = 999999999L;
    public static volatile boolean sIsEnabledRecovery = true;
    public static volatile boolean sIsEnabledCheckResources = false;
    public static volatile boolean sForceCheckResources = false;
    public static volatile boolean sIsEnabledNextSlotDownloading = false;
    public static volatile boolean sIsEnabledSendingOfCDNMetric = false;
    public static volatile boolean sIsEnabledCheckCompatibleClientVersion = false;
    public static volatile String sTypeDownloadResources = "patch_index_json";

    /**
     * File rules for the update manager — JSON array string, exactly like the
     * original getUpdateFileRules(): ASTC device + no-logo flavor (GHRP game
     * data ships nologo/astc assets; the rebranded launcher.bpc has no BR
     * logo textures).
     */
    public static final String FILE_RULES = "[\"astc\",\"nologo\",\"nologo.astc\",\"loader.video\"]";

    private Settings() {
    }

    // ------------------------------------------------------------------
    // Fetchers (background thread)
    // ------------------------------------------------------------------
    public static boolean fetchUrlConfig() {
        try {
            String raw = com.grandhorizonrp.launcher.Http.getString(URL_CONFIG_URL, 20000);
            JSONArray regions = new JSONArray(raw);
            JSONObject chosen = null;
            for (int i = 0; i < regions.length(); i++) {
                JSONObject r = regions.optJSONObject(i);
                if (r == null) continue;
                if ("WORLD".equals(r.optString("region"))) {
                    chosen = r;
                    break;
                }
            }
            if (chosen == null && regions.length() > 0) {
                chosen = regions.optJSONObject(0);
            }
            if (chosen == null) {
                GHRPLog.e("fetchUrlConfig: empty region list");
                return false;
            }
            sCdnUrl = stripSlash(chosen.optString("cdnUrl"));
            sCdnBackupUrl = stripSlash(chosen.optString("cdnBackupUrl"));
            sApiUsername = chosen.optString("apiUsername", "");
            sApiPassword = chosen.optString("apiPassword", "");
            sCharacterService = chosen.optString("characterService", sCharacterService);
            sRegistrationService = chosen.optString("registrationService", sRegistrationService);
            sPolicyUrl = chosen.optString("policyUrl", "");
            sDataPolicyUrl = chosen.optString("dataPolicyUrl", "");
            sDiscordUrl = chosen.optString("discordUrl", "");
            GHRPLog.i("url-config loaded: cdn=" + sCdnUrl + " characterService=" + sCharacterService);
            return true;
        } catch (Throwable t) {
            GHRPLog.e("fetchUrlConfig failed", t);
            return false;
        }
    }

    public static boolean fetchFeatureFlag() {
        try {
            String raw = com.grandhorizonrp.launcher.Http.getString(FEATURE_FLAG_URL, 20000);
            JSONObject o = new JSONObject(raw);
            sConnectionTimeout = o.optInt("connection_timeout", 15000);
            sDownloadTimeout = o.optInt("download_timeout", 1200000);
            sDownloadSpeedLimit = o.optInt("download_speed_limit", 0);
            sDownloadSizeWithoutConfirm = o.optLong("download_size_without_confirm", 999999999L);
            sIsEnabledRecovery = o.optBoolean("is_enabled_recovery", true);
            sIsEnabledCheckResources = o.optBoolean("is_enabled_check_resources", false);
            sForceCheckResources = o.optBoolean("force_check_resources", false);
            JSONObject cand = o.optJSONObject("candidate_versions");
            if (cand != null) sCandidateVersion = cand.optInt("site", cand.optInt("defaultValue", 9999));
            JSONObject slot = o.optJSONObject("next_slot_downloading");
            if (slot != null) sIsEnabledNextSlotDownloading = slot.optBoolean("is_enabled", false);
            JSONObject metric = o.optJSONObject("sending_of_cdn_metric");
            if (metric != null) sIsEnabledSendingOfCDNMetric = metric.optBoolean("is_enabled", false);
            JSONObject compat = o.optJSONObject("check_compatible_client_version");
            if (compat != null) sIsEnabledCheckCompatibleClientVersion = compat.optBoolean("is_enabled", false);
            JSONObject type = o.optJSONObject("type_download_resources_3");
            if (type != null) sTypeDownloadResources = type.optString("site", "patch_index_json");
            GHRPLog.i("feature flag loaded: ct=" + sConnectionTimeout + " dt=" + sDownloadTimeout
                    + " recovery=" + sIsEnabledRecovery);
            return true;
        } catch (Throwable t) {
            GHRPLog.e("fetchFeatureFlag failed — using code defaults (15000/1200000)", t);
            return false;
        }
    }

    /**
     * Build the httpData JSON passed as the first argument of
     * JNILib.tryGetPatchIndex (contract: {"cdn","backup_cdn","username","password"}).
     * GHRP: credentials are EMPTY (public GitHub release CDN) — never the
     * original's hardcoded BR CDN basic-auth pair.
     */
    public static String buildHttpData() {
        try {
            JSONObject o = new JSONObject();
            o.put("cdn", sCdnUrl);
            o.put("backup_cdn", sCdnBackupUrl);
            o.put("username", sApiUsername == null ? "" : sApiUsername);
            o.put("password", sApiPassword == null ? "" : sApiPassword);
            return o.toString();
        } catch (Throwable t) {
            return "{\"cdn\":\"" + sCdnUrl + "\",\"backup_cdn\":\"" + sCdnBackupUrl
                    + "\",\"username\":\"\",\"password\":\"\"}";
        }
    }

    private static String stripSlash(String s) {
        if (s == null) return "";
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ------------------------------------------------------------------
    // Serialized settings (engine reads these via getSerializedSettings)
    // ------------------------------------------------------------------
    public static byte[] buildSerializedSettings(SharedPreferences prefs) {
        try {
            JSONObject o = new JSONObject();
            // CDN / API endpoints
            o.put("apiUrl", sCdnUrl);
            o.put("apiBackupUrl", sCdnBackupUrl);
            o.put("apiUserName", sApiUsername);
            o.put("apiPassword", sApiPassword);
            o.put("apiUserAgent", "GHRP-Launcher/1529");
            // Language — English build
            o.put("language", prefs.getString("language", "en"));
            o.put("uiLanguage", prefs.getString("language", "en"));
            o.put("region", "WORLD");
            // Auth state (engine Auth2 flow)
            o.put("authFlowType", prefs.getString("AUTH_FLOW_TYPE", ""));
            o.put("authAccessToken", prefs.getString("AUTH_ACCESS_TOKEN", ""));
            o.put("authRefreshToken", prefs.getString("AUTH_REFRESH_TOKEN", ""));
            o.put("playerName", prefs.getString("playerName", ""));
            o.put("playerServerId", prefs.getInt("USER_SERVER_ID", 0));
            o.put("playerCharacterId", prefs.getInt("playerCharacterId", 0));
            o.put("isPolicyAccepted", prefs.getBoolean("isPolicyAccepted", false));
            // URLs
            o.put("policyUrl", sPolicyUrl);
            o.put("dataPolicyUrl", sDataPolicyUrl);
            o.put("discordUrl", sDiscordUrl);
            o.put("offerUrl", "");
            o.put("telegramUrl", "");
            o.put("telegramBotUrl", "");
            o.put("vkUrl", "");
            // Graphics defaults (engine fills its own; keys preserved)
            o.put("maxFps", 60);
            o.put("fps", 60);
            return o.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            GHRPLog.e("buildSerializedSettings failed", t);
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Persist engine-pushed settings updates (onSettingsJsonDataUpdate). */
    public static void applySerializedSettings(SharedPreferences prefs, String json) {
        try {
            JSONObject o = new JSONObject(json);
            SharedPreferences.Editor e = prefs.edit();
            copyIfExists(o, e, "language");
            copyIfExists(o, e, "AUTH_FLOW_TYPE");
            copyIfExists(o, e, "AUTH_ACCESS_TOKEN");
            copyIfExists(o, e, "AUTH_REFRESH_TOKEN");
            copyIfExists(o, e, "playerName");
            if (o.has("playerServerId")) e.putInt("USER_SERVER_ID", o.optInt("playerServerId", 0));
            if (o.has("playerCharacterId")) e.putInt("playerCharacterId", o.optInt("playerCharacterId", 0));
            if (o.has("isPolicyAccepted")) e.putBoolean("isPolicyAccepted", o.optBoolean("isPolicyAccepted", false));
            e.apply();
        } catch (Throwable t) {
            GHRPLog.e("applySerializedSettings failed", t);
        }
    }

    private static void copyIfExists(JSONObject o, SharedPreferences.Editor e, String key) {
        if (o.has(key)) e.putString(key, o.optString(key, ""));
    }
}
