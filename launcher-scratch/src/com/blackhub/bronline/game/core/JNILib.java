package com.blackhub.bronline.game.core;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/**
 * GHRP from-scratch launcher — engine JNI bridge.
 * Class/method names and signatures are the exact contract required by
 * libblackrussia-client.so / libupdate-manager.so (Java_ symbol registration).
 * Source of truth: original launcher smali (com.blackhub.bronline.game.core.JNILib).
 */
public class JNILib {
    static {
        System.loadLibrary("update-manager");
        System.loadLibrary("blackrussia-client");
        sLoaded = true;
    }

    private static boolean sLoaded = false;

    public static boolean isLoaded() {
        return sLoaded;
    }

    // ------------------------------------------------------------------
    // Engine lifecycle
    // ------------------------------------------------------------------
    public static native void init(String pathToRes, String pathToSaves, int version, int distType, int buildType);
    public static native boolean initRender(int width, int height, int densityDpi, float density, int version);
    public static native void resize(int width, int height);
    public static native void step();
    public static native void pauseEvent();
    public static native void resumeEvent();
    public static native void orientationChanged(int w, int h, int orientation);
    public static native void initFileLogger();
    public static native void networkConnectionChanged(boolean connected);

    // ------------------------------------------------------------------
    // Messaging (both directions; engine -> Java arrives in JNIJSONTransport)
    // ------------------------------------------------------------------
    public static native void sendJsonData(int id, byte[] json);
    public static native void deliverNetworkEvents(int type, byte[] json);
    public static native String sendUpdateSystemCodedMessage(int a, int b, String msg);
    public static native void requestPlayers(byte[] json);
    public static native void sendChatMessage(byte[] json);

    /** Convenience wrapper used by GUIManager (matches original JNILib.deliverJson). */
    public static void deliverJson(int type, JSONObject json) {
        if (json == null) return;
        deliverNetworkEvents(type, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // Config delivery to engine
    // ------------------------------------------------------------------
    public static native void onUrlConfigReceived(byte[] urlConfigJson);
    public static native void onAppConfigReceived(byte[] appConfigJson);

    // ------------------------------------------------------------------
    // Update manager
    // ------------------------------------------------------------------
    public static native String tryGetPatchIndex(String jsonHttpData, String fileRules, boolean isEnabledCheckResources,
                                                 int version, int candidateVersion, int downloadTimeout,
                                                 int connectionTimeout, int distributionType, boolean useBackupCdn,
                                                 boolean isDevModUpdateManager, boolean forceCheckResources);
    public static native boolean tryDownloadResources(boolean isEnabledRecovery, int downloadSpeedLimit,
                                                      boolean isEnabledCheckResources, int downloadTimeout,
                                                      int connectionTimeout, boolean isEnabledSendingOfCDNMetric,
                                                      boolean forceCheckResources);
    public static native boolean tryDownloadNextSlot(boolean a, int b, boolean c, int d, int e, String f, boolean g);
    public static native void cancelDownloadResources();
    public static native String getAdditionDownloadPatchData();
    public static native String getCurrentPatchVersion();
    public static native String getTargetPatchVersion();
    public static native void removeLastCheckDateMarker();
    /** Declared in original smali; resolves in library variants that export it. */
    public static native int checkResourcesSubscribe();
    /** Declared in original smali (Cyrillic leading 'c' is intentional). */
    public static native int сheckResources();
    public static native int getResourcesState();

    // ------------------------------------------------------------------
    // Assets / archive access
    // ------------------------------------------------------------------
    public static native String getJsonFromArchive(String name);
    public static native void getBitmapFromAssetsAsync(String name);
    public static native void getFileFromAssetsAsync(String name);

    // ------------------------------------------------------------------
    // Player / audio / misc
    // ------------------------------------------------------------------
    public static native int getPlayerId();
    public static native int getPlayerVehicleType();
    public static native boolean getMutePlayer(int player);
    public static native void setMutePlayer(int player, boolean mute);
    public static native int getVolumePlayer(int player);
    public static native void setVolumePlayer(int player, int volume);
    public static native boolean isDonateAllowed();
    public static native void setDebugMenuVisible(boolean visible);
    public static native void toggleBloor(boolean on);
    public static native void toggleDrawing2dStuff(boolean on);
    public static native void onFrameDecoded(long frame);
    public static native int onMediaOpened(long id, int a, int b);
    public static native void multiTouchEvent(int x0, int y0, int x1, int y1, int x2, int y2,
                                              int actionPointerId, int actionMasked);
}
