package com.betasafe.app.browser;

import android.webkit.WebView;

/** Installs an early CSS shield so media is obscured before frame inference catches up. */
public final class DomController {
    private static final String INJECTION =
            "(function(){try{" +
            "if(window.__betasafeInstalled)return;window.__betasafeInstalled=true;" +
            "var s=document.createElement('style');s.id='__betasafe_preblur';" +
            "s.textContent='img,video,picture>*,[role=\\\"img\\\"]{" +
            "filter:blur(24px) brightness(.82)!important;transition:filter 120ms linear}';" +
            "(document.head||document.documentElement).appendChild(s);" +
            "var h=(location.hostname||'').toLowerCase(),rules=[];" +
            "if(h.indexOf('reddit.com')>=0)rules.push('shreddit-post[post-type=\\\"image\\\"] img');" +
            "if(h.indexOf('x.com')>=0||h.indexOf('twitter.com')>=0)" +
            "rules.push('div[data-testid=\\\"sensitiveMediaBoundary\\\"]');" +
            "if(rules.length){var r=document.createElement('style');" +
            "r.textContent=rules.join(',')+'{display:none!important}';" +
            "(document.head||document.documentElement).appendChild(r);}" +
            "}catch(e){console.log('BetaSafe DOM shield',e);}})();";

    private DomController() {}

    public static void inject(WebView webView) {
        webView.evaluateJavascript(INJECTION, null);
    }
}
