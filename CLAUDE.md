# Tumbler: guide for Claude

Tumbler is an Android app lock by Coco Experiments (https://cocoexperiments.com). The user picks apps (WhatsApp, Instagram, ...); opening one shows a 3x3 board of "digit groups" that reshuffles when the lock opens and after a wrong tap. Two modes per app: Privacy (PIN each time, grace window) and Digital wellbeing (pause, PIN, time-boxed window). It does not touch the system lock screen (Android forbids that). Repo: github.com/cocoexperiments/tumbler. Read `README.md` first for product behaviour and platform limits.

## Docs

- [Automated versioning architecture](https://claude.ai/artifact/G3MqshSQ2L94SdAC3z64cD): design for auto-incrementing the version on every push to `main`. Implemented in `.github/workflows/release.yml`. Sign-in to claude.ai is required.
- `README.md`: build, first run, layout, "one lock instead of two", staying locked, known platform limits.
- `docs/` is the GitHub Pages landing page (`index.html`, logo). It must stay in step with the product (two use cases: privacy and digital wellbeing). Do not put internal documentation there; it is published.

Keep this file current. When architecture, build, release or conventions change, update the relevant section here and the linked doc in the same piece of work.

## Stack

- Kotlin 2.0.21, Jetpack Compose with Material 3 (dark only), AGP 8.7.3, Gradle 8.9 (no wrapper checked in; CI and local use a system `gradle`), JDK 17.
- minSdk 26, targetSdk/compileSdk 35. Single module `:app`, package `com.cocoexperiments.tumbler`.
- Dependencies are managed in `gradle/libs.versions.toml` (version catalog). Add libraries there, not inline.
- Storage: `EncryptedSharedPreferences` via `androidx.security:security-crypto` (deprecated upstream, still works).

## Architecture

- `domain/`: pure Kotlin, no Android imports, unit tested. `KeypadGenerator` builds the board (every digit appears at least twice, three distinct digits per button). `PinSession` is the step-counter state machine; `LockoutPolicy` handles escalating lockout (free for 4 wrong taps, then 30s doubling up to 15 min). `UnlockPolicy` decides when the fingerprint is offered. `AppLockPolicy` holds the per-app rules: `LockMode` (PRIVACY/WELLBEING), `LockedApp`, `shouldLock`, grant expiry, the wellbeing pause, and the `package|MODE|minutes` storage encoding.
- `data/PinRepository`: PIN, master switch, locked-app list, per-day open counts, fingerprint and `pinRequired` flags (encrypted prefs).
- `service/`: `LockGuardService` (accessibility service, the trigger: on a window change of a locked package with no grant it starts the lock; a timer re-locks when a grant expires while the app is open; SCREEN_OFF clears Privacy grants), `LockLauncher` (starts `LockScreenActivity` with `EXTRA_TARGET`), `LockState` (in-memory cache of settings plus per-package grants on `elapsedRealtime`), `BootReceiver` (sets `pinRequired` after boot).
- `LockScreenActivity` (also hosts the fingerprint prompt): shown in front of the locked app, excluded from Recents, own task (`taskAffinity=""`, `singleTask`). Back goes to the launcher. It finishes on unlock, which reveals the app. `MainActivity` is setup, home and the locked-apps picker (`ui/main/LockedAppsScreen`).
- `ui/`: Compose. `lock/`, `main/`, `components/`, `theme/` (brand colours live in `theme/Brand.kt`).

## Product rules (do not break these)

- Tumbler has one PIN of its own shared by all locked apps (Android gives apps no access to the system PIN, and its prompt cannot shuffle). Do not add per-app PINs.
- An app is unlocked only by a grant in `LockState`, created by a correct PIN or, for Privacy apps when the user enabled it, a successful system fingerprint prompt (`LockViewModel.unlock`). Never create grants elsewhere. Grants live in memory only.
- Wellbeing apps never offer the fingerprint (it would skip the pause). `AppLockPolicy.fingerprintAllowedFor` decides.
- Do not use `noHistory`, `ACTION_CLOSE_SYSTEM_DIALOGS` (blocked since Android 12) or Lock Task mode (needs Device Owner). Do not add a foreground service or notifications: the accessibility service is kept alive by the system.
- The accessibility service must only watch window changes, never read screen content. Keep its config minimal (`canRetrieveWindowContent=false`). It is required, not optional.
- Fingerprint is an opt-in alternative, never a replacement: the PIN keypad is always usable. It uses `androidx.biometric` `BiometricPrompt` with `BIOMETRIC_STRONG` and no device-credential fallback. `LockScreenActivity` is a `FragmentActivity` for this reason. `PinRepository.pinRequired` is set on boot and cleared only by a correct PIN; while set, the fingerprint is not offered.
- Keep `domain/` free of Android dependencies so it stays unit testable.
- Use a `<queries>` launcher-intent entry for the app picker, never `QUERY_ALL_PACKAGES`.
- Manifest: `LockScreenActivity`, the guard service and `allowBackup=false` are deliberate. Only `MainActivity` and `BootReceiver` are exported.
- Risk to remember: Google Play reviews Accessibility use strictly. Distribution is by APK for now.

## Build and test

- `gradle testDebugUnitTest assembleDebug`
- Unit tests are in `app/src/test/.../domain/` (`KeypadGeneratorTest`, `PinSessionTest`, `UnlockPolicyTest`, `AppLockPolicyTest`). Add tests for any new `domain/` logic.
- If you change the logo parameters, run `python3 logo/gen_logo.py <repo root>` and re-export `docs/logo.png` from `docs/logo.svg`.

## CI, versioning and releases

- `.github/workflows/release.yml` (Build and Release): runs on every push to `main`. Computes the version, runs unit tests, builds the debug APK with that version, uploads it as an Actions artifact, then tags the commit `v<version>` and publishes a GitHub Release with `tumbler-<version>.apk`. A failed build creates no tag, so the number is not used up.
- `.github/workflows/android.yml` (Android CI): pull requests and manual runs only. Tests and debug build, no tag, no release, no version.
- Version: patch increments on every push to `main` (v1.0.1, v1.0.2, ...). Git tags are the source of truth: the next version is the highest plain `vMAJOR.MINOR.PATCH` tag plus one patch. `versionName` is that version; `versionCode` is `git rev-list --count HEAD`. Design and rationale: see the versioning doc above.
- To start a new minor or major line, push a tag by hand (for example `v1.1.0`). It does not build by itself; the next push to `main` becomes `1.1.1`.
- `app/build.gradle.kts` reads `-PappVersionName` and `-PappVersionCode`. Local builds without them get `0.0.0-dev` and code 1. Never hand-edit the version, and never add version-bump commits.
- The tag is pushed with `GITHUB_TOKEN`, which does not start other workflows, so the release is published in the same workflow. Do not add a separate tag-triggered release workflow.
- Runs are serialized with a `concurrency` group. GitHub keeps only the newest waiting run, so if several pushes land while a build is running, the older queued ones are skipped and get no version of their own.
- Never rewrite `main` history (force push, rebase of pushed commits): it can lower the commit count and break `versionCode` ordering.
- APKs are debug-signed. There is no release keystore yet, so each build's signature depends on the debug key of the runner. Moving to a stable release key is an open item; do it before sharing builds beyond sideloading.

## Conventions

- Commits are authored as Sooraj Parameswaran <sooraj@cocoexperiments.com>. Do not change the git identity or author commits as Claude.
- Work is pushed straight to `main`; pull requests are for outside contributors.
- Match existing style: Kotlin official code style, short KDoc on non-obvious classes, no unused dependencies.
