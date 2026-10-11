# План реализации: Приведение цвета глюкозы к международному стандарту (AGP/TIR)

## 1. Анализ стандарта из проекта TIRUp
В проекте `TIRUp` (`Theme.kt` и `TargetRanges.kt`) используется 6-полосный клинический стандарт AGP / Time in Range (Battelino et al., 2019):
- `< 3.0 ммоль/л` — **Very Low** (Критическая гипогликемия): `#EF4444` (Urgent Red)
- `3.0 .. 3.8 ммоль/л` — **Low** (Предупреждение о гипогликемии): `#F59E0B` (Warning Amber)
- `3.9 .. 7.8 ммоль/л` — **Tight Target** (Оптимальная норма): `#4ADE80` (Bright Pale Green)
- `7.9 .. 10.0 ммоль/л` — **Target** (Целевой диапазон TIR): `#10B981` (Emerald Green)
- `10.1 .. 13.9 ммоль/л` — **High** (Предупреждение о гипергликемии): `#F59E0B` (Warning Amber)
- `>= 14.0 ммоль/л` — **Very High** (Критическая гипергликемия): `#EF4444` (Urgent Red)
- Оффлайн / нет данных: `#94A3B8` (серый)

## 2. Задачи
1. **[Completed] Обновление ресурсов цветов (`colors.xml`):**
   - Добавлена палитра `glucose_very_low`, `glucose_low`, `glucose_tight`, `glucose_target`, `glucose_high`, `glucose_very_high`, `glucose_stale`.
2. **[Completed] Обновление логики окрашивания на главном экране (`MainActivity.java`):**
   - Добавлен метод `MainActivity.getGlucoseStandardColor(double valueMmol)`.
   - Метод `applyTextColor()` обновлен для работы со стандартной 6-полосной шкалой AGP/TIR с сохранением диммирования яркости.
3. **[Completed] Обновление экрана настроек (`SettingsActivity.java`, `activity_settings.xml`):**
   - Метод `updateGlucoseBrightnessDisplay()` в настройках переведен на `MainActivity.getGlucoseStandardColor()`.
   - В разметке `activity_settings.xml` обновлены тестовые значения и цвета предпросмотра (2.8 — `#EF4444`, 5.5 — `#4ADE80`, 11.4 — `#F59E0B`).
4. **[Completed] Проверка и сборка:**
   - Компиляция `compileDebugJavaWithJavac` и сборка APK `assembleDebug` успешно выполнены.
