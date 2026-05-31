package com.diaclock.nightstand;

import android.content.Intent;
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
    private TextView tvGlucoseUnit;
    private ImageView ivSettings;
    private ImageView ivAlarmBell;
    private ImageView ivNetworkWarning;

    // Handlers and Runnables
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler breathingHandler = new Handler(Looper.getMainLooper());
    
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload all configuration values in case they were modified in SettingsActivity
        loadSettings();
        updateAlarmBellIcon();
        applyTextColor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAlarmSound();
        mainHandler.removeCallbacksAndMessages(null);
        breathingHandler.removeCallbacksAndMessages(null);
    }

    private void initViews() {
        mainRootLayout = findViewById(R.id.mainRootLayout);
        layoutTime = findViewById(R.id.layoutTime);
        layoutGlucose = findViewById(R.id.layoutGlucose);
        tvHours = findViewById(R.id.tvHours);
        tvColon = findViewById(R.id.tvColon);
        tvMinutes = findViewById(R.id.tvMinutes);
        tvGlucose = findViewById(R.id.tvGlucose);
        tvGlucoseUnit = findViewById(R.id.tvGlucoseUnit);
        ivSettings = findViewById(R.id.ivSettings);
        ivAlarmBell = findViewById(R.id.ivAlarmBell);
        ivNetworkWarning = findViewById(R.id.ivNetworkWarning);
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
        // Toggle Alarms Enabled state (Bell) with informative Toast hints
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

        // Tapping the central screen color cycles is disabled now, as requested.
        findViewById(R.id.centralClickArea).setOnClickListener(null);
    }

    private void updateAlarmBellIcon() {
        if (alarmEnabled) {
            ivAlarmBell.setImageResource(R.drawable.ic_bell_active);
        } else {
            ivAlarmBell.setImageResource(R.drawable.ic_bell_disabled);
        }
    }

    // Apply the customizable solid text color chosen in SettingsActivity
    private void applyTextColor() {
        tvHours.setTextColor(textColor);
        tvColon.setTextColor(textColor);
        tvMinutes.setTextColor(textColor);
        tvGlucose.setTextColor(textColor);
        
        // Remove shaders to let the solid color shine
        tvHours.getPaint().setShader(null);
        tvColon.getPaint().setShader(null);
        tvMinutes.getPaint().setShader(null);
        tvGlucose.getPaint().setShader(null);
        
        tvHours.invalidate();
        tvColon.invalidate();
        tvMinutes.invalidate();
        tvGlucose.invalidate();
    }

    // Convert xDrip+ English direction strings into clean Unicode trend arrows
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
                mainHandler.postDelayed(this, 60000L); // Pull every 60s
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
                    applyTextColor();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        showNetworkWarning(true);
                        tvGlucose.setText("---");
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
                            sgv = array.get(0).getAsJsonObject().get("sgv").getAsInt();
                            if (array.get(0).getAsJsonObject().has("direction")) {
                                direction = array.get(0).getAsJsonObject().get("direction").getAsString();
                            }
                        }
                    } else if (element.isJsonObject()) {
                        sgv = element.getAsJsonObject().get("sgv").getAsInt();
                        if (element.getAsJsonObject().has("direction")) {
                            direction = element.getAsJsonObject().get("direction").getAsString();
                        }
                    }

                    if (sgv > 0) {
                        final double glucoseMmol = sgv / 18.0;
                        lastGlucoseMmol = glucoseMmol;
                        final String finalDirection = direction;
                        lastDirection = direction;
                        
                        runOnUiThread(() -> {
                            showNetworkWarning(false);
                            // Append the parsed Unicode trend arrow directly onto tvGlucose text
                            tvGlucose.setText(String.format(Locale.US, "%.1f %s", glucoseMmol, getTrendArrow(finalDirection)));
                            applyTextColor();
                            checkAlarms(glucoseMmol);
                        });
                    } else {
                        runOnUiThread(() -> {
                            showNetworkWarning(true);
                            tvGlucose.setText("---");
                            applyTextColor();
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parsing JSON failed: " + e.getMessage());
                    runOnUiThread(() -> {
                        showNetworkWarning(true);
                        tvGlucose.setText("---");
                        applyTextColor();
                    });
                }
            }
        });
    }

    private void showNetworkWarning(boolean error) {
        hasConnectionError = error;
        if (error) {
            ivNetworkWarning.setVisibility(View.VISIBLE);
        } else {
            ivNetworkWarning.setVisibility(View.GONE);
        }
    }

    // 5. Intelligent Multi-time Slot Alarms
    private void checkAlarms(double glucoseVal) {
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

        if (glucoseVal < currentLow || glucoseVal > currentHigh) {
            if (alarmEnabled) {
                startAlarmSound();
            } else {
                stopAlarmSound();
            }
        } else {
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
                mediaPlayer.start();
                
                Toast.makeText(this, "Внимание! Сахар вышел из нормы!", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Playing alarm sound failed: " + e.getMessage());
            }
        }
    }

    private void stopAlarmSound() {
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
}
