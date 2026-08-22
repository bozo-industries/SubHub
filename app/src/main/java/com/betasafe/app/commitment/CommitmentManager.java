package com.betasafe.app.commitment;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.betasafe.app.settings.SettingsRepository;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Bounded, app-local commitment pact. It never controls Android uninstall or emergency stops. */
public final class CommitmentManager {
    public static final long MIN_DURATION_MS = 30L * 60L * 1000L;
    public static final long MAX_DURATION_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final String KEY_ENDS_AT = "commitment_ends_at";
    private static final String KEY_STARTED_AT = "commitment_started_at";
    private static final String KEY_SALT = "commitment_code_salt";
    private static final String KEY_HASH = "commitment_code_hash";
    private static final String KEY_DURATION = "commitment_duration";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;

    private CommitmentManager() {}

    public static boolean start(Context context, long requestedDurationMs, String code) {
        String value = code == null ? "" : code.trim();
        if (value.length() < 4 || value.length() > 64) return false;
        long duration = Math.max(MIN_DURATION_MS,
                Math.min(MAX_DURATION_MS, requestedDurationMs));
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash;
        try {
            hash = derive(value, salt);
        } catch (GeneralSecurityException exception) {
            return false;
        }
        long now = System.currentTimeMillis();
        preferences(context).edit()
                .putLong(KEY_STARTED_AT, now)
                .putLong(KEY_ENDS_AT, now + duration)
                .putLong(KEY_DURATION, duration)
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply();
        return true;
    }

    public static boolean isActive(Context context) {
        SharedPreferences values = preferences(context);
        long end = values.getLong(KEY_ENDS_AT, 0L);
        if (end <= System.currentTimeMillis()) {
            if (end != 0L) release(context);
            return false;
        }
        if (!values.contains(KEY_HASH)) {
            release(context);
            return false;
        }
        return true;
    }

    public static long remainingMillis(Context context) {
        return isActive(context)
                ? Math.max(0L, preferences(context).getLong(KEY_ENDS_AT, 0L)
                        - System.currentTimeMillis())
                : 0L;
    }

    public static boolean verifyAndRelease(Context context, String code) {
        if (!isActive(context)) return true;
        SharedPreferences values = preferences(context);
        try {
            byte[] salt = Base64.decode(values.getString(KEY_SALT, ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(values.getString(KEY_HASH, ""), Base64.NO_WRAP);
            byte[] actual = derive(code == null ? "" : code.trim(), salt);
            if (!MessageDigest.isEqual(expected, actual)) return false;
            release(context);
            return true;
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            return false;
        }
    }

    /** The safety release is intentionally unconditional and must remain reachable in the UI. */
    public static void emergencyRelease(Context context) {
        release(context);
    }

    public static long originalDurationMillis(Context context) {
        return preferences(context).getLong(KEY_DURATION, 0L);
    }

    public static long startedAtMillis(Context context) {
        return preferences(context).getLong(KEY_STARTED_AT, 0L);
    }

    private static void release(Context context) {
        preferences(context).edit()
                .remove(KEY_STARTED_AT).remove(KEY_ENDS_AT).remove(KEY_DURATION)
                .remove(KEY_SALT).remove(KEY_HASH).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static byte[] derive(String code, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(code.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
