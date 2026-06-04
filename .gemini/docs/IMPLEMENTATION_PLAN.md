# План реализации: Исправление багов стабильности, оптимизация OkHttpClient и защита от некорректного ввода

## Предложенные изменения

### MainActivity.java
[MODIFY] [MainActivity.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/MainActivity.java)

1. **Отмена таймера неактивности в `onPause`:**
   - В метод `onPause()` добавить `inactivityHandler.removeCallbacks(inactivityRunnable);`.
2. **Оптимизация `alarmVisualAnimator` в фоне:**
   - В метод `onPause()` добавить приостановку мигания экрана при звонящем будильнике:
     ```java
     if (alarmVisualAnimator != null) {
         if (android.os.Build.VERSION.SDK_INT >= 19) {
             alarmVisualAnimator.pause();
         } else {
             alarmVisualAnimator.cancel();
         }
     }
     ```
   - В метод `onResume()` добавить возобновление мигания, если будильник все еще активен:
     ```java
     if (isAlarmSounding && alarmVisualAnimator != null) {
         if (android.os.Build.VERSION.SDK_INT >= 19 && alarmVisualAnimator.isPaused()) {
             alarmVisualAnimator.resume();
         } else if (!alarmVisualAnimator.isRunning()) {
             alarmVisualAnimator.start();
         }
     }
     ```
3. **Защита от некорректного IP-адреса (Crash Protection):**
   - Обернуть блоки создания `Request.Builder().url(url)` в `fetchGlucoseData()` и `fetchIoBData()` в блоки `try-catch` для перехвата `IllegalArgumentException`. При возникновении исключения обрабатывать это как сетевую ошибку (показывать значок ошибки, скрывать IoB, выводить "---"), не допуская падения приложения.
   - Использовать `serverIp.trim()` при формировании URL.

---

### SettingsActivity.java
[MODIFY] [SettingsActivity.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/SettingsActivity.java)

1. **Защита от некорректного IP-адреса в `testConnection`:**
   - Обернуть создание тестового HTTP-запроса в `try-catch` для перехвата `IllegalArgumentException` на случай ввода некорректного IP. Выводить диалоговое окно об ошибке вместо крэша приложения.
2. **Оптимизация автопоиска (OkHttpClient reuse):**
   - Вынести создание `OkHttpClient` с короткими таймаутами из метода `verifyXdrip()` в `startNetworkAutoDiscovery()`.
   - Создавать `scanningClient` один раз перед циклом сканирования 254 IP-адресов и передавать его параметром в `verifyXdrip`.
   - Сохранять `scanningClient` в качестве приватного поля класса `SettingsActivity`.
3. **Отмена фоновых запросов сканирования при закрытии настроек:**
   - В методе `onDestroy()` вызывать `scanningClient.dispatcher().cancelAll();`, чтобы немедленно завершить все асинхронные HTTP-запросы сканирования, если пользователь закрыл экран настроек до окончания поиска.

---

## План верификации

### Автоматические тесты
- Сборка приложения через Gradle: `.\gradlew.bat assembleDebug`.

### Ручная верификация
- Ввод невалидного IP-адреса (например, с пробелом) в настройках и нажатие кнопки "Тест" / сохранение — приложение не должно падать.
- Запуск автопоиска и закрытие экрана настроек до его окончания — проверка отсутствия утечек и остановка фоновых сетевых запросов.
