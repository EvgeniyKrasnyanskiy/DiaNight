package com.diaclock.nightstand;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "DiaClockPrefs";

    private TextInputEditText etIpAddress;
    private TextInputEditText etToggleInterval;
    private TextInputEditText etDayStart;
    private TextInputEditText etDayEnd;
    private TextInputEditText etDayLow;
    private TextInputEditText etDayHigh;
    private TextInputEditText etNightLow;
    private TextInputEditText etNightHigh;

    private Button btnCancel;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Landscape immersive view for settings
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
        etToggleInterval = findViewById(R.id.etToggleInterval);
        etDayStart = findViewById(R.id.etDayStart);
        etDayEnd = findViewById(R.id.etDayEnd);
        etDayLow = findViewById(R.id.etDayLow);
        etDayHigh = findViewById(R.id.etDayHigh);
        etNightLow = findViewById(R.id.etNightLow);
        etNightHigh = findViewById(R.id.etNightHigh);
        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);
    }

    private void loadSavedSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        etIpAddress.setText(prefs.getString("ip_address", "192.168.1.100"));
        etToggleInterval.setText(String.valueOf(prefs.getInt("toggle_interval", 5)));
        
        etDayStart.setText(prefs.getString("day_start", "08:00"));
        etDayEnd.setText(prefs.getString("day_end", "22:00"));
        
        etDayLow.setText(String.format(Locale.US, "%.1f", prefs.getFloat("day_low", 4.0f)));
        etDayHigh.setText(String.format(Locale.US, "%.1f", prefs.getFloat("day_high", 10.0f)));
        
        etNightLow.setText(String.format(Locale.US, "%.1f", prefs.getFloat("night_low", 3.6f)));
        etNightHigh.setText(String.format(Locale.US, "%.1f", prefs.getFloat("night_high", 11.0f)));
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveSettings());
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
            showToast("Please enter valid decimal values for Day Thresholds");
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
            showToast("Please enter valid decimal values for Night Thresholds");
            return;
        }

        // Save successfully
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString("ip_address", ip);
        editor.putInt("toggle_interval", interval);
        editor.putString("day_start", dayStart);
        editor.putString("day_end", dayEnd);
        editor.putFloat("day_low", dayLow);
        editor.putFloat("day_high", dayHigh);
        editor.putFloat("night_low", nightLow);
        editor.putFloat("night_high", nightHigh);
        editor.apply();

        Toast.makeText(this, getString(R.string.msg_saved), Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean validateTimeFormat(String timeStr) {
        // Regex validating standard HH:mm 24h format
        String timeRegex = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$";
        return timeStr.matches(timeRegex);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
