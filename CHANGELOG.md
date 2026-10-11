# Changelog

## [Unreleased] - 2026-10-11

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
