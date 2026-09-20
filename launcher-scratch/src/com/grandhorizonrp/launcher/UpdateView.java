package com.grandhorizonrp.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Update / init overlay in the launcher's design language:
 * dark #0d0e12 backdrop, GHRP wordmark, orange-red gradient accents
 * (matches the SSO + engine XAML rebrand).
 */
public final class UpdateView {
    private static final int BG = 0xFF0D0E12;
    private static final int ACCENT_START = 0xFFC60000;
    private static final int ACCENT_END = 0xFFFF6318;
    private static final int TEXT_MAIN = 0xFFF2F3F5;
    private static final int TEXT_DIM = 0xFF9A9DA3;

    private final Activity mActivity;
    private final FrameLayout mRoot;
    private TextView mStatus;
    private TextView mTitle;
    private TextView mDetail;
    private TextView mPercent;
    private ProgressBar mBar;
    private LinearLayout mProgressPanel;
    private Runnable mPollTask;
    private UpdateController mController;

    public UpdateView(Activity activity, FrameLayout overlay) {
        mActivity = activity;
        mRoot = build();
        overlay.addView(mRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setController(UpdateController controller) {
        mController = controller;
    }

    // ------------------------------------------------------------------
    private FrameLayout build() {
        FrameLayout root = new FrameLayout(mActivity);
        root.setBackgroundColor(BG);

        LinearLayout center = new LinearLayout(mActivity);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(40), dp(24), dp(40), dp(24));

        mTitle = new TextView(mActivity);
        mTitle.setText("GRAND HORIZON");
        mTitle.setTextColor(TEXT_MAIN);
        mTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mTitle.setTextSize(30);
        mTitle.setLetterSpacing(0.14f);
        mTitle.setGravity(Gravity.CENTER);
        center.addView(mTitle, linParams());

        TextView sub = new TextView(mActivity);
        sub.setText("R O L E P L A Y");
        sub.setTextColor(TEXT_DIM);
        sub.setTextSize(12);
        sub.setLetterSpacing(0.42f);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = linParams();
        subParams.topMargin = dp(6);
        subParams.bottomMargin = dp(42);
        center.addView(sub, subParams);

        mBar = new ProgressBar(mActivity, null, android.R.attr.progressBarStyleHorizontal);
        mBar.setIndeterminate(true);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(4));
        track.setColor(0xFF232630);
        mBar.setProgressDrawable(buildProgressDrawable());
        mBar.setVisibility(View.VISIBLE);
        center.addView(mBar, barParams());

        mStatus = new TextView(mActivity);
        mStatus.setText("Starting…");
        mStatus.setTextColor(TEXT_DIM);
        mStatus.setTextSize(14);
        mStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = linParams();
        statusParams.topMargin = dp(18);
        center.addView(mStatus, statusParams);

        mPercent = new TextView(mActivity);
        mPercent.setTextColor(TEXT_MAIN);
        mPercent.setTextSize(16);
        mPercent.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mPercent.setGravity(Gravity.CENTER);
        mPercent.setVisibility(View.GONE);
        LinearLayout.LayoutParams pctParams = linParams();
        pctParams.topMargin = dp(8);
        center.addView(mPercent, pctParams);

        mDetail = new TextView(mActivity);
        mDetail.setTextColor(TEXT_DIM);
        mDetail.setTextSize(11);
        mDetail.setGravity(Gravity.CENTER);
        mDetail.setVisibility(View.GONE);
        LinearLayout.LayoutParams detParams = linParams();
        detParams.topMargin = dp(6);
        center.addView(mDetail, detParams);

        root.addView(center, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private GradientDrawable buildProgressDrawable() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(0xFF232630);
        return bg;
    }

    private LinearLayout.LayoutParams linParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams barParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        return p;
    }

    // ------------------------------------------------------------------
    public void setStatus(final String s) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatus.setText(s);
            }
        });
    }

    public void showDownloadUi(final long totalBytes) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatus.setText("Downloading game data");
                mBar.setIndeterminate(false);
                mBar.setMax(1000);
                mPercent.setVisibility(View.VISIBLE);
                mDetail.setVisibility(View.VISIBLE);
                mDetail.setText(formatBytes(0) + " / " + formatBytes(totalBytes));
                startPolling();
            }
        });
    }

    public void updateProgress(final long done, final long total, final String file) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (total > 0) {
                    int permille = (int) Math.min(1000, done * 1000 / total);
                    mBar.setProgress(permille);
                    mPercent.setText(String.format("%.1f%%", permille / 10f));
                    mDetail.setText(formatBytes(done) + " / " + formatBytes(total)
                            + (file == null || file.isEmpty() ? "" : "\n" + file));
                } else if (done > 0) {
                    mDetail.setText(formatBytes(done) + " downloaded"
                            + (file == null || file.isEmpty() ? "" : "\n" + file));
                }
            }
        });
    }

    public void hideDownloadUi() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopPolling();
                mBar.setIndeterminate(true);
                mPercent.setVisibility(View.GONE);
                mDetail.setVisibility(View.GONE);
                mStatus.setText("Reconnecting…");
            }
        });
    }

    public void onUpdateComplete() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopPolling();
                mStatus.setText("Starting engine…");
                mRoot.setVisibility(View.GONE);
            }
        });
    }

    public void showErrorWithRetry(String message, final Runnable retryAction) {
        stopPolling();
        new AlertDialog.Builder(mActivity)
                .setTitle("Grand Horizon RP")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mRoot.setVisibility(View.VISIBLE);
                        retryAction.run();
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mActivity.finish();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------
    private void startPolling() {
        stopPolling();
        mPollTask = new Runnable() {
            @Override
            public void run() {
                if (mController != null) {
                    mController.pollProgress();
                }
                if (mPollTask != null) {
                    mRoot.postDelayed(this, 1000);
                }
            }
        };
        mRoot.post(mPollTask);
    }

    private void stopPolling() {
        mPollTask = null;
    }

    private int dp(int v) {
        return Math.round(v * mActivity.getResources().getDisplayMetrics().density);
    }

    private String formatBytes(long b) {
        if (b >= 1L << 30) return String.format("%.2f GB", b / (float) (1L << 30));
        if (b >= 1L << 20) return String.format("%.1f MB", b / (float) (1L << 20));
        if (b >= 1L << 10) return String.format("%.0f KB", b / (float) (1L << 10));
        return b + " B";
    }
}
