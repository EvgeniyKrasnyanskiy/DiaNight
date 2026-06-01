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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

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

    private View viewColorPreview;
    private Button btnColorWhite;
    private Button btnColorGrey;
    private Button btnColorDarkGrey;
    private Button btnColorGreen;
    private Button btnColorBlue;
    private Button btnColorCustom;

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

        viewColorPreview = findViewById(R.id.viewColorPreview);
        btnColorWhite = findViewById(R.id.btnColorWhite);
        btnColorGrey = findViewById(R.id.btnColorGrey);
        btnColorDarkGrey = findViewById(R.id.btnColorDarkGrey);
        btnColorGreen = findViewById(R.id.btnColorGreen);
        btnColorBlue = findViewById(R.id.btnColorBlue);
        btnColorCustom = findViewById(R.id.btnColorCustom);

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

        // Preset Colors
        btnColorWhite.setOnClickListener(v -> updateColor(Color.WHITE));
        btnColorGrey.setOnClickListener(v -> updateColor(Color.parseColor("#8E8E93")));
        btnColorDarkGrey.setOnClickListener(v -> updateColor(Color.parseColor("#3A3A3C")));
        btnColorGreen.setOnClickListener(v -> updateColor(Color.parseColor("#34C759")));
        btnColorBlue.setOnClickListener(v -> updateColor(Color.parseColor("#007AFF")));

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTestAlarm();
    }
}
