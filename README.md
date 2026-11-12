# Tumbler

<p align="center"><img src="docs/logo.png" width="128" alt="Tumbler logo"></p>

**tumbler** - a Coco Experiments product (https://cocoexperiments.com). Simple, purposeful security.

Compose + Material 3 (dark) **app lock** with a shuffling 3x3 "digit group" keypad. Pick the apps to protect (WhatsApp, Instagram, ...); opening one shows Tumbler's keypad in front of it. The board shuffles when the lock opens and after a wrong tap, and stays fixed while you enter correct digits.

Two uses, chosen per app:
- **Privacy**: PIN (or fingerprint) every time, with a grace window (1/5/15 min) so switching apps does not re-prompt. Grants are dropped when the screen turns off.
- **Digital wellbeing**: friction against doom scrolling. A 10 s pause with the keypad disabled ("Take a breath", with today's open count), then the PIN, then a time-boxed window (5/10/15/30 min, chosen on the lock screen) after which the app locks again. Fingerprint is not offered here because it would skip the pause.

## Brand
The logo is a padlock whose body is the 3x3 keypad. `logo/gen_logo.py <repo root>` regenerates the launcher icon, the in-app logo and `docs/logo.svg` from one set of parameters (re-export `docs/logo.png` from the SVG if you change it).
All brand colours live in `ui/theme/Brand.kt` (plus the launcher icon in `res/drawable/ic_launcher_*.xml` and `res/values/colors.xml`). Replace them with official Coco Experiments values when available.

## One PIN
Android does not let apps read or verify the phone's own PIN (the only route is BiometricPrompt's device-credential screen, which cannot shuffle), so Tumbler has its own 4-digit PIN. It is shared by every locked app; what differs per app is the mode and timing. Tumbler does not touch the system lock screen.

## Fingerprint unlock
Optional, off by default: turn on **Unlock with fingerprint** on the Home screen. A Privacy lock then offers the system fingerprint prompt (strong biometrics only) and the PIN keypad stays available underneath ("Use PIN" or the "Use fingerprint" button), so either one unlocks. The first unlock after a reboot always needs the PIN, like Android's own lock. Only Privacy apps offer it.

## Build
Open in Android Studio (Ladybug or newer, JDK 17). Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21.
`gradle testDebugUnitTest assembleDebug` (or `gradle wrapper` once to generate `gradlew`). Every push to `main` is built, given the next version (1.0.1, 1.0.2, ...) and published as a GitHub Release with the APK attached; download it on your phone and install it. Pull requests only get a test and build check. To start a new minor or major line, push a tag by hand such as `v1.1.0`.

## First run
1. Create and confirm a 4-digit PIN.
2. Grant **Display over other apps** and turn on the **Tumbler lock guard** in Accessibility settings (required: it is how Tumbler notices an app opening).
3. Open **Locked apps**, switch on the apps to protect and choose Privacy or Wellbeing for each.
4. Turn on "Enable app lock", then open a locked app to test.

## Layout
- `domain/KeypadGenerator.kt` - board generator (every digit appears >= 2 times, 3 distinct digits per button)
- `domain/PinSession.kt` - step counter state machine + lockout policy
- `domain/AppLockPolicy.kt` - per-app modes, when to lock, grants, pause, storage format
- `domain/UnlockPolicy.kt` - when the fingerprint may be offered
- `data/PinRepository.kt` - EncryptedSharedPreferences storage (PIN, locked apps, open counts)
- `service/LockGuardService.kt` - accessibility service that detects a locked app coming forward and shows the lock; `LockState` caches settings and grants; `LockLauncher` starts the lock; `BootReceiver` requires the PIN after a reboot
- `LockScreenActivity.kt` - the lock in front of an app; `ui/` - Compose screens and components (`ui/main/LockedAppsScreen.kt` is the picker)

## Known platform limits
- Android does not let third-party apps replace or unlock the system lock screen, so Tumbler locks apps, not the phone.
- The lock is an overlay activity started by an accessibility service; there is a brief moment where the app may be visible before the lock covers it.
- Anyone with the unlocked phone can uninstall Tumbler or switch off its Accessibility permission. That is fine as friction, and worth knowing for privacy.
- Apps that use the Accessibility API face strict Google Play review; for now Tumbler is distributed as an APK. `androidx.security:security-crypto` is deprecated upstream but still works.
- Aggressive OEM battery managers may stop the accessibility service; exclude the app from battery optimisation.

## How locking works
- The guard sees `TYPE_WINDOW_STATE_CHANGED` (package name only, `canRetrieveWindowContent=false`). For a locked app with no active grant it starts `LockScreenActivity` with the target package.
- A correct PIN (or fingerprint, Privacy only) records an in-memory grant for that package and finishes the lock, which reveals the app. Grants end by time; a timer in the guard re-locks an app that is still open when a Wellbeing window runs out.
- Back or Home on the lock goes to the launcher, never to the locked app. The lock is excluded from Recents and lives in its own task.
- Reboot: the first unlock needs the PIN (fingerprint is not offered until then).
- If you are ever stuck, switch off the accessibility service in Settings, or reboot into Safe mode and uninstall.

## Thanks
- Thanks to [Jijo Johnson](https://github.com/jijorcj), whose idea Tumbler is.
