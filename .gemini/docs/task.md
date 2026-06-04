# Чек-лист реализации

- [x] Исправить локализацию (замена захардкоженных строк на getString(R.string...)) в [SettingsActivity.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/SettingsActivity.java)
- [x] Закрыть HTTP-утечки в [MainActivity.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/MainActivity.java) (`fetchGlucoseData` и `fetchIoBData`)
- [x] Повысить надежность отправки телеметрии в [TelemetryTracker.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/TelemetryTracker.java) (проверка ответа и версионирование ключа запуска)
- [x] Оптимизировать таймер ночника `startToggleCycle()` в [MainActivity.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/MainActivity.java)
- [x] Проверить сборку проекта через `./gradlew assembleDebug`
- [x] Зафиксировать изменения в Git (Conventional Commits) и выполнить `git push`
