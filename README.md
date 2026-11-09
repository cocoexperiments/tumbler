# Dynamic Lock

Compose + Material 3 (dark) PIN lock screen with a shuffling 3x3 "digit group" keypad.

## Build
Open in Android Studio (Ladybug or newer, JDK 17). Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21.
`./gradlew testDebugUnitTest assembleDebug` (generate the wrapper with `gradle wrapper` if you build from the CLI).

## First run
1. Create and confirm a 4-digit PIN.
2. Grant: Display over other apps, Notifications, Full-screen alerts (Android 14+).
3. Turn on "Enable lock screen", then press the power button twice to test.

## Layout
- `domain/KeypadGenerator.kt` - board generator (every digit appears >= 2 times, 3 distinct digits per button)
- `domain/PinSession.kt` - step counter state machine + lockout policy
- `data/PinRepository.kt` - EncryptedSharedPreferences storage
- `service/` - foreground service (SCREEN_OFF/ON), launcher, boot receiver
- `LockScreenActivity.kt` - shows over the keyguard; `ui/` - Compose screens and components

## Known platform limits
- Android does not let third-party apps replace or unlock the system lock screen. This app sits above it;
  on success it calls requestDismissKeyguard() (the system PIN/biometric still applies if one is set).
- The Home/Recents buttons cannot be blocked without Device Owner / Lock Task mode.
- `androidx.security:security-crypto` is deprecated upstream but still works; USE_FULL_SCREEN_INTENT is restricted on Google Play to calling/alarm apps.
- Aggressive OEM battery managers may kill the service; exclude the app from battery optimisation.
