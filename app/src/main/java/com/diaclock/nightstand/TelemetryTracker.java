package com.diaclock.nightstand;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Helper class to track app installation telemetry securely and anonymously via Google Forms.
 */
public class TelemetryTracker {

    private static final String TAG = "TelemetryTracker";
    private static final String PREFS_NAME = "DiaClockPrefs";
    private static final String KEY_TRACKED = "first_launch_tracked";

    // --- Google Form configuration constants (to be filled by the user) ---
    // Example: "https://docs.google.com/forms/d/e/1FAIpQLSfXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/formResponse"
    private static final String FORM_URL = "https://docs.google.com/forms/d/e/1FAIpQLSdwGtctq_69FE-k3v8XqPm-eXiBCfeKarTZ7ikDHhygXOLLcQ/formResponse"; 
    
    // Example: "entry.111111111"
    private static final String ENTRY_MARKET = "entry.337109886"; 
    
    // Example: "entry.222222222"
    private static final String ENTRY_VERSION = "entry.10982061"; 
    
    // Example: "entry.333333333"
    private static final String ENTRY_DEVICE = "entry.607409092"; 

    public static void trackInstall(final Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isTracked = prefs.getBoolean(KEY_TRACKED, false);
        
        if (isTracked) {
            Log.d(TAG, "Install telemetry already sent previously.");
            return;
        }

        if (FORM_URL == null || FORM_URL.trim().isEmpty()) {
            Log.d(TAG, "Install telemetry is not configured (FORM_URL is empty).");
            return;
        }

        // Determine installer package name
        String installer = null;
        try {
            installer = context.getPackageManager().getInstallerPackageName(context.getPackageName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve installer name: " + e.getMessage());
        }

        // Map installer ID to user-friendly market name
        String marketName;
        if (installer == null) {
            marketName = "APK / Direct Install";
        } else {
            switch (installer) {
                case "com.android.vending":
                    marketName = "Google Play";
                    break;
                case "ru.yoomoney.rustore":
                case "ru.rustore.store":
                    marketName = "RuStore";
                    break;
                default:
                    marketName = "Installer: " + installer;
                    break;
            }
        }

        // Get app version
        String versionName = "unknown";
        try {
            versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve version name: " + e.getMessage());
        }

        // Get device model
        String deviceName = Build.MANUFACTURER + " " + Build.MODEL;

        Log.d(TAG, "Tracking new install: Market=" + marketName + ", Version=" + versionName + ", Device=" + deviceName);

        OkHttpClient client = new OkHttpClient();
        RequestBody formBody = new FormBody.Builder()
                .add(ENTRY_MARKET, marketName)
                .add(ENTRY_VERSION, versionName)
                .add(ENTRY_DEVICE, deviceName)
                .build();

        Request request = new Request.Builder()
                .url(FORM_URL)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to send telemetry to Google Form: " + e.getMessage());
                // Leave KEY_TRACKED = false to retry on next launch
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Install telemetry sent to Google Form successfully.");
                    prefs.edit().putBoolean(KEY_TRACKED, true).apply();
                } else {
                    Log.e(TAG, "Google Form returned error code: " + response.code());
                }
                response.close();
            }
        });
    }
}
