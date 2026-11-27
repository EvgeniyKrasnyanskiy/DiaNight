package com.diaclock.nightstand;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Orchestrates network glucose fetching across Nightscout and local Wi-Fi xDrip+ sources
 * with priority fallback (Nightscout -> Wi-Fi), /pebble-first single-request optimization,
 * and timestamp-based freshness validation.
 */
public class GlucoseDataFetcher {

    private static final String TAG = "DiaNightFetcher";

    public static final String SOURCE_BROADCAST = "LOC";
    public static final String SOURCE_NIGHTSCOUT = "NS";
    public static final String SOURCE_WIFI = "Wi-Fi";

    public static class Config {
        public final boolean nightscoutEnabled;
        public final String nightscoutUrl;
        public final String nightscoutSecretHash;
        public final boolean wifiEnabled;
        public final String wifiServerIp;
        public final String wifiSecretHash;
        public final long staleThresholdMs;

        public Config(boolean nightscoutEnabled,
                      @Nullable String nightscoutUrl,
                      @Nullable String nightscoutSecretHash,
                      boolean wifiEnabled,
                      @Nullable String wifiServerIp,
                      @Nullable String wifiSecretHash,
                      long staleThresholdMs) {
            this.nightscoutEnabled = nightscoutEnabled;
            this.nightscoutUrl = nightscoutUrl != null ? nightscoutUrl.trim() : "";
            this.nightscoutSecretHash = nightscoutSecretHash;
            this.wifiEnabled = wifiEnabled;
            this.wifiServerIp = wifiServerIp != null ? wifiServerIp.trim() : "";
            this.wifiSecretHash = wifiSecretHash;
            this.staleThresholdMs = Math.max(60_000L, staleThresholdMs);
        }
    }

    public static class GlucoseReading {
        public final double glucoseMmol;
        public final String direction;
        public final double iob;
        public final long timestampMs;
        public final String sourceTag;
        public final boolean isStale;

        public GlucoseReading(double glucoseMmol,
                              @Nullable String direction,
                              double iob,
                              long timestampMs,
                              @NonNull String sourceTag,
                              boolean isStale) {
            this.glucoseMmol = glucoseMmol;
            this.direction = direction != null ? direction : "";
            this.iob = iob;
            this.timestampMs = timestampMs;
            this.sourceTag = sourceTag;
            this.isStale = isStale;
        }
    }

    public interface FetchCallback {
        void onFreshReading(@NonNull GlucoseReading reading);
        void onStaleReading(@NonNull GlucoseReading reading);
        void onFailure(boolean wifiAttemptedAndFailed);
    }

    private interface SingleSourceCallback {
        void onResult(@Nullable GlucoseReading reading, boolean networkFailed);
    }

    private final AtomicInteger generation = new AtomicInteger(0);
    private volatile Call activeCall = null;

    /**
     * Cancels any in-flight HTTP request and invalidates pending callbacks.
     */
    public void cancel() {
        generation.incrementAndGet();
        Call call = activeCall;
        activeCall = null;
        if (call != null) {
            call.cancel();
        }
    }

    /**
     * Starts a prioritized network fetch cycle: Nightscout first (if enabled),
     * falling back to Wi-Fi xDrip+ only if Nightscout fails or returns stale data.
     */
    public void fetch(@NonNull Config config, @NonNull FetchCallback callback) {
        cancel();
        final int gen = generation.get();

        boolean canUseNightscout = config.nightscoutEnabled && !config.nightscoutUrl.isEmpty();
        boolean canUseWifi = config.wifiEnabled && !config.wifiServerIp.isEmpty();

        if (canUseNightscout) {
            fetchNightscout(config, gen, (nsReading, nsFailed) -> {
                if (gen != generation.get()) return;

                if (nsReading != null && !nsReading.isStale) {
                    callback.onFreshReading(nsReading);
                    return;
                }

                // Nightscout failed or returned stale data -> try Wi-Fi fallback if enabled
                if (canUseWifi) {
                    Log.d(TAG, "Nightscout " + (nsReading == null ? "failed" : "stale") + ", falling back to Wi-Fi master");
                    fetchWifi(config, gen, (wifiReading, wifiFailed) -> {
                        if (gen != generation.get()) return;

                        if (wifiReading != null && !wifiReading.isStale) {
                            callback.onFreshReading(wifiReading);
                        } else if (wifiReading != null || nsReading != null) {
                            GlucoseReading best = pickFresher(nsReading, wifiReading);
                            callback.onStaleReading(best);
                        } else {
                            callback.onFailure(wifiFailed);
                        }
                    });
                } else {
                    if (nsReading != null) {
                        callback.onStaleReading(nsReading);
                    } else {
                        callback.onFailure(false);
                    }
                }
            });
        } else if (canUseWifi) {
            fetchWifi(config, gen, (wifiReading, wifiFailed) -> {
                if (gen != generation.get()) return;

                if (wifiReading != null && !wifiReading.isStale) {
                    callback.onFreshReading(wifiReading);
                } else if (wifiReading != null) {
                    callback.onStaleReading(wifiReading);
                } else {
                    callback.onFailure(wifiFailed);
                }
            });
        } else {
            callback.onFailure(false);
        }
    }

