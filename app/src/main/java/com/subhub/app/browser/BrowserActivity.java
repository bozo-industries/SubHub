package com.subhub.app.browser;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.subhub.app.R;
import com.subhub.app.capture.CensorRenderer;
import com.subhub.app.commitment.CommitmentActivity;
import com.subhub.app.commitment.CommitmentManager;
import com.subhub.app.databinding.ActivityBrowserBinding;
import com.subhub.app.detection.DetectionEngine;
import com.subhub.app.security.ControllerEditMode;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.settings.SettingsRepository;
import com.subhub.app.stats.StatsRepository;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Hardened local browser with tabs, privacy tabs, shields, bookmarks, and safe downloads. */
public final class BrowserActivity extends AppCompatActivity {
    private static final int MAX_TABS = 12;
    private static final int MAX_IMAGE_DOWNLOAD_BYTES = 25 * 1024 * 1024;
    private static final String KEY_PREBLUR = "browser_preblur";
    private static final String KEY_HIDE_BACKGROUNDS = "browser_hide_backgrounds";
    private static final String KEY_SITE_FILTERS = "browser_site_filters";
    private static final String KEY_SUGGESTIONS = "browser_online_suggestions";
    private static final String KEY_PRIVATE_NOTICE_SEEN = "browser_private_notice_seen";

