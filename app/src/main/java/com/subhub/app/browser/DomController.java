package com.subhub.app.browser;

import android.webkit.WebView;

/** Installs an early CSS shield so media is obscured before frame inference catches up. */
public final class DomController {
    private DomController() {}

    public static void inject(
            WebView webView, boolean preBlur, boolean hideBackgrounds, boolean siteFilters) {
        String mediaRule = preBlur
                ? "img,video,picture>*,[role=\\\"img\\\"]{filter:blur(24px) brightness(.82)!important;transition:filter 120ms linear}"
                : "";
        String backgroundRule = hideBackgrounds
                ? "*[style*=\\\"background-image\\\"]{background-image:none!important}"
                : "";
        String siteRule = siteFilters
                ? "var h=(location.hostname||'').toLowerCase(),rules=[];"
                    + "if(h.indexOf('reddit.com')>=0)rules.push('shreddit-post[post-type=\\\"image\\\"] img');"
                    + "if(h.indexOf('x.com')>=0||h.indexOf('twitter.com')>=0)rules.push('div[data-testid=\\\"sensitiveMediaBoundary\\\"]');"
                    + "if(rules.length)s.textContent+=rules.join(',')+'{display:none!important}';"
                : "";
        String injection = "(function(){try{"
                + "var old=document.getElementById('__subhub_shield');if(old)old.remove();"
                + "var s=document.createElement('style');s.id='__subhub_shield';"
                + "s.textContent=" + org.json.JSONObject.quote(mediaRule + backgroundRule) + ";"
                + siteRule
                + "(document.head||document.documentElement).appendChild(s);"
                + "}catch(e){console.log('SubHub DOM shield',e);}})();";
        webView.evaluateJavascript(injection, null);
    }
}
