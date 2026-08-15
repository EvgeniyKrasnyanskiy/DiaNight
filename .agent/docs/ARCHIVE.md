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

## Сессия 15–16 Августа 2026 [Completed]

### 1. Реализованные фичи и улучшения:
- **Устранение багов код-ревью:**
  - Аудиопоток тревог переведён на аппаратный `STREAM_ALARM` / `USAGE_ALARM` с асинхронной подготовкой `prepareAsync()`.
  - Исправлено сопоставление времени автозакрытия (`isAutoCloseTimeMatch`) для форматов с/без ведущих нулей.
  - Устранена утечка камеры в `onPause()`.
  - Безопасная очистка пула потоков сканирования подсети (`scanExecutor.shutdownNow()`).
  - Защита от сбоя SHA-1 в `CryptoUtils.computeSHA1()`.
- **Встроенные программные звуки тревоги (`SoundGenerator.java`):**
  - Алгоритмический PCM-синтезатор (44.1 кГц, 16-бит) на базе `AudioTrack.MODE_STATIC` (`STREAM_ALARM`).
  - 3 встроенных звука: «Импульсный бипер» (880/1760 Гц), «Радар / Сирена» (600–1400 Гц), «Мягкий перезвон» (аккорд с затуханием).
  - Аппаратный циклический повтор (`setLoopPoints`) и мгновенное предпрослушивание в диалоге настроек.
- **Регулировка яркости цвета сахара и IoB (Color Dimmer):**
  - В раздел «Настройки табло» добавлен ползунок `SeekBar` (диапазон 15%–100%, по умолчанию 80%) с интерактивным предпросмотром цветов.
  - Динамическое масштабирование цвета через HSV Value в `MainActivity.java`.
- **Насыщенный рубиновый цвет тревоги:**
  - Цвет текста сахара при гипо/гипергликемии: `#FF0038`.
  - Глубокая бархатно-красная пульсация фона: `#4A000A`.
- **Сетевые настройки и удержание Wi-Fi:**
  - Чекбокс «Статический IP (не сканировать сеть)» для отключения лишнего фонового сканирования.
  - Внедрён системный `WifiLock` (`WIFI_MODE_FULL_HIGH_PERF`), предотвращающий уход Wi-Fi чипа в глубокий сон при работе экрана.
  - Опция «Автоперезапуск Wi-Fi при сбое связи» (Watchdog) для автоматического переподключения/рестарта Wi-Fi модуля при 10 сбоях подряд на старых устройствах (MediaTek / Android 4.4).
- **Защита от лимитов GitHub API (Ошибка 403):**
  - Двухуровневая проверка обновлений: если GitHub REST API возвращает 403 (лимит 60 req/hr на общий IP мобильного оператора), приложение автоматически считывает версию через веб-редирект `github.com/releases/latest`, работающий со 100% надёжностью на всех версиях Android.
- **Скрытие экранной клавиатуры:**
  - Установлен режим `windowSoftInputMode="stateHidden|adjustResize"` и убран автоматический фокус с первого поля ввода при входе в настройки.
- **Актуализация руководства:**
  - Встроенная справка дополнена описанием всех новых возможностей.

### 2. Сборка и Релизы:
- Скомпилированы релизные APK: `v1.1.73`, `v1.1.74`, `v1.1.75`, `v1.1.77`.
- Релиз `v1.1.77` установлен на подключённый смартфон Alcatel OneTouch IDOL 2 через ADB.
- Опубликован официальный релиз на GitHub: `v1.1.77` (`https://github.com/EvgeniyKrasnyanskiy/DiaNight/releases/tag/v1.1.77`).

### 3. Коммиты сессии:
1. `ea8f82c` — `feat: add static IP option and fix alarm stream, autoclose, and resource leaks`
2. `45563aa` — `feat: add built-in PCM sound generator, glucose color brightness slider, and rich ruby alarm red`
3. `a1c99b3` — `fix: refactor SoundGenerator to MODE_STATIC for reliable playback and preview`
4. `cda6fbf` — `feat: add WifiLock high-performance keepalive, WiFi watchdog reconnect option, and update help manual`
5. `ddececf` — `fix: resolve GitHub API 403 rate limit with fallback and hide soft keyboard on settings launch`
6. `2822963` — `docs: mark all tasks as completed for v1.1.77`
