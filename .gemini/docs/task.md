# Список задач

- [x] MainActivity.java: Отмена таймера неактивности (`inactivityRunnable`) в `onPause()`
- [x] MainActivity.java: Оптимизация `alarmVisualAnimator` в `onPause()` и `onResume()`
- [x] MainActivity.java: Добавление `try-catch` (`IllegalArgumentException`) в `fetchGlucoseData()` и `fetchIoBData()`
- [x] SettingsActivity.java: Добавление `try-catch` (`IllegalArgumentException`) в `testConnection()`
- [x] SettingsActivity.java: Оптимизация OkHttpClient в автопоиске (использование единого `scanningClient`)
- [x] SettingsActivity.java: Отмена запросов сканирования в `onDestroy()`
- [x] Верификация: Проверка успешной компиляции проекта через Gradle (`.\gradlew.bat assembleDebug`)