    private void fetchNightscout(@NonNull Config config, int gen, @NonNull SingleSourceCallback cb) {
        String pebbleUrl = buildNightscoutUrl(config.nightscoutUrl, "/pebble", null);
        String sgvUrl = buildNightscoutUrl(config.nightscoutUrl, "/api/v1/entries/sgv.json", "count=1");
        fetchPebbleThenSgv(pebbleUrl, sgvUrl, config.nightscoutSecretHash, SOURCE_NIGHTSCOUT, config.staleThresholdMs, gen, cb);
    }

    private void fetchWifi(@NonNull Config config, int gen, @NonNull SingleSourceCallback cb) {
        String base = "http://" + config.wifiServerIp + ":17580";
        String pebbleUrl = base + "/pebble";
        String sgvUrl = base + "/sgv.json?count=1";
        fetchPebbleThenSgv(pebbleUrl, sgvUrl, config.wifiSecretHash, SOURCE_WIFI, config.staleThresholdMs, gen, cb);
    }

    /**
     * Requests /pebble first (returns SGV, direction, datetime, and IoB in a single HTTP request).
     * Falls back to the SGV endpoint if /pebble fails or does not contain SGV data.
     */
    private void fetchPebbleThenSgv(@NonNull String pebbleUrl,
                                    @NonNull String sgvUrl,
                                    @Nullable String secretHash,
                                    @NonNull String sourceTag,
                                    long staleThresholdMs,
                                    int gen,
                                    @NonNull SingleSourceCallback cb) {
        enqueueGet(pebbleUrl, secretHash, gen, new HttpResultHandler() {
            @Override
            public void onSuccess(String body) {
                GlucoseReading reading = parsePebbleResponse(body, sourceTag, staleThresholdMs, System.currentTimeMillis());
                if (reading != null) {
                    cb.onResult(reading, false);
                } else {
                    // /pebble did not contain usable SGV -> fallback to sgv.json
                    fetchSgvOnly(sgvUrl, secretHash, sourceTag, staleThresholdMs, gen, cb);
                }
            }

            @Override
            public void onError(String reason) {
                Log.d(TAG, sourceTag + " /pebble failed (" + reason + "), falling back to SGV endpoint");
                fetchSgvOnly(sgvUrl, secretHash, sourceTag, staleThresholdMs, gen, cb);
            }
        });
    }

    private void fetchSgvOnly(@NonNull String sgvUrl,
                              @Nullable String secretHash,
                              @NonNull String sourceTag,
                              long staleThresholdMs,
                              int gen,
                              @NonNull SingleSourceCallback cb) {
        enqueueGet(sgvUrl, secretHash, gen, new HttpResultHandler() {
            @Override
            public void onSuccess(String body) {
                GlucoseReading reading = parseSgvResponse(body, sourceTag, staleThresholdMs, System.currentTimeMillis());
                if (reading != null) {
                    cb.onResult(reading, false);
                } else {
                    cb.onResult(null, true);
                }
            }

            @Override
            public void onError(String reason) {
                Log.e(TAG, sourceTag + " SGV fetch failed: " + reason);
                cb.onResult(null, true);
            }
        });
    }

