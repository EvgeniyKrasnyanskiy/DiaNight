package com.diaclock.nightstand;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.view.MotionEvent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "DiaClockPrefs";

    // UI elements
    private View mainRootLayout;
    private View layoutTime;
    private View layoutGlucose;
    private TextView tvHours;
    private TextView tvColon;
    private TextView tvMinutes;
    private TextView tvGlucose;
    private ImageView ivSettings;
    private ImageView ivAlarmBell;
    private ImageView ivNetworkWarning;
    private ImageView ivExit;
    
    // New UI elements for IoB & Battery
    private TextView tvIoB;
    private View ivBatteryContainer;
    private ImageView ivBatteryIcon;
    private TextView tvBatteryPercent;

    // Handlers and Runnables
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler inactivityHandler = new Handler(Looper.getMainLooper());
    private final Handler pixelShiftHandler = new Handler(Looper.getMainLooper());
    
    // Breathing animation (hardware-accelerated via ObjectAnimator)
    private ObjectAnimator breathingAnimator;
    
    // Lifecycle guard to prevent OkHttp callbacks from accessing destroyed Activity
    private volatile boolean isActivityDestroyed = false;
    private boolean isBatteryReceiverRegistered = false;

    // Flashlight overlay and gesture detector
    private View viewFlashlightOverlay;
    private boolean isFlashlightActive = false;
    private ValueAnimator flashlightBrightnessAnimator;
    private android.view.GestureDetector gestureDetector;
    
    private boolean isControlsFaded = false;
    private final Runnable inactivityRunnable = this::fadeAttributesOut;
    
    // Cached views for pixel shift (avoid repeated findViewById)
    private View centralClickArea;
    private View statusBarContainer;
    
    private final Runnable pixelShiftRunnable = new Runnable() {
        @Override
        public void run() {
            // Random offset between -16 and +16 pixels
            float shiftX = (float) (Math.random() * 32 - 16);
            float shiftY = (float) (Math.random() * 32 - 16);
            
            if (centralClickArea != null) {
                centralClickArea.setTranslationX(shiftX);
                centralClickArea.setTranslationY(shiftY);
            }

            // Shift the status bar container as well to prevent burn-in on top row icons (bell, gear, battery)
            if (statusBarContainer != null) {
                float statusShiftX = (float) (Math.random() * 16 - 8);
                float statusShiftY = (float) (Math.random() * 16 - 8);
                statusBarContainer.setTranslationX(statusShiftX);
                statusBarContainer.setTranslationY(statusShiftY);
            }
            
            // Repeat every 2 minutes
            pixelShiftHandler.postDelayed(this, 120000L);
        }
    };
    
    // Toggle state variables
    private boolean isShowingTime = true;
    private int toggleIntervalSeconds = 5;
    private String dataSource = "network";
    private boolean isReceiverRegistered = false;
    private boolean nightlightMode = false;
    
    // Network variables
    private String serverIp = "192.168.0.111";
    private String apiSecret = "FBB9F80F9AC22E5B15F6DA1FFE599E14";
    private String cachedSecretHash = null; // Cached SHA-1 hash of apiSecret
    private double lastGlucoseMmol = -1.0;
    private String lastDirection = "";
    private volatile boolean hasConnectionError = false;
    
    // Adaptive polling interval with exponential backoff on errors
    private volatile long networkPollInterval = 15000L; // 15 seconds base (CGMs update every 1-5 min)
    private static final long BASE_POLL_INTERVAL = 15000L;
    private static final long MAX_POLL_INTERVAL = 120000L; // 2 minutes max backoff
    
    // Runnable fields for explicit lifecycle control
    private Runnable timeRunnable;
    private String lastDisplayedHours = "";
    private String lastDisplayedMinutes = "";

    // Alarm state variables
    private boolean alarmEnabled = true;
    private MediaPlayer mediaPlayer = null;
    private boolean isAlarmSounding = false;
    private long alarmSnoozeUntilTime = 0; // Epoch milliseconds until which alarms are silenced
    private static final long MAX_ALARM_DURATION_MS = 15 * 60 * 1000L; // 15 minutes
    
    private final Runnable autoSnoozeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAlarmSounding) {
                snoozeAlarm(true);
            }
        }
    };

    // Custom text color (default White)
    private int textColor = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Hide Android System navigation/status bar to maximize nightstand canvas
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        initViews();
        loadSettings();
        setupListeners();

        // Track installation analytics
        TelemetryTracker.trackInstall(this);

        // Start tasks
        startTimeUpdates();
        startToggleCycle();
        
        if (nightlightMode) {
            layoutGlucose.setVisibility(View.GONE);
            layoutTime.setVisibility(View.VISIBLE);
            ivAlarmBell.setVisibility(View.GONE);
            ivNetworkWarning.setVisibility(View.GONE);
        } else {
            if ("broadcast".equals(dataSource)) {
                registerXdripReceiver();
                tvGlucose.setText(getString(R.string.msg_waiting));
                tvIoB.setVisibility(View.GONE);
            } else {
                startNetworkPolling();
            }
        }
        
        // Register battery monitor
        if (!isBatteryReceiverRegistered) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            }
            isBatteryReceiverRegistered = true;
        }
        
        // Start pixel shifting protection
        pixelShiftHandler.post(pixelShiftRunnable);
        
        // Start breathing animation (hardware-accelerated)
        startBreathingAnimation();
        
        // Initialize flashlight gesture detector and listeners
        initFlashlight();

        // Initial inactivity trigger
        adjustTextSizes();
        resetUserInactivityTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Resume breathing animation
        if (breathingAnimator != null) {
            if (android.os.Build.VERSION.SDK_INT >= 19 && breathingAnimator.isPaused()) {
                breathingAnimator.resume();
            } else if (!breathingAnimator.isRunning()) {
                breathingAnimator.start();
            }
        }

        String oldSource = dataSource;
        boolean oldNightlight = nightlightMode;
        loadSettings();
        updateAlarmBellIcon();
        applyTextColor();
        
        if (nightlightMode) {
            // Unregister/Stop monitoring to save battery
            unregisterXdripReceiver();
            mainHandler.removeCallbacks(networkRunnable);
            mainHandler.removeCallbacks(toggleRunnable);
            
            // Adjust layouts and icons
            layoutGlucose.setVisibility(View.GONE);
            layoutTime.setVisibility(View.VISIBLE);
            ivAlarmBell.setVisibility(View.GONE);
            ivNetworkWarning.setVisibility(View.GONE);
            stopAlarmSound();
        } else {
            // Restore alarm bell visibility (warning only if there is a network error)
            ivAlarmBell.setVisibility(View.VISIBLE);
            if (hasConnectionError) {
                ivNetworkWarning.setVisibility(View.VISIBLE);
            } else {
                ivNetworkWarning.setVisibility(View.GONE);
            }
            
            // If nightlight mode was previously active and is now disabled, restart the toggle cycle
            if (oldNightlight) {
                startToggleCycle();
            }
            
            // Resume/Start polling or broadcast receiver if needed
            if (!dataSource.equals(oldSource) || oldNightlight) {
                Log.d(TAG, "Data source or nightlight mode changed");
                if ("broadcast".equals(dataSource)) {
                    mainHandler.removeCallbacks(networkRunnable);
                    registerXdripReceiver();
                    tvGlucose.setText(getString(R.string.msg_waiting));
                    tvIoB.setVisibility(View.GONE);
                } else {
                    unregisterXdripReceiver();
                    mainHandler.removeCallbacks(networkRunnable);
                    mainHandler.post(networkRunnable);
                }
            } else {
                if ("broadcast".equals(dataSource)) {
                    registerXdripReceiver();
                } else {
                    unregisterXdripReceiver();
                    mainHandler.removeCallbacks(networkRunnable);
                    mainHandler.post(networkRunnable);
                }
            }
        }
        
        // Refresh inactivity timer on resume
        adjustTextSizes();
        resetUserInactivityTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (breathingAnimator != null) {
            if (android.os.Build.VERSION.SDK_INT >= 19) {
                breathingAnimator.pause();
            } else {
                breathingAnimator.cancel();
            }
        }
    }

    @Override
    protected void onDestroy() {
        isActivityDestroyed = true;
        super.onDestroy();
        stopAlarmSound();
        mainHandler.removeCallbacksAndMessages(null);

        if (flashlightBrightnessAnimator != null) {
            flashlightBrightnessAnimator.cancel();
            flashlightBrightnessAnimator = null;
        }

        // Clean up breathing animation
        if (breathingAnimator != null) {
            breathingAnimator.cancel();
            breathingAnimator = null;
        }

        // Clean up battery receiver
        if (isBatteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Unregistering battery receiver failed: " + e.getMessage());
            }
            isBatteryReceiverRegistered = false;
        }

        // Clean up xDrip receiver
        unregisterXdripReceiver();

        // Clean up OkHttp resources
        HttpClientProvider.getClient().dispatcher().cancelAll();

        // Clean up handlers
        pixelShiftHandler.removeCallbacksAndMessages(null);
        inactivityHandler.removeCallbacksAndMessages(null);
    }

    private void initViews() {
        mainRootLayout = findViewById(R.id.mainRootLayout);
        layoutTime = findViewById(R.id.layoutTime);
        layoutGlucose = findViewById(R.id.layoutGlucose);
        tvHours = findViewById(R.id.tvHours);
        tvColon = findViewById(R.id.tvColon);
        tvMinutes = findViewById(R.id.tvMinutes);
        tvGlucose = findViewById(R.id.tvGlucose);
        ivSettings = findViewById(R.id.ivSettings);
        ivAlarmBell = findViewById(R.id.ivAlarmBell);
        ivNetworkWarning = findViewById(R.id.ivNetworkWarning);
        ivExit = findViewById(R.id.ivExit);
        
        tvIoB = findViewById(R.id.tvIoB);
        ivBatteryContainer = findViewById(R.id.ivBatteryContainer);
        ivBatteryIcon = findViewById(R.id.ivBatteryIcon);
        tvBatteryPercent = findViewById(R.id.tvBatteryPercent);
        
        // Cache views used in pixelShiftRunnable to avoid repeated findViewById
        centralClickArea = findViewById(R.id.centralClickArea);
        statusBarContainer = findViewById(R.id.statusBarContainer);

        viewFlashlightOverlay = findViewById(R.id.viewFlashlightOverlay);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        serverIp = prefs.getString("ip_address", "192.168.0.111");
        apiSecret = prefs.getString("api_secret", "FBB9F80F9AC22E5B15F6DA1FFE599E14");
        toggleIntervalSeconds = prefs.getInt("toggle_interval", 5);
        alarmEnabled = prefs.getBoolean("alarm_enabled", true);
        textColor = prefs.getInt("text_color", Color.WHITE);
        dataSource = prefs.getString("data_source", "network");
        nightlightMode = prefs.getBoolean("nightlight_mode", false);
        
        // Cache SHA-1 hash of API secret to avoid recomputing on every request
        cachedSecretHash = (apiSecret != null && !apiSecret.trim().isEmpty())
                ? CryptoUtils.computeSHA1(apiSecret) : null;
    }

    private void setupListeners() {
        // Exit application click listener
        ivExit.setOnClickListener(v -> finish());

        // Toggle Alarms Enabled state (Bell) with Toast hints
        ivAlarmBell.setOnClickListener(v -> {
            alarmEnabled = !alarmEnabled;
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean("alarm_enabled", alarmEnabled);
            editor.apply();
            updateAlarmBellIcon();
            
            // Show localized Toast hint
            if (alarmEnabled) {
                Toast.makeText(MainActivity.this, getString(R.string.hint_bell_active), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, getString(R.string.hint_bell_disabled), Toast.LENGTH_SHORT).show();
            }
            
            // Check alarms instantly based on state change
            if (!alarmEnabled) {
                stopAlarmSound();
            } else if (lastGlucoseMmol > 0) {
                checkAlarms(lastGlucoseMmol);
            }
        });

        // Open Settings screen
        ivSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Open Settings screen from the network warning icon (exclamation mark) as requested
        ivNetworkWarning.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Enable clickability on the root layout to catch alarm dismiss taps anywhere on the screen
        mainRootLayout.setClickable(true);
        mainRootLayout.setFocusable(true);
        mainRootLayout.setOnClickListener(v -> {
            if (isAlarmSounding) {
                snoozeAlarm();
            }
        });
    }

    private void snoozeAlarm() {
        snoozeAlarm(false);
    }

    private void snoozeAlarm(boolean isAuto) {
        stopAlarmSound();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int snoozeMin = prefs.getInt("snooze_interval", 60);
        // Silences alarms until epoch timestamp
        alarmSnoozeUntilTime = System.currentTimeMillis() + (snoozeMin * 60 * 1000L);
        String message = isAuto 
            ? getString(R.string.msg_snoozed_auto, snoozeMin) 
            : getString(R.string.msg_snoozed_manual, snoozeMin);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updateAlarmBellIcon() {
        if (alarmEnabled) {
            ivAlarmBell.setImageResource(R.drawable.ic_bell_active);
        } else {
            ivAlarmBell.setImageResource(R.drawable.ic_bell_disabled);
        }
    }

    // Dynamic medical color-coding for glucose, clock remains customizable solid text color
    private void applyTextColor() {
        tvHours.setTextColor(textColor);
        tvColon.setTextColor(textColor);
        tvMinutes.setTextColor(textColor);
        
        // Dynamically color-code glucose values separately from clock
        if (lastGlucoseMmol > 0) {
            if (lastGlucoseMmol < 3.9) {
                tvGlucose.setTextColor(Color.parseColor("#FF3B30")); // Red: Hypoglycemia
            } else if (lastGlucoseMmol >= 3.9 && lastGlucoseMmol <= 7.8) {
                tvGlucose.setTextColor(Color.parseColor("#34C759")); // Green: Target normal range
            } else if (lastGlucoseMmol > 7.8 && lastGlucoseMmol <= 13.9) {
                tvGlucose.setTextColor(Color.parseColor("#FFD700")); // Yellow: High glucose
            } else {
                tvGlucose.setTextColor(Color.parseColor("#FF3B30")); // Red: Severe Hyperglycemia
            }
        } else {
            tvGlucose.setTextColor(textColor); // Default color when offline
        }
        
        tvHours.getPaint().setShader(null);
        tvColon.getPaint().setShader(null);
        tvMinutes.getPaint().setShader(null);
        tvGlucose.getPaint().setShader(null);
        
        tvHours.invalidate();
        tvColon.invalidate();
        tvMinutes.invalidate();
        tvGlucose.invalidate();
    }

    // Convert xDrip+ English direction strings into Unicode trend arrows
    private String getTrendArrow(String direction) {
        if (direction == null) return "";
        switch (direction) {
            case "DoubleUp": return "⇈";
            case "SingleUp": return "↑";
            case "FortyFiveUp": return "↗";
            case "Flat": return "→";
            case "FortyFiveDown": return "↘";
            case "SingleDown": return "↓";
            case "DoubleDown": return "⇊";
            default: return "";
        }
    }

    // 1. Time Update Logic
    private void startTimeUpdates() {
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                Calendar c = Calendar.getInstance();
                String hoursStr = String.format(Locale.US, "%02d", c.get(Calendar.HOUR_OF_DAY));
                String minutesStr = String.format(Locale.US, "%02d", c.get(Calendar.MINUTE));

                // Only update UI if text actually changed to reduce unnecessary rendering
                if (!hoursStr.equals(lastDisplayedHours)) {
                    tvHours.setText(hoursStr);
                    lastDisplayedHours = hoursStr;
                }
                if (!minutesStr.equals(lastDisplayedMinutes)) {
                    tvMinutes.setText(minutesStr);
                    lastDisplayedMinutes = minutesStr;
                }
                
                mainHandler.postDelayed(this, 1000); // Poll clock checks every second
            }
        };
        mainHandler.post(timeRunnable);
    }

    // 2. Colon Breathing Animation powered by hardware-accelerated ObjectAnimator
    private void startBreathingAnimation() {
        if (breathingAnimator != null) {
            breathingAnimator.cancel();
        }
        breathingAnimator = ObjectAnimator.ofFloat(tvColon, "alpha", 1.0f, 0.3f);
        breathingAnimator.setDuration(1000); // 1 second fade down
        breathingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathingAnimator.setRepeatMode(ValueAnimator.REVERSE);
        breathingAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        breathingAnimator.start();
    }

    // 3. Central view toggling Handler
    private final Runnable toggleRunnable = new Runnable() {
        @Override
        public void run() {
            if (nightlightMode) {
                isShowingTime = true;
                layoutGlucose.setVisibility(View.GONE);
                layoutTime.setVisibility(View.VISIBLE);
                return;
            }
            isShowingTime = !isShowingTime;
            if (isShowingTime) {
                layoutGlucose.setVisibility(View.GONE);
                layoutTime.setVisibility(View.VISIBLE);
            } else {
                layoutTime.setVisibility(View.GONE);
                layoutGlucose.setVisibility(View.VISIBLE);
                applyTextColor();
            }
            mainHandler.postDelayed(this, toggleIntervalSeconds * 1000L);
        }
    };

    private void startToggleCycle() {
        mainHandler.removeCallbacks(toggleRunnable);
        if (nightlightMode) {
            isShowingTime = true;
            layoutGlucose.setVisibility(View.GONE);
            layoutTime.setVisibility(View.VISIBLE);
            return;
        }
        mainHandler.postDelayed(toggleRunnable, toggleIntervalSeconds * 1000L);
    }

    // 4. Nightscout / xDrip Network Integration with adaptive polling interval
    private final Runnable networkRunnable = new Runnable() {
        @Override
        public void run() {
            if ("network".equals(dataSource) && !isActivityDestroyed) {
                fetchGlucoseData();
                mainHandler.postDelayed(this, networkPollInterval);
            }
        }
    };

    private void startNetworkPolling() {
        mainHandler.removeCallbacks(networkRunnable);
        if ("network".equals(dataSource)) {
            mainHandler.post(networkRunnable);
        }
    }

    /**
     * Returns the cached SHA-1 hash of the API secret.
     * The hash is computed once in loadSettings() and cached in cachedSecretHash.
     */
    private String getSecretHash() {
        return cachedSecretHash;
    }

    private void fetchGlucoseData() {
        if (serverIp == null || serverIp.trim().isEmpty()) {
            showNetworkWarning(true);
            return;
        }

        String url = "http://" + serverIp + ":17580/sgv.json";
        
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (cachedSecretHash != null) {
            requestBuilder.addHeader("api-secret", cachedSecretHash);
        }
        Request request = requestBuilder.build();

        HttpClientProvider.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network call failed: " + e.getMessage());
                // Exponential backoff on network errors
                networkPollInterval = Math.min(networkPollInterval * 2, MAX_POLL_INTERVAL);
                if (isActivityDestroyed) return;
                runOnUiThread(() -> {
                    showNetworkWarning(true);
                    tvGlucose.setText("---");
                    tvIoB.setVisibility(View.GONE);
                    adjustGlucoseAndIoBTextSizes();
                    applyTextColor();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        networkPollInterval = Math.min(networkPollInterval * 2, MAX_POLL_INTERVAL);
                        if (isActivityDestroyed) return;
                        runOnUiThread(() -> {
                            showNetworkWarning(true);
                            tvGlucose.setText("---");
                            tvIoB.setVisibility(View.GONE);
                            adjustGlucoseAndIoBTextSizes();
                            applyTextColor();
                        });
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    try {
                        JsonElement element = JsonParser.parseString(responseBody);
                        int sgv = 0;
                        String direction = "";
                        
                        if (element.isJsonArray()) {
                            JsonArray array = element.getAsJsonArray();
                            if (array.size() > 0) {
                                com.google.gson.JsonObject obj = array.get(0).getAsJsonObject();
                                sgv = obj.get("sgv").getAsInt();
                                if (obj.has("direction")) {
                                    direction = obj.get("direction").getAsString();
                                }
                            }
                        } else if (element.isJsonObject()) {
                            com.google.gson.JsonObject obj = element.getAsJsonObject();
                            sgv = obj.get("sgv").getAsInt();
                            if (obj.has("direction")) {
                                direction = obj.get("direction").getAsString();
                            }
                        }

                        if (sgv > 0) {
                            final double glucoseMmol = sgv / 18.0;
                            final String finalDirection = direction;
                            
                            // Reset polling interval on successful data fetch
                            networkPollInterval = BASE_POLL_INTERVAL;
                            
                            if (isActivityDestroyed) return;
                            runOnUiThread(() -> {
                                // Update shared state on UI thread to prevent data races
                                lastGlucoseMmol = glucoseMmol;
                                lastDirection = finalDirection;
                                showNetworkWarning(false);
                                tvGlucose.setText(String.format(Locale.US, "%.1f%s", glucoseMmol, getTrendArrow(finalDirection)));
                                adjustGlucoseAndIoBTextSizes();
                                applyTextColor();
                                checkAlarms(glucoseMmol);
                            });
                            
                            // Fetch IoB from the separate /pebble endpoint
                            fetchIoBData();
                        } else {
                            if (isActivityDestroyed) return;
                            runOnUiThread(() -> {
                                showNetworkWarning(true);
                                tvGlucose.setText("---");
                                tvIoB.setVisibility(View.GONE);
                                adjustGlucoseAndIoBTextSizes();
                                applyTextColor();
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Parsing SGV JSON failed: " + e.getMessage());
                        if (isActivityDestroyed) return;
                        runOnUiThread(() -> {
                            showNetworkWarning(true);
                            tvGlucose.setText("---");
                            tvIoB.setVisibility(View.GONE);
                            adjustGlucoseAndIoBTextSizes();
                            applyTextColor();
                        });
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Fetches IoB (Insulin on Board) from the xDrip+ /pebble endpoint.
     * The /sgv.json endpoint does NOT include IoB data.
     * The /pebble response structure: {"status":[{"iob":{"iob":1.25,...},...}],...}
     */
    private void fetchIoBData() {
        String pebbleUrl = "http://" + serverIp + ":17580/pebble";
        
        Request.Builder requestBuilder = new Request.Builder().url(pebbleUrl);
        if (cachedSecretHash != null) {
            requestBuilder.addHeader("api-secret", cachedSecretHash);
        }
        Request pebbleRequest = requestBuilder.build();

        HttpClientProvider.getClient().newCall(pebbleRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d(TAG, "Pebble IoB fetch failed: " + e.getMessage());
                if (isActivityDestroyed) return;
                runOnUiThread(() -> tvIoB.setVisibility(View.GONE));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        if (!isActivityDestroyed) {
                            runOnUiThread(() -> tvIoB.setVisibility(View.GONE));
                        }
                        return;
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Pebble raw response (first 500 chars): " + body.substring(0, Math.min(body.length(), 500)));
                    try {
                        com.google.gson.JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                        double iobValue = -1.0;
                        
                        // Parse IoB from: {"bgs":[{"iob":"0,64",...}]} (Standard Nightscout Pebble format in xDrip+)
                        if (root.has("bgs")) {
                            JsonArray bgsArray = root.getAsJsonArray("bgs");
                            if (bgsArray.size() > 0) {
                                com.google.gson.JsonObject bgsObj = bgsArray.get(0).getAsJsonObject();
                                if (bgsObj.has("iob")) {
                                    iobValue = parseIobElement(bgsObj.get("iob"));
                                }
                            }
                        }
                        
                        // Fallback to: {"status":[{"iob":{"iob":1.25,...}}]}
                        if (iobValue < 0 && root.has("status")) {
                            JsonArray statusArray = root.getAsJsonArray("status");
                            if (statusArray.size() > 0) {
                                com.google.gson.JsonObject statusObj = statusArray.get(0).getAsJsonObject();
                                if (statusObj.has("iob")) {
                                    JsonElement statusIobElement = statusObj.get("iob");
                                    if (statusIobElement.isJsonObject()) {
                                        com.google.gson.JsonObject iobObj = statusIobElement.getAsJsonObject();
                                        if (iobObj.has("iob")) {
                                            iobValue = parseIobElement(iobObj.get("iob"));
                                        }
                                    } else {
                                        iobValue = parseIobElement(statusIobElement);
                                    }
                                }
                            }
                        }
                        
                        final double finalIob = iobValue;
                        if (isActivityDestroyed) return;
                        runOnUiThread(() -> {
                            if (finalIob >= 0) {
                                tvIoB.setText(String.format(Locale.US, "%.2f", finalIob));
                                tvIoB.setVisibility(View.VISIBLE);
                                Log.d(TAG, "IoB displayed: " + finalIob);
                            } else {
                                tvIoB.setVisibility(View.GONE);
                            }
                            adjustGlucoseAndIoBTextSizes();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Parsing Pebble IoB failed: " + e.getMessage());
                        if (isActivityDestroyed) return;
                        runOnUiThread(() -> {
                            tvIoB.setVisibility(View.GONE);
                            adjustGlucoseAndIoBTextSizes();
                        });
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Safely parses an IoB JsonElement to double, handling potential localized decimal comma separators (e.g. "0,64").
     */
    private double parseIobElement(JsonElement iobElement) {
        if (iobElement == null || iobElement.isJsonNull()) {
            return -1.0;
        }
        try {
            if (iobElement.isJsonPrimitive()) {
                String strValue = iobElement.getAsString().trim();
                if (strValue.isEmpty()) {
                    return -1.0;
                }
                // Handle European comma decimal separator
                strValue = strValue.replace(",", ".");
                return Double.parseDouble(strValue);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse IoB element: " + e.getMessage());
        }
        return -1.0;
    }

    private void showNetworkWarning(boolean error) {
        hasConnectionError = error;
        if (error && !nightlightMode) {
            ivNetworkWarning.setVisibility(View.VISIBLE);
        } else {
            ivNetworkWarning.setVisibility(View.GONE);
        }
    }

    // 5. Intelligent Multi-time Slot Alarms with Snooze & Auto-resets
    private void checkAlarms(double glucoseVal) {
        if (!alarmEnabled || nightlightMode) {
            stopAlarmSound();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Load threshold ranges
        String dayStartStr = prefs.getString("day_start", "08:00");
        String dayEndStr = prefs.getString("day_end", "22:00");
        
        float dayHigh = prefs.getFloat("day_high", 10.0f);
        float dayLow = prefs.getFloat("day_low", 4.0f);
        
        float nightHigh = prefs.getFloat("night_high", 11.0f);
        float nightLow = prefs.getFloat("night_low", 3.6f);

        boolean isDaytime = determineIsDaytime(dayStartStr, dayEndStr);
        
        double currentLow = isDaytime ? dayLow : nightLow;
        double currentHigh = isDaytime ? dayHigh : nightHigh;

        Log.d(TAG, "Checking Alarms - Mode daytime: " + isDaytime + " | Sugar: " + glucoseVal + " | Bounds: " + currentLow + " - " + currentHigh);

        boolean isOutOfRange = glucoseVal < currentLow || glucoseVal > currentHigh;
        boolean isSnoozed = System.currentTimeMillis() < alarmSnoozeUntilTime;

        if (isOutOfRange) {
            if (!isSnoozed) {
                startAlarmSound();
            } else {
                stopAlarmSound();
            }
        } else {
            // Safety auto-reset: If glucose returns back to safe zones, cancel any active snooze
            // so next out-of-range event triggers alarm instantly without waiting for the snooze window to finish.
            alarmSnoozeUntilTime = 0;
            stopAlarmSound();
        }
    }

    private boolean determineIsDaytime(String startStr, String endStr) {
        try {
            String[] startParts = startStr.split(":");
            String[] endParts = endStr.split(":");
            
            int startMin = Integer.parseInt(startParts[0]) * 60 + Integer.parseInt(startParts[1]);
            int endMin = Integer.parseInt(endParts[0]) * 60 + Integer.parseInt(endParts[1]);

            Calendar now = Calendar.getInstance();
            int currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            if (startMin < endMin) {
                return currentMin >= startMin && currentMin < endMin;
            } else { // Handles slot wrapping past midnight (e.g. Day slot: 22:00 to 08:00)
                return currentMin >= startMin || currentMin < endMin;
            }
        } catch (Exception e) {
            Log.e(TAG, "Parsing day/night boundary string failed, using fallback: " + e.getMessage());
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            return hour >= 8 && hour < 22;
        }
    }

    private void startAlarmSound() {
        if (!alarmEnabled) {
            stopAlarmSound();
            return;
        }
        
        // Early exit if alarm is already playing to prevent race conditions
        if (isAlarmSounding) return;

        if (mediaPlayer == null) {
            try {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String alarmUriStr = prefs.getString("alarm_uri", null);
                
                if (alarmUriStr != null && alarmUriStr.equals("silent")) {
                    return; // Silent mode chosen by the user
                }

                Uri alarmUri;
                if (alarmUriStr == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    if (alarmUri == null) {
                        alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    }
                } else {
                    alarmUri = Uri.parse(alarmUriStr);
                }
                
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(this, alarmUri);
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                
                isAlarmSounding = true;
                mediaPlayer.start();
                
                // Schedule auto-snooze
                mainHandler.removeCallbacks(autoSnoozeRunnable);
                mainHandler.postDelayed(autoSnoozeRunnable, MAX_ALARM_DURATION_MS);
                
                Toast.makeText(this, getString(R.string.msg_alarm_warning), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Playing alarm sound failed: " + e.getMessage());
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.release();
                    } catch (Exception ex) {
                        // ignore
                    }
                    mediaPlayer = null;
                }
            }
        }
    }

    private void stopAlarmSound() {
        isAlarmSounding = false;
        mainHandler.removeCallbacks(autoSnoozeRunnable);
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception e) {
                Log.e(TAG, "Stopping mediaPlayer failed: " + e.getMessage());
            } finally {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }

    // Touch intercepting to track user activity
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        resetUserInactivityTimer();
        return super.dispatchTouchEvent(ev);
    }

    // Battery BroadcastReceiver
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            if (level >= 0 && scale > 0) {
                int levelPercent = (level * 100) / scale;
                tvBatteryPercent.setText(String.format(Locale.US, "%d%%", levelPercent));
                if (isCharging) {
                    ivBatteryIcon.setImageResource(R.drawable.ic_battery_charging);
                } else {
                    ivBatteryIcon.setImageResource(R.drawable.ic_battery);
                }
            }
        }
    };

    // xDrip Broadcast Receiver for local offline mode
    private final BroadcastReceiver xdripReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && "com.eveningoutpost.dexdrip.BgEstimate".equals(intent.getAction())) {
                double rawBg = intent.getDoubleExtra("com.eveningoutpost.dexdrip.Extras.BgEstimate", -1.0);
                String slopeName = intent.getStringExtra("com.eveningoutpost.dexdrip.Extras.BgSlopeName");
                
                Log.d(TAG, "Received xDrip broadcast: bg=" + rawBg + ", slope=" + slopeName);
                
                if (rawBg > 0) {
                    double glucoseMmol = rawBg / 18.0;
                    lastGlucoseMmol = glucoseMmol;
                    lastDirection = slopeName;
                    
                    runOnUiThread(() -> {
                        showNetworkWarning(false);
                        tvGlucose.setText(String.format(Locale.US, "%.1f%s", glucoseMmol, getTrendArrow(slopeName)));
                        tvIoB.setVisibility(View.GONE); // IOB is not broadcasted, hide it
                        adjustGlucoseAndIoBTextSizes();
                        applyTextColor();
                        checkAlarms(glucoseMmol);
                    });
                }
            }
        }
    };

    private void registerXdripReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter("com.eveningoutpost.dexdrip.BgEstimate");
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(xdripReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(xdripReceiver, filter);
            }
            isReceiverRegistered = true;
            Log.d(TAG, "xDrip BroadcastReceiver registered.");
        }
    }

    private void unregisterXdripReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(xdripReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
            }
            isReceiverRegistered = false;
            Log.d(TAG, "xDrip BroadcastReceiver unregistered.");
        }
    }

    // User Inactivity Timer & Transitions
    private void resetUserInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable);
        fadeControlsIn();
        inactivityHandler.postDelayed(inactivityRunnable, 15000L); // 15 seconds
    }

    private void fadeControlsIn() {
        if (isControlsFaded) {
            isControlsFaded = false;
            ivSettings.animate().alpha(1.0f).setDuration(200).start();
            ivBatteryContainer.animate().alpha(1.0f).setDuration(200).start();
            ivAlarmBell.animate().alpha(1.0f).setDuration(200).start();
            ivExit.animate().alpha(1.0f).setDuration(200).start();
        }
        
        // Ensure everything is fully visible
        ivSettings.setAlpha(1.0f);
        ivBatteryContainer.setAlpha(1.0f);
        ivAlarmBell.setAlpha(1.0f);
        ivExit.setAlpha(1.0f);
    }

    private void fadeAttributesOut() {
        isControlsFaded = true;
        ivSettings.animate().alpha(0.0f).setDuration(1500).start();
        ivBatteryContainer.animate().alpha(0.0f).setDuration(1500).start();
        ivExit.animate().alpha(0.0f).setDuration(1500).start();
        
        // Task 7: Hide the alarm bell icon only if alarms are disabled
        if (!alarmEnabled) {
            ivAlarmBell.animate().alpha(0.0f).setDuration(1500).start();
        }
    }

    /**
     * Dynamically adjusts the text sizes of the clock, glucose, and IoB TextViews
     * based on the physical screen width (in dp) to prevent text clipping on short/narrow screens.
     */
    private void adjustTextSizes() {
        try {
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            float widthDp = metrics.widthPixels / metrics.density;
            
            // Base scaling factor targeting an 800dp wide standard screen
            float scale = widthDp / 800f;
            
            // Safe bounds for the scaling factor to avoid extreme micro/macro scales
            if (scale < 0.55f) scale = 0.55f;
            if (scale > 1.25f) scale = 1.25f;
            
            int timeTextSize = (int) (180 * scale);
            
            tvHours.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, timeTextSize);
            tvColon.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, timeTextSize);
            tvMinutes.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, timeTextSize);
            
            Log.d(TAG, "Clock text scaling applied: widthDp=" + widthDp + ", scale=" + scale + ", timeSp=" + timeTextSize);
            
            // Dynamically scale glucose and IoB based on screen sizes and actual content
            adjustGlucoseAndIoBTextSizes();
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting text sizes: " + e.getMessage());
        }
    }

    /**
     * Dynamically adjusts the text sizes of the glucose and IoB TextViews based on physical screen
     * width (in dp) and actual text length/visibility to prevent clipping on short/narrow screens.
     */
    private void adjustGlucoseAndIoBTextSizes() {
        try {
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            float widthDp = metrics.widthPixels / metrics.density;
            
            // Base scaling factor targeting an 800dp wide standard screen
            float scale = widthDp / 800f;
            
            // Safe bounds for the scaling factor to avoid extreme micro/macro scales
            if (scale < 0.55f) scale = 0.55f;
            if (scale > 1.25f) scale = 1.25f;
            
            String glucoseText = tvGlucose.getText() != null ? tvGlucose.getText().toString() : "";
            boolean isIoBVisible = tvIoB.getVisibility() == View.VISIBLE;
            String iobText = tvIoB.getText() != null ? tvIoB.getText().toString() : "";
            
            int glucoseLen = glucoseText.length();
            int iobLen = isIoBVisible ? iobText.length() : 0;
            
            float lengthFactor = 1.0f;
            
            // Adjust factor if the text load is high (e.g. 11.2↗ or double arrows) and IoB is present
            if (glucoseLen >= 5 || (glucoseLen >= 4 && iobLen > 0)) {
                if (widthDp < 700) {
                    lengthFactor = 0.90f; // Narrow screen (like Alcatel Shine) with 3-digit sugar & IoB: reduce by 10%
                } else if (widthDp < 850) {
                    lengthFactor = 0.93f; // Medium screen: reduce by 7%
                }
            }
            
            // Extreme load (e.g. 6 chars sugar like 11.2⇈ + 4 chars IoB like 1.25)
            if (glucoseLen >= 6 && iobLen >= 4) {
                lengthFactor *= 0.95f; // Extra 5% reduction
            }
            
            int glucoseTextSize = (int) (160 * scale * lengthFactor);
            int iobTextSize = (int) (48 * scale * lengthFactor);
            
            tvGlucose.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, glucoseTextSize);
            tvIoB.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, iobTextSize);
            
            Log.d(TAG, "Dynamic glucose/iob text scaling applied: widthDp=" + widthDp + ", baseScale=" + scale 
                + ", lengthFactor=" + lengthFactor + ", glucoseSp=" + glucoseTextSize + ", iobSp=" + iobTextSize);
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting glucose/iob text sizes: " + e.getMessage());
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustTextSizes();
    }

    private void initFlashlight() {
        gestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isAlarmSounding) {
                    return false; // Snooze handled in onSingleTapConfirmed/onClick
                }
                toggleFlashlight();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isAlarmSounding) {
                    snoozeAlarm();
                    return true;
                }
                return false;
            }
        });

        // Set touch listener on root layout to detect double-tap on empty areas
        mainRootLayout.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        // Set touch listener on the white overlay so we can double-tap to turn it off
        viewFlashlightOverlay.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void toggleFlashlight() {
        if (isActivityDestroyed) return;

        isFlashlightActive = !isFlashlightActive;

        if (flashlightBrightnessAnimator != null) {
            flashlightBrightnessAnimator.cancel();
        }

        if (isFlashlightActive) {
            // Fade in white overlay
            viewFlashlightOverlay.setAlpha(0.0f);
            viewFlashlightOverlay.setVisibility(View.VISIBLE);
            viewFlashlightOverlay.animate().alpha(1.0f).setDuration(750).start();

            // Smoothly animate screen brightness up to maximum (1.0f)
            flashlightBrightnessAnimator = ValueAnimator.ofFloat(0.1f, 1.0f);
            flashlightBrightnessAnimator.setDuration(750);
            flashlightBrightnessAnimator.addUpdateListener(animation -> {
                if (isActivityDestroyed) return;
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = (float) animation.getAnimatedValue();
                getWindow().setAttributes(lp);
            });
            flashlightBrightnessAnimator.start();
        } else {
            // Fade out white overlay
            viewFlashlightOverlay.animate().alpha(0.0f).setDuration(750).withEndAction(() -> {
                viewFlashlightOverlay.setVisibility(View.GONE);
            }).start();

            // Smoothly animate screen brightness down to minimal, then restore default
            flashlightBrightnessAnimator = ValueAnimator.ofFloat(1.0f, 0.1f);
            flashlightBrightnessAnimator.setDuration(750);
            flashlightBrightnessAnimator.addUpdateListener(animation -> {
                if (isActivityDestroyed) return;
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = (float) animation.getAnimatedValue();
                getWindow().setAttributes(lp);
            });
            flashlightBrightnessAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (isActivityDestroyed) return;
                    android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; // restore default
                    getWindow().setAttributes(lp);
                }
            });
            flashlightBrightnessAnimator.start();
        }
    }
}
