package com.subhub.app.browser;

import android.webkit.WebView;

/** One in-memory WebView tab. Private tabs disable persistent browser features where WebView allows. */
final class BrowserTab {
    final int id;
    final WebView webView;
    final boolean privateTab;
    String title = "New tab";
    String url = "about:blank";

    BrowserTab(int id, WebView webView, boolean privateTab) {
        this.id = id;
        this.webView = webView;
        this.privateTab = privateTab;
    }
}
