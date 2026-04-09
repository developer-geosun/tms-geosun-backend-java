package com.geosun.tms.auth.security.crypto;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Генерація непрозорого refresh / verification токена (не JWT).
 */
public final class OpaqueTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    private OpaqueTokenGenerator() {
    }

    public static String generate() {
        byte[] buf = new byte[BYTES];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
