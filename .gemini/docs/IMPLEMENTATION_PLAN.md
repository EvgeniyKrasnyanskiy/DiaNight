# План реализации: Исправление состояния гонки, утечек ресурсов, поддержка портретного режима и функция экранного фонарика по двойному тапу

Этот план описывает изменения для устранения состояния гонки на `networkPollInterval`, предотвращения утечки ресурсов `MediaPlayer` и диалоговых окон, оптимизации работы аниматора в фоне, а также перехода на единый экземпляр `OkHttpClient`.
Дополнительно добавляется поддержка вертикального (портретного) режима устройства для главного экрана и экрана настроек, а также функция «экранного фонарика» с плавной регулировкой яркости на протяжении 750 мс и переключением по двойному тапу.

---

## 1. Логика изменений по файлам

### 1.1 Сетевой уровень и HttpClient
*   **[NEW] [HttpClientProvider.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/HttpClientProvider.java)**:
    Создание класса с единственным статическим экземпляром `OkHttpClient` (Shared Client) и таймаутами по умолчанию в 5 секунд.
*   **[MODIFY] [TelemetryTracker.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/TelemetryTracker.java)**:
    Использование `HttpClientProvider.getClient()` вместо создания нового экземпляра `OkHttpClient`.

### 1.2 Ресурсы и макеты
*   **[MODIFY] [activity_main.xml](file:///h:/DiaNight/app/src/main/res/layout/activity_main.xml)**:
    *   Добавление `View` с id `viewFlashlightOverlay` на весь экран с белым фоном, высокой высотой (`elevation="10dp"`) и начальной видимостью `gone`.
*   **[MODIFY] [strings.xml (RU)](file:///h:/DiaNight/app/src/main/res/values-ru/strings.xml) и [strings.xml (Default/EN)](file:///h:/DiaNight/app/src/main/res/values/strings.xml)**:
    *   Добавить в справку `help_manual_text` пункт о включении и выключении фонарика с помощью быстрого двойного тапа по экрану.

### 1.3 Главный экран и логика работы
*   **[MODIFY] [MainActivity.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/MainActivity.java)**:
    *   Объявить `networkPollInterval` как `volatile` для безопасного доступа.
    *   Использовать `HttpClientProvider.getClient()`.
    *   Освобождать ресурсы `mediaPlayer` в блоке `catch` метода `startAlarmSound()`.
    *   Приостанавливать аниматор двоеточия `breathingAnimator` в `onPause()` и возобновлять в `onResume()`.
    *   Заменить `getWindowManager().getDefaultDisplay().getMetrics(metrics)` на `getResources().getDisplayMetrics()`.
    *   Зарегистрировать `batteryReceiver` с флагом `RECEIVER_NOT_EXPORTED` на API 33+.
    *   **Поддержка портретного режима**: Переопределить `onConfigurationChanged()` и вызывать в нем `adjustTextSizes()`.
    *   **Логика фонарика по двойному тапу**: Инициализировать `GestureDetector` с `SimpleOnGestureListener`. Привязать `onTouchListener` к `mainRootLayout` и `viewFlashlightOverlay`. При двойном тапе переключать фонарик: плавно анимировать альфу оверлея от 0.0f до 1.0f (и обратно) и яркость окна (`lp.screenBrightness` от 0.1f до 1.0f и обратно) за **750 мс** через `ValueAnimator`. При отключении сбрасывать яркость к системным настройкам.

### 1.4 Экран настроек
*   **[MODIFY] [SettingsActivity.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/SettingsActivity.java)**:
    *   Использовать `HttpClientProvider.getClient()`.
    *   Перенести `ProgressDialog progressDialog` в поле класса и закрывать его в `onDestroy()`.
    *   Перенести проверки `isFinishing() || isDestroyed()` внутрь колбэков `runOnUiThread()` в методе `checkForUpdates()`.

### 1.5 Утилиты и Манифест
*   **[MODIFY] [CryptoUtils.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/CryptoUtils.java)**:
    *   Оптимизировать метод `computeSHA1()` с помощью массива символов и `StandardCharsets.UTF_8`.
*   **[MODIFY] [AndroidManifest.xml](file:///h:/DiaNight/app/src/main/AndroidManifest.xml)**:
    *   Изменить `screenOrientation="sensorLandscape"` на `screenOrientation="sensor"` для **обоих** Activity (`MainActivity` и `SettingsActivity`) для полной поддержки поворотов.
    *   Удалить атрибут `package="com.diaclock.nightstand"`.

---

## 2. План верификации

### Автоматические тесты
*   Запустить сборку проекта через Gradle: `./gradlew assembleDebug` для проверки компиляции и отсутствия синтаксических ошибок.

### Ручная верификация
1.  **Портретный режим (MainActivity и SettingsActivity)**:
    *   Повернуть телефон вертикально на главном экране. Убедиться, что время, показатели глюкозы и настройки адаптируются.
    *   Открыть настройки и повернуть телефон. Убедиться, что экран настроек переворачивается в портретный режим и прокручивается по вертикали.
2.  **Экранный фонарик по двойному тапу**:
    *   Быстро дважды тапнуть по экрану. Белый оверлей должен плавно проявиться (750 мс), а яркость дисплея — подняться до максимума.
    *   Быстро дважды тапнуть по экрану во время работы фонарика. Белый оверлей должен плавно угаснуть (750 мс), а яркость экрана — вернуться к системным настройкам.
    *   Проверить, что одиночный тап во время фонарика не приводит к его выключению.
3.  **Утечки окон**: Проверить поворот экрана в настройках во время сканирования IP.
4.  **Устранение предупреждений Gradle**: Проверить лог сборки Gradle на отсутствие предупреждения о дублировании namespace.
