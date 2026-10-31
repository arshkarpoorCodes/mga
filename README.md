# MyBoard – a minimal keyboard (IME) for Android / LineageOS

Features: QWERTY + number row, shift (tap = one capital, double-tap = caps lock),
auto-capitalise, 2 symbol pages, hold-to-repeat backspace, double-space = ". ",
haptic feedback, light/dark theme follows the system, multi-touch typing,
correct Enter/Search/Go/Send behaviour, number pad layout for number/phone fields.

## Build the APK
1. Install Android Studio and open this folder (File > Open). Let it sync (it will fetch Gradle).
2. Build > Build APK(s). Output: app/build/outputs/apk/debug/app-debug.apk
   (or CLI: `gradle assembleDebug` / `./gradlew assembleDebug` once the wrapper exists)
3. Copy the APK to your phone and install it (or `adb install app-debug.apk`).

## Turn it on
Open the MyBoard app > "1. Enable MyBoard" > switch it on, then "2. Switch to MyBoard".
Or: Settings > System > Languages & input > On-screen keyboard > Manage keyboards.
