# Changelog

## [v1.1.80] - 2026-10-11

### Added
- **Screen Brightness Swipe Control & HUD**:
  - Implemented vertical swipe gesture across the screen: dragging down dims the screen smoothly down to 1% (ideal for nighttime sleeping without glare), dragging up brightens up to 100%.
  - Added floating HUD indicator pill with sun icon and real-time percentage (`layoutBrightnessHud`, `ic_sun.xml`, `bg_brightness_hud.xml`), automatically fading out after 1 second.
  - Custom brightness level is saved to `SharedPreferences` and restored on app launch.
  - Preserved gesture separation: single tap (snooze alarm) and double tap (flashlight toggle) remain fully operational without false triggers.
- **Project Roadmap (`ROADMAP.md`)**:
  - Documented strategic improvement paths in `.agent/docs/ROADMAP.md`: predictive hypo alarms, night/day threshold profiles, deep red/amber OLED sleep mode, proximity sensor dimming, and flip-to-snooze.

### Changed
- **Enhanced Landscape Scaling (1.495x)**:
  - Increased clock, colon margin, glucose, and IoB sizes by an additional 1.15x (multiplier `1.495f` relative to base) for superior readability from a nightstand distance.
  - Added safety margins preventing glyph clipping against display edges during OLED burn-in pixel shifts (`±16px`) on compact screens.

## [v1.1.79] - 2026-10-11

### Added
- **R8 ProGuard / APK Size Optimization**:
  - Enabled R8 minification (`minifyEnabled true`) and resource shrinking (`shrinkResources true`) in `build.gradle`.
  - Created strict `proguard-rules.pro` keeping all application classes (`com.diaclock.nightstand.**`), Gson serialization, and OkHttp 3.12 reflection mechanisms to guarantee 100% backward compatibility down to Android 4.4 KitKat (`minSdk 19`).
  - Added `resConfigs "en", "ru"` to strip unused Material Design localizations across 80+ unused languages.
  - Reduced release APK size from 5.14 MB to 1.73 MB (65% reduction) with a lightweight single-dex payload for faster loading and minimal RAM footprint on legacy devices.

### Changed
- **Landscape Screen Scaling (1.3x)**:
  - Scaled clock digits (HH:mm), colon separator margin, glucose value, and IoB indicators up by 1.3x in landscape orientation (`Configuration.ORIENTATION_LANDSCAPE` / wide displays) for enhanced nighttime readability from a nightstand distance.

## [v1.1.78] - 2026-10-11

### Changed
- **International AGP/TIR Glucose Color Standard**:
  - Aligned glucose color formatting with the 6-band clinical consensus (Battelino et al., 2019) standard matching the `TIRUp` project:
    - `< 3.0 mmol/L`: Very Low / Level 2 Hypo -> `#EF4444` (Urgent Red)
    - `3.0 .. 3.8 mmol/L`: Low / Level 1 Hypo -> `#F59E0B` (Warning Amber)
    - `3.9 .. 7.8 mmol/L`: Tight Target TITR -> `#4ADE80` (Bright Pale Green)
    - `7.9 .. 10.0 mmol/L`: Target TIR -> `#10B981` (Emerald Green)
    - `10.1 .. 13.9 mmol/L`: High / Level 1 Hyper -> `#F59E0B` (Warning Amber)
    - `>= 14.0 mmol/L`: Very High / Level 2 Hyper -> `#EF4444` (Urgent Red)
  - Added unified glucose color definitions (`glucose_very_low`, `glucose_low`, `glucose_tight`, `glucose_target`, `glucose_high`, `glucose_very_high`, `glucose_stale`) to `colors.xml`.
  - Created centralized static method `MainActivity.getGlucoseStandardColor(valueMmol)` with brightness scaling support.
  - Updated glucose brightness preview in `SettingsActivity` and `activity_settings.xml`.

### Added
- **NightMonitorService (Foreground Service)**:
  - Added foreground service keeping app in high-priority state to prevent Android Low Memory Killer (LMK) from terminating overnight monitoring.
  - Added persistent low-priority notification with quick tap access back to MainActivity.
  - Added `FOREGROUND_SERVICE` and `POST_NOTIFICATIONS` permissions.

### Fixed
- **Subnet Auto-Discovery Without API Secret**:
  - Removed check requiring non-empty API Secret, allowing automatic discovery of unauthenticated local xDrip+ master servers.
- **Hardware Torch / Flashlight Camera Selection**:
  - Added `FLASH_INFO_AVAILABLE` check with preference for rear-facing camera to ensure torch works reliably across multi-camera devices.
- **Battery Receiver Export Flag**:
  - Set `RECEIVER_EXPORTED` for `ACTION_BATTERY_CHANGED` on API 33+ to prevent future `SecurityException` crashes.
