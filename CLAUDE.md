# Tumbler: guide for Claude

Tumbler is an Android PIN lock screen app by Coco Experiments (https://cocoexperiments.com). Its keypad is a 3x3 board of "digit groups" that reshuffles when the lock opens and after a wrong tap. Repo: github.com/cocoexperiments/tumbler. Read `README.md` first for product behaviour and platform limits.

## Docs

- [Automated versioning architecture](https://claude.ai/artifact/G3MqshSQ2L94SdAC3z64cD): design for auto-incrementing the version on every push to `main`. Status: designed, not yet implemented. Sign-in to claude.ai is required.
- `README.md`: build, first run, layout, "one lock instead of two", staying locked, known platform limits.
- `docs/` is the GitHub Pages landing page (`index.html`, logo). Do not put internal documentation there; it is published.

Keep this file current. When architecture, build, release or conventions change, update the relevant section here and the linked doc in the same piece of work.

## Stack

- Kotlin 2.0.21, Jetpack Compose with Material 3 (dark only), AGP 8.7.3, Gradle 8.9 (no wrapper checked in; CI and local use a system `gradle`), JDK 17.
- minSdk 26, targetSdk/compileSdk 35. Single module `:app`, package `com.cocoexperiments.tumbler`.
- Dependencies are managed in `gradle/libs.versions.toml` (version catalog). Add libraries there, not inline.
- Storage: `EncryptedSharedPreferences` via `androidx.security:security-crypto` (deprecated upstream, still works).

## Architecture

- `domain/`: pure Kotlin, no Android imports, unit tested. `KeypadGenerator` builds the board (every digit appears at least twice, three distinct digits per button). `PinSession` is the step-counter state machine; `LockoutPolicy` handles escalating lockout (free for 4 wrong taps, then 30s doubling up to 15 min).
- `data/PinRepository`: PIN and the persisted `lockArmed` flag.
- `service/`: `LockScreenService` (foreground service listening for SCREEN_OFF/ON), `LockLauncher`, `BootReceiver` (boot and package replaced), `LockGuardService` (optional accessibility service that re-shows the lock when Home, Recents or another app comes forward), `LockState` (process-wide state).
- `LockScreenActivity`: shown over the keyguard, excluded from Recents, own task (`taskAffinity=""`, `singleTask`). `MainActivity` is setup and home.
- `ui/`: Compose. `lock/`, `main/`, `components/`, `theme/` (brand colours live in `theme/Brand.kt`).

## Product rules (do not break these)

- Tumbler is the only unlock the user sees and must not be bypassable via Recents or swipe-away. `LockState.armed` is set on screen-off and boot and cleared only by a correct PIN. Never clear it elsewhere.
- Do not use `noHistory` (destroys the lock on screen-off), `ACTION_CLOSE_SYSTEM_DIALOGS` (blocked since Android 12) or Lock Task mode (needs Device Owner). See README for why.
- The accessibility service must only watch window changes, never read screen content. Keep its config minimal.
- Keep `domain/` free of Android dependencies so it stays unit testable.
- Manifest: `LockScreenActivity`, services and `allowBackup=false` are deliberate. Only `MainActivity` and `BootReceiver` are exported.

## Build and test

- `gradle testDebugUnitTest assembleDebug`
- Unit tests are in `app/src/test/.../domain/` (`KeypadGeneratorTest`, `PinSessionTest`). Add tests for any new `domain/` logic.
- If you change the logo parameters, run `python3 logo/gen_logo.py <repo root>` and re-export `docs/logo.png` from `docs/logo.svg`.

## CI and releases

- `.github/workflows/android.yml`: on push to `main`, pull requests and manual runs. Runs unit tests, builds the debug APK and uploads it as `tumbler-debug-apk`.
- `.github/workflows/release.yml`: on push of a `v*` tag. Builds a debug APK and publishes a GitHub Release with `tumbler-<tag>.apk`.
- APKs are currently debug-signed. There is no release keystore yet.
- `app/build.gradle.kts` hardcodes `versionCode = 1` and `versionName = "1.0.0"` today.

## Versioning (planned, see the doc above)

- Every push to `main` gets the next patch version, starting at `1.0.0`. Git tags `v<major>.<minor>.<patch>` are the source of truth, tagged only after a successful build. `versionCode` is the commit count on `main`. A hand-pushed tag such as `v1.1.0` sets a new minor or major baseline.
- Open point when implementing: tags pushed with the default `GITHUB_TOKEN` do not trigger `release.yml`. Either build and release in the same workflow that creates the tag, or push the tag with a token that can trigger workflows.
- Do not add version-bump commits and do not hand-edit `versionCode` or `versionName` once this is in place.

## Conventions

- Commits are authored as Sooraj Parameswaran <sooraj@cocoexperiments.com>. Do not change the git identity or author commits as Claude.
- Work is pushed straight to `main`; pull requests are for outside contributors.
- Match existing style: Kotlin official code style, short KDoc on non-obvious classes, no unused dependencies.
