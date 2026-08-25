package com.subhub.app.update;

import org.json.JSONException;
import org.json.JSONObject;

/** A compatible GitHub release and its verified-shape update manifest. */
public final class UpdateCandidate {
    public final UpdateManifest manifest;
    public final String notes;
    public final String releaseUrl;

    public UpdateCandidate(UpdateManifest manifest, String notes, String releaseUrl) {
        this.manifest = manifest;
        this.notes = notes == null ? "" : notes;
        this.releaseUrl = releaseUrl == null ? "" : releaseUrl;
    }

    public String json() throws JSONException {
        return new JSONObject().put("manifest", new JSONObject(manifest.json()))
                .put("notes", notes).put("releaseUrl", releaseUrl).toString();
    }

    public static UpdateCandidate parse(String value) throws JSONException {
        JSONObject json = new JSONObject(value);
        return new UpdateCandidate(UpdateManifest.parse(json.getJSONObject("manifest").toString()),
                json.optString("notes", ""), json.optString("releaseUrl", ""));
    }
}
