package com.diaclock.nightstand;

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
    
    // New UI elements for IoB & Battery
    private TextView tvIoB;
    private View ivBatteryContainer;
    private ImageView ivBatteryIcon;
    private TextView tvBatteryPercent;

    // Handlers and Runnables
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler breathingHandler = new Handler(Looper.getMainLooper());
    private final Handler inactivityHandler = new Handler(Looper.getMainLooper());
    private final Handler pixelShiftHandler = new Handler(Looper.getMainLooper());
    
    private boolean isControlsFaded = false;
    private final Runnable inactivityRunnable = this::fadeAttributesOut;
    
    private final Runnable pixelShiftRunnable = new Runnable() {
        @Override
        public void run() {
            // Random offset between -16 and +16 pixels
            float shiftX = (float) (Math.random() * 32 - 16);
            float shiftY = (float) (Math.random() * 32 - 16);
            
            View viewToShift = findViewById(R.id.centralClickArea);
            if (viewToShift != null) {
                viewToShift.setTranslationX(shiftX);
                viewToShift.setTranslationY(shiftY);
            }
            
            // Repeat every 2 minutes
            pixelShiftHandler.postDelayed(this, 120000L);
        }
    };
    
    // Toggle state variables
    private boolean isShowingTime = true;
    private int toggleIntervalSeconds = 5;
    
    // Network variables
    private final OkHttpClient httpClient = new OkHttpClient();
    private String serverIp = "192.168.0.111";
    private String apiSecret = "FBB9F80F9AC22E5B15F6DA1FFE599E14";
    private double lastGlucoseMmol = -1.0;
    private String lastDirection = "";
    private boolean hasConnectionError = false;

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

    // Breathing Animation variables
    private float currentAlpha = 1.0f;
    private boolean alphaDecreasing = true;
    private final float alphaStep = 0.05f;

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

        // Start tasks
        startTimeUpdates();
        startBreathingAnimation();
        startToggleCycle();
        startNetworkPolling();
        
        // Register battery monitor
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        
        // Start pixel shifting protection
        pixelShiftHandler.post(pixelShiftRunnable);
        
        // Initial inactivity trigger
        resetUserInactivityTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload all configuration values in case they were modified in SettingsActivity
        loadSettings();
        updateAlarmBellIcon();
        applyTextColor();
        
        // Refresh inactivity timer on resume
        resetUserInactivityTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAlarmSound();
        mainHandler.removeCallbacksAndMessages(null);
        breathingHandler.removeCallbacksAndMessages(null);
        
        // Clean up battery receiver
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Unregistering battery receiver failed: " + e.getMessage());
        }
        
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
        
        tvIoB = findViewById(R.id.tvIoB);
        ivBatteryContainer = findViewById(R.id.ivBatteryContainer);
        ivBatteryIcon = findViewById(R.id.ivBatteryIcon);
        tvBatteryPercent = findViewById(R.id.tvBatteryPercent);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        serverIp = prefs.getString("ip_address", "192.168.0.111");
        apiSecret = prefs.getString("api_secret", "FBB9F80F9AC22E5B15F6DA1FFE599E14");
        toggleIntervalSeconds = prefs.getInt("toggle_interval", 5);
        alarmEnabled = prefs.getBoolean("alarm_enabled", true);
        textColor = prefs.getInt("text_color", Color.WHITE);
    }

    private void setupListeners() {
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
            ? "Сигнал автоматически отложен на " + snoozeMin + " мин." 
            : "Сигнал отложен на " + snoozeMin + " мин.";
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
        Runnable timeRunnable = new Runnable() {
            @Override
            public void run() {
                Calendar c = Calendar.getInstance();
                String hoursStr = String.format(Locale.US, "%02d", c.get(Calendar.HOUR_OF_DAY));
                String minutesStr = String.format(Locale.US, "%02d", c.get(Calendar.MINUTE));

                tvHours.setText(hoursStr);
                tvMinutes.setText(minutesStr);
                
                applyTextColor();
                mainHandler.postDelayed(this, 1000); // Poll clock checks every second
            }
        };
        mainHandler.post(timeRunnable);
    }

    // 2. Colon Breathing Animation powered strictly by a Handler
    private void startBreathingAnimation() {
        Runnable breathingRunnable = new Runnable() {
            @Override
            public void run() {
                if (alphaDecreasing) {
                    currentAlpha -= alphaStep;
                    if (currentAlpha <= 0.3f) {
                        currentAlpha = 0.3f;
                        alphaDecreasing = false;
                    }
                } else {
                    currentAlpha += alphaStep;
                    if (currentAlpha >= 1.0f) {
                        currentAlpha = 1.0f;
                        alphaDecreasing = true;
                    }
                }
                tvColon.setAlpha(currentAlpha);
                breathingHandler.postDelayed(this, 50);
            }
        };
        breathingHandler.post(breathingRunnable);
    }

    // 3. Central view toggling Handler
    private void startToggleCycle() {
        Runnable toggleRunnable = new Runnable() {
            @Override
            public void run() {
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
        mainHandler.postDelayed(toggleRunnable, toggleIntervalSeconds * 1000L);
    }

    // 4. Nightscout / xDrip Network Integration
    private void startNetworkPolling() {
        Runnable networkRunnable = new Runnable() {
            @Override
            public void run() {
                fetchGlucoseData();
                mainHandler.postDelayed(this, 10000L); // Pull every 10s
            }
        };
        mainHandler.post(networkRunnable);
    }

    private String computeSHA1(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] messageDigest = md.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
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

    private void fetchGlucoseData() {
        if (serverIp == null || serverIp.trim().isEmpty()) {
            showNetworkWarning(true);
            return;
        }

        String url = "http://" + serverIp + ":17580/sgv.json";
        
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (apiSecret != null && !apiSecret.trim().isEmpty()) {
            String hashedSecret = computeSHA1(apiSecret);
            requestBuilder.addHeader("api-secret", hashedSecret);
        }
        Request request = requestBuilder.build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network call failed: " + e.getMessage());
                runOnUiThread(() -> {
                    showNetworkWarning(true);
                    tvGlucose.setText("---");
                    tvIoB.setVisibility(View.GONE);
                    applyTextColor();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        showNetworkWarning(true);
                        tvGlucose.setText("---");
                        tvIoB.setVisibility(View.GONE);
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
                        lastGlucoseMmol = glucoseMmol;
                        final String finalDirection = direction;
                        lastDirection = direction;
                        
                        runOnUiThread(() -> {
                            showNetworkWarning(false);
                            tvGlucose.setText(String.format(Locale.US, "%.1f%s", glucoseMmol, getTrendArrow(finalDirection)));
                            applyTextColor();
                            checkAlarms(glucoseMmol);
                        });
                        
                        // Fetch IoB from the separate /pebble endpoint
                        fetchIoBData();
                    } else {
                        runOnUiThread(() -> {
                            showNetworkWarning(true);
                            tvGlucose.setText("---");
                            tvIoB.setVisibility(View.GONE);
                            applyTextColor();
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parsing SGV JSON failed: " + e.getMessage());
                    runOnUiThread(() -> {
                        showNetworkWarning(true);
                        tvGlucose.setText("---");
                        tvIoB.setVisibility(View.GONE);
                        applyTextColor();
                    });
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
        if (apiSecret != null && !apiSecret.trim().isEmpty()) {
            String hashedSecret = computeSHA1(apiSecret);
            requestBuilder.addHeader("api-secret", hashedSecret);
        }
        Request pebbleRequest = requestBuilder.build();

        httpClient.newCall(pebbleRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d(TAG, "Pebble IoB fetch failed: " + e.getMessage());
                runOnUiThread(() -> tvIoB.setVisibility(View.GONE));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> tvIoB.setVisibility(View.GONE));
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
                    runOnUiThread(() -> {
                        if (finalIob >= 0) {
                            tvIoB.setText(String.format(Locale.US, "%.2f", finalIob));
                            tvIoB.setVisibility(View.VISIBLE);
                            Log.d(TAG, "IoB displayed: " + finalIob);
                        } else {
                            tvIoB.setVisibility(View.GONE);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Parsing Pebble IoB failed: " + e.getMessage());
                    runOnUiThread(() -> tvIoB.setVisibility(View.GONE));
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
        if (error) {
            ivNetworkWarning.setVisibility(View.VISIBLE);
        } else {
            ivNetworkWarning.setVisibility(View.GONE);
        }
    }

    // 5. Intelligent Multi-time Slot Alarms with Snooze & Auto-resets
    private void checkAlarms(double glucoseVal) {
        if (!alarmEnabled) {
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
                
                Toast.makeText(this, "Внимание! Сахар вышел из нормы! (Тапните для откладывания)", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Playing alarm sound failed: " + e.getMessage());
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
        }
        
        // Ensure everything is fully visible
        ivSettings.setAlpha(1.0f);
        ivBatteryContainer.setAlpha(1.0f);
        ivAlarmBell.setAlpha(1.0f);
    }

    private void fadeAttributesOut() {
        isControlsFaded = true;
        ivSettings.animate().alpha(0.0f).setDuration(1500).start();
        ivBatteryContainer.animate().alpha(0.0f).setDuration(1500).start();
        
        // Task 7: Hide the alarm bell icon only if alarms are disabled
        if (!alarmEnabled) {
            ivAlarmBell.animate().alpha(0.0f).setDuration(1500).start();
        }
    }
}
