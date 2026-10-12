package com.simfat.backend.web;

import java.util.regex.Pattern;

/**
 * Strict format gate for the public detail route's {@code comunaId} (GADM GID_3 as stored in the
 * seed, e.g. {@code CHL.6.1.1_1}). Anchored, ASCII-only and length-bounded, so nothing outside
 * the closed id space ever reaches a repository call or an error message.
 */
public final class MapbiomasComunaId {

    private static final Pattern GID_3 = Pattern.compile("^CHL\\.[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{1,2}_1$");

    private MapbiomasComunaId() {
    }

    public static boolean isValid(String raw) {
        return raw != null && GID_3.matcher(raw).matches();
    }
}
