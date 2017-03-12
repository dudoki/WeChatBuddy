package com.weibuddy;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX;
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.modelmsg.WXVideoObject;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;
import com.weibuddy.util.ViewUtils;

import java.lang.ref.WeakReference;

public class WebActivity extends AppBaseCompatActivity {

    public static final String EXTRA_IS_SHOW_SHARING = "com.weibuddy.intent.extra.IS_SHOW_SHARING";

    private ProgressBar mProgressBar;
    private WebView mWebView;

    private Content mContent;
    private boolean mIsShowSharing;

    public static void start(Context context, Content content, boolean isShowSharing) {
        Intent intent = new Intent(context, WebActivity.class);
        intent.putExtra(Intent.EXTRA_REFERRER, content);
        intent.putExtra(EXTRA_IS_SHOW_SHARING, isShowSharing);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        setUpArguments();
        setUpViews();
    }

    private void setUpArguments() {
        final Intent intent = getIntent();
        mIsShowSharing = intent.getBooleanExtra(EXTRA_IS_SHOW_SHARING, false);
        mContent = intent.getParcelableExtra(Intent.EXTRA_REFERRER);
    }

    private void setUpViews() {
        Toolbar toolbar = ViewUtils.findViewById(this, R.id.toolbar);
        TextView title = ViewUtils.findViewById(this, R.id.title);
        ImageButton send = ViewUtils.findViewById(this, R.id.send);

        mProgressBar = ViewUtils.findViewById(this, R.id.progress);
        mWebView = ViewUtils.findViewById(this, R.id.web_view);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        title.setText(mContent.getName());
        send.setVisibility(mIsShowSharing ? View.VISIBLE : View.GONE);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doShare();
            }
        });

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setAllowFileAccess(true);

        mWebView.setSaveEnabled(false);
        mWebView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        mWebView.setWebViewClient(new XWebViewClient());
        mWebView.setWebChromeClient(new XWebChromeClient());
        mWebView.addJavascriptInterface(new JSInvokeJava(getApplication()), "android");
        mWebView.loadUrl(mContent.getContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWebView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mWebView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWebView.destroy();
    }

    private void doShare() {
        final IWXAPI mWXApi = WXAPIFactory.createWXAPI(this, BuildConfig.APP_KEY_WECHAT, false);
        mWXApi.registerApp(BuildConfig.APP_KEY_WECHAT);

        if (!mWXApi.isWXAppInstalled()) {
            Toast.makeText(this, R.string.wechat_app_not_installed, Toast.LENGTH_SHORT).show();
            return;
        }

        if (mContent == null) {
            Toast.makeText(this, R.string.data_is_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        Glide.with(this)
                .load(mContent.getVideoPic())
                .asBitmap()
                .override(100, 100)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                        WXVideoObject videoObj = new WXVideoObject();
                        videoObj.videoUrl = mContent.getContent();

                        WXMediaMessage msg = new WXMediaMessage();
                        msg.mediaObject = videoObj;
                        msg.title = mContent.getName();
                        msg.setThumbImage(resource);

                        final SendMessageToWX.Req req = new SendMessageToWX.Req();
                        req.scene = SendMessageToWX.Req.WXSceneSession;
                        req.message = msg;
                        req.transaction = String.valueOf(System.currentTimeMillis());

                        mWXApi.sendReq(req);
                    }
                });
    }

    private class XWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            mProgressBar.setProgress(newProgress);
        }
    }

    private class XWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            mProgressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            mProgressBar.setVisibility(View.GONE);
        }
    }

    public static class JSInvokeJava {

        private WeakReference<Context> contextRef;

        public JSInvokeJava(Context context) {
            this.contextRef = new WeakReference<>(context);
        }

        @JavascriptInterface
        public void onCallPhone(String phoneNumber) {
            final Context context = contextRef.get();
            if (context == null) {
                return;
            }

            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:".concat(phoneNumber)));
            context.startActivity(intent);
        }
    }
}
