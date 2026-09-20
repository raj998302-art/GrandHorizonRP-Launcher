package com.blackhub.bronline.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.blackhub.bronline.game.core.JNIJSONTransport;
import com.blackhub.bronline.game.core.JNILib;
import com.blackhub.bronline.game.core.keyboardHelper.SoftwareKeyboardBridge;
import com.grandhorizonrp.launcher.AuthController;
import com.grandhorizonrp.launcher.GHRPLog;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Engine GUI event dispatcher. Receives onJsonDataIncoming messages from the
 * engine (via JNIJSONTransport) and drives the Android-side overlays:
 *  - screen 0x58 (88): WebView authentication (exact original flow:
 *    engine sends {auth_url}; SSO page calls Android.initToken(front_token);
 *    token goes back to the engine as message 0x58).
 *  - error dialogs (synchronous RPC — releases the engine's dialog latch)
 *  - software keyboard host (invisible EditText feeding SoftwareKeyboardBridge)
 */
public final class GUIManager {
    public static final int SCREEN_WEB_AUTH = 0x58;

    private static volatile boolean sWebAuthOpen = false;
    private static EditText sKeyboardHost;

    private GUIManager() {
    }

    // ------------------------------------------------------------------
    // Engine -> Java message dispatch
    // ------------------------------------------------------------------
    public static void onEngineMessage(int id, String json) {
        if (id == SCREEN_WEB_AUTH) {
            openWebAuth(json);
            return;
        }
        switch (id) {
            case 0x4d:
            case 0x4f:
            case 0x50:
            case 0x51:
            case 0x52:
            case 0x53:
            case 0x54:
            case 0x55:
            case 0x56:
                // Auxiliary launcher screens of the original (donate/help/news/...)
                // — not present in GHRP build. Logged and gracefully ignored so
                // the engine's flow is never blocked.
                GHRPLog.i("GUIManager: auxiliary screen 0x" + Integer.toHexString(id) + " ignored");
                break;
            default:
                GHRPLog.d("GUIManager: unhandled engine message 0x" + Integer.toHexString(id));
                break;
        }
    }

    // ------------------------------------------------------------------
    // WebView authentication (screen 0x58)
    // ------------------------------------------------------------------
    private static void openWebAuth(String json) {
        if (sWebAuthOpen) {
            GHRPLog.w("GUIManager: web auth already open");
            return;
        }
        String authUrl = null;
        try {
            JSONObject o = new JSONObject(json == null ? "{}" : json);
            authUrl = o.optString("auth_url", null);
            if (authUrl == null || authUrl.isEmpty()) authUrl = o.optString("url", null);
            if (authUrl == null || authUrl.isEmpty()) authUrl = o.optString("authUrl", null);
        } catch (Throwable t) {
            GHRPLog.e("openWebAuth: bad payload", t);
        }
        if (authUrl == null || authUrl.isEmpty()) {
            GHRPLog.e("openWebAuth: no auth_url in engine payload — cannot open SSO");
            return;
        }
        final String url = authUrl;
        final Activity activity = com.blackhub.bronline.game.core.JNIActivity.getContext();
        if (activity == null) return;
        sWebAuthOpen = true;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    AuthController.open(activity, url, new AuthController.Listener() {
                        @Override
                        public void onToken(String tokenJson) {
                            // Exact original contract: token JSON goes back to the
                            // engine as message 0x58 (sendJsonData), then the
                            // webview closes.
                            try {
                                JNILib.sendJsonData(SCREEN_WEB_AUTH, tokenJson.getBytes(StandardCharsets.UTF_8));
                            } catch (Throwable t) {
                                GHRPLog.e("sendJsonData(0x58, token) failed", t);
                            }
                        }

                        @Override
                        public void onClose(String error) {
                            sWebAuthOpen = false;
                            if (error != null && !error.isEmpty()) {
                                JNIJSONTransport.showErrorDialog(error);
                            }
                        }
                    });
                } catch (Throwable t) {
                    sWebAuthOpen = false;
                    GHRPLog.e("openWebAuth failed", t);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Dialogs (release the engine's awaitDialogClose latch on dismiss)
    // ------------------------------------------------------------------
    public static void showErrorDialog(Activity activity, String message) {
        new AlertDialog.Builder(activity)
                .setTitle("Grand Horizon RP")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        JNIJSONTransport.releaseDialogLatch();
                    }
                })
                .show();
    }

    public static void showEngineDialog(Activity activity, String title, String content,
                                        String leftBtn, String rightBtn) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(title == null || title.isEmpty() ? "Grand Horizon RP" : title)
                .setCancelable(false)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        JNIJSONTransport.releaseDialogLatch();
                    }
                });
        if (content != null && !content.isEmpty()) b.setMessage(content);
        if (leftBtn != null && !leftBtn.isEmpty()) {
            b.setPositiveButton(leftBtn, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        }
        if (rightBtn != null && !rightBtn.isEmpty()) {
            b.setNegativeButton(rightBtn, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        }
        if (leftBtn == null || (leftBtn.isEmpty() && (rightBtn == null || rightBtn.isEmpty()))) {
            b.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        }
        b.show();
    }

    // ------------------------------------------------------------------
    // Overlay management
    // ------------------------------------------------------------------
    public static void closeOverlays(boolean keepGame) {
        Activity activity = com.blackhub.bronline.game.core.JNIActivity.getContext();
        if (activity == null) return;
        if (sWebAuthOpen) {
            AuthController.close(activity);
            sWebAuthOpen = false;
        }
        closeSoftwareKeyboard(activity);
    }

    // ------------------------------------------------------------------
    // Software keyboard host (invisible EditText -> SoftwareKeyboardBridge)
    // ------------------------------------------------------------------
    public static void ensureKeyboardHost(final Activity activity) {
        if (sKeyboardHost != null) return;
        final EditText host = new EditText(activity);
        host.setLayoutParams(new FrameLayout.LayoutParams(1, 1, Gravity.BOTTOM | Gravity.START));
        host.setAlpha(0f);
        host.setInputType(InputType.TYPE_CLASS_TEXT);
        host.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        host.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    SoftwareKeyboardBridge.commitText(s.toString(), 0, s.length());
                    s.clear();
                }
            }
        });
        host.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    SoftwareKeyboardBridge.onKeyDown(keyCode);
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    SoftwareKeyboardBridge.onKeyUp(keyCode);
                }
                return false;
            }
        });
        sKeyboardHost = host;
    }

    public static void openSoftwareKeyboard(Activity activity, String hint) {
        ensureKeyboardHost(activity);
        final EditText host = sKeyboardHost;
        if (host == null) return;
        if (hint != null) host.setHint(hint);
        host.setFocusable(true);
        host.setFocusableInTouchMode(true);
        host.requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(host, InputMethodManager.SHOW_IMPLICIT);
            SoftwareKeyboardBridge.onSetVisible(true);
        }
    }

    public static void closeSoftwareKeyboard(Activity activity) {
        if (sKeyboardHost == null) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(sKeyboardHost.getWindowToken(), 0);
            SoftwareKeyboardBridge.onSetVisible(false);
        }
        sKeyboardHost.clearFocus();
    }

    public static void setKeyboardText(String text) {
        if (sKeyboardHost != null && text != null) {
            sKeyboardHost.setText(text);
            sKeyboardHost.setSelection(text.length());
        }
    }

    /** Attach the invisible keyboard host into the activity overlay. */
    public static void attachKeyboardHost(Activity activity, FrameLayout overlay) {
        ensureKeyboardHost(activity);
        if (sKeyboardHost != null && sKeyboardHost.getParent() == null) {
            overlay.addView(sKeyboardHost);
        }
    }

    // ------------------------------------------------------------------
    // Misc engine events
    // ------------------------------------------------------------------
    public static void onEngineSpawn() {
        // In-game spawn — nothing needed on the Java side.
    }

    public static void onSplashScreenDestroyed() {
        GHRPLog.i("Engine splash destroyed — start screen visible");
    }

    public static void onAsyncBitmap(String name, android.graphics.Bitmap bitmap) {
        if (bitmap != null) {
            GHRPLog.d("onAsyncBitmap " + name + " " + bitmap.getWidth() + "x" + bitmap.getHeight());
        }
    }

    public static void onAsyncFile(String name, String content) {
        GHRPLog.d("onAsyncFile " + name);
    }
}
