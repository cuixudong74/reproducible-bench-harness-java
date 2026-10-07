package com.github.cuixudong74.reproduciblebenchharnessjava.benchmark.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 hashing for deterministic output values and result fingerprints. */
final class ResultHasher {
    private ResultHasher() {
    }

    static String sha256(String value) {
        if (value == null) {
            value = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    static String outputHash(Object output) {
        return sha256(String.valueOf(output));
    }
}
