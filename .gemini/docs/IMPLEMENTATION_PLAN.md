# План реализации: Оптимизация энергоэффективности, автоскрытие колокольчика и мягкая индикация тревоги в MainActivity

## Предложенные изменения

### MainActivity.java
[MODIFY] [MainActivity.java](file:///h:/DiaNight/app/src/main/java/com/diaclock/nightstand/MainActivity.java)

1. **Оптимизация `timeRunnable`:**
   - Инициализировать `timeRunnable` как `final` поле класса один раз.
   - Метод `startTimeUpdates()` упростить до безопасного сброса и постановки в очередь:
     ```java
     private void startTimeUpdates() {
         mainHandler.removeCallbacks(timeRunnable);
         mainHandler.post(timeRunnable);
     }
     ```
2. **Вынос регистрации `batteryReceiver`:**
   - Создать методы `registerBatteryReceiver()` и `unregisterBatteryReceiver()` для безопасной регистрации/разрегистрации ресивера с проверкой флага `isBatteryReceiverRegistered`.
3. **Остановка и запуск процессов в жизненном цикле (`onPause` / `onResume`):**
   - В `onPause()` останавливать **все** фоновые задачи и отписываться от событий:
     ```java
     mainHandler.removeCallbacks(timeRunnable);
     mainHandler.removeCallbacks(networkRunnable);
     mainHandler.removeCallbacks(toggleRunnable);
     pixelShiftHandler.removeCallbacks(pixelShiftRunnable);
     unregisterBatteryReceiver();
     unregisterXdripReceiver();
     ```
   - В `onResume()` запускать задачи заново:
     ```java
     registerBatteryReceiver();
     startTimeUpdates();
     pixelShiftHandler.removeCallbacks(pixelShiftRunnable);
     pixelShiftHandler.post(pixelShiftRunnable);
     if (!nightlightMode) {
         startToggleCycle();
     }
     ```
4. **Скрытие иконки «Колокольчик»:**
   - Изменить метод `fadeAttributesOut()`, чтобы иконка `ivAlarmBell` скрывалась через 15 секунд всегда (убрав условие `if (!alarmEnabled)`).
5. **Визуальная индикация тревоги (мягкое мерцание):**
   - Создать поле `private ValueAnimator alarmVisualAnimator` класса `MainActivity`.
   - В `startAlarmSound()` инициализировать и запустить пульсацию фона `mainRootLayout` между черным цветом и глубоким темно-красным (`#330000`):
     ```java
     alarmVisualAnimator = ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), Color.BLACK, Color.parseColor("#330000"));
     alarmVisualAnimator.setDuration(1500);
     alarmVisualAnimator.setRepeatCount(ValueAnimator.INFINITE);
     alarmVisualAnimator.setRepeatMode(ValueAnimator.REVERSE);
     alarmVisualAnimator.addUpdateListener(animation -> {
         if (mainRootLayout != null && !isActivityDestroyed) {
             mainRootLayout.setBackgroundColor((int) animation.getAnimatedValue());
         }
     });
     alarmVisualAnimator.start();
     ```
   - В `stopAlarmSound()` отменять анимацию и возвращать фон в чисто черный (`Color.BLACK`):
     ```java
     if (alarmVisualAnimator != null) {
         alarmVisualAnimator.cancel();
         alarmVisualAnimator = null;
     }
     if (mainRootLayout != null) {
         mainRootLayout.setBackgroundColor(Color.BLACK);
     }
     ```

---

## План верификации

### Автоматические тесты
- Запустить сборку проекта через Gradle: `.\gradlew.bat assembleDebug` для проверки компиляции.

### Ручная верификация
- Убедиться, что колокольчик скрывается через 15 секунд независимо от состояния тревоги.
- Проверить, что при срабатывании тревоги фон экрана мягко пульсирует темно-красным цветом, а при откладывании (snooze) или нормализации сахара — возвращается к черному.
