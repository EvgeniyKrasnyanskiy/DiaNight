package com.diaclock.nightstand;

import android.util.Log;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for cryptographic operations.
 * Centralizes SHA-1 hashing logic previously duplicated in MainActivity and SettingsActivity.
 */
public final class CryptoUtils {

    private static final String TAG = "CryptoUtils";
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private CryptoUtils() {
        // Prevent instantiation
    }

    /**
     * Computes SHA-1 hash of the input string.
     * Used for hashing API Secret before sending to xDrip+ web service.
     *
     * @param input The string to hash
     * @return Lowercase hex-encoded SHA-1 hash, or the original input if hashing fails
     */
    public static String computeSHA1(String input) {
        if (input == null) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            char[] hexChars = new char[messageDigest.length * 2];
            for (int i = 0; i < messageDigest.length; i++) {
                int v = messageDigest[i] & 0xFF;
                hexChars[i * 2] = HEX_CHARS[v >>> 4];
                hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
            }
            return new String(hexChars);
        } catch (Exception e) {
            Log.e(TAG, "SHA-1 hashing failed: " + e.getMessage());
            return "";
        }
    }
}
