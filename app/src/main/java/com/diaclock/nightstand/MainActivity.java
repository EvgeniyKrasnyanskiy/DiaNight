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
    
    // New UI elements for IoB, Battery & Active Source Badge
    private TextView tvIoB;
    private TextView tvDataSourceBadge;
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

    // Screen Brightness & Vertical Swipe Dimmer UI
    private static final String PREF_SCREEN_BRIGHTNESS = "screen_brightness_percent";
    private View layoutBrightnessHud;
    private TextView tvBrightnessHudValue;
    private final Handler brightnessHudHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideBrightnessHudRunnable = () -> {
        if (layoutBrightnessHud != null && !isActivityDestroyed) {
            layoutBrightnessHud.animate().alpha(0.0f).setDuration(350).withEndAction(() -> {
                if (layoutBrightnessHud != null) layoutBrightnessHud.setVisibility(View.GONE);
            }).start();
        }
    };
    private float currentScreenBrightness = -1.0f;
    private float touchStartX = 0f;
    private float touchStartY = 0f;
    private boolean isVerticalSwipe = false;
    private float initialSwipeBrightness = 0.5f;
    
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
    private boolean sourceBroadcastEnabled = false;
    private boolean sourceNightscoutEnabled = false;
    private boolean sourceWifiEnabled = true;
    private boolean isReceiverRegistered = false;
    private boolean nightlightMode = false;

    // Network & Nightscout variables
    private String serverIp = "192.168.0.111";
    private String apiSecret = "FBB9F80F9AC22E5B15F6DA1FFE599E14";
    private String cachedSecretHash = null; // Cached SHA-1 hash of Wi-Fi xDrip apiSecret
    private String nightscoutUrl = "";
    private String nightscoutApiSecret = "";
    private String cachedNightscoutSecretHash = null; // Cached SHA-1 hash of Nightscout apiSecret
    private final GlucoseDataFetcher dataFetcher = new GlucoseDataFetcher();
    private volatile long lastBroadcastTimestampMs = 0L;
    private String activeSourceTag = "";
    private double lastGlucoseMmol = -1.0;
    private String lastDirection = "";
    private volatile boolean hasConnectionError = false;

    // Automatic background master IP discovery fields
    private volatile int consecutiveNetworkFailures = 0;
    private volatile boolean isAutoScanningSubnet = false;

    // Adaptive polling interval with exponential backoff on errors (default 60s)
    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 60;
    private static final int DEFAULT_STALE_THRESHOLD_MINUTES = 2;
    private volatile long basePollIntervalMs = DEFAULT_POLL_INTERVAL_SECONDS * 1000L;
    private volatile long maxPollIntervalMs = 300_000L;
    private volatile long networkPollInterval = DEFAULT_POLL_INTERVAL_SECONDS * 1000L;
    private volatile long staleThresholdMs = DEFAULT_STALE_THRESHOLD_MINUTES * 60_000L;
    
    // New features state
    private boolean alarmUseFlash = false;
    private boolean enableAutoClose = false;
    private String autoCloseTime = "07:00";
    private String lastAutoCloseTriggeredTime = "";
    private boolean disableAutoScan = false;
    private boolean wifiWatchdogEnabled = false;
    private long lastWifiRestartTimestamp = 0;
    private android.net.wifi.WifiManager.WifiLock wifiLock = null;
    private int glucoseBrightness = 80;
    private String alarmSoundType = SoundGenerator.SOUND_BUILTIN_PULSE;
    private String alarmUriStr = null;

    // Camera Flash Alarm state and handlers
    private final Handler flashHandler = new Handler(Looper.getMainLooper());
    private boolean isFlashPulsing = false;
    private boolean isFlashOn = false;
    private android.hardware.Camera cameraLegacy = null;

    private final Runnable flashBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAlarmSounding || !alarmUseFlash) {
                stopFlashBlinking();
                return;
            }
            isFlashOn = !isFlashOn;
            setHardwareTorchMode(isFlashOn);
            flashHandler.postDelayed(this, 500);
        }
    };

    private String torchCameraId = null;

    private String getFlashCameraId(android.hardware.camera2.CameraManager cameraManager) {
        if (cameraManager == null) return null;
        try {
            String[] ids = cameraManager.getCameraIdList();
            if (ids == null) return null;
            // 1. Prefer back-facing camera with flash
            for (String id : ids) {
                android.hardware.camera2.CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(hasFlash) && facing != null &&
                        facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                    return id;
                }
            }
            // 2. Fallback to any camera with flash
            for (String id : ids) {
                android.hardware.camera2.CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) {
                    return id;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving flash camera ID: " + e.getMessage());
        }
        return null;
    }

    private void setHardwareTorchMode(boolean on) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                android.hardware.camera2.CameraManager cameraManager = (android.hardware.camera2.CameraManager) getSystemService(Context.CAMERA_SERVICE);
                if (cameraManager != null) {
                    if (torchCameraId == null) {
                        torchCameraId = getFlashCameraId(cameraManager);
                    }
                    if (torchCameraId != null) {
                        cameraManager.setTorchMode(torchCameraId, on);
                    }
                }
            } else {
                if (on) {
                    if (cameraLegacy == null) {
                        cameraLegacy = android.hardware.Camera.open();
                    }
                    if (cameraLegacy != null) {
                        android.hardware.Camera.Parameters p = cameraLegacy.getParameters();
                        p.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_TORCH);
                        cameraLegacy.setParameters(p);
                        cameraLegacy.startPreview();
                    }
                } else {
                    if (cameraLegacy != null) {
                        try {
                            android.hardware.Camera.Parameters p = cameraLegacy.getParameters();
                            p.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_OFF);
                            cameraLegacy.setParameters(p);
                            cameraLegacy.stopPreview();
                        } catch (Exception e) {
                            // ignore
                        } finally {
                            cameraLegacy.release();
                            cameraLegacy = null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Hardware torch control failed: " + e.getMessage());
        }
    }

    private void startFlashBlinking() {
        if (isFlashPulsing) return;
        isFlashPulsing = true;
        flashHandler.removeCallbacks(flashBlinkRunnable);
        flashHandler.post(flashBlinkRunnable);
    }

    private void stopFlashBlinking() {
        isFlashPulsing = false;
        isFlashOn = false;
        flashHandler.removeCallbacks(flashBlinkRunnable);
        setHardwareTorchMode(false);
    }

    // Runnable fields for explicit lifecycle control
    private final Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            Calendar c = Calendar.getInstance();
            int currentH = c.get(Calendar.HOUR_OF_DAY);
            int currentM = c.get(Calendar.MINUTE);
            String hoursStr = String.format(Locale.US, "%02d", currentH);
            String minutesStr = String.format(Locale.US, "%02d", currentM);

            // Only update UI if text actually changed to reduce unnecessary rendering
            if (!hoursStr.equals(lastDisplayedHours)) {
                tvHours.setText(hoursStr);
                lastDisplayedHours = hoursStr;
            }
            if (!minutesStr.equals(lastDisplayedMinutes)) {
                tvMinutes.setText(minutesStr);
                lastDisplayedMinutes = minutesStr;
            }
            
            // Check scheduled auto-close trigger
            String currentTime = hoursStr + ":" + minutesStr;
            if (enableAutoClose && isAutoCloseTimeMatch(currentH, currentM, autoCloseTime) && !currentTime.equals(lastAutoCloseTriggeredTime)) {
                lastAutoCloseTriggeredTime = currentTime;
                Log.i(TAG, "Scheduled auto-close time (" + currentTime + ") reached. Finishing activity.");
                Toast.makeText(MainActivity.this, getString(R.string.msg_autoclose_triggered), Toast.LENGTH_SHORT).show();
                mainHandler.postDelayed(() -> {
                    if (android.os.Build.VERSION.SDK_INT >= 21) {
                        finishAndRemoveTask();
                    } else {
                        finish();
                    }
                }, 1000);
            }

            mainHandler.postDelayed(this, 1000); // Poll clock checks every second
        }
    };
    private ValueAnimator alarmVisualAnimator = null;
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
        if (savedInstanceState != null) {
            lastGlucoseMmol = savedInstanceState.getDouble("lastGlucoseMmol", -1.0);
            lastDirection = savedInstanceState.getString("lastDirection", "");
            hasConnectionError = savedInstanceState.getBoolean("hasConnectionError", false);
            isShowingTime = savedInstanceState.getBoolean("isShowingTime", true);
            alarmSnoozeUntilTime = savedInstanceState.getLong("alarmSnoozeUntilTime", 0L);
        }
        setupListeners();

        // Track installation analytics
        TelemetryTracker.trackInstall(this);

        // Start foreground monitoring service to protect against LMK termination
        NightMonitorService.start(this);

        // Start tasks
        startTimeUpdates();
        startToggleCycle();
        
        if (nightlightMode) {
            layoutGlucose.setVisibility(View.GONE);
            layoutTime.setVisibility(View.VISIBLE);
            ivAlarmBell.setVisibility(View.GONE);
            ivNetworkWarning.setVisibility(View.GONE);
            if (tvDataSourceBadge != null) tvDataSourceBadge.setVisibility(View.GONE);
        } else {
            if (sourceBroadcastEnabled) {
                registerXdripReceiver();
            }
            if (lastGlucoseMmol > 0) {
                tvGlucose.setText(String.format(Locale.US, "%.1f%s", lastGlucoseMmol, getTrendArrow(lastDirection)));
            } else {
                tvGlucose.setText(isAnyNetworkSourceEnabled() ? "---" : getString(R.string.msg_waiting));
            }
            if (!isAnyNetworkSourceEnabled()) {
                tvIoB.setVisibility(View.GONE);
            } else {
                startNetworkPolling();
            }
        }
        
        // Register battery monitor
        registerBatteryReceiver();
        
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

        // Resume alarm visual animation if sounding
        if (isAlarmSounding && alarmVisualAnimator != null) {
            if (android.os.Build.VERSION.SDK_INT >= 19 && alarmVisualAnimator.isPaused()) {
                alarmVisualAnimator.resume();
            } else if (!alarmVisualAnimator.isRunning()) {
                alarmVisualAnimator.start();
            }
        }

        // Resume battery monitoring
        registerBatteryReceiver();

        // Resume time updates and pixel shifting
        startTimeUpdates();
        pixelShiftHandler.removeCallbacks(pixelShiftRunnable);
        pixelShiftHandler.post(pixelShiftRunnable);

        boolean oldBroadcast = sourceBroadcastEnabled;
        boolean oldNightscout = sourceNightscoutEnabled;
        boolean oldWifi = sourceWifiEnabled;
        boolean oldNightlight = nightlightMode;
        loadSettings();
        updateAlarmBellIcon();
        applyTextColor();

        if (nightlightMode) {
            // Unregister/Stop monitoring to save battery
            unregisterXdripReceiver();
            dataFetcher.cancel();
            mainHandler.removeCallbacks(networkRunnable);
            mainHandler.removeCallbacks(toggleRunnable);
            releaseWifiLock();

            // Adjust layouts and icons
            layoutGlucose.setVisibility(View.GONE);
            layoutTime.setVisibility(View.VISIBLE);
            ivAlarmBell.setVisibility(View.GONE);
            ivNetworkWarning.setVisibility(View.GONE);
            if (tvDataSourceBadge != null) tvDataSourceBadge.setVisibility(View.GONE);
            stopAlarmSound();
        } else {
            // Hold WifiLock only when a network data source is enabled
            if (isAnyNetworkSourceEnabled()) {
                acquireWifiLock();
            } else {
                releaseWifiLock();
            }

            // Restore alarm bell visibility (warning only if there is a network error)
            ivAlarmBell.setVisibility(View.VISIBLE);
            if (hasConnectionError) {
                ivNetworkWarning.setVisibility(View.VISIBLE);
            } else {
                ivNetworkWarning.setVisibility(View.GONE);
            }
            if (tvDataSourceBadge != null && !activeSourceTag.isEmpty()) {
                tvDataSourceBadge.setVisibility(View.VISIBLE);
            }

            // Restart the toggle cycle when app is active
            startToggleCycle();

            boolean sourcesChanged = (oldBroadcast != sourceBroadcastEnabled)
                    || (oldNightscout != sourceNightscoutEnabled)
                    || (oldWifi != sourceWifiEnabled)
                    || oldNightlight;

            if (sourceBroadcastEnabled) {
                registerXdripReceiver();
            } else {
                unregisterXdripReceiver();
            }

            if (isAnyNetworkSourceEnabled()) {
                if (sourcesChanged) {
                    consecutiveNetworkFailures = 0;
                    networkPollInterval = basePollIntervalMs;
                }
                mainHandler.removeCallbacks(networkRunnable);
                mainHandler.post(networkRunnable);
            } else {
                dataFetcher.cancel();
                mainHandler.removeCallbacks(networkRunnable);
                if (sourcesChanged && lastGlucoseMmol <= 0) {
                    tvGlucose.setText(getString(R.string.msg_waiting));
                }
                tvIoB.setVisibility(View.GONE);
            }
        }

        // Ensure foreground monitoring service is active
        NightMonitorService.start(this);

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
        if (alarmVisualAnimator != null) {
            if (android.os.Build.VERSION.SDK_INT >= 19) {
                alarmVisualAnimator.pause();
            } else {
                alarmVisualAnimator.cancel();
            }
        }
        mainHandler.removeCallbacks(timeRunnable);
        mainHandler.removeCallbacks(networkRunnable);
        mainHandler.removeCallbacks(toggleRunnable);
        dataFetcher.cancel();
        pixelShiftHandler.removeCallbacks(pixelShiftRunnable);
        inactivityHandler.removeCallbacks(inactivityRunnable);
        stopFlashBlinking();
        releaseWifiLock();
        unregisterBatteryReceiver();
        unregisterXdripReceiver();
    }

    @Override
    protected void onDestroy() {
        isActivityDestroyed = true;
        super.onDestroy();
        stopAlarmSound();
        releaseWifiLock();
        mainHandler.removeCallbacksAndMessages(null);
        brightnessHudHandler.removeCallbacksAndMessages(null);

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
        unregisterBatteryReceiver();

        // Clean up xDrip receiver
        unregisterXdripReceiver();

        // Clean up OkHttp resources
        dataFetcher.cancel();
        HttpClientProvider.getClient().dispatcher().cancelAll();

        // Stop foreground monitoring service
        NightMonitorService.stop(this);

        // Clean up handlers
        pixelShiftHandler.removeCallbacksAndMessages(null);
        inactivityHandler.removeCallbacksAndMessages(null);
    }

    private void registerBatteryReceiver() {
        if (!isBatteryReceiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(batteryReceiver, filter);
            }
            isBatteryReceiverRegistered = true;
            Log.d(TAG, "BatteryReceiver registered.");
        }
    }

    private void unregisterBatteryReceiver() {
        if (isBatteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Unregistering battery receiver failed: " + e.getMessage());
            }
            isBatteryReceiverRegistered = false;
            Log.d(TAG, "BatteryReceiver unregistered.");
        }
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
        tvDataSourceBadge = findViewById(R.id.tvDataSourceBadge);
        ivBatteryContainer = findViewById(R.id.ivBatteryContainer);
        ivBatteryIcon = findViewById(R.id.ivBatteryIcon);
        tvBatteryPercent = findViewById(R.id.tvBatteryPercent);
        
        // Cache views used in pixelShiftRunnable to avoid repeated findViewById
        centralClickArea = findViewById(R.id.centralClickArea);
        statusBarContainer = findViewById(R.id.statusBarContainer);

        viewFlashlightOverlay = findViewById(R.id.viewFlashlightOverlay);
        layoutBrightnessHud = findViewById(R.id.layoutBrightnessHud);
        tvBrightnessHudValue = findViewById(R.id.tvBrightnessHudValue);
    }

    private boolean isAnyNetworkSourceEnabled() {
        return sourceNightscoutEnabled || sourceWifiEnabled;
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        serverIp = prefs.getString("ip_address", "192.168.0.111");
        apiSecret = prefs.getString("api_secret", "FBB9F80F9AC22E5B15F6DA1FFE599E14");
        nightscoutUrl = prefs.getString("nightscout_url", "");
        nightscoutApiSecret = prefs.getString("nightscout_api_secret", "");
        toggleIntervalSeconds = prefs.getInt("toggle_interval", 5);
        alarmEnabled = prefs.getBoolean("alarm_enabled", true);
        textColor = prefs.getInt("text_color", Color.WHITE);

        // Load multi-source flags with backward-compatible migration from legacy data_source
        if (prefs.contains("source_broadcast_enabled")
                || prefs.contains("source_nightscout_enabled")
                || prefs.contains("source_wifi_enabled")) {
            sourceBroadcastEnabled = prefs.getBoolean("source_broadcast_enabled", false);
            sourceNightscoutEnabled = prefs.getBoolean("source_nightscout_enabled", false);
            sourceWifiEnabled = prefs.getBoolean("source_wifi_enabled", true);
        } else {
            String legacySource = prefs.getString("data_source", "network");
            sourceBroadcastEnabled = "broadcast".equals(legacySource);
            sourceNightscoutEnabled = false;
            sourceWifiEnabled = !"broadcast".equals(legacySource);
        }
        if (!sourceBroadcastEnabled && !sourceNightscoutEnabled && !sourceWifiEnabled) {
            sourceWifiEnabled = true;
        }

        int pollSec = prefs.getInt("poll_interval_seconds", DEFAULT_POLL_INTERVAL_SECONDS);
        if (pollSec < 15) pollSec = 15;
        basePollIntervalMs = pollSec * 1000L;
        maxPollIntervalMs = Math.max(basePollIntervalMs * 4L, 300_000L);
        networkPollInterval = basePollIntervalMs;

        int staleMin = prefs.getInt("stale_threshold_minutes", DEFAULT_STALE_THRESHOLD_MINUTES);
        if (staleMin < 1) staleMin = 1;
        staleThresholdMs = staleMin * 60_000L;

        nightlightMode = prefs.getBoolean("nightlight_mode", false);
        alarmUseFlash = prefs.getBoolean("alarm_use_flash", false);
        enableAutoClose = prefs.getBoolean("enable_autoclose", false);
        autoCloseTime = prefs.getString("autoclose_time", "07:00");
        disableAutoScan = prefs.getBoolean("disable_auto_scan", false);
        wifiWatchdogEnabled = prefs.getBoolean("wifi_watchdog", false);
        glucoseBrightness = prefs.getInt("glucose_brightness", 80);

        int savedBrightnessPercent = prefs.getInt(PREF_SCREEN_BRIGHTNESS, -1);
        if (savedBrightnessPercent >= 1 && savedBrightnessPercent <= 100) {
            applyScreenBrightness(savedBrightnessPercent / 100.0f);
        }

        alarmSoundType = prefs.getString("alarm_sound_type", null);
        alarmUriStr = prefs.getString("alarm_uri", null);
        if (alarmSoundType == null) {
            if ("silent".equals(alarmUriStr)) {
                alarmSoundType = SoundGenerator.SOUND_SILENT;
            } else if (alarmUriStr != null) {
                alarmSoundType = SoundGenerator.SOUND_SYSTEM;
            } else {
                alarmSoundType = SoundGenerator.SOUND_BUILTIN_PULSE;
            }
        }

        // Cache SHA-1 hashes of API secrets to avoid recomputing on every request
        cachedSecretHash = (apiSecret != null && !apiSecret.trim().isEmpty())
                ? CryptoUtils.computeSHA1(apiSecret) : null;
        cachedNightscoutSecretHash = (nightscoutApiSecret != null && !nightscoutApiSecret.trim().isEmpty())
                ? CryptoUtils.computeSHA1(nightscoutApiSecret) : null;
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
        
        // Dynamically color-code glucose values separately from clock with custom brightness dimming
        if (lastGlucoseMmol > 0) {
            int baseColor = getGlucoseStandardColor(lastGlucoseMmol);
            tvGlucose.setTextColor(applyBrightnessToColor(baseColor, glucoseBrightness));
        } else {
            tvGlucose.setTextColor(textColor); // Default color when offline
        }

        if (tvIoB != null) {
            tvIoB.setTextColor(applyBrightnessToColor(Color.parseColor("#8E8E93"), glucoseBrightness));
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

    /**
     * Returns color Int for given glucose value in mmol/L conforming strictly
     * to the 6-band clinical AGP/TIR standard (from TIRUp):
     * < 3.0: Very Low / Level 2 Hypo (#EF4444)
     * 3.0 .. 3.8: Low / Level 1 Hypo (#F59E0B)
     * 3.9 .. 7.8: Tight Target TITR (#4ADE80)
     * 7.9 .. 10.0: Target TIR (#10B981)
     * 10.1 .. 13.9: High / Level 1 Hyper (#F59E0B)
     * >= 14.0: Very High / Level 2 Hyper (#EF4444)
     */
    public static int getGlucoseStandardColor(double valueMmol) {
        if (valueMmol <= 0.0) {
            return Color.parseColor("#94A3B8");
        } else if (valueMmol < 3.0) {
            return Color.parseColor("#EF4444"); // Very Low (Urgent Red)
        } else if (valueMmol < 3.9) {
            return Color.parseColor("#F59E0B"); // Low (Warning Amber)
        } else if (valueMmol <= 7.8) {
            return Color.parseColor("#4ADE80"); // Tight Target (Bright Pale Green)
        } else if (valueMmol <= 10.0) {
            return Color.parseColor("#10B981"); // Target (Emerald Green)
        } else if (valueMmol <= 13.9) {
            return Color.parseColor("#F59E0B"); // High (Warning Amber)
        } else {
            return Color.parseColor("#EF4444"); // Very High (Urgent Red)
        }
    }

    private int applyBrightnessToColor(int color, int brightnessPercent) {
        float factor = Math.max(15, Math.min(100, brightnessPercent)) / 100.0f;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = hsv[2] * factor; // Scale V component
        return Color.HSVToColor(Color.alpha(color), hsv);
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
        mainHandler.removeCallbacks(timeRunnable);
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

    // 4. Multi-Source Polling (Broadcast -> Nightscout -> Wi-Fi xDrip+) with adaptive interval
    private final Runnable networkRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAnyNetworkSourceEnabled() && !nightlightMode && !isActivityDestroyed) {
                boolean broadcastFresh = sourceBroadcastEnabled
                        && lastBroadcastTimestampMs > 0
                        && (System.currentTimeMillis() - lastBroadcastTimestampMs) <= staleThresholdMs;

                if (broadcastFresh) {
                    Log.d(TAG, "Local broadcast data is fresh; skipping network poll.");
                    networkPollInterval = basePollIntervalMs;
                    consecutiveNetworkFailures = 0;
                    updateDataSourceBadge(GlucoseDataFetcher.SOURCE_BROADCAST, false);
                } else {
                    fetchGlucoseData();
                }
                mainHandler.postDelayed(this, networkPollInterval);
            }
        }
    };

    private void startNetworkPolling() {
        mainHandler.removeCallbacks(networkRunnable);
        if (isAnyNetworkSourceEnabled() && !nightlightMode) {
            mainHandler.post(networkRunnable);
        }
    }

    /**
     * Returns the cached SHA-1 hash of the Wi-Fi xDrip API secret.
     */
    private String getSecretHash() {
        return cachedSecretHash;
    }

    private void updateDataSourceBadge(final String sourceTag, final boolean isStale) {
        activeSourceTag = sourceTag != null ? sourceTag : "";
        if (isActivityDestroyed || tvDataSourceBadge == null) return;
        runOnUiThread(() -> {
            if (isActivityDestroyed || tvDataSourceBadge == null) return;
            if (nightlightMode || activeSourceTag.isEmpty()) {
                tvDataSourceBadge.setVisibility(View.GONE);
                return;
            }
            tvDataSourceBadge.setText(isStale ? (activeSourceTag + " ⚠") : activeSourceTag);
            tvDataSourceBadge.setTextColor(getResources().getColor(
                    isStale ? R.color.color_warning_orange : R.color.text_secondary));
            tvDataSourceBadge.setVisibility(View.VISIBLE);
            tvDataSourceBadge.setAlpha(isControlsFaded ? 0.35f : 0.85f);
        });
    }

    private void fetchGlucoseData() {
        GlucoseDataFetcher.Config config = new GlucoseDataFetcher.Config(
                sourceNightscoutEnabled,
                nightscoutUrl,
                cachedNightscoutSecretHash,
                sourceWifiEnabled,
                serverIp,
                cachedSecretHash,
                staleThresholdMs
        );

        dataFetcher.fetch(config, new GlucoseDataFetcher.FetchCallback() {
            @Override
            public void onFreshReading(@NonNull GlucoseDataFetcher.GlucoseReading reading) {
                networkPollInterval = basePollIntervalMs;
                consecutiveNetworkFailures = 0;
                if (isActivityDestroyed) return;
                runOnUiThread(() -> applyGlucoseReading(reading, false));
            }

            @Override
            public void onStaleReading(@NonNull GlucoseDataFetcher.GlucoseReading reading) {
                networkPollInterval = basePollIntervalMs;
                consecutiveNetworkFailures = 0;
                if (isActivityDestroyed) return;
                runOnUiThread(() -> applyGlucoseReading(reading, true));
            }

            @Override
            public void onFailure(boolean wifiAttemptedAndFailed) {
                networkPollInterval = Math.min(networkPollInterval * 2, maxPollIntervalMs);
                consecutiveNetworkFailures++;
                if (wifiAttemptedAndFailed && sourceWifiEnabled && consecutiveNetworkFailures >= 3 && !isAutoScanningSubnet) {
                    triggerSilentSubnetAutoDiscovery();
                }
                if (consecutiveNetworkFailures >= 10 && wifiWatchdogEnabled) {
                    triggerWifiWatchdogRestart();
                }
                if (isActivityDestroyed) return;
                runOnUiThread(() -> {
                    boolean showWarning = consecutiveNetworkFailures >= 3;
                    showNetworkWarning(showWarning);
                    if (consecutiveNetworkFailures >= 10) {
                        tvGlucose.setText("---");
                        tvIoB.setVisibility(View.GONE);
                        adjustGlucoseAndIoBTextSizes();
                        applyTextColor();
                    }
                });
            }
        });
    }

    private void applyGlucoseReading(@NonNull GlucoseDataFetcher.GlucoseReading reading, boolean isStale) {
        if (isActivityDestroyed) return;
        lastGlucoseMmol = reading.glucoseMmol;
        lastDirection = reading.direction;
        showNetworkWarning(isStale);
        updateDataSourceBadge(reading.sourceTag, isStale);
        tvGlucose.setText(String.format(Locale.US, "%.1f%s", reading.glucoseMmol, getTrendArrow(reading.direction)));
        if (reading.iob >= 0) {
            tvIoB.setText(String.format(Locale.US, "%.2f", reading.iob));
            tvIoB.setVisibility(View.VISIBLE);
        } else {
            tvIoB.setVisibility(View.GONE);
        }
        adjustGlucoseAndIoBTextSizes();
        applyTextColor();
        checkAlarms(reading.glucoseMmol);
    }

    private void acquireWifiLock() {
        try {
            if (wifiLock == null) {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "DiaNight:WifiLock");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
                Log.d(TAG, "WifiLock acquired (WIFI_MODE_FULL_HIGH_PERF)");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to acquire WifiLock: " + e.getMessage());
        }
    }

    private void releaseWifiLock() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                Log.d(TAG, "WifiLock released");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to release WifiLock: " + e.getMessage());
        }
    }

    private void triggerWifiWatchdogRestart() {
        if (!wifiWatchdogEnabled) return;
        long now = System.currentTimeMillis();
        if (now - lastWifiRestartTimestamp < 120_000L) {
            return; // Rate limit: don't restart more than once every 2 mins
        }
        lastWifiRestartTimestamp = now;
        Log.w(TAG, "WiFi Watchdog triggered: reconnecting / restarting WiFi module.");
        new Thread(() -> {
            try {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wm.reconnect();
                    wm.setWifiEnabled(false);
                    Thread.sleep(1500);
                    wm.setWifiEnabled(true);
                }
            } catch (Exception e) {
                Log.e(TAG, "WiFi Watchdog exception: " + e.getMessage());
            }
        }, "WifiWatchdogThread").start();
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
        
        float dayHigh = prefs.getFloat("day_high", 8.5f);
        float dayLow = prefs.getFloat("day_low", 4.5f);
        
        float nightHigh = prefs.getFloat("night_high", 10.0f);
        float nightLow = prefs.getFloat("night_low", 3.8f);

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

        if (SoundGenerator.SOUND_SILENT.equals(alarmSoundType) || "silent".equals(alarmUriStr)) {
            return; // Silent mode chosen by the user
        }

        // 1. Built-in algorithmic sound generator via AudioTrack
        if (SoundGenerator.isBuiltin(alarmSoundType)) {
            isAlarmSounding = true;
            SoundGenerator.startAlarm(alarmSoundType);

            if (alarmUseFlash) {
                startFlashBlinking();
            }

            // Start rich pulsing animation between black and deep ruby red
            if (alarmVisualAnimator == null) {
                alarmVisualAnimator = ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), Color.BLACK, Color.parseColor("#4A000A"));
                alarmVisualAnimator.setDuration(1200);
                alarmVisualAnimator.setRepeatCount(ValueAnimator.INFINITE);
                alarmVisualAnimator.setRepeatMode(ValueAnimator.REVERSE);
                alarmVisualAnimator.addUpdateListener(animation -> {
                    if (mainRootLayout != null && !isActivityDestroyed) {
                        mainRootLayout.setBackgroundColor((int) animation.getAnimatedValue());
                    }
                });
                alarmVisualAnimator.start();
            }

            // Schedule auto-snooze
            mainHandler.removeCallbacks(autoSnoozeRunnable);
            mainHandler.postDelayed(autoSnoozeRunnable, MAX_ALARM_DURATION_MS);
            
            Toast.makeText(MainActivity.this, getString(R.string.msg_alarm_warning), Toast.LENGTH_LONG).show();
            return;
        }

        // 2. System ringtone playback via MediaPlayer
        if (mediaPlayer == null) {
            try {
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
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    mediaPlayer.setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                } else {
                    mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_ALARM);
                }
                mediaPlayer.setOnPreparedListener(mp -> {
                    if (isActivityDestroyed || !alarmEnabled) {
                        stopAlarmSound();
                        return;
                    }
                    try {
                        isAlarmSounding = true;
                        mp.start();

                        if (alarmUseFlash) {
                            startFlashBlinking();
                        }

                        // Start rich pulsing animation between black and deep ruby red
                        if (alarmVisualAnimator == null) {
                            alarmVisualAnimator = ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), Color.BLACK, Color.parseColor("#4A000A"));
                            alarmVisualAnimator.setDuration(1200);
                            alarmVisualAnimator.setRepeatCount(ValueAnimator.INFINITE);
                            alarmVisualAnimator.setRepeatMode(ValueAnimator.REVERSE);
                            alarmVisualAnimator.addUpdateListener(animation -> {
                                if (mainRootLayout != null && !isActivityDestroyed) {
                                    mainRootLayout.setBackgroundColor((int) animation.getAnimatedValue());
                                }
                            });
                            alarmVisualAnimator.start();
                        }
                        
                        // Schedule auto-snooze
                        mainHandler.removeCallbacks(autoSnoozeRunnable);
                        mainHandler.postDelayed(autoSnoozeRunnable, MAX_ALARM_DURATION_MS);
                        
                        Toast.makeText(MainActivity.this, getString(R.string.msg_alarm_warning), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting MediaPlayer on prepared: " + e.getMessage());
                        stopAlarmSound();
                    }
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MediaPlayer error occurred: what=" + what + ", extra=" + extra);
                    stopAlarmSound();
                    return true;
                });
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                Log.e(TAG, "Playing alarm sound failed: " + e.getMessage());
                stopAlarmSound();
            }
        }
    }

    private void stopAlarmSound() {
        isAlarmSounding = false;
        SoundGenerator.stopAlarm();
        stopFlashBlinking();
        mainHandler.removeCallbacks(autoSnoozeRunnable);
        if (alarmVisualAnimator != null) {
            alarmVisualAnimator.cancel();
            alarmVisualAnimator = null;
        }
        if (mainRootLayout != null) {
            mainRootLayout.setBackgroundColor(Color.BLACK);
        }
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
                    lastBroadcastTimestampMs = System.currentTimeMillis();
                    consecutiveNetworkFailures = 0;
                    lastGlucoseMmol = glucoseMmol;
                    lastDirection = slopeName;
                    
                    runOnUiThread(() -> {
                        showNetworkWarning(false);
                        updateDataSourceBadge(GlucoseDataFetcher.SOURCE_BROADCAST, false);
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
            if (tvDataSourceBadge != null) {
                tvDataSourceBadge.animate().alpha(0.85f).setDuration(200).start();
            }
        }
        
        // Ensure everything is fully visible
        ivSettings.setAlpha(1.0f);
        ivBatteryContainer.setAlpha(1.0f);
        ivAlarmBell.setAlpha(1.0f);
        ivExit.setAlpha(1.0f);
        if (tvDataSourceBadge != null) {
            tvDataSourceBadge.setAlpha(0.85f);
        }
    }

    private void fadeAttributesOut() {
        isControlsFaded = true;
        ivSettings.animate().alpha(0.0f).setDuration(1500).start();
        ivBatteryContainer.animate().alpha(0.0f).setDuration(1500).start();
        ivExit.animate().alpha(0.0f).setDuration(1500).start();
        ivAlarmBell.animate().alpha(0.0f).setDuration(1500).start();
        if (tvDataSourceBadge != null) {
            tvDataSourceBadge.animate().alpha(0.35f).setDuration(1500).start();
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

            // In landscape orientation, scale up clock and sugar by 1.495x (1.3x * 1.15x)
            boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    || metrics.widthPixels > metrics.heightPixels;
            float orientationMultiplier = isLandscape ? 1.495f : 1.0f;
            
            int timeTextSize = (int) (180 * scale * orientationMultiplier);
            // Safety boundary: ensure margin clearance for pixelShiftRunnable (+-16px) on narrow screens
            int maxSafeTimeSp = (int) ((widthDp - 48) / 2.75f);
            if (timeTextSize > maxSafeTimeSp) {
                timeTextSize = maxSafeTimeSp;
            }
            
            tvHours.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, timeTextSize);
            tvColon.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, timeTextSize);
            tvMinutes.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, timeTextSize);
            
            // Dynamically scale the bottom margin of the colon based on screen width/scale
            // to keep it vertically centered with the giant digits.
            if (tvColon.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams) {
                android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) tvColon.getLayoutParams();
                lp.bottomMargin = (int) (20 * scale * orientationMultiplier * metrics.density);
                tvColon.setLayoutParams(lp);
            }
            
            Log.d(TAG, "Clock text scaling applied: widthDp=" + widthDp + ", scale=" + scale + ", orientationMult=" + orientationMultiplier + ", timeSp=" + timeTextSize);
            
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

            // In landscape orientation, scale up clock and sugar by 1.495x (1.3x * 1.15x)
            boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    || metrics.widthPixels > metrics.heightPixels;
            float orientationMultiplier = isLandscape ? 1.495f : 1.0f;
            
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
            
            int glucoseTextSize = (int) (160 * scale * lengthFactor * orientationMultiplier);
            int iobTextSize = (int) (48 * scale * lengthFactor * orientationMultiplier);
            
            // Safety boundary: ensure margin clearance for pixelShiftRunnable (+-16px) on narrow screens
            int maxSafeGlucoseSp = (int) ((widthDp - 48) / 2.85f);
            if (glucoseTextSize > maxSafeGlucoseSp) {
                glucoseTextSize = maxSafeGlucoseSp;
            }
            
            tvGlucose.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, glucoseTextSize);
            tvIoB.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, iobTextSize);
            
            Log.d(TAG, "Dynamic glucose/iob text scaling applied: widthDp=" + widthDp + ", baseScale=" + scale 
                + ", orientationMult=" + orientationMultiplier + ", lengthFactor=" + lengthFactor 
                + ", glucoseSp=" + glucoseTextSize + ", iobSp=" + iobTextSize);
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting glucose/iob text sizes: " + e.getMessage());
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustTextSizes();
    }

    private void applyScreenBrightness(float brightness) {
        if (isActivityDestroyed) return;
        currentScreenBrightness = Math.max(0.01f, Math.min(1.0f, brightness));
        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = currentScreenBrightness;
        getWindow().setAttributes(lp);
    }

    private void showBrightnessHud(int percent) {
        if (layoutBrightnessHud == null || isActivityDestroyed) return;
        brightnessHudHandler.removeCallbacks(hideBrightnessHudRunnable);
        if (tvBrightnessHudValue != null) {
            tvBrightnessHudValue.setText(percent + "%");
        }
        if (layoutBrightnessHud.getVisibility() != View.VISIBLE) {
            layoutBrightnessHud.setAlpha(0.0f);
            layoutBrightnessHud.setVisibility(View.VISIBLE);
            layoutBrightnessHud.animate().alpha(1.0f).setDuration(150).start();
        }
        brightnessHudHandler.postDelayed(hideBrightnessHudRunnable, 1000L);
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

        // Set touch listener on root layout to support vertical swipe brightness and tap gestures
        mainRootLayout.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = event.getRawX();
                    touchStartY = event.getRawY();
                    isVerticalSwipe = false;
                    android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                    initialSwipeBrightness = (lp.screenBrightness >= 0.01f) ? lp.screenBrightness : (currentScreenBrightness >= 0.01f ? currentScreenBrightness : 0.5f);
                    break;

                case MotionEvent.ACTION_MOVE:
                    float deltaY = touchStartY - event.getRawY(); // dragging UP increases, DOWN decreases
                    float deltaX = event.getRawX() - touchStartX;
                    
                    if (!isVerticalSwipe) {
                        float absDeltaY = Math.abs(deltaY);
                        float absDeltaX = Math.abs(deltaX);
                        int touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
                        if (absDeltaY > touchSlop * 1.5f && absDeltaY > absDeltaX * 1.25f) {
                            isVerticalSwipe = true;
                        }
                    }

                    if (isVerticalSwipe) {
                        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                        float screenHeight = Math.max(1, dm.heightPixels);
                        // Full vertical screen drag spans 1.0 (100%) brightness range
                        float targetBrightness = initialSwipeBrightness + (deltaY / screenHeight);
                        targetBrightness = Math.max(0.01f, Math.min(1.0f, targetBrightness));
                        applyScreenBrightness(targetBrightness);
                        showBrightnessHud(Math.round(targetBrightness * 100));
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isVerticalSwipe) {
                        isVerticalSwipe = false;
                        int percent = Math.round(currentScreenBrightness * 100);
                        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                        editor.putInt(PREF_SCREEN_BRIGHTNESS, percent);
                        editor.apply();
                        return true;
                    }
                    break;
            }

            return gestureDetector.onTouchEvent(event);
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

            // Smoothly animate screen brightness down to minimal, then restore chosen brightness
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
                    lp.screenBrightness = (currentScreenBrightness >= 0.01f) ? currentScreenBrightness : android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                    getWindow().setAttributes(lp);
                }
            });
            flashlightBrightnessAnimator.start();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble("lastGlucoseMmol", lastGlucoseMmol);
        outState.putString("lastDirection", lastDirection);
        outState.putBoolean("hasConnectionError", hasConnectionError);
        outState.putBoolean("isShowingTime", isShowingTime);
        outState.putLong("alarmSnoozeUntilTime", alarmSnoozeUntilTime);
    }

    private boolean isAutoCloseTimeMatch(int currentH, int currentM, String targetTime) {
        if (targetTime == null || !targetTime.contains(":")) return false;
        try {
            String[] parts = targetTime.split(":");
            int targetH = Integer.parseInt(parts[0].trim());
            int targetM = Integer.parseInt(parts[1].trim());
            return currentH == targetH && currentM == targetM;
        } catch (Exception e) {
            return false;
        }
    }

    private String getLocalSubnetPrefix() {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                java.net.NetworkInterface intf = en.nextElement();
                if (intf.isLoopback() || !intf.isUp()) continue;
                for (java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        String ip = inetAddress.getHostAddress();
                        if (ip != null && !ip.equals("0.0.0.0") && (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))) {
                            int lastDot = ip.lastIndexOf('.');
                            return ip.substring(0, lastDot + 1);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void triggerSilentSubnetAutoDiscovery() {
        if (disableAutoScan || isAutoScanningSubnet || isActivityDestroyed) return;
        if (!sourceWifiEnabled) return;

        final String currentIp = serverIp != null ? serverIp.trim() : "";
        final String secret = cachedSecretHash;

        isAutoScanningSubnet = true;
        Log.i(TAG, "Consecutive network failures >= 3. Triggering silent background subnet discovery...");

        new Thread(() -> {
            java.util.concurrent.ExecutorService scanExecutor = null;
            OkHttpClient scanClient = null;
            try {
                String basePrefix = "";
                if (currentIp.contains(".")) {
                    int lastDot = currentIp.lastIndexOf('.');
                    basePrefix = currentIp.substring(0, lastDot + 1);
                }
                if (basePrefix.isEmpty()) {
                    String detectedPrefix = getLocalSubnetPrefix();
                    basePrefix = detectedPrefix != null ? detectedPrefix : "192.168.1.";
                }
                final String subnetPrefix = basePrefix;

                scanClient = HttpClientProvider.getClient().newBuilder()
                        .connectTimeout(1200, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .readTimeout(1200, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .build();

                scanExecutor = java.util.concurrent.Executors.newFixedThreadPool(28);
                final java.util.concurrent.atomic.AtomicBoolean found = new java.util.concurrent.atomic.AtomicBoolean(false);
                final OkHttpClient finalScanClient = scanClient;

                for (int i = 1; i <= 254; i++) {
                    if (found.get()) break;
                    final String targetIp = subnetPrefix + i;
                    scanExecutor.execute(() -> {
                        if (found.get() || Thread.currentThread().isInterrupted()) return;
                        try {
                            String checkUrl = "http://" + targetIp + ":17580/sgv.json";
                            Request.Builder reqBuilder = new Request.Builder().url(checkUrl);
                            if (secret != null && !secret.isEmpty()) {
                                reqBuilder.addHeader("api-secret", secret);
                            }
                            Request req = reqBuilder.build();
                            try (Response resp = finalScanClient.newCall(req).execute()) {
                                if (resp.isSuccessful() || resp.code() == 401) {
                                    if (found.compareAndSet(false, true)) {
                                        Log.i(TAG, "Silent auto-discovery found Master server at: " + targetIp);
                                        serverIp = targetIp;
                                        consecutiveNetworkFailures = 0;
                                        SharedPreferences prefs = getSharedPreferences("DiaClockPrefs", MODE_PRIVATE);
                                        prefs.edit().putString("ip_address", targetIp).apply();

                                        if (!isActivityDestroyed) {
                                            runOnUiThread(() -> {
                                                Toast.makeText(MainActivity.this, getString(R.string.msg_auto_discovered_master, targetIp), Toast.LENGTH_SHORT).show();
                                                fetchGlucoseData();
                                            });
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    });
                }
                scanExecutor.shutdown();
                scanExecutor.awaitTermination(3500, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Log.e(TAG, "Silent auto-discovery error: " + e.getMessage());
            } finally {
                if (scanExecutor != null) {
                    try {
                        scanExecutor.shutdownNow();
                    } catch (Exception ignored) {}
                }
                if (scanClient != null) {
                    try {
                        scanClient.dispatcher().cancelAll();
                    } catch (Exception ignored) {}
                }
                isAutoScanningSubnet = false;
            }
        }).start();
    }
}
