package com.winlator.core;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.winlator.R;
import com.winlator.math.Mathf;

public class DownloadProgressDialog {
    private final Activity activity;
    private Dialog dialog;

    public DownloadProgressDialog(Activity activity) {
        this.activity = activity;
    }

    private void create() {
        dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.download_progress_dialog);

        Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
        
        // 默认隐藏取消按钮
        dialog.findViewById(R.id.LLBottomBar).setVisibility(View.GONE);
    }

    public void show() {
        show(null);
    }

    public void show(int textResId) {
        show(textResId, null);
    }

    public void show(Runnable onCancelCallback) {
        show(0, onCancelCallback);
    }

    public void show(int textResId, final Runnable onCancelCallback) {
        if (isShowing()) return;
        close();
        create(); // 每次都重新创建，确保布局正确初始化

        if (textResId > 0) ((TextView)dialog.findViewById(R.id.TextView)).setText(textResId);

        setProgress(0);
        android.util.Log.d("DownloadProgress", "onCancelCallback is null: " + (onCancelCallback == null));
        if (onCancelCallback != null) {
            android.util.Log.d("DownloadProgress", "Setting up cancel button");
            dialog.findViewById(R.id.BTCancel).setOnClickListener((v) -> {
                android.util.Log.d("DownloadProgress", "Cancel button clicked");
                onCancelCallback.run();
            });
            dialog.findViewById(R.id.LLBottomBar).setVisibility(View.VISIBLE);
            android.util.Log.d("DownloadProgress", "Bottom bar visibility set to VISIBLE");
        }
        dialog.show();
        android.util.Log.d("DownloadProgress", "Dialog shown");
    }

    public void setProgress(int progress) {
        if (dialog == null) return;
        progress = Mathf.clamp(progress, 0, 100);
        ((CircularProgressIndicator)dialog.findViewById(R.id.CircularProgressIndicator)).setProgress(progress);
        ((TextView)dialog.findViewById(R.id.TVProgress)).setText(progress+"%");
    }

    public void close() {
        try {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null; // 重置为 null，确保下次 show 时重新创建
            }
        }
        catch (Exception e) {}
    }

    public void closeOnUiThread() {
        activity.runOnUiThread(this::close);
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}
