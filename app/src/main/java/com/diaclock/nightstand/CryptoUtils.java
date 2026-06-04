package com.diaclock.nightstand;

import android.util.Log;

/**
 * Utility class for cryptographic operations.
 * Centralizes SHA-1 hashing logic previously duplicated in MainActivity and SettingsActivity.
 */
public final class CryptoUtils {

    private static final String TAG = "CryptoUtils";

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
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] messageDigest = md.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder(40);
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "SHA-1 hashing failed: " + e.getMessage());
            return input;
        }
    }
}
