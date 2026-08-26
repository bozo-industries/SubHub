package com.subhub.app.update;

import android.content.Context;
import android.os.Build;

import com.subhub.app.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only client for SubHub's public GitHub Releases feed. */
public final class GitHubReleaseRepository {
    static final String RELEASES_URL = "https://api.github.com/repos/bozo-industries/SubHub/releases?per_page=30";
    private static final int MAX_RESPONSE = 2 * 1024 * 1024;
    private final UpdateStateStore state;

    public enum Failure { OFFLINE, RATE_LIMITED, SERVER, INVALID_RELEASE }

    public static final class Result {
        public final UpdateCandidate candidate;
        public final Failure failure;
        public final String detail;
        private Result(UpdateCandidate candidate, Failure failure, String detail) {
            this.candidate = candidate;
            this.failure = failure;
            this.detail = detail;
        }
        public static Result success(UpdateCandidate candidate) { return new Result(candidate, null, ""); }
        public static Result failure(Failure failure, String detail) { return new Result(null, failure, detail); }
        public boolean succeeded() { return failure == null; }
    }

    static final class Release {
        final SemanticVersion version;
        final String tag;
        final String body;
        final String htmlUrl;
        final String manifestUrl;
        Release(SemanticVersion version, String tag, String body, String htmlUrl, String manifestUrl) {
            this.version = version;
            this.tag = tag;
            this.body = body;
            this.htmlUrl = htmlUrl;
            this.manifestUrl = manifestUrl;
        }
    }

    public GitHubReleaseRepository(Context context) {
        state = new UpdateStateStore(context);
    }

    public Result check() {
        try {
            HttpResponse response = get(RELEASES_URL, state.etag(), MAX_RESPONSE);
            if (response.code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                state.setLastCheck(System.currentTimeMillis());
                UpdateCandidate cached = state.candidate();
                return Result.success(cached != null && cached.manifest.versionCode > BuildConfig.VERSION_CODE
                        ? cached : null);
            }
            if (response.code == 403 && "0".equals(response.rateRemaining)) {
                return Result.failure(Failure.RATE_LIMITED, "GitHub API rate limit reached");
            }
            if (response.code < 200 || response.code >= 300) {
                return Result.failure(Failure.SERVER, "GitHub returned HTTP " + response.code);
            }
            JSONArray releases = new JSONArray(response.body);
            List<Release> candidates = parseReleases(releases);
            candidates.sort(Comparator.comparing((Release value) -> value.version).reversed());
            SemanticVersion installed = SemanticVersion.parse(BuildConfig.VERSION_NAME);
            UpdateCandidate available = null;
            for (Release release : candidates) {
                if (release.version.compareTo(installed) <= 0 || release.manifestUrl.isEmpty()) continue;
                HttpResponse manifestResponse = get(release.manifestUrl, "", 1024 * 1024);
                if (manifestResponse.code < 200 || manifestResponse.code >= 300) continue;
                UpdateManifest manifest = UpdateManifest.parse(manifestResponse.body);
                if (!release.tag.equals(manifest.tag) || !manifest.isCompatible(BuildConfig.VERSION_CODE)
                        || manifest.selectAsset(Build.SUPPORTED_ABIS) == null) continue;
                String notes = releaseNotes(manifest, release.body);
                available = new UpdateCandidate(manifest, notes, release.htmlUrl);
                break;
            }
            state.setEtag(response.etag);
            state.setLastCheck(System.currentTimeMillis());
            if (available != null) state.setCandidate(available);
            else {
                state.clearCandidate();
                state.clearDownload(true);
            }
            return Result.success(available);
        } catch (java.net.UnknownHostException | java.net.SocketTimeoutException exception) {
            return Result.failure(Failure.OFFLINE, exception.getClass().getSimpleName());
        } catch (Exception exception) {
            return Result.failure(Failure.INVALID_RELEASE, exception.getClass().getSimpleName());
        }
    }

    static List<Release> parseReleases(JSONArray releases) {
        List<Release> parsed = new ArrayList<>();
        for (int index = 0; index < releases.length(); index++) {
            JSONObject release = releases.optJSONObject(index);
            if (release == null || release.optBoolean("draft", true)) continue;
            String tag = release.optString("tag_name", "");
            SemanticVersion version;
            try { version = SemanticVersion.parse(tag); }
            catch (IllegalArgumentException ignored) { continue; }
            String expectedManifest = "SubHub-" + tag.substring(1) + "-update.json";
            String manifestUrl = "";
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) for (int assetIndex = 0; assetIndex < assets.length(); assetIndex++) {
                JSONObject asset = assets.optJSONObject(assetIndex);
                if (asset != null && expectedManifest.equals(asset.optString("name"))) {
                    manifestUrl = asset.optString("browser_download_url", "");
                    break;
                }
            }
            parsed.add(new Release(version, tag, release.optString("body", ""),
                    release.optString("html_url", ""), manifestUrl));
        }
        return parsed;
    }

    static String releaseNotes(UpdateManifest manifest, String releaseBody) {
        return manifest.releaseNotes.isEmpty()
                ? ReleaseNotesFormatter.changelogOnly(releaseBody) : manifest.releaseNotes;
    }

    static final class HttpResponse {
        final int code;
        final String body;
        final String etag;
        final String rateRemaining;
        HttpResponse(int code, String body, String etag, String rateRemaining) {
            this.code = code;
            this.body = body;
            this.etag = etag;
            this.rateRemaining = rateRemaining;
        }
    }

    static HttpResponse get(String address, String etag, int limit) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
        connection.setRequestProperty("User-Agent", "SubHub-Android/" + BuildConfig.VERSION_NAME);
        if (etag != null && !etag.isEmpty()) connection.setRequestProperty("If-None-Match", etag);
        int code = connection.getResponseCode();
        String body = "";
        if (code != HttpURLConnection.HTTP_NOT_MODIFIED) {
            java.io.InputStream raw = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (raw != null) try (BufferedInputStream input = new BufferedInputStream(raw);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (output.size() + count > Math.min(limit, MAX_RESPONSE)) {
                        throw new IllegalStateException("Response too large");
                    }
                    output.write(buffer, 0, count);
                }
                body = output.toString(StandardCharsets.UTF_8.name());
            }
        }
        HttpResponse response = new HttpResponse(code, body, connection.getHeaderField("ETag"),
                connection.getHeaderField("X-RateLimit-Remaining"));
        connection.disconnect();
        return response;
    }
}
