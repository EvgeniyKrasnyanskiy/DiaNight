package com.diaclock.nightstand;
 
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.app.ProgressDialog;
 
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
 
import com.google.android.material.textfield.TextInputEditText;
 
import java.io.IOException;
import java.util.Locale;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "DiaClockPrefs";
    private static final int REQUEST_CODE_RINGTONE_PICKER = 999;

    private TextInputEditText etIpAddress;
    private TextInputEditText etApiSecret;
    private TextInputEditText etToggleInterval;
    private TextInputEditText etDayStart;
    private TextInputEditText etDayEnd;
    private TextInputEditText etDayLow;
    private TextInputEditText etDayHigh;
    private TextInputEditText etNightLow;
    private TextInputEditText etNightHigh;
    private TextInputEditText etSnoozeInterval;
    
    private Button btnTestConnection;
    private Button btnAutoSearchBeta;
    private ImageView ivHelp;

    private View viewColorPreview;
    private Button btnColorWhite;
    private Button btnColorGrey;
    private Button btnColorDarkGrey;
    private Button btnColorGreen;
    private Button btnColorBlue;
    private Button btnColorCustom;
    private Button btnColorOrange;
    private Button btnColorRed;
    private Button btnColorPurple;
    private Button btnColorYellow;
    private Button btnColorTeal;

    // Alarm Ringtone UI Elements
    private TextView tvRingtoneName;
    private Button btnChooseRingtone;
    private Button btnTestRingtone;

    private Button btnCancel;
    private Button btnSave;

    // State Variables
    private int selectedColor = Color.WHITE;
    private String selectedRingtoneUri = null;
    private MediaPlayer testMediaPlayer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        initViews();
        loadSavedSettings();
        setupListeners();
    }

    private void initViews() {
        etIpAddress = findViewById(R.id.etIpAddress);
        etApiSecret = findViewById(R.id.etApiSecret);
        etToggleInterval = findViewById(R.id.etToggleInterval);
        etDayStart = findViewById(R.id.etDayStart);
        etDayEnd = findViewById(R.id.etDayEnd);
        etDayLow = findViewById(R.id.etDayLow);
        etDayHigh = findViewById(R.id.etDayHigh);
        etNightLow = findViewById(R.id.etNightLow);
        etNightHigh = findViewById(R.id.etNightHigh);
        etSnoozeInterval = findViewById(R.id.etSnoozeInterval);
        
        btnTestConnection = findViewById(R.id.btnTestConnection);
        btnAutoSearchBeta = findViewById(R.id.btnAutoSearchBeta);
        ivHelp = findViewById(R.id.ivHelp);

        viewColorPreview = findViewById(R.id.viewColorPreview);
        btnColorWhite = findViewById(R.id.btnColorWhite);
        btnColorGrey = findViewById(R.id.btnColorGrey);
        btnColorDarkGrey = findViewById(R.id.btnColorDarkGrey);
        btnColorGreen = findViewById(R.id.btnColorGreen);
        btnColorBlue = findViewById(R.id.btnColorBlue);
        btnColorCustom = findViewById(R.id.btnColorCustom);
        btnColorOrange = findViewById(R.id.btnColorOrange);
        btnColorRed = findViewById(R.id.btnColorRed);
        btnColorPurple = findViewById(R.id.btnColorPurple);
        btnColorYellow = findViewById(R.id.btnColorYellow);
        btnColorTeal = findViewById(R.id.btnColorTeal);

        tvRingtoneName = findViewById(R.id.tvRingtoneName);
        btnChooseRingtone = findViewById(R.id.btnChooseRingtone);
        btnTestRingtone = findViewById(R.id.btnTestRingtone);

        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);
    }

    private void loadSavedSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        etIpAddress.setText(prefs.getString("ip_address", "192.168.0.111"));
        etApiSecret.setText(prefs.getString("api_secret", "FBB9F80F9AC22E5B15F6DA1FFE599E14"));
        etToggleInterval.setText(String.valueOf(prefs.getInt("toggle_interval", 5)));
        
        etDayStart.setText(prefs.getString("day_start", "08:00"));
        etDayEnd.setText(prefs.getString("day_end", "22:00"));
        
        etDayLow.setText(String.format(Locale.US, "%.1f", prefs.getFloat("day_low", 4.0f)));
        etDayHigh.setText(String.format(Locale.US, "%.1f", prefs.getFloat("day_high", 10.0f)));
        
        etNightLow.setText(String.format(Locale.US, "%.1f", prefs.getFloat("night_low", 3.6f)));
        etNightHigh.setText(String.format(Locale.US, "%.1f", prefs.getFloat("night_high", 11.0f)));
        etSnoozeInterval.setText(String.valueOf(prefs.getInt("snooze_interval", 60)));

        selectedColor = prefs.getInt("text_color", Color.WHITE);
        viewColorPreview.setBackgroundColor(selectedColor);

        // Load and resolve chosen Ringtone Uri details
        selectedRingtoneUri = prefs.getString("alarm_uri", null);
        resolveRingtoneNameDisplay();
    }

    private void resolveRingtoneNameDisplay() {
        if (selectedRingtoneUri == null) {
            tvRingtoneName.setText(getString(R.string.default_ringtone_name));
        } else if (selectedRingtoneUri.equals("silent")) {
            tvRingtoneName.setText("Без звука 🔕");
        } else {
            try {
                Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(selectedRingtoneUri));
                if (r != null) {
                    tvRingtoneName.setText(r.getTitle(this));
                } else {
                    tvRingtoneName.setText("Пользовательский сигнал 🔔");
                }
            } catch (Exception e) {
                tvRingtoneName.setText("Сигнал по умолчанию 🔔");
            }
        }
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveSettings());
        
        ivHelp.setOnClickListener(v -> showHelpDialog());
        btnTestConnection.setOnClickListener(v -> testConnection());
        btnAutoSearchBeta.setOnClickListener(v -> startNetworkAutoDiscovery());

        // Preset Colors
        btnColorWhite.setOnClickListener(v -> updateColor(Color.WHITE));
        btnColorGrey.setOnClickListener(v -> updateColor(Color.parseColor("#8E8E93")));
        btnColorDarkGrey.setOnClickListener(v -> updateColor(Color.parseColor("#3A3A3C")));
        btnColorGreen.setOnClickListener(v -> updateColor(Color.parseColor("#34C759")));
        btnColorBlue.setOnClickListener(v -> updateColor(Color.parseColor("#007AFF")));
        btnColorOrange.setOnClickListener(v -> updateColor(Color.parseColor("#FF9500")));
        btnColorRed.setOnClickListener(v -> updateColor(Color.parseColor("#FF3B30")));
        btnColorPurple.setOnClickListener(v -> updateColor(Color.parseColor("#AF52DE")));
        btnColorYellow.setOnClickListener(v -> updateColor(Color.parseColor("#FFCC00")));
        btnColorTeal.setOnClickListener(v -> updateColor(Color.parseColor("#30B0C7")));

        btnColorCustom.setOnClickListener(v -> openColorPickerDialog());

        // Choose Ringtone Button Launcher
        btnChooseRingtone.setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Выберите мелодию тревоги");
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            if (selectedRingtoneUri != null && !selectedRingtoneUri.equals("silent")) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(selectedRingtoneUri));
            }
            startActivityForResult(intent, REQUEST_CODE_RINGTONE_PICKER);
        });

        // Test Alarm Button Trigger
        btnTestRingtone.setOnClickListener(v -> toggleTestAlarm());
    }

    private void updateColor(int color) {
        selectedColor = color;
        viewColorPreview.setBackgroundColor(selectedColor);
    }

    private void openColorPickerDialog() {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(50, 40, 50, 40);

        final View dialogPreview = new View(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(140, 140);
        previewParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        previewParams.bottomMargin = 40;
        dialogPreview.setLayoutParams(previewParams);
        dialogPreview.setBackgroundColor(selectedColor);

        // Sliders
        final TextView tvRed = new TextView(this);
        tvRed.setText(getString(R.string.label_red) + ": " + Color.red(selectedColor));
        tvRed.setTextColor(Color.WHITE);
        final SeekBar sbRed = new SeekBar(this);
        sbRed.setMax(255);
        sbRed.setProgress(Color.red(selectedColor));

        final TextView tvGreen = new TextView(this);
        tvGreen.setText(getString(R.string.label_green_slider) + ": " + Color.green(selectedColor));
        tvGreen.setTextColor(Color.WHITE);
        final SeekBar sbGreen = new SeekBar(this);
        sbGreen.setMax(255);
        sbGreen.setProgress(Color.green(selectedColor));

        final TextView tvBlue = new TextView(this);
        tvBlue.setText(getString(R.string.label_blue_slider) + ": " + Color.blue(selectedColor));
        tvBlue.setTextColor(Color.WHITE);
        final SeekBar sbBlue = new SeekBar(this);
        sbBlue.setMax(255);
        sbBlue.setProgress(Color.blue(selectedColor));

        SeekBar.OnSeekBarChangeListener pickerListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int r = sbRed.getProgress();
                int g = sbGreen.getProgress();
                int b = sbBlue.getProgress();
                int newCol = Color.rgb(r, g, b);
                
                dialogPreview.setBackgroundColor(newCol);
                tvRed.setText(getString(R.string.label_red) + ": " + r);
                tvGreen.setText(getString(R.string.label_green_slider) + ": " + g);
                tvBlue.setText(getString(R.string.label_blue_slider) + ": " + b);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        sbRed.setOnSeekBarChangeListener(pickerListener);
        sbGreen.setOnSeekBarChangeListener(pickerListener);
        sbBlue.setOnSeekBarChangeListener(pickerListener);

        dialogLayout.addView(dialogPreview);
        dialogLayout.addView(tvRed);
        dialogLayout.addView(sbRed);
        dialogLayout.addView(tvGreen);
        dialogLayout.addView(sbGreen);
        dialogLayout.addView(tvBlue);
        dialogLayout.addView(sbBlue);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.dialog_color_title))
                .setView(dialogLayout)
                .setPositiveButton("OK", (dialog, which) -> {
                    int r = sbRed.getProgress();
                    int g = sbGreen.getProgress();
                    int b = sbBlue.getProgress();
                    updateColor(Color.rgb(r, g, b));
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_RINGTONE_PICKER && resultCode == RESULT_OK) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                selectedRingtoneUri = uri.toString();
            } else {
                selectedRingtoneUri = "silent";
            }
            resolveRingtoneNameDisplay();
        }
    }

    private void toggleTestAlarm() {
        if (testMediaPlayer != null && testMediaPlayer.isPlaying()) {
            stopTestAlarm();
        } else {
            startTestAlarm();
        }
    }

    private void startTestAlarm() {
        if (selectedRingtoneUri != null && selectedRingtoneUri.equals("silent")) {
            Toast.makeText(this, "Выбран бесшумный режим", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri ringtoneUri;
        if (selectedRingtoneUri == null) {
            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
        } else {
            ringtoneUri = Uri.parse(selectedRingtoneUri);
        }

        try {
            testMediaPlayer = new MediaPlayer();
            testMediaPlayer.setDataSource(this, ringtoneUri);
            testMediaPlayer.setLooping(false);
            testMediaPlayer.prepare();
            testMediaPlayer.start();
            btnTestRingtone.setText(getString(R.string.btn_test_alarm_stop));

            // Automatically restore button label when playback finishes
            testMediaPlayer.setOnCompletionListener(mp -> stopTestAlarm());
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось запустить воспроизведение", Toast.LENGTH_SHORT).show();
            stopTestAlarm();
        }
    }

    private void stopTestAlarm() {
        if (testMediaPlayer != null) {
            try {
                if (testMediaPlayer.isPlaying()) {
                    testMediaPlayer.stop();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                testMediaPlayer.release();
                testMediaPlayer = null;
            }
        }
        btnTestRingtone.setText(getString(R.string.btn_test_alarm_start));
    }

    private void saveSettings() {
        // 1. IP Validation
        String ip = etIpAddress.getText() != null ? etIpAddress.getText().toString().trim() : "";
        if (ip.isEmpty()) {
            showToast(getString(R.string.msg_invalid_ip));
            return;
        }

        // 2. Toggle Interval Validation
        String intervalStr = etToggleInterval.getText() != null ? etToggleInterval.getText().toString().trim() : "";
        int interval;
        try {
            interval = Integer.parseInt(intervalStr);
            if (interval < 1) {
                showToast(getString(R.string.msg_invalid_interval));
                return;
            }
        } catch (NumberFormatException e) {
            showToast(getString(R.string.msg_invalid_interval));
            return;
        }

        // 3. Time Slots HH:mm Validations
        String dayStart = etDayStart.getText() != null ? etDayStart.getText().toString().trim() : "";
        String dayEnd = etDayEnd.getText() != null ? etDayEnd.getText().toString().trim() : "";
        
        if (!validateTimeFormat(dayStart) || !validateTimeFormat(dayEnd)) {
            showToast(getString(R.string.msg_invalid_time_format));
            return;
        }

        // 4. Day Threshold Validations
        String dayLowStr = etDayLow.getText() != null ? etDayLow.getText().toString().trim() : "";
        String dayHighStr = etDayHigh.getText() != null ? etDayHigh.getText().toString().trim() : "";
        float dayLow, dayHigh;
        try {
            dayLow = Float.parseFloat(dayLowStr);
            dayHigh = Float.parseFloat(dayHighStr);
            if (dayLow <= 0 || dayHigh <= 0 || dayHigh <= dayLow) {
                showToast(getString(R.string.msg_invalid_thresholds));
                return;
            }
        } catch (NumberFormatException e) {
            showToast("Введите корректные десятичные значения для порогов Дня");
            return;
        }

        // 5. Night Threshold Validations
        String nightLowStr = etNightLow.getText() != null ? etNightLow.getText().toString().trim() : "";
        String nightHighStr = etNightHigh.getText() != null ? etNightHigh.getText().toString().trim() : "";
        float nightLow, nightHigh;
        try {
            nightLow = Float.parseFloat(nightLowStr);
            nightHigh = Float.parseFloat(nightHighStr);
            if (nightLow <= 0 || nightHigh <= 0 || nightHigh <= nightLow) {
                showToast(getString(R.string.msg_invalid_thresholds));
                return;
            }
        } catch (NumberFormatException e) {
            showToast("Введите корректные десятичные значения для порогов Ночи");
            return;
        }

        // 6. Snooze Interval Validation
        String snoozeStr = etSnoozeInterval.getText() != null ? etSnoozeInterval.getText().toString().trim() : "";
        int snoozeInterval;
        try {
            snoozeInterval = Integer.parseInt(snoozeStr);
            if (snoozeInterval < 1) {
                showToast(getString(R.string.msg_invalid_snooze));
                return;
            }
        } catch (NumberFormatException e) {
            showToast(getString(R.string.msg_invalid_snooze));
            return;
        }

        String apiSecret = etApiSecret.getText() != null ? etApiSecret.getText().toString().trim() : "";

        // Save successfully
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString("ip_address", ip);
        editor.putString("api_secret", apiSecret);
        editor.putInt("toggle_interval", interval);
        editor.putString("day_start", dayStart);
        editor.putString("day_end", dayEnd);
        editor.putFloat("day_low", dayLow);
        editor.putFloat("day_high", dayHigh);
        editor.putFloat("night_low", nightLow);
        editor.putFloat("night_high", nightHigh);
        editor.putInt("text_color", selectedColor);
        editor.putString("alarm_uri", selectedRingtoneUri);
        editor.putInt("snooze_interval", snoozeInterval);
        editor.apply();

        Toast.makeText(this, getString(R.string.msg_saved), Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean validateTimeFormat(String timeStr) {
        String timeRegex = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$";
        return timeStr.matches(timeRegex);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String getLocalIpAddress() {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                java.net.NetworkInterface intf = en.nextElement();
                if (intf.isLoopback() || !intf.isUp()) {
                    continue;
                }
                for (java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        String ip = inetAddress.getHostAddress();
                        if (ip != null && !ip.equals("0.0.0.0")) {
                            // Ensure it is a typical local subnet address (192.168.x.x, 10.x.x.x, 172.x.x.x)
                            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                                android.util.Log.d("DiaNightScan", "Found local IPv4 interface: " + ip + " on " + intf.getName());
                                return ip;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("DiaNightScan", "Error getting local IP address", e);
        }
        // Fallback to old WiFi manager method just in case
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            int ipAddress = wm.getConnectionInfo().getIpAddress();
            if (ipAddress != 0) {
                return String.format(Locale.US, "%d.%d.%d.%d",
                        (ipAddress & 0xff),
                        (ipAddress >> 8 & 0xff),
                        (ipAddress >> 16 & 0xff),
                        (ipAddress >> 24 & 0xff));
            }
        } catch (Exception e) {
            // Ignore
        }
        return "192.168.1.1";
    }

    private void startNetworkAutoDiscovery() {
        String basePrefix = "";
        
        // 1. Preferential Subnet: Extract from the current IP field if available
        String fieldIp = etIpAddress.getText() != null ? etIpAddress.getText().toString().trim() : "";
        if (fieldIp.contains(".") && !fieldIp.isEmpty()) {
            int lastDot = fieldIp.lastIndexOf('.');
            basePrefix = fieldIp.substring(0, lastDot + 1);
            android.util.Log.d("DiaNightScan", "Using preferential subnet prefix from IP field: " + basePrefix);
        }
        
        // 2. Backup Subnet: Auto-detect device IP if field was empty or invalid
        if (basePrefix.isEmpty()) {
            String ip = getLocalIpAddress();
            android.util.Log.d("DiaNightScan", "Detected device IP for scan backup: " + ip);
            if (ip != null && ip.contains(".") && !ip.equals("0.0.0.0") && !ip.equals("127.0.0.1")) {
                int lastDot = ip.lastIndexOf('.');
                basePrefix = ip.substring(0, lastDot + 1);
            }
        }
        
        // 3. Ultimate Fallback
        if (basePrefix.isEmpty()) {
            basePrefix = "192.168.1.";
        }

        final String subnetPrefix = basePrefix;
        android.util.Log.d("DiaNightScan", "Final scanning subnet prefix: " + subnetPrefix);

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Автопоиск мастера");
        progressDialog.setMessage("Сканирование подсети: " + subnetPrefix + "X\nПожалуйста, подождите...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        final java.util.concurrent.atomic.AtomicBoolean found = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicInteger finishedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // Custom dispatcher to allow all 254 requests to execute in parallel immediately
        okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
        dispatcher.setMaxRequests(254);
        dispatcher.setMaxRequestsPerHost(254);

        OkHttpClient scanClient = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();

        for (int i = 1; i <= 254; i++) {
            final String targetIp = subnetPrefix + i;
            String url = "http://" + targetIp + ":17580/sgv.json?brief_mode=Y";

            Request request = new Request.Builder().url(url).build();
            scanClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    checkScanProgress(finishedCount, found, progressDialog);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final int code = response.code();
                    android.util.Log.d("DiaNightScan", "Response from " + targetIp + ": code=" + code);
                    
                    // 200 OK or 401 Unauthorized mean the xDrip+ service is active at this IP!
                    if (response.isSuccessful() || code == 401) {
                        if (found.compareAndSet(false, true)) {
                            // Cancel any remaining queued requests to free up resources
                            scanClient.dispatcher().cancelAll();
                            
                            runOnUiThread(() -> {
                                etIpAddress.setText(targetIp);
                                progressDialog.dismiss();
                                if (code == 401) {
                                    Toast.makeText(SettingsActivity.this, "xDrip+ найден! IP: " + targetIp + "\nНо требуется авторизация. Пожалуйста, проверьте API Secret!", Toast.LENGTH_LONG).show();
                                    new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                            .setTitle("xDrip+ найден!")
                                            .setMessage("Мастер-устройство успешно обнаружено на IP: " + targetIp + ".\n\nОднако на нём включена защита. Убедитесь, что вы ввели правильный «Секретный ключ веб-службы (API Secret)» ниже, иначе данные не будут поступать.")
                                            .setPositiveButton("ОК", null)
                                            .show();
                                } else {
                                    Toast.makeText(SettingsActivity.this, "xDrip+ успешно найден! IP: " + targetIp, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                    response.close();
                    checkScanProgress(finishedCount, found, progressDialog);
                }
            });
        }
    }

    private void checkScanProgress(java.util.concurrent.atomic.AtomicInteger finishedCount, 
                                   java.util.concurrent.atomic.AtomicBoolean found, 
                                   ProgressDialog progressDialog) {
        int current = finishedCount.incrementAndGet();
        if (current >= 254 && !found.get()) {
            runOnUiThread(() -> {
                progressDialog.dismiss();
                Toast.makeText(SettingsActivity.this, "xDrip+ не найден в вашей Wi-Fi сети. Убедитесь, что веб-служба включена в xDrip+.", Toast.LENGTH_LONG).show();
            });
        }
    }

    private void showHelpDialog() {
        String helpText = "Как настроить DiaNight:\n\n" +
                "1. На смартфоне-мастере с xDrip+:\n" +
                "   • Откройте xDrip+ -> Настройки -> Настройки межпрограммного взаимодействия (Inter-app settings).\n" +
                "   • Включите «Локальный веб-сервер» (Local Web Service).\n" +
                "   • Включите «Локальное вещание» (Broadcast locally).\n" +
                "   • В поле «Секретный ключ веб-службы» (API Secret) задайте или скопируйте ключ (минимум 12 символов).\n\n" +
                "2. Настройка смартфона DiaNight:\n" +
                "   • Подключите оба устройства к одной Wi-Fi сети.\n" +
                "   • Нажмите кнопку «Автопоиск (Beta)» под настройками для автоматического нахождения мастера.\n" +
                "   • Если автопоиск не сработал, введите IP-адрес мастера вручную.\n" +
                "   • Введите API Secret точно такой же, как в xDrip+.\n" +
                "   • Для быстрой проверки нажмите кнопку «Проверить связь».\n" +
                "   • Нажмите «Сохранить».\n\n" +
                "3. Оптимизация батареи (ВАЖНО!):\n" +
                "   • В настройках Android обоих смартфонов отключите оптимизацию батареи для xDrip+ и DiaNight, чтобы система не закрывала их ночью.";

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Инструкция по настройке")
                .setMessage(helpText)
                .setPositiveButton("Понятно", null)
                .show();
    }

    /**
     * Performs a fast connection check with the currently typed IP and API Secret.
     */
    private void testConnection() {
        final String ip = etIpAddress.getText() != null ? etIpAddress.getText().toString().trim() : "";
        final String secret = etApiSecret.getText() != null ? etApiSecret.getText().toString().trim() : "";
        
        if (ip.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, введите IP-адрес для проверки", Toast.LENGTH_SHORT).show();
            return;
        }

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Проверка связи");
        progressDialog.setMessage("Подключение к xDrip+ по адресу " + ip + "...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String url = "http://" + ip + ":17580/sgv.json?brief_mode=Y";
        
        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(url);
        if (!secret.isEmpty()) {
            String hashedSecret = computeSHA1(secret);
            requestBuilder.addHeader("api-secret", hashedSecret);
        }
        okhttp3.Request request = requestBuilder.build();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Ошибка связи ❌")
                            .setMessage("Не удалось подключиться к xDrip+ по адресу:\n" + ip + "\n\nДетали ошибки:\n" + e.getMessage() + "\n\nУбедитесь, что:\n1. Оба телефона подключены к одной Wi-Fi сети.\n2. На мастере в настройках xDrip+ включен «Локальный веб-сервер» в Inter-app settings.")
                            .setPositiveButton("ОК", null)
                            .show();
                });
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                final int code = response.code();
                final String responseBody = response.body() != null ? response.body().string() : "";
                response.close();

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (code == 200) {
                        new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle("Успешно! ✅")
                                .setMessage("Связь с xDrip+ успешно установлена!\n\nКод ответа: 200 OK\nУстройство найдено, данные читаются корректно.")
                                .setPositiveButton("Отлично", null)
                                .show();
                    } else if (code == 401) {
                        new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle("Требуется авторизация ⚠️")
                                .setMessage("Связь с xDrip+ установлена (устройство найдено), но сервер отклонил запрос с кодом 401 (Unauthorized).\n\nПожалуйста, убедитесь, что вы правильно ввели «Секретный ключ веб-службы (API Secret)»!")
                                .setPositiveButton("ОК", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle("Необычный ответ ℹ️")
                                .setMessage("Подключение удалось, но сервер xDrip+ вернул код: " + code + ".\n\nОтвет сервера:\n" + responseBody)
                                .setPositiveButton("ОК", null)
                                .show();
                    }
                });
            }
        });
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
            android.util.Log.e("DiaNightScan", "SHA-1 hashing failed: " + e.getMessage());
            return input;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTestAlarm();
    }
}
