package org.simulpiscator.billboardolino;

import android.os.Looper;
import android.view.View;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;

class WebRenderer {
    private static final String TAG = "bbl:WebRenderer";
    private final WebView mView;
    private final String mError;

    WebRenderer(final Context context, final String url, int width, int height, boolean js) {
        if(Looper.myLooper() == null)
            Looper.prepare();
        mView = new WebView(context.getApplicationContext());

        mView.getSettings().setLoadWithOverviewMode(true);
        mView.getSettings().setUseWideViewPort(false);
        mView.getSettings().setJavaScriptEnabled(js);
        mView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

        int wspec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int hspec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        mView.measure(wspec, hspec);
        mView.layout(0, 0, width, height);

        final StringBuilder error = new StringBuilder();
        mView.loadUrl(url);
        mView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                error.append(description);
                Looper.myLooper().quit();
            }
        });
        mView.setPictureListener(new WebView.PictureListener() {
            @Override
            public void onNewPicture(WebView view, Picture picture) {
                Looper.myLooper().quit();
            }
        });
        Looper.loop();
        mError = error.toString();
    }

    String getError() {
        return mError;
    }
    void render(Canvas canvas) {
        mView.draw(canvas);
    }

}