    private interface HttpResultHandler {
        void onSuccess(String body);
        void onError(String reason);
    }

    private void enqueueGet(@NonNull String url,
                            @Nullable String secretHash,
                            int gen,
                            @NonNull HttpResultHandler handler) {
        if (gen != generation.get()) return;
        try {
            Request.Builder requestBuilder = new Request.Builder().url(url);
            if (secretHash != null && !secretHash.isEmpty()) {
                requestBuilder.addHeader("api-secret", secretHash);
            }
            Call call = HttpClientProvider.getClient().newCall(requestBuilder.build());
            activeCall = call;
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call c, @NonNull IOException e) {
                    if (gen != generation.get() || c.isCanceled()) return;
                    handler.onError(e.getMessage() != null ? e.getMessage() : "IOException");
                }

                @Override
                public void onResponse(@NonNull Call c, @NonNull Response response) throws IOException {
                    try {
                        if (gen != generation.get() || c.isCanceled()) return;
                        if (!response.isSuccessful()) {
                            handler.onError("HTTP " + response.code());
                            return;
                        }
                        String body = response.body() != null ? response.body().string() : "";
                        handler.onSuccess(body);
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (IllegalArgumentException e) {
            handler.onError("Invalid URL: " + e.getMessage());
        }
    }

    /**
     * Normalizes a user-entered Nightscout URL and appends the given API path and optional query param,
     * preserving any existing query parameters (such as ?token=...).
     */
    @NonNull
    public static String buildNightscoutUrl(@NonNull String rawUrl, @NonNull String path, @Nullable String extraQuery) {
        String trimmed = rawUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }

        String existingQuery = "";
        int qIdx = trimmed.indexOf('?');
        if (qIdx >= 0) {
            existingQuery = trimmed.substring(qIdx + 1);
            trimmed = trimmed.substring(0, qIdx);
        }

        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/api/v1")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/api/v1".length());
        }

