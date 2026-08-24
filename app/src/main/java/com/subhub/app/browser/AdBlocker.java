package com.subhub.app.browser;

import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Small deterministic domain filter for common ad, tracker, and adult-ad networks. */
public final class AdBlocker {
    private static final Set<String> BLOCKED = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "connect.facebook.net", "ads.pubmatic.com",
            "adnxs.com", "adsrvr.org", "rubiconproject.com", "openx.net",
            "criteo.com", "outbrain.com", "taboola.com", "mgid.com",
            "popads.net", "popcash.net", "propellerads.com", "amazon-adsystem.com",
            "scorecardresearch.com", "quantserve.com", "hotjar.com", "mixpanel.com",
            "segment.com", "mouseflow.com", "crazyegg.com", "exoclick.com",
            "juicyads.com", "trafficjunky.com", "trafficstars.com", "exosrv.com",
            "tsyndicate.com", "hilltopads.net", "clickadu.com"));

    private AdBlocker() {}

    public static boolean shouldBlock(String value) {
        try {
            String host = new URI(value).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String domain : BLOCKED) {
                if (host.equals(domain) || host.endsWith('.' + domain)) return true;
            }
        } catch (Exception ignored) {
            // Invalid URLs are handled by the WebView client rather than the filter.
        }
        return false;
    }

    public static WebResourceResponse emptyResponse() {
        return new WebResourceResponse(
                "text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }
}
