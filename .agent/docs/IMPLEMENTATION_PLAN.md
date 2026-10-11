# План реализации: DiaNight

## 1. Завершённые задачи [Completed]
1. **[Completed] Цвета глюкозы по стандарту AGP/TIR (из проекта TIRUp):**
   - Внедрена 6-полосная шкала международного клинического консенсуса (Battelino et al., 2019).
   - Единый метод `MainActivity.getGlucoseStandardColor(double valueMmol)`.
   - Обновлен предпросмотр в `SettingsActivity` и `activity_settings.xml`.
2. **[Completed] Исправление тихого автопоиска подсети без API Secret:**
   - Устранена блокировка, запрещавшая запуск фонового поиска мастера, если пароль пустой.
   - Заголовок `api-secret` теперь добавляется только при наличии ключа.
   - В `SettingsActivity` снято требование обязательного ввода пароля для поиска сервера.
3. **[Completed] Надежный выбор камеры со вспышкой (Hardware Torch):**
   - Добавлен метод `getFlashCameraId()` с проверкой `CameraCharacteristics.FLASH_INFO_AVAILABLE`.
   - Реализован приоритет основной (задней) камеры при наличии нескольких модулей.
4. **[Completed] Внедрение Foreground Service (`NightMonitorService`):**
   - Добавлен сервис `NightMonitorService` с тихим постоянным уведомлением низкого приоритета (`IMPORTANCE_LOW`).
   - Защита ночного процесса мониторинга от уничтожения системой (Low Memory Killer).
   - Добавлены разрешения `FOREGROUND_SERVICE` и `POST_NOTIFICATIONS`.
5. **[Completed] Исправление флага `BatteryReceiver`:**
   - Переключен на `Context.RECEIVER_EXPORTED` для системного интента `ACTION_BATTERY_CHANGED` на API 33+, устраняя риск падения на API 34+.

6. **[Completed] Минимизация размера APK (R8, ProGuard, resConfigs):**
   - Включены `minifyEnabled true`, `shrinkResources true`, `resConfigs "en", "ru"`.
   - Настроен `app/proguard-rules.pro` с сохранением `com.diaclock.nightstand.**`, Gson и OkHttp 3.12 (полная совместимость с Android 4.4 KitKat / minSdk 19).
   - Размер релиза сокращен с 5.14 МБ до 1.73 МБ (на 65%).
7. **[Completed] Масштабирование часов и сахара в альбомном режиме (1.3x):**
   - В `MainActivity.java` в методах `adjustTextSizes()` и `adjustGlucoseAndIoBTextSizes()` применен множитель `1.3f` для времени, двоеточия, сахара и IoB в ландшафтном режиме.
8. **[Completed] Релизная сборка и проверка:**
   - Собран релиз `DiaNight-v1.1.79-release.apk` (1.73 МБ), проверен single-dex и ресурсы.
9. **[Completed] Дополнительное масштабирование в ландшафтном режиме (1.495x / +15%):**
   - Увеличен коэффициент до `1.495f` в `MainActivity.java` с динамической защитой безопасных полей экрана для OLED pixel-shift (±16px).
10. **[Completed] Регулировка яркости свайпом по экрану и HUD:**
   - Реализован вертикальный свайп с плавным диммированием от 1% до 100%, всплывающий индикатор `layoutBrightnessHud` и сохранение в настройки.
11. **[Completed] Формирование Roadmap проекта:**
   - Создан документ `.agent/docs/ROADMAP.md` со стратегическими направлениями развития (предиктивная гипо, глубокий красный ночной OLED режим, Flip-to-Snooze, Proximity Dimmer).
