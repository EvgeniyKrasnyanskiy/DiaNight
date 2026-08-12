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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import androidx.core.os.LocaleListCompat;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

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

    private Button btnSave;
    private Button btnSaveBottom;
    private TextView tvVersionInfo;
    private TextView tvTelegramLink;
    
    // New UI Elements
    private RadioGroup rgDataSource;
    private RadioButton rbSourceNetwork;
    private RadioButton rbSourceBroadcast;
    private View cardCoreSetup;
    private TextView tvDataSourceHint;
    
    private CheckBox chkAutoCheckUpdates;
    private Button btnCheckUpdate;
    private Button btnDownloadUpdate;
    private TextView tvUpdateStatus;
    private Button btnCancelBottom;
    
    private CheckBox chkNightlightMode;
    private Spinner spAppLanguage;
    
    private CheckBox chkUseFlashOnAlarm;
    private CheckBox chkEnableAutoClose;
    private EditText etAutoCloseTime;
    
    private String downloadUrl = null;

    // State Variables
    private int selectedColor = Color.WHITE;
    private String selectedRingtoneUri = null;
    private MediaPlayer testMediaPlayer = null;
    private java.util.concurrent.ExecutorService scanExecutor = null;
    private ProgressDialog progressDialog = null;
    private okhttp3.OkHttpClient scanningClient = null;

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

        setupColorButtonTint(btnColorWhite, Color.WHITE);
        setupColorButtonTint(btnColorGrey, Color.parseColor("#8E8E93"));
        setupColorButtonTint(btnColorDarkGrey, Color.parseColor("#3A3A3C"));
        setupColorButtonTint(btnColorGreen, Color.parseColor("#34C759"));
        setupColorButtonTint(btnColorBlue, Color.parseColor("#007AFF"));
        setupColorButtonTint(btnColorOrange, Color.parseColor("#FF9500"));
        setupColorButtonTint(btnColorRed, Color.parseColor("#FF3B30"));
        setupColorButtonTint(btnColorPurple, Color.parseColor("#AF52DE"));
        setupColorButtonTint(btnColorYellow, Color.parseColor("#FFCC00"));
        setupColorButtonTint(btnColorTeal, Color.parseColor("#30B0C7"));

        tvRingtoneName = findViewById(R.id.tvRingtoneName);
        btnChooseRingtone = findViewById(R.id.btnChooseRingtone);
        btnTestRingtone = findViewById(R.id.btnTestRingtone);

        btnSave = findViewById(R.id.btnSave);
        btnSaveBottom = findViewById(R.id.btnSaveBottom);
        tvVersionInfo = findViewById(R.id.tvVersionInfo);
        tvTelegramLink = findViewById(R.id.tvTelegramLink);

        rgDataSource = findViewById(R.id.rgDataSource);
        rbSourceNetwork = findViewById(R.id.rbSourceNetwork);
        rbSourceBroadcast = findViewById(R.id.rbSourceBroadcast);
        cardCoreSetup = findViewById(R.id.cardCoreSetup);
        tvDataSourceHint = findViewById(R.id.tvDataSourceHint);
        
        chkAutoCheckUpdates = findViewById(R.id.chkAutoCheckUpdates);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnDownloadUpdate = findViewById(R.id.btnDownloadUpdate);
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus);
        btnCancelBottom = findViewById(R.id.btnCancelBottom);

        chkNightlightMode = findViewById(R.id.chkNightlightMode);
        spAppLanguage = findViewById(R.id.spAppLanguage);

        chkUseFlashOnAlarm = findViewById(R.id.chkUseFlashOnAlarm);
        chkEnableAutoClose = findViewById(R.id.chkEnableAutoClose);
        etAutoCloseTime = findViewById(R.id.etAutoCloseTime);
    }

    private void loadSavedSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        etIpAddress.setText(prefs.getString("ip_address", "192.168.0.111"));
        etApiSecret.setText(prefs.getString("api_secret", "FBB9F80F9AC22E5B15F6DA1FFE599E14"));
        etToggleInterval.setText(String.valueOf(prefs.getInt("toggle_interval", 5)));
        
        etDayStart.setText(prefs.getString("day_start", "08:00"));
        etDayEnd.setText(prefs.getString("day_end", "22:00"));
        
        etDayLow.setText(String.format(Locale.US, "%.1f", prefs.getFloat("day_low", 4.5f)));
        etDayHigh.setText(String.format(Locale.US, "%.1f", prefs.getFloat("day_high", 8.5f)));
        
        etNightLow.setText(String.format(Locale.US, "%.1f", prefs.getFloat("night_low", 3.8f)));
        etNightHigh.setText(String.format(Locale.US, "%.1f", prefs.getFloat("night_high", 10.0f)));
        etSnoozeInterval.setText(String.valueOf(prefs.getInt("snooze_interval", 60)));

        if (chkUseFlashOnAlarm != null) {
            chkUseFlashOnAlarm.setChecked(prefs.getBoolean("alarm_use_flash", false));
        }
        if (chkEnableAutoClose != null) {
            chkEnableAutoClose.setChecked(prefs.getBoolean("enable_autoclose", false));
        }
        if (etAutoCloseTime != null) {
            etAutoCloseTime.setText(prefs.getString("autoclose_time", "07:00"));
        }

        selectedColor = prefs.getInt("text_color", Color.WHITE);
        viewColorPreview.setBackgroundColor(selectedColor);

        // Load and resolve chosen Ringtone Uri details
        selectedRingtoneUri = prefs.getString("alarm_uri", null);
        resolveRingtoneNameDisplay();

        // Display actual application version dynamically from PackageInfo
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersionInfo.setText(getString(R.string.label_version) + ": " + versionName);
        } catch (Exception e) {
            tvVersionInfo.setText(getString(R.string.label_version) + ": 1.1");
        }

        // Load new settings
        String dataSource = prefs.getString("data_source", "network");
        if ("broadcast".equals(dataSource)) {
            rbSourceBroadcast.setChecked(true);
            updateCoreSetupInteractivity(false);
            tvDataSourceHint.setText(getString(R.string.hint_source_broadcast));
            tvDataSourceHint.setTextColor(getResources().getColor(R.color.color_warning_orange));
        } else {
            rbSourceNetwork.setChecked(true);
            updateCoreSetupInteractivity(true);
            tvDataSourceHint.setText(getString(R.string.hint_source_network));
            tvDataSourceHint.setTextColor(getResources().getColor(R.color.text_secondary));
        }

        boolean autoCheck = prefs.getBoolean("auto_check_updates", true);
        chkAutoCheckUpdates.setChecked(autoCheck);

        // Load new nightlight mode and language preferences
        chkNightlightMode.setChecked(prefs.getBoolean("nightlight_mode", false));

        String[] languages = {
                getString(R.string.lang_system),
                getString(R.string.lang_ru),
                getString(R.string.lang_en)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                languages
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAppLanguage.setAdapter(adapter);

        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        if (currentLocales.isEmpty()) {
            spAppLanguage.setSelection(0); // System Default
        } else {
            String lang = currentLocales.get(0).getLanguage();
            if ("ru".equals(lang)) {
                spAppLanguage.setSelection(1);
            } else if ("en".equals(lang)) {
                spAppLanguage.setSelection(2);
            } else {
                spAppLanguage.setSelection(0);
            }
        }

        if (autoCheck) {
            checkForUpdates(true); // Silent check on load
        }
    }

    private void resolveRingtoneNameDisplay() {
        if (selectedRingtoneUri == null) {
            tvRingtoneName.setText(getString(R.string.default_ringtone_name));
        } else if (selectedRingtoneUri.equals("silent")) {
            tvRingtoneName.setText(getString(R.string.ringtone_silent));
        } else {
            try {
                Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(selectedRingtoneUri));
                if (r != null) {
                    tvRingtoneName.setText(r.getTitle(this));
                } else {
                    tvRingtoneName.setText(getString(R.string.ringtone_custom));
                }
            } catch (Exception e) {
                tvRingtoneName.setText(getString(R.string.ringtone_default));
            }
        }
    }

    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveSettings());
        btnSaveBottom.setOnClickListener(v -> saveSettings());
        btnCancelBottom.setOnClickListener(v -> finish());
        
        rgDataSource.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isNetwork = checkedId == R.id.rbSourceNetwork;
            updateCoreSetupInteractivity(isNetwork);
            if (isNetwork) {
                tvDataSourceHint.setText(getString(R.string.hint_source_network));
                tvDataSourceHint.setTextColor(getResources().getColor(R.color.text_secondary));
            } else {
                tvDataSourceHint.setText(getString(R.string.hint_source_broadcast));
                tvDataSourceHint.setTextColor(getResources().getColor(R.color.color_warning_orange));
            }
        });

        btnCheckUpdate.setOnClickListener(v -> checkForUpdates(false));
        btnDownloadUpdate.setOnClickListener(v -> {
            if (downloadUrl != null) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)));
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.err_open_browser), Toast.LENGTH_SHORT).show();
                }
            }
        });
        
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
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.title_choose_ringtone));
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            if (selectedRingtoneUri != null && !selectedRingtoneUri.equals("silent")) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(selectedRingtoneUri));
            }
            startActivityForResult(intent, REQUEST_CODE_RINGTONE_PICKER);
        });

        // Test Alarm Button Trigger
        btnTestRingtone.setOnClickListener(v -> toggleTestAlarm());

        // Open Telegram channel link
        tvTelegramLink.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/DiaKia")));
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.err_open_browser), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateColor(int color) {
        selectedColor = color;
        viewColorPreview.setBackgroundColor(selectedColor);
    }

    private void setupColorButtonTint(Button btn, int color) {
        if (btn != null) {
            androidx.core.view.ViewCompat.setBackgroundTintList(btn, android.content.res.ColorStateList.valueOf(color));
        }
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
            Toast.makeText(this, getString(R.string.msg_silent_mode), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, getString(R.string.err_play_ringtone), Toast.LENGTH_SHORT).show();
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
        if (rbSourceNetwork.isChecked() && ip.isEmpty()) {
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
            showToast(getString(R.string.err_invalid_day_thresholds));
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
            showToast(getString(R.string.err_invalid_night_thresholds));
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

        String autoCloseTime = etAutoCloseTime != null && etAutoCloseTime.getText() != null ? etAutoCloseTime.getText().toString().trim() : "";
        boolean enableAutoClose = chkEnableAutoClose != null && chkEnableAutoClose.isChecked();
        if (enableAutoClose && !validateTimeFormat(autoCloseTime)) {
            showToast(getString(R.string.err_invalid_autoclose_time));
            return;
        }

        boolean useFlashAlarm = chkUseFlashOnAlarm != null && chkUseFlashOnAlarm.isChecked();

        String apiSecret = etApiSecret.getText() != null ? etApiSecret.getText().toString().trim() : "";
        
        String dataSource = rbSourceNetwork.isChecked() ? "network" : "broadcast";
        boolean autoCheck = chkAutoCheckUpdates.isChecked();
        boolean nightlightMode = chkNightlightMode.isChecked();

        // Save successfully
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString("data_source", dataSource);
        editor.putBoolean("auto_check_updates", autoCheck);
        editor.putBoolean("nightlight_mode", nightlightMode);
        editor.putBoolean("alarm_use_flash", useFlashAlarm);
        editor.putBoolean("enable_autoclose", enableAutoClose);
        editor.putString("autoclose_time", autoCloseTime);
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

        int selectedLangPos = spAppLanguage.getSelectedItemPosition();
        String targetLangCode = "";
        if (selectedLangPos == 1) {
            targetLangCode = "ru";
        } else if (selectedLangPos == 2) {
            targetLangCode = "en";
        }

        LocaleListCompat currentLocales2 = AppCompatDelegate.getApplicationLocales();
        String activeLangCode = currentLocales2.isEmpty() ? "" : currentLocales2.get(0).getLanguage();

        if (!targetLangCode.equals(activeLangCode)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(targetLangCode));
        }

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

    private static class DiscoveredDevice {
        final String ip;
        final int code;
        DiscoveredDevice(String ip, int code) {
            this.ip = ip;
            this.code = code;
        }
    }

    private void startNetworkAutoDiscovery() {
        String secretKey = etApiSecret.getText() != null ? etApiSecret.getText().toString().trim() : "";
        if (secretKey.isEmpty()) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(getString(R.string.dialog_api_secret_required_title))
                    .setMessage(getString(R.string.dialog_api_secret_required_msg))
                    .setPositiveButton(getString(R.string.btn_ok), null)
                    .show();
            return;
        }

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

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(getString(R.string.dialog_scan_title));
        progressDialog.setMessage(getString(R.string.dialog_scan_msg_format, subnetPrefix));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        final java.util.List<DiscoveredDevice> discoveredDevices = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final java.util.concurrent.atomic.AtomicInteger finishedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // Cancel previous executor if running
        if (scanExecutor != null) {
            try {
                scanExecutor.shutdownNow();
            } catch (Exception e) {
                // Ignore
            }
        }

        // Cancel previous scanning client calls if active
        if (scanningClient != null) {
            try {
                scanningClient.dispatcher().cancelAll();
            } catch (Exception e) {
                // Ignore
            }
        }

        // Create a single shared scanning client with short timeouts for the entire scan run
        scanningClient = HttpClientProvider.getClient().newBuilder()
                .connectTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();

        // Initialize high performance light-weight executor pool (28 parallel threads)
        scanExecutor = java.util.concurrent.Executors.newFixedThreadPool(28);

        for (int i = 1; i <= 254; i++) {
            final String targetIp = subnetPrefix + i;
            final java.util.concurrent.ExecutorService currentExecutor = scanExecutor;
            final okhttp3.OkHttpClient currentClient = scanningClient;

            scanExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (Thread.currentThread().isInterrupted()) {
                        checkScanProgress(finishedCount, discoveredDevices, progressDialog, currentExecutor);
                        return;
                    }

                    boolean portOpen = false;
                    try (java.net.Socket socket = new java.net.Socket()) {
                        // Quick TCP-handshake ping on port 17580 with 1.0s timeout
                        socket.connect(new java.net.InetSocketAddress(targetIp, 17580), 1000);
                        portOpen = true;
                        android.util.Log.d("DiaNightScan", "TCP port 17580 is OPEN on candidate IP: " + targetIp);
                    } catch (java.io.IOException e) {
                        // Port is closed or host is not reachable
                    }

                    if (portOpen) {
                        // Candidate found, perform single target HTTP-validation to confirm xDrip+
                        verifyXdrip(targetIp, discoveredDevices, progressDialog, finishedCount, currentExecutor, currentClient);
                    } else {
                        checkScanProgress(finishedCount, discoveredDevices, progressDialog, currentExecutor);
                    }
                }
            });
        }
    }

    private void verifyXdrip(final String targetIp, 
                             final java.util.List<DiscoveredDevice> discoveredDevices, 
                             final ProgressDialog progressDialog,
                             final java.util.concurrent.atomic.AtomicInteger finishedCount,
                             final java.util.concurrent.ExecutorService executor,
                             final okhttp3.OkHttpClient clientToUse) {
        String url = "http://" + targetIp + ":17580/sgv.json?brief_mode=Y";
        
        final String secret = etApiSecret.getText() != null ? etApiSecret.getText().toString().trim() : "";
        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(url);
        if (!secret.isEmpty()) {
            String hashedSecret = CryptoUtils.computeSHA1(secret);
            requestBuilder.addHeader("api-secret", hashedSecret);
            android.util.Log.d("DiaNightScan", "Verifying IP " + targetIp + " with SHA-1 hashed api-secret header");
        }

        Request request = requestBuilder.build();
        clientToUse.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                android.util.Log.d("DiaNightScan", "HTTP verification failed for IP " + targetIp + ": " + e.getMessage());
                checkScanProgress(finishedCount, discoveredDevices, progressDialog, executor);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    final int code = response.code();
                    android.util.Log.d("DiaNightScan", "HTTP verification response from " + targetIp + ": code=" + code);
                    // 200 OK or 401 Unauthorized strongly proves it's indeed xDrip+ server!
                    if (code == 200 || code == 401) {
                        discoveredDevices.add(new DiscoveredDevice(targetIp, code));
                    }
                } finally {
                    response.close();
                }
                checkScanProgress(finishedCount, discoveredDevices, progressDialog, executor);
            }
        });
    }

    private void checkScanProgress(final java.util.concurrent.atomic.AtomicInteger finishedCount, 
                                   final java.util.List<DiscoveredDevice> discoveredDevices, 
                                   final ProgressDialog progressDialog,
                                   final java.util.concurrent.ExecutorService executor) {
        int current = finishedCount.incrementAndGet();
        if (current >= 254) {
            executor.shutdown();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progressDialog.dismiss();
                if (discoveredDevices.isEmpty()) {
                    Toast.makeText(SettingsActivity.this, getString(R.string.msg_xdrip_not_found), Toast.LENGTH_LONG).show();
                } else if (discoveredDevices.size() == 1) {
                    selectDiscoveredDevice(discoveredDevices.get(0));
                } else {
                    showDeviceSelectionDialog(discoveredDevices);
                }
            });
        }
    }

    private void selectDiscoveredDevice(DiscoveredDevice device) {
        final String targetIp = device.ip;
        final int code = device.code;

        // Automatically save IP in SharedPreferences
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString("ip_address", targetIp);
        editor.apply();

        etIpAddress.setText(targetIp);
        etIpAddress.requestFocus();

        if (code == 401) {
            Toast.makeText(SettingsActivity.this, getString(R.string.msg_xdrip_found_auth_required, targetIp), Toast.LENGTH_LONG).show();
            new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(getString(R.string.dialog_xdrip_found_auth_required_title))
                    .setMessage(getString(R.string.dialog_xdrip_found_auth_required_msg, targetIp))
                    .setPositiveButton(getString(R.string.btn_ok), null)
                    .show();
        } else {
            Toast.makeText(SettingsActivity.this, getString(R.string.msg_xdrip_found_success, targetIp), Toast.LENGTH_LONG).show();
        }
    }

    private void showDeviceSelectionDialog(final java.util.List<DiscoveredDevice> devices) {
        final String[] items = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            DiscoveredDevice dev = devices.get(i);
            String status = dev.code == 200 
                ? getString(R.string.device_status_available) 
                : getString(R.string.device_status_auth_required);
            items[i] = dev.ip + " (" + status + ")";
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.dialog_multiple_xdrip_title))
                .setItems(items, (dialog, which) -> {
                    if (which >= 0 && which < devices.size()) {
                        selectDiscoveredDevice(devices.get(which));
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void showHelpDialog() {
        String helpText = getString(R.string.help_manual_text);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.dialog_help_title))
                .setMessage(helpText)
                .setPositiveButton(getString(R.string.btn_ok), null)
                .show();
    }

    /**
     * Performs a fast connection check with the currently typed IP and API Secret.
     */
    private void testConnection() {
        final String ip = etIpAddress.getText() != null ? etIpAddress.getText().toString().trim() : "";
        final String secret = etApiSecret.getText() != null ? etApiSecret.getText().toString().trim() : "";
        
        if (ip.isEmpty()) {
            Toast.makeText(this, getString(R.string.err_empty_ip), Toast.LENGTH_SHORT).show();
            return;
        }

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(getString(R.string.dialog_test_connection_title));
        progressDialog.setMessage(getString(R.string.dialog_test_connection_msg, ip));
        progressDialog.setCancelable(false);
        progressDialog.show();

        String url = "http://" + ip + ":17580/sgv.json?brief_mode=Y";
        
        try {
            okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(url);
            if (!secret.isEmpty()) {
                String hashedSecret = CryptoUtils.computeSHA1(secret);
                requestBuilder.addHeader("api-secret", hashedSecret);
            }
            okhttp3.Request request = requestBuilder.build();

            HttpClientProvider.getClient().newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progressDialog.dismiss();
                        new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle(getString(R.string.dialog_test_error_title))
                                .setMessage(getString(R.string.dialog_test_error_msg, ip, e.getMessage()))
                                .setPositiveButton(getString(R.string.btn_ok), null)
                                .show();
                    });
                }

                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                    final int code = response.code();
                    final String responseBody = response.body() != null ? response.body().string() : "";
                    response.close();

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progressDialog.dismiss();
                        if (code == 200) {
                            new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                    .setTitle(getString(R.string.dialog_test_success_title))
                                    .setMessage(getString(R.string.dialog_test_success_msg))
                                    .setPositiveButton(getString(R.string.dialog_test_success_btn), null)
                                    .show();
                        } else if (code == 401) {
                            new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                    .setTitle(getString(R.string.dialog_test_auth_title))
                                    .setMessage(getString(R.string.dialog_test_auth_msg))
                                    .setPositiveButton(getString(R.string.btn_ok), null)
                                    .show();
                        } else {
                            new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                    .setTitle(getString(R.string.dialog_test_unknown_title))
                                    .setMessage(getString(R.string.dialog_test_unknown_msg, code, responseBody))
                                    .setPositiveButton(getString(R.string.btn_ok), null)
                                    .show();
                        }
                    });
                }
            });
        } catch (IllegalArgumentException e) {
            progressDialog.dismiss();
            new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(getString(R.string.dialog_test_error_title))
                    .setMessage(getString(R.string.dialog_test_error_msg, ip, e.getMessage()))
                    .setPositiveButton(getString(R.string.btn_ok), null)
                    .show();
        }
    }

    /**
     * @deprecated Use {@link CryptoUtils#computeSHA1(String)} instead.
     * Kept temporarily for backward compatibility reference.
     */
    private String computeSHA1(String input) {
        return CryptoUtils.computeSHA1(input);
    }

    private void updateCoreSetupInteractivity(boolean isNetwork) {
        cardCoreSetup.setAlpha(isNetwork ? 1.0f : 0.5f);
        etIpAddress.setEnabled(isNetwork);
        etApiSecret.setEnabled(isNetwork);
        btnTestConnection.setEnabled(isNetwork);
        btnAutoSearchBeta.setEnabled(isNetwork);
        btnSave.setEnabled(isNetwork);
    }

    private void checkForUpdates(boolean silent) {
        if (!silent) {
            runOnUiThread(() -> tvUpdateStatus.setText(getString(R.string.msg_checking_updates)));
        }
        
        String url = "https://api.github.com/repos/EvgeniyKrasnyanskiy/DiaNight/releases/latest";
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "DiaNight-App")
                .build();
                
        HttpClientProvider.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tvUpdateStatus.setText(getString(R.string.msg_update_check_failed));
                    if (!silent) {
                        Toast.makeText(SettingsActivity.this, getString(R.string.err_update_check), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    final int code = response.code();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        tvUpdateStatus.setText(getString(R.string.msg_update_check_error_code, code));
                        if (!silent) {
                            Toast.makeText(SettingsActivity.this, getString(R.string.err_update_check_code, code), Toast.LENGTH_SHORT).show();
                        }
                    });
                    response.close();
                    return;
                }
                
                String body = response.body() != null ? response.body().string() : "";
                response.close();
                
                try {
                    com.google.gson.JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    String tagName = root.has("tag_name") ? root.get("tag_name").getAsString() : "";
                    String htmlUrl = root.has("html_url") ? root.get("html_url").getAsString() : "";
                    
                    String apkUrl = htmlUrl;
                    if (root.has("assets")) {
                        JsonArray assets = root.getAsJsonArray("assets");
                        for (int i = 0; i < assets.size(); i++) {
                            com.google.gson.JsonObject asset = assets.get(i).getAsJsonObject();
                            if (asset.has("name") && asset.get("name").getAsString().endsWith(".apk")) {
                                if (asset.has("browser_download_url")) {
                                    apkUrl = asset.get("browser_download_url").getAsString();
                                    break;
                                }
                            }
                        }
                    }
                    
                    final String remoteVer = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    
                    String localVerStr = "1.1.0";
                    try {
                        localVerStr = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    } catch (Exception e) {
                        // ignore
                    }
                    
                    final String localVer = localVerStr;
                    final boolean isNewer = isVersionNewer(localVer, remoteVer);
                    final String finalApkUrl = apkUrl;
                    
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (isNewer) {
                            tvUpdateStatus.setText(getString(R.string.msg_update_available, remoteVer, localVer));
                            btnDownloadUpdate.setVisibility(View.VISIBLE);
                            downloadUrl = finalApkUrl;
                            if (!silent) {
                                new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                        .setTitle(getString(R.string.dialog_update_title))
                                        .setMessage(getString(R.string.dialog_update_msg, remoteVer))
                                        .setPositiveButton(getString(R.string.btn_download), (dialog, which) -> {
                                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(finalApkUrl)));
                                        })
                                        .setNegativeButton(getString(R.string.btn_cancel), null)
                                        .show();
                                Toast.makeText(SettingsActivity.this, getString(R.string.msg_update_toast, remoteVer), Toast.LENGTH_LONG).show();
                            }
                        } else {
                            tvUpdateStatus.setText(getString(R.string.msg_up_to_date, localVer));
                            btnDownloadUpdate.setVisibility(View.GONE);
                            if (!silent) {
                                Toast.makeText(SettingsActivity.this, getString(R.string.msg_no_updates), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        tvUpdateStatus.setText(getString(R.string.msg_update_parse_error));
                        if (!silent) {
                            Toast.makeText(SettingsActivity.this, getString(R.string.err_update_parse), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private boolean isVersionNewer(String local, String remote) {
        if (local == null || remote == null || local.isEmpty() || remote.isEmpty()) {
            return false;
        }
        
        String[] localParts = local.split("\\.");
        String[] remoteParts = remote.split("\\.");
        
        int length = Math.max(localParts.length, remoteParts.length);
        for (int i = 0; i < length; i++) {
            int localVal = i < localParts.length ? parseVersionPart(localParts[i]) : 0;
            int remoteVal = i < remoteParts.length ? parseVersionPart(remoteParts[i]) : 0;
            
            if (remoteVal > localVal) {
                return true;
            } else if (remoteVal < localVal) {
                return false;
            }
        }
        return false;
    }
    
    private int parseVersionPart(String part) {
        try {
            String clean = part.replaceAll("[^0-9]", "");
            return clean.isEmpty() ? 0 : Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTestAlarm();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        if (scanExecutor != null) {
            try {
                scanExecutor.shutdownNow();
            } catch (Exception e) {
                // Ignore
            }
        }
        if (scanningClient != null) {
            try {
                scanningClient.dispatcher().cancelAll();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
