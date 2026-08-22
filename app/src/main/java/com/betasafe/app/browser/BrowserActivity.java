package com.betasafe.app.browser;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.betasafe.app.databinding.ActivityBrowserBinding;

/** Hardened single-tab browser with ad blocking and immediate DOM media shielding. */
public final class BrowserActivity extends AppCompatActivity {
    private ActivityBrowserBinding binding;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBrowserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WebSettings settings = binding.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, false);

        binding.webView.setWebViewClient(new SafeClient());
        binding.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                binding.progress.setProgress(progress);
                binding.progress.setVisibility(progress >= 100 ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                binding.browserTitle.setText(title == null || title.trim().isEmpty()
                        ? "SAFE BROWSER" : title);
            }
        });

        binding.buttonBack.setOnClickListener(view -> {
            if (binding.webView.canGoBack()) binding.webView.goBack();
            else finish();
        });
        binding.buttonForward.setOnClickListener(view -> {
            if (binding.webView.canGoForward()) binding.webView.goForward();
        });
        binding.buttonGo.setOnClickListener(view -> navigate());
        binding.address.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate();
                return true;
            }
            return false;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        binding.webView.loadUrl("https://www.google.com");
    }

    private void navigate() {
        binding.webView.loadUrl(BrowserUrl.fromInput(binding.address.getText().toString()));
    }

    @Override
    protected void onDestroy() {
        binding.webView.stopLoading();
        binding.webView.setWebChromeClient(null);
        binding.webView.setWebViewClient(null);
        binding.webView.destroy();
        binding = null;
        super.onDestroy();
    }

    private final class SafeClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !BrowserUrl.isWebUrl(request.getUrl().toString());
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(
                WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            return AdBlocker.shouldBlock(url) ? AdBlocker.emptyResponse() : null;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            binding.address.setText(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            binding.address.setText(url);
            DomController.inject(view);
        }
    }
}
