package com.betasafe.app.profiles;

import android.content.Context;
import android.content.SharedPreferences;

import com.betasafe.app.settings.SettingsRepository;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Versioned, secret-free settings backup. Private images, models, and browsing data are excluded. */
public final class SettingsBackupManager {
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_BACKUP_BYTES = 256 * 1024;
    private final SharedPreferences settings;

    public SettingsBackupManager(Context context) {
        settings = context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void exportTo(OutputStream output) throws IOException {
        JSONObject root = new JSONObject();
        try {
            root.put("format", "betasafe-settings");
            root.put("version", FORMAT_VERSION);
            root.put("settings", ProfileManager.snapshot(settings));
        } catch (Exception impossible) {
            throw new IOException("Could not encode settings backup", impossible);
        }
        output.write(root.toString().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public boolean importFrom(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > MAX_BACKUP_BYTES) throw new IOException("Settings backup is too large");
            output.write(buffer, 0, read);
        }
        try {
            JSONObject root = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            if (!"betasafe-settings".equals(root.optString("format"))
                    || root.optInt("version") != FORMAT_VERSION) return false;
            JSONObject snapshot = root.optJSONObject("settings");
            return snapshot != null && ProfileManager.apply(settings, snapshot);
        } catch (Exception error) {
            return false;
        }
    }
}