    private final List<BrowserTab> tabs = new ArrayList<>();
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final AtomicInteger suggestionGeneration = new AtomicInteger();
    private ActivityBrowserBinding binding;
    private SharedPreferences preferences;
    private BookmarkStore bookmarks;
    private int nextTabId = 1;
    private int activeTabId = -1;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private ControllerEditMode editMode;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBrowserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferences = getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
        bookmarks = new BookmarkStore(this);
        new StatsRepository(this).recordBrowserSession();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });

        binding.buttonNewTab.setOnClickListener(
                view -> createTab(false, "https://www.google.com"));
        binding.buttonPrivateTab.setOnClickListener(
                view -> createPrivateTab());
        binding.buttonBack.setOnClickListener(view -> goBackOrFinish());
        binding.buttonForward.setOnClickListener(view -> {
            BrowserTab tab = activeTab();
            if (tab != null && tab.webView.canGoForward()) tab.webView.goForward();
        });
        binding.buttonGo.setOnClickListener(view -> navigate());
        binding.buttonBookmarks.setOnClickListener(view -> showBookmarks());
        binding.buttonShields.setOnClickListener(view -> showShieldSettings());
        editMode = ControllerEditMode.bind(
                this, binding.buttonEditLock, editing -> { });
        binding.address.setOnItemClickListener((parent, view, position, id) -> navigate());
        binding.address.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate();
                return true;
            }
            return false;
        });
        binding.address.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                requestSuggestions(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { goBackOrFinish(); }
        });
        createTab(false, "https://www.google.com");
    }

    @Override protected void onResume() {
        super.onResume();
        if (editMode != null) editMode.refresh();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createTab(boolean privateTab, String initialUrl) {
        if (tabs.size() >= MAX_TABS) {
            Toast.makeText(this, R.string.browser_tab_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        WebView webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 13, 20));
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        BrowserTab tab = new BrowserTab(nextTabId++, webView, privateTab);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(!privateTab);
        settings.setDatabaseEnabled(!privateTab);
        settings.setSaveFormData(!privateTab);
        settings.setCacheMode(privateTab ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.setWebViewClient(new SafeClient(tab));
        webView.setWebChromeClient(new SafeChromeClient(tab));
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                handleDownload(url, userAgent, contentDisposition, mimeType));
        tabs.add(tab);
        binding.webContainer.addView(webView);
        switchTab(tab.id);
        webView.loadUrl(initialUrl);
    }

    private void switchTab(int id) {
        activeTabId = id;
        for (BrowserTab tab : tabs) {
            boolean active = tab.id == id;
            tab.webView.setVisibility(active ? View.VISIBLE : View.GONE);
            if (active) {
                binding.address.setText(tab.url.equals("about:blank") ? "" : tab.url);
                binding.browserTitle.setText(tab.title);
                binding.privateNotice.setVisibility(tab.privateTab ? View.VISIBLE : View.GONE);
            }
        }
        refreshTabStrip();
    }

    private void createPrivateTab() {
        if (preferences.getBoolean(KEY_PRIVATE_NOTICE_SEEN, false)) {
            createTab(true, "https://www.google.com");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_private_title)
                .setMessage(R.string.browser_private_explanation)
                .setPositiveButton(R.string.browser_private_open, (dialog, which) -> {
                    preferences.edit().putBoolean(KEY_PRIVATE_NOTICE_SEEN, true).apply();
                    createTab(true, "https://www.google.com");
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void closeTab(int id) {
        BrowserTab closing = null;
        int index = -1;
        for (int current = 0; current < tabs.size(); current++) {
            if (tabs.get(current).id == id) {
                closing = tabs.get(current);
                index = current;
                break;
            }
        }
        if (closing == null) return;
        binding.webContainer.removeView(closing.webView);
        tabs.remove(closing);
        destroyTab(closing);
        if (tabs.isEmpty()) {
            createTab(false, "https://www.google.com");
        } else if (activeTabId == id) {
            switchTab(tabs.get(Math.max(0, Math.min(index, tabs.size() - 1))).id);
        } else {
            if (binding != null) refreshTabStrip();
        }
    }

    private void refreshTabStrip() {
        binding.tabStrip.removeAllViews();
        for (BrowserTab tab : tabs) {
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(10), 0, dp(4), 0);
            chip.setBackgroundResource(tab.id == activeTabId
                    ? R.drawable.bg_tab_active : android.R.color.transparent);
            TextView title = new TextView(this);
            String shortTitle = shorten(tab.title, 18);
            title.setText(tab.privateTab
                    ? getString(R.string.browser_tab_private_title, shortTitle) : shortTitle);
            title.setTextColor(getColor(tab.id == activeTabId
                    ? R.color.text_primary : R.color.text_muted));
            title.setTextSize(11);
            title.setSingleLine(true);
            title.setMinWidth(dp(74));
            title.setOnClickListener(view -> switchTab(tab.id));
            TextView close = new TextView(this);
            close.setText("×");
            close.setTextColor(getColor(R.color.accent));
            close.setTextSize(18);
            close.setGravity(android.view.Gravity.CENTER);
            close.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(36)));
            close.setOnClickListener(view -> closeTab(tab.id));
            chip.addView(title);
            chip.addView(close);
            binding.tabStrip.addView(chip);
        }
    }

    private void navigate() {
        BrowserTab tab = activeTab();
        if (tab == null) return;
        tab.webView.loadUrl(BrowserUrl.fromInput(binding.address.getText().toString()));
    }

    private void goBackOrFinish() {
        if (fullscreenView != null) {
            hideFullscreen();
            return;
        }
        BrowserTab tab = activeTab();
        if (tab != null && tab.webView.canGoBack()) tab.webView.goBack();
        else finish();
    }

    private BrowserTab activeTab() {
        for (BrowserTab tab : tabs) if (tab.id == activeTabId) return tab;
        return null;
    }

    private void showBookmarks() {
        List<BookmarkStore.Bookmark> values = bookmarks.load();
        String[] labels = new String[values.size()];
        for (int index = 0; index < values.size(); index++) {
            labels[index] = values.get(index).title + "\n" + values.get(index).url;
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.browser_bookmarks)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.browser_add_bookmark, (ignored, which) -> {
                    BrowserTab tab = activeTab();
                    if (tab != null && BrowserUrl.isWebUrl(tab.url)) {
                        bookmarks.add(tab.title, tab.url);
                    }
                });
        if (values.isEmpty()) {
            dialog.setMessage(R.string.browser_no_bookmarks);
        } else {
            dialog.setItems(labels, (ignored, which) -> {
                BrowserTab tab = activeTab();
                if (tab != null) tab.webView.loadUrl(values.get(which).url);
            });
            dialog.setNeutralButton(R.string.browser_remove_bookmark,
                    (ignored, which) -> showBookmarkRemoval());
        }
        dialog.show();
    }

    private void showBookmarkRemoval() {
        List<BookmarkStore.Bookmark> values = bookmarks.load();
        if (values.isEmpty()) return;
        String[] labels = new String[values.size()];
        for (int index = 0; index < values.size(); index++) labels[index] = values.get(index).title;
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_remove_bookmark)
                .setItems(labels, (dialog, which) -> bookmarks.remove(values.get(which).url))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showShieldSettings() {
        if (CommitmentManager.isActive(this)) {
            startActivity(new Intent(this, CommitmentActivity.class));
            return;
        }
        String[] labels = getResources().getStringArray(R.array.browser_shield_options);
        boolean[] checked = {
                preferences.getBoolean(KEY_PREBLUR, true),
                preferences.getBoolean(KEY_HIDE_BACKGROUNDS, false),
                preferences.getBoolean(KEY_SITE_FILTERS, true),
                preferences.getBoolean(KEY_SUGGESTIONS, false)};
        if (!ControllerPinManager.isSessionUnlocked()) {
            StringBuilder summary = new StringBuilder();
            for (int index = 0; index < labels.length; index++) {
                if (summary.length() > 0) summary.append('\n');
                summary.append(checked[index] ? "✓  " : "○  ").append(labels[index]);
            }
            summary.append("\n\n").append(getString(R.string.controller_edit_lock_hint));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.browser_shields)
                    .setMessage(summary.toString())
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_shields)
                .setMultiChoiceItems(labels, checked, (dialog, which, value) -> checked[which] = value)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    preferences.edit()
                            .putBoolean(KEY_PREBLUR, checked[0])
                            .putBoolean(KEY_HIDE_BACKGROUNDS, checked[1])
                            .putBoolean(KEY_SITE_FILTERS, checked[2])
                            .putBoolean(KEY_SUGGESTIONS, checked[3])
                            .apply();
                    BrowserTab tab = activeTab();
                    if (tab != null) injectShields(tab.webView);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void injectShields(WebView webView) {
        DomController.inject(
                webView,
                preferences.getBoolean(KEY_PREBLUR, true),
                preferences.getBoolean(KEY_HIDE_BACKGROUNDS, false),
                preferences.getBoolean(KEY_SITE_FILTERS, true));
    }

    private void requestSuggestions(String query) {
        int generation = suggestionGeneration.incrementAndGet();
        BrowserTab tab = activeTab();
        String value = query.trim();
        if (!preferences.getBoolean(KEY_SUGGESTIONS, false) || tab == null || tab.privateTab
                || value.length() < 2 || value.contains(".") || value.contains("://")) return;
        worker.execute(() -> {
            List<String> values = fetchSuggestions(value);
            runOnUiThread(() -> {
                if (binding == null || generation != suggestionGeneration.get()) return;
                binding.address.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_dropdown_item_1line, values));
                if (binding.address.hasFocus() && !values.isEmpty()) binding.address.showDropDown();
            });
        });
    }

    private List<String> fetchSuggestions(String query) {
        List<String> values = new ArrayList<>();
        HttpURLConnection connection = null;
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            connection = (HttpURLConnection) new URL(
                    "https://suggestqueries.google.com/complete/search?client=firefox&q="
                            + encoded).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("User-Agent", "SubHub/1.0");
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int read;
                while ((read = reader.read(buffer)) >= 0 && response.length() < 65536) {
                    response.append(buffer, 0, Math.min(read, 65536 - response.length()));
                }
            }
            JSONArray suggestions = new JSONArray(response.toString()).optJSONArray(1);
            if (suggestions != null) {
                for (int index = 0; index < Math.min(5, suggestions.length()); index++) {
                    String item = suggestions.optString(index, "").trim();
                    if (!item.isEmpty()) values.add(item);
                }
            }
        } catch (Exception ignored) {
            // Suggestions are optional and never block navigation.
        } finally {
            if (connection != null) connection.disconnect();
        }
        return values;
    }

    private void handleDownload(
            String url, String userAgent, String contentDisposition, String mimeType) {
        if (!BrowserUrl.isWebUrl(url)) return;
        boolean image = (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/"))
                || url.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp)(\\?.*)?$");
        if (!image) {
            enqueueDownload(url, userAgent, contentDisposition, mimeType);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_image_download)
                .setItems(new String[]{getString(R.string.browser_censor_and_save),
                                getString(R.string.browser_download_original)},
                        (dialog, which) -> {
                            if (which == 0) censorAndSaveDownload(url, userAgent);
                            else enqueueDownload(url, userAgent, contentDisposition, mimeType);
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void enqueueDownload(
            String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String filename = safeFilename(URLUtil.guessFileName(url, contentDisposition, mimeType));
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle(filename)
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (mimeType != null) request.setMimeType(mimeType);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, "SubHub/" + filename);
            } else {
                request.setDestinationInExternalFilesDir(
                        this, Environment.DIRECTORY_DOWNLOADS, filename);
            }
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
            Toast.makeText(this, R.string.browser_download_started, Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, R.string.browser_download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void censorAndSaveDownload(String url, String userAgent) {
        Toast.makeText(this, R.string.browser_censoring_download, Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            Bitmap source = null;
            Bitmap output = null;
            try {
                byte[] bytes = downloadBounded(url, userAgent);
                source = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (source == null) throw new IllegalArgumentException("Not an image");
                try (DetectionEngine engine = new DetectionEngine(
                             this, new SettingsRepository(this).loadDetectorConfig());
                     CensorRenderer renderer = new CensorRenderer(this)) {
                    engine.initialize();
                    CensorRenderer.RenderResult result = renderer.renderWithDetection(source, engine);
                    output = result.getBitmap();
                    saveCensoredDownload(output);
                }
                new StatsRepository(this).addExportedImages(1);
                runOnUiThread(() -> Toast.makeText(
                        this, R.string.browser_censored_saved, Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        this, R.string.browser_download_failed, Toast.LENGTH_LONG).show());
            } finally {
                if (output != null && !output.isRecycled()) output.recycle();
                if (source != null && !source.isRecycled()) source.recycle();
            }
        });
    }

    private byte[] downloadBounded(String url, String userAgent) throws Exception {
        String cookies = CookieManager.getInstance().getCookie(url);
        return BoundedHttpClient.download(
                url, userAgent, cookies, MAX_IMAGE_DOWNLOAD_BYTES);
    }

    private void saveCensoredDownload(Bitmap bitmap) throws Exception {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        String name = "SubHub_Browser_" + timestamp + ".jpg";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/SubHub/Browser");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri destination = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (destination == null) throw new IllegalStateException("No gallery destination");
            boolean complete = false;
            try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                if (output == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    throw new IllegalStateException("Encode failed");
                }
                complete = true;
            } finally {
                if (complete) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(destination, ready, null, null);
                } else getContentResolver().delete(destination, null, null);
            }
            return;
        }
        File directory = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "SubHub/Browser");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("No output folder");
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw new IllegalStateException("Encode failed");
            }
        }
    }

    void showFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (fullscreenView != null) hideFullscreen();
        fullscreenView = view;
        fullscreenCallback = callback;
        binding.browserContent.setVisibility(View.GONE);
        binding.browserRoot.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void hideFullscreen() {
        if (fullscreenView == null) return;
        binding.browserRoot.removeView(fullscreenView);
        fullscreenView = null;
        binding.browserContent.setVisibility(View.VISIBLE);
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
    }

    private void destroyTab(BrowserTab tab) {
        tab.webView.stopLoading();
        if (tab.privateTab) {
            tab.webView.clearHistory();
            tab.webView.clearCache(true);
            tab.webView.clearFormData();
        }
        tab.webView.setWebChromeClient(null);
        tab.webView.setWebViewClient(null);
        tab.webView.destroy();
    }

    @Override
    protected void onDestroy() {
        hideFullscreen();
        worker.shutdownNow();
        for (BrowserTab tab : new ArrayList<>(tabs)) destroyTab(tab);
        tabs.clear();
        if (binding != null) binding.webContainer.removeAllViews();
        binding = null;
        super.onDestroy();
    }

    private final class SafeClient extends WebViewClient {
        private final BrowserTab tab;

        SafeClient(BrowserTab tab) { this.tab = tab; }

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
            tab.url = url;
            if (tab.id == activeTabId && binding != null) binding.address.setText(url);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) { injectShields(view); }

        @Override
        public void onPageFinished(WebView view, String url) {
            tab.url = url;
            injectShields(view);
            new StatsRepository(BrowserActivity.this).addBrowserPage();
            if (tab.id == activeTabId && binding != null) binding.address.setText(url);
        }
    }

    private final class SafeChromeClient extends WebChromeClient {
        private final BrowserTab tab;

        SafeChromeClient(BrowserTab tab) { this.tab = tab; }

        @Override
        public void onProgressChanged(WebView view, int progress) {
            if (tab.id != activeTabId || binding == null) return;
            binding.progress.setProgress(progress);
            binding.progress.setVisibility(progress >= 100 ? View.INVISIBLE : View.VISIBLE);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            tab.title = title == null || title.trim().isEmpty()
                    ? getString(R.string.browser_title) : title.trim();
            if (tab.id == activeTabId && binding != null) binding.browserTitle.setText(tab.title);
            if (binding != null) refreshTabStrip();
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            showFullscreen(view, callback);
        }

        @Override
        public void onHideCustomView() { hideFullscreen(); }
    }

    private static String safeFilename(String value) {
        String cleaned = value == null ? "download" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isEmpty()) return "download";
        return cleaned.length() <= 120 ? cleaned : cleaned.substring(cleaned.length() - 120);
    }

    private static String shorten(String value, int maximum) {
        String safe = value == null || value.trim().isEmpty() ? "New tab" : value.trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum - 1) + "…";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
