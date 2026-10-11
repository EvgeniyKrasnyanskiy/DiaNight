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
