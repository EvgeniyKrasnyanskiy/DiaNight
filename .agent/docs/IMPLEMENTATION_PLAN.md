# [Completed] План реализации: Приоритет источников данных (Broadcast → Nightscout → Wi-Fi), интервал опроса и автосохранение настроек

## Цель
Пересобрать приложение DiaNight с поддержкой выбора и приоритета источников данных (`Broadcast → Nightscout → Wi-Fi xDrip+`), контролем свежести данных (по умолчанию 2 мин), настраиваемым интервалом опроса (по умолчанию 60 сек), индикатором активного источника на главном экране, оптимизацией сетевых запросов (`/pebble`-first, умный `WifiLock`) и автосохранением настроек.

## Шаги реализации

1. **Создать `GlucoseDataFetcher.java`**
   - Инкапсулировать сетевой опрос Nightscout и Wi-Fi xDrip+.
   - Реализовать стратегию `/pebble`-first (1 запрос для SGV + тренд + время + IoB) с fallback на `/api/v1/entries/sgv.json?count=1` (для Nightscout) и `/sgv.json` (для Wi-Fi xDrip+).
   - Проверять свежесть данных по timestamp (`datetime` / `date`) с порогом `staleThresholdMs` (по умолчанию 2 мин).
   - Поддержать приоритетный fallback `Nightscout → Wi-Fi`: если Nightscout недоступен или его данные устарели, автоматически обращаться к Wi-Fi мастеру.
   - Добавить отмену активных HTTP-запросов при паузе/смене настроек.

2. **Обновить `MainActivity.java` и `activity_main.xml`**
   - Загружать флаги источников (`source_broadcast_enabled`, `source_nightscout_enabled`, `source_wifi_enabled`) с миграцией из legacy `data_source`, а также `nightscout_url`, `nightscout_api_secret`, `poll_interval_seconds` (дефолт 60), `stale_threshold_minutes` (дефолт 2).
   - Отслеживать `lastBroadcastTimestamp` при получении `com.eveningoutpost.dexdrip.BgEstimate`.
   - В `networkRunnable`: если Broadcast включён и его данные свежие (`now - lastBroadcastTimestamp <= staleThresholdMs`), пропускать сетевые запросы и показывать `LOC`; иначе запускать `GlucoseDataFetcher` (Nightscout → Wi-Fi).
   - Добавить ненавязчивый индикатор активного источника (`tvDataSourceBadge`: `LOC` / `NS` / `Wi-Fi`) в `statusBarContainer` в `activity_main.xml`.
   - Удерживать `WifiLock` только когда включён хотя бы один сетевой источник и выключен режим ночника.
   - Автопоиск подсети запускать только если включён источник Wi-Fi и именно Wi-Fi не отвечает.

3. **Обновить `SettingsActivity.java` и `activity_settings.xml`**
   - Заменить `RadioGroup` выбора источника на 3 `CheckBox` с явным порядком приоритета:
     1. Локальный Broadcast (приоритет 1)
     2. Nightscout (приоритет 2)
     3. Wi-Fi xDrip+ (приоритет 3)
   - Добавить секцию настроек Nightscout: URL, API Secret, кнопка «Проверить связь».
   - Добавить поля «Интервал опроса (секунды)» (по умолчанию 60) и «Порог устаревания данных (минуты)» (по умолчанию 2).
   - Убрать нижние кнопки «Сохранить» и «Отмена», оставить кнопку «Применить» в блоке источников/подключения.
   - Реализовать автосохранение всех настроек при изменении и в `onPause()` с валидацией числовых/временных полей без перезаписи некорректными промежуточными значениями.

4. **Локализация, документация и сборка**
   - Обновить `app/src/main/res/values/strings.xml` и `app/src/main/res/values-ru/strings.xml`.
   - Обновить `CHANGELOG.md`.
   - Собрать и проверить APK (`gradlew assembleRelease` / `assembleDebug`), запустить unit-тесты.
