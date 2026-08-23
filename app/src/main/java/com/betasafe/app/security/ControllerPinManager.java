package com.betasafe.app.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.betasafe.app.settings.SettingsRepository;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted, app-local controller PIN with a short in-memory-style settings session. */
public final class ControllerPinManager {
    private static final String KEY_SALT = "controller_pin_salt";
    private static final String KEY_HASH = "controller_pin_hash";
    private static final int ITERATIONS = 150_000;
    private static final int KEY_BITS = 256;
    private static final long UNLOCK_WINDOW_MILLIS = 5L * 60L * 1_000L;
    private static volatile long unlockedUntilElapsedRealtime;

    private ControllerPinManager() {}

    public static boolean isConfigured(Context context) {
        SharedPreferences preferences = preferences(context);
        return preferences.contains(KEY_SALT) && preferences.contains(KEY_HASH);
    }

    public static boolean setPin(Context context, String pin) {
        String value = normalize(pin);
        if (!value.matches("[0-9]{4,12}")) return false;
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        try {
            byte[] hash = derive(value, salt);
            preferences(context).edit()
                    .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .commit();
            markUnlocked();
            return true;
        } catch (GeneralSecurityException exception) {
            return false;
        }
    }

    public static boolean verify(Context context, String pin) {
        if (!isConfigured(context)) return false;
        try {
            SharedPreferences preferences = preferences(context);
            byte[] salt = Base64.decode(preferences.getString(KEY_SALT, ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(preferences.getString(KEY_HASH, ""), Base64.NO_WRAP);
            byte[] actual = derive(normalize(pin), salt);
            boolean matches = MessageDigest.isEqual(expected, actual);
            if (matches) markUnlocked();
            return matches;
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            return false;
        }
    }

    public static boolean isSessionUnlocked() {
        return android.os.SystemClock.elapsedRealtime() < unlockedUntilElapsedRealtime;
    }

    public static void lockNow() {
        unlockedUntilElapsedRealtime = 0L;
    }

    private static void markUnlocked() {
        unlockedUntilElapsedRealtime = android.os.SystemClock.elapsedRealtime()
                + UNLOCK_WINDOW_MILLIS;
    }

    private static String normalize(String pin) {
        return pin == null ? "" : pin.trim();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(
                SettingsRepository.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static byte[] derive(String pin, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
