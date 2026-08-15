# План реализации: Исправление багов и опция статического IP (отключение автосканирования)

## 1. Контекст и цели
1. Устранить все найденные в ходе код-ревью дефекты (поток будильника `STREAM_ALARM`, `prepareAsync`, время автозакрытия, освобождение камеры в `onPause`, очистка пула потоков сканера, безопасный fallback в `CryptoUtils`).
2. Добавить новую настройку: чекбокс «Статический IP (не сканировать сеть)» в меню настроек, отключающий фоновый автопоиск сервера при потере связи.

## 2. Задачи и изменяемые файлы

### 1. `app/src/main/java/com/diaclock/nightstand/CryptoUtils.java`
- В `computeSHA1`: при возникновении ошибки возвращать пустую строку `""` вместо сырого `input`.

### 2. `app/src/main/res/values/strings.xml` & `app/src/main/res/values-ru/strings.xml`
- Добавить локализованную строку `chk_disable_auto_scan`:
  - RU: `Статический IP (не сканировать сеть)`
  - EN: `Static IP (disable network scan)`

### 3. `app/src/main/res/layout/activity_settings.xml`
- Добавить `CheckBox` `chkDisableAutoScan` в карточку `cardCoreSetup` под полем `etApiSecret`.

### 4. `app/src/main/java/com/diaclock/nightstand/SettingsActivity.java`
- Привязать чекбокс `chkDisableAutoScan`, сохранять/загружать ключ `"disable_auto_scan"`.
- Нормализовать сохранение времени автозакрытия (`HH:mm`).
- Перевести `testMediaPlayer` на `STREAM_ALARM` и `prepareAsync()`.
- Добавить проверку `progressDialog != null && progressDialog.isShowing()` перед созданием нового диалога.

### 5. `app/src/main/java/com/diaclock/nightstand/MainActivity.java`
- Перевести `mediaPlayer` на `STREAM_ALARM` / `AudioAttributes.USAGE_ALARM` и `prepareAsync()`.
- В `onPause()` вызывать `stopFlashBlinking()` и освобождать камеру.
- В `timeRunnable` сопоставлять часы и минуты численно или через нормализованную строку.
- В `triggerSilentSubnetAutoDiscovery`:
  - Проверять флаг `disable_auto_scan` перед стартом.
  - Использовать активный локальный IP интерфейса при необходимости.
  - Вызывать `scanExecutor.shutdownNow()` и `scanClient.dispatcher().cancelAll()` при завершении/таймауте.

## 3. План верификации
- Компиляция через `./gradlew assembleDebug`.
- Проверка сохранения настроек и корректности сборки.
