package com.diaclock.nightstand;

import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

/**
 * Singleton provider for sharing a single OkHttpClient instance across the entire application.
 * This optimizes connection reuse and prevents resource leakages from multiple clients.
 */
public final class HttpClientProvider {

    private static final OkHttpClient sharedClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    private HttpClientProvider() {
        // Prevent instantiation
    }

    /**
     * Returns the shared OkHttpClient instance.
     *
     * @return Single OkHttpClient instance
     */
    public static OkHttpClient getClient() {
        return sharedClient;
    }
}
