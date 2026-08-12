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

- **Оптимизация опроса сети и защита от ложных предупреждений (v1.1.70 / v1.1.71):**
  - Увеличен базовый интервал опроса мастер-источника с 15 до 30 секунд.
  - Увеличены таймауты сетевых соединений `connectTimeout` и `readTimeout` в `HttpClientProvider` с 10 до 15 секунд.
  - Порог показа предупреждения (желтый треугольник `showNetworkWarning`) увеличен до **10 сбоев подряд (5 минут)** (`consecutiveNetworkFailures >= 10`), что полностью устраняет случайные мигания значка.

### 2. Сборка и Релизы:
- Скомпилирован и проверен релизный APK `DiaNight-v1.1.71-release.apk`.
- Успешно установлен и проверен через ADB на подключенном смартфоне.
- Опубликован официальный релиз на GitHub: `v1.1.71` (`https://github.com/EvgeniyKrasnyanskiy/DiaNight/releases/tag/v1.1.71`).

### 3. Коммиты сессии:
1. `b0a7ed9` — `feat: adapt build.gradle for Android 4.4 KitKat (minSdk 19) and OkHttp 3.12.13`
2. `ae048e3` — `feat: fix color palette button tinting on API 19 and add background master IP auto-discovery`
3. `5c4a491` — `feat: add camera flash alarm, scheduled auto-close, and update default glucose thresholds`
4. `744292d` — `fix: enable TLS 1.2 compatibility for GitHub update checks on Android 4.4 KitKat`
5. `0cb28c2` — `fix: resolve Trust anchor CA certificate exception for GitHub API on Android 4.4 KitKat`
6. `e94da65` — `feat: configure release signing with official dianight.jks keystore`
7. `43415a9` — `fix: adjust poll interval to 30s, timeout to 15s, and trigger warning on 2+ consecutive failures`
8. `8efad07` — `fix: increase network warning threshold to 10 consecutive failures (5 min)`
