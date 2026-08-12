# Архив задач и сессий (DiaNight)

## Сессия 12 Августа 2026 [Completed]

### 1. Реализованные фичи и улучшения:
- **Исправление палитры ночника:** Внедрена динамическая заливка через `ViewCompat.setBackgroundTintList()` для 10 цветных кнопок в `SettingsActivity.java`, решившая проблему бирюзового наложения `colorAccent` на Android 4.4 KitKat.
- **Бесшумный фоновый автопоиск IP Мастера:** В `MainActivity.java` добавлен отслеживающий счётчик сбоев `consecutiveNetworkFailures`. При отвале связи свыше 3 раз запускается 28 параллельных потоков сканирования подсети `192.168.x.x` с автоматическим сохранением нового IP при совпадении SHA-1 от `api_secret`.
- **Стробоскоп/Вспышка при тревогах (Flashlight Alarm):** Добавлен чекбокс `alarm_use_flash`. При тревогах запускается импульсное мигание вспышки каждые 500 мс. Поддерживает как API 19 (`android.hardware.Camera`), так и API 23+ (`CameraManager`).
- **Автозакрытие приложения по расписанию (Scheduled Auto-Close):** Настройка `enable_autoclose` и `autoclose_time` (`ЧЧ:ММ`). Ровно в назначенную минуту выводится Toast и вызывается `finishAndRemoveTask()` / `finish()`.
- **Новые дефолтные диапазоны сахара:**
  - Дневной: `4.5` – `8.5` ммоль/л.
  - Ночной: `3.8` – `10.0` ммоль/л.
- **Поддержка TLS 1.2 и сертификатов для GitHub API на Android 4.4:** В `HttpClientProvider.java` реализован `TLSSocketFactory` и `X509TrustManager`, устранивший ошибки `CertPathValidatorException` (Trust Anchor Not Found) при проверке обновлений.
- **Официальная подпись релизов (`dianight.jks`):** Настроена интеграция ключа `dianight.jks` в `app/build.gradle` с безопасным чтением паролей из `local.properties`.

### 2. Сборка и Релизы:
- Скомпилирован и проверен релизний APK `DiaNight-v1.1.66-release.apk`.
- Успешно установлен и проверен через ADB на смартфоне Alcatel 6037Y.
- Опубликован официальный релиз на GitHub: `v1.1.66` (`https://github.com/EvgeniyKrasnyanskiy/DiaNight/releases/tag/v1.1.66`).

### 3. Коммиты сессии:
1. `b0a7ed9` — `feat: adapt build.gradle for Android 4.4 KitKat (minSdk 19) and OkHttp 3.12.13`
2. `ae048e3` — `feat: fix color palette button tinting on API 19 and add background master IP auto-discovery`
3. `5c4a491` — `feat: add camera flash alarm, scheduled auto-close, and update default glucose thresholds`
4. `744292d` — `fix: enable TLS 1.2 compatibility for GitHub update checks on Android 4.4 KitKat`
5. `0cb28c2` — `fix: resolve Trust anchor CA certificate exception for GitHub API on Android 4.4 KitKat`
6. `e94da65` — `feat: configure release signing with official dianight.jks keystore`