        StringBuilder sb = new StringBuilder(trimmed).append(path);
        boolean hasQuery = false;
        if (extraQuery != null && !extraQuery.isEmpty()) {
            sb.append('?').append(extraQuery);
            hasQuery = true;
        }
        if (!existingQuery.isEmpty()) {
            sb.append(hasQuery ? '&' : '?').append(existingQuery);
        }
        return sb.toString();
    }

    @Nullable
    public static GlucoseReading parsePebbleResponse(@Nullable String body,
                                                     @NonNull String sourceTag,
                                                     long staleThresholdMs,
                                                     long nowMs) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            JsonElement rootEl = JsonParser.parseString(body);
            if (!rootEl.isJsonObject()) return null;
            JsonObject root = rootEl.getAsJsonObject();

            double sgvRaw = -1.0;
            String direction = "";
            long timestampMs = 0L;
            double iobValue = -1.0;

            if (root.has("bgs") && root.get("bgs").isJsonArray()) {
                JsonArray bgsArray = root.getAsJsonArray("bgs");
                if (bgsArray.size() > 0 && bgsArray.get(0).isJsonObject()) {
                    JsonObject bgsObj = bgsArray.get(0).getAsJsonObject();
                    if (bgsObj.has("sgv")) {
                        sgvRaw = parseDoubleElement(bgsObj.get("sgv"));
                    }
                    if (bgsObj.has("direction") && !bgsObj.get("direction").isJsonNull()) {
                        direction = bgsObj.get("direction").getAsString();
                    }
                    if (bgsObj.has("datetime")) {
                        timestampMs = parseLongElement(bgsObj.get("datetime"));
                    }
                    if (bgsObj.has("iob")) {
                        iobValue = parseIobElement(bgsObj.get("iob"));
                    }
                }
            }

            if (iobValue < 0 && root.has("status") && root.get("status").isJsonArray()) {
                JsonArray statusArray = root.getAsJsonArray("status");
                if (statusArray.size() > 0 && statusArray.get(0).isJsonObject()) {
                    JsonObject statusObj = statusArray.get(0).getAsJsonObject();
                    if (statusObj.has("iob")) {
                        JsonElement statusIob = statusObj.get("iob");
                        if (statusIob.isJsonObject()) {
                            JsonObject iobObj = statusIob.getAsJsonObject();
                            if (iobObj.has("iob")) {
                                iobValue = parseIobElement(iobObj.get("iob"));
                            }
                        } else {
                            iobValue = parseIobElement(statusIob);
                        }
                    }
                }
            }

            if (sgvRaw <= 0) return null;

            // Pebble may return mg/dL (e.g. 108) or mmol/L (e.g. 6.0) depending on Nightscout units
            double glucoseMmol = (sgvRaw > 35.0) ? (sgvRaw / 18.0) : sgvRaw;
            boolean isStale = isTimestampStale(timestampMs, staleThresholdMs, nowMs);
            return new GlucoseReading(glucoseMmol, direction, iobValue, timestampMs, sourceTag, isStale);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static GlucoseReading parseSgvResponse(@Nullable String body,
                                                  @NonNull String sourceTag,
                                                  long staleThresholdMs,
                                                  long nowMs) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            JsonElement element = JsonParser.parseString(body);
            JsonObject obj = null;
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                if (array.size() > 0 && array.get(0).isJsonObject()) {
                    obj = array.get(0).getAsJsonObject();
                }
            } else if (element.isJsonObject()) {
                obj = element.getAsJsonObject();
            }
            if (obj == null || !obj.has("sgv")) return null;

            double sgvRaw = parseDoubleElement(obj.get("sgv"));
            if (sgvRaw <= 0) return null;

            String direction = "";
            if (obj.has("direction") && !obj.get("direction").isJsonNull()) {
                direction = obj.get("direction").getAsString();
            }

            long timestampMs = 0L;
            if (obj.has("date")) {
                timestampMs = parseLongElement(obj.get("date"));
            }
            if (timestampMs <= 0 && obj.has("mills")) {
                timestampMs = parseLongElement(obj.get("mills"));
            }
            if (timestampMs <= 0 && obj.has("dateString") && !obj.get("dateString").isJsonNull()) {
                timestampMs = parseIsoDate(obj.get("dateString").getAsString());
            }

            double glucoseMmol = sgvRaw / 18.0;
            boolean isStale = isTimestampStale(timestampMs, staleThresholdMs, nowMs);
            return new GlucoseReading(glucoseMmol, direction, -1.0, timestampMs, sourceTag, isStale);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isTimestampStale(long timestampMs, long staleThresholdMs, long nowMs) {
        if (timestampMs <= 0) return false;
        return (nowMs - timestampMs) > staleThresholdMs;
    }

    @NonNull
    private static GlucoseReading pickFresher(@Nullable GlucoseReading a, @Nullable GlucoseReading b) {
        if (a == null) return b;
        if (b == null) return a;
        return (b.timestampMs > a.timestampMs) ? b : a;
    }

    private static double parseDoubleElement(@Nullable JsonElement el) {
        if (el == null || el.isJsonNull()) return -1.0;
        try {
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isNumber()) {
                    return el.getAsDouble();
                }
                String str = el.getAsString().trim().replace(",", ".");
                if (!str.isEmpty()) {
                    return Double.parseDouble(str);
                }
            }
        } catch (Exception ignored) {
        }
        return -1.0;
    }

    private static long parseLongElement(@Nullable JsonElement el) {
        if (el == null || el.isJsonNull()) return 0L;
        try {
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isNumber()) {
                    return el.getAsLong();
                }
                String str = el.getAsString().trim();
                if (!str.isEmpty()) {
                    return Long.parseLong(str);
                }
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    public static double parseIobElement(@Nullable JsonElement el) {
        if (el == null || el.isJsonNull()) return -1.0;
        try {
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isNumber()) {
                    return el.getAsDouble();
                }
                String str = el.getAsString().trim().replace(",", ".");
                if (!str.isEmpty()) {
                    return Double.parseDouble(str);
                }
            }
        } catch (Exception ignored) {
        }
        return -1.0;
    }

    private static long parseIsoDate(@Nullable String iso) {
        if (iso == null || iso.trim().isEmpty()) return 0L;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                java.util.Date parsed = sdf.parse(iso.trim());
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) {
            }
        }
        return 0L;
    }
}
