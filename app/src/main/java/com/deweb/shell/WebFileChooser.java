package com.deweb.shell;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebChromeClient;

/** Bridges a WebView <input type=file> to Android's system file picker. */
public class WebFileChooser {
    public static final int REQUEST = 0x5EED;
    private final Activity activity;
    private ValueCallback<Uri[]> callback;

    public WebFileChooser(Activity activity) {
        this.activity = activity;
    }

    public WebChromeClient chromeClient() {
        return new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                return show(filePathCallback, fileChooserParams);
            }
        };
    }

    private boolean show(ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams params) {
        if (callback != null) {
            callback.onReceiveValue(null);
            callback = null;
        }
        callback = filePathCallback;

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String type = "*/*";
        String[] accept = params == null ? null : params.getAcceptTypes();
        if (accept != null && accept.length > 0 && accept[0] != null && accept[0].contains("/")) {
            type = accept[0];
        }
        intent.setType(type);
        if (accept != null && accept.length > 1) {
            String[] mimes = new String[accept.length];
            int n = 0;
            for (String a : accept) {
                if (a != null && a.contains("/")) mimes[n++] = a;
            }
            if (n > 0) {
                String[] trimmed = new String[n];
                System.arraycopy(mimes, 0, trimmed, 0, n);
                intent.putExtra(Intent.EXTRA_MIME_TYPES, trimmed);
            }
        }
        if (params != null && params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        try {
            activity.startActivityForResult(Intent.createChooser(intent, "选择文件"), REQUEST);
            return true;
        } catch (Throwable t) {
            callback = null;
            filePathCallback.onReceiveValue(null);
            return false;
        }
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST || callback == null) return false;
        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        callback.onReceiveValue(result);
        callback = null;
        return true;
    }
}
