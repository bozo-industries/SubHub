package com.subhub.app.service;

import java.util.Objects;

/** Device-local calibration identity. Runtime window/node ids and page content are not keys. */
final class ScrollLearningKey {
    enum Axis { X, Y }
    enum Evidence { EXPLICIT, ABSOLUTE, INDEXED }

    final String packageName, surfaceDigest;
    final long appVersion;
    final int width, height, densityDpi, rotation, refreshMilliHz;
    final Axis axis;
    final Evidence evidence;

    ScrollLearningKey(String packageName, long appVersion, String surfaceDigest,
            int width, int height, int densityDpi, int rotation, int refreshMilliHz,
            Axis axis, Evidence evidence) {
        if (packageName == null || packageName.length() > 255
                || !packageName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) {
            throw new IllegalArgumentException("Invalid package identity");
        }
        if (surfaceDigest == null || !surfaceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A stable structural digest is required");
        }
        if (appVersion < 0 || width <= 0 || height <= 0 || densityDpi <= 0
                || rotation < 0 || rotation > 3 || refreshMilliHz < 10000 || refreshMilliHz > 1000000) {
            throw new IllegalArgumentException("Invalid display/app configuration");
        }
        this.packageName = packageName;
        this.appVersion = appVersion;
        this.surfaceDigest = surfaceDigest;
        this.width = width;
        this.height = height;
        this.densityDpi = densityDpi;
        this.rotation = rotation;
        this.refreshMilliHz = refreshMilliHz;
        this.axis = Objects.requireNonNull(axis);
        this.evidence = Objects.requireNonNull(evidence);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ScrollLearningKey)) return false;
        ScrollLearningKey key = (ScrollLearningKey) other;
        return packageName.equals(key.packageName) && appVersion == key.appVersion
                && surfaceDigest.equals(key.surfaceDigest) && width == key.width && height == key.height
                && densityDpi == key.densityDpi && rotation == key.rotation
                && refreshMilliHz == key.refreshMilliHz && axis == key.axis && evidence == key.evidence;
    }

    @Override public int hashCode() {
        return Objects.hash(packageName, appVersion, surfaceDigest, width, height, densityDpi,
                rotation, refreshMilliHz, axis, evidence);
    }
}
