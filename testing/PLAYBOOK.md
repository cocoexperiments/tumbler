# Tumbler test playbook

Every behaviour of the app, how it is checked, and what "pass" means. Three kinds of check:

| Tag | Meaning | Runs with |
|---|---|---|
| **U** | Unit test (pure `domain/` logic) | `gradle testDebugUnitTest` |
| **S** | Static check on the built APK / source | `python3 testing/e2e/tumbler_e2e.py static` |
| **E** | End-to-end on an emulator or phone, driven by script | `python3 testing/e2e/tumbler_e2e.py run [IDs]` |
| **M** | Manual or Claude-driven: needs a human judgement, a real device, a reboot, or a long wait | Follow the steps; record the result in the run log below |

Add a scenario here for every new behaviour, and automate it (U, S or E) whenever it can be. Keep IDs stable.

## Setup for E and M runs

1. Emulator: Android 15 (API 35) arm64, e.g. AVD `tumbler-pixel`. Any device with API 26+ works for manual runs.
2. Environment: `export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`, JDK 17, Gradle 8.x.
3. Build: `gradle assembleDebug`. The driver installs `app/build/outputs/apk/debug/app-debug.apk`.
4. Test apps: any two launchable apps that are not Tumbler. Defaults are Calendar (`com.google.android.calendar`) and YouTube (`com.google.android.youtube`); override with `--app-a` and `--app-b` (package names). Names shown in the UI are read from the device.
5. Test PIN: `6248`. Everything below assumes it.
6. State: the driver resets with `pm clear`, so each run starts from a fresh install.

The keypad shuffles, so the driver reads the screen (`uiautomator dump`) to find which button contains the next digit. Manual runs do the same by looking.

## Scenarios

### A. First run and PIN

| ID | Scenario | Steps | Expected | Tag |
|---|---|---|---|---|
| A01 | Fresh install shows PIN creation | Install, launch | "Create your PIN" with a numeric pad; no home screen | E |
| A02 | PIN mismatch is rejected | Enter 6248, then 1111 | "PINs didn't match", back to the first entry | E |
| A03 | PIN is created | Enter 6248 twice | Home screen appears | E |
| A04 | Enable switch is gated | On home with no permissions | "Enable app lock" switch disabled; status says to grant permissions | E |
| A05 | Permission rows reflect state | Grant overlay; enable guard | Rows flip to "Granted"; switch becomes enabled only when both are granted | E |
| A06 | Change PIN needs the current PIN | Home, Change PIN | Asks for the current PIN first; a wrong one shows "Incorrect PIN" | M |
| A07 | New PIN takes effect | Change to 1357 | Locked apps accept 1357; 6248 is rejected | M |
| A08 | PIN is 4 digits only | Try to save a shorter PIN | Not accepted | U (`PinPolicy`) |

### B. Locked-apps picker

| ID | Scenario | Steps | Expected | Tag |
|---|---|---|---|---|
| B01 | List loads | Home, Locked apps | Spinner, then launchable apps sorted by name; Tumbler itself absent | E |
| B02 | Locking defaults | Switch an app on | Mode Privacy, "PIN each time · re-lock after 1 min", count increments | E |
| B03 | Switch to Wellbeing | Tap Wellbeing chip | Summary "10 s pause · open for 10 min"; options 5/10/15/30 | E |
| B04 | Back to Privacy resets timing | Tap Privacy chip | Summary back to 1 min; options 1/5/15 | E |
| B05 | Change timing | Tap another minutes chip | Summary updates | E |
| B06 | Settings persist | Leave the screen, force-stop Tumbler, reopen | Same apps, modes and timings | E |
| B07 | Unlocking an app | Switch an app off | Entry removed; app opens with no lock | E |
| B08 | Order | Reopen the picker | Locked apps first, then alphabetical; rows do not jump while toggling | M |
| B09 | Storage format round trip and damaged entries | n/a | `encode`/`decode` round trip; bad entries dropped | U (`AppLockPolicyTest`) |

### C. Privacy lock

| ID | Scenario | Steps | Expected | Tag |
|---|---|---|---|---|
| C01 | Locked app is covered | Open a Privacy app | Lock appears showing that app's icon and "NAME is locked", 3x3 keypad | E |
| C02 | Unlocked apps are untouched | Open an app that is not locked | Opens normally, no lock | E |
| C03 | Correct PIN reveals the app | Enter 6248 | Lock closes; the app is in front and stays in front | E |
| C04 | Wrong tap reshuffles | Tap a button without the next digit | Dots reset to step 0, board changes, error flash | E |
| C05 | Correct digits keep the board | Tap correct groups | Board does not change between digits | E |
| C06 | Board rules | n/a | Every digit appears at least twice; three distinct digits per button; nine buttons | U (`KeypadGeneratorTest`) |
| C07 | Lockout | 5 wrong taps in a row | "Too many wrong attempts. Try again in N s", keypad disabled; first lockout 30 s | E |
| C08 | Lockout escalates and caps | Keep failing | 30 s, 60 s, ... capped at 15 min; first 4 failures are free | U (`PinSessionTest`) |
| C09 | Lockout survives leaving | Trigger lockout, press Home, reopen the app | Countdown still running | E |
| C10 | Grace window | Unlock, press Home, reopen within the grace time | Opens with no lock | E |
| C11 | Grace expiry | Unlock, wait past the grace time (1 min), reopen | Lock appears again | E |
| C12 | Screen off clears Privacy | Unlock, screen off then on | Lock is in front when the screen is back | E |
| C13 | Switching locked apps | Lock open for app A; open app B (also locked) | Lock retargets to B with B's name and icon; one lock, not stacked | E |
| C14 | Same app twice | Lock open; guard fires again for the same app | Digits and board are not reset mid-entry | M |
| C15 | Back on the lock | Press Back | Launcher appears (never the locked app); lock removed from the task list | E |
| C16 | Home on the lock | Press Home | Launcher appears; reopening the app shows the lock again | E |
| C17 | Recents on the lock | Open Recents | Lock is not listed; no stranded lock after leaving | E |
| C18 | Screenshots blocked | Take a screenshot while the lock is shown | Image is black (FLAG_SECURE) | E |
| C19 | Cover speed | Cold and warm open of a locked app | Lock's first frame within 1 s on cold start, 0.5 s warm; record the times | E |
| C20 | Master switch off | Turn "Enable app lock" off, open a locked app | No lock | E |
| C21 | Guard off | Disable the accessibility service, open a locked app | No lock (known limit); home shows the guard row as not granted | E |
| C22 | Overlay revoked | Revoke "Display over other apps" | No lock, home shows the row as not granted | M |
| C23 | Lock while the app is mid-use | Unlock, keep using past the grace time without leaving | Not interrupted (Privacy only re-locks on the next open) | M |
| C24 | Rotation | Rotate the device on the lock | Stays portrait, state kept | M |
| C25 | Incoming call | Lock open, call arrives | Call UI is usable; lock does not fight it | M |

### D. Wellbeing lock

| ID | Scenario | Steps | Expected | Tag |
|---|---|---|---|---|
| D01 | Pause screen | Open a Wellbeing app | "Take a breath", countdown from 10, keypad dimmed | E |
| D02 | Taps ignored during the pause | Tap buttons during the countdown | Still "Digit 1 of 4", no errors, no lockout counted | E |
| D03 | Keypad opens after 10 s | Wait | Countdown gone; "Open for how long?" with chips 5/10/15/30 | E |
| D04 | Open count text | Open on consecutive times | "You've opened NAME 1 time today..." then "2 times" | E |
| D05 | Unlock grants the window | Enter 6248 | App in front; reopening within the window shows no lock | E |
| D06 | Chosen window is used | Pick 15 min, unlock | Grant lasts 15 min, not the default | M |
| D07 | Window ends while the app is open | Unlock with the shortest window and keep the app open | Lock returns by itself within a few seconds of expiry | E (5 min wait) |
| D08 | Next open after expiry | Reopen after expiry | New pause, new keypad | E |
| D09 | Screen off keeps the window | Unlock, screen off/on within the window | No lock | E |
| D10 | No fingerprint | Fingerprint enabled | No prompt, no "Use fingerprint" button on Wellbeing | E |
| D11 | Open count resets daily | Change the device date to tomorrow | Count text reverts to the plain pause message | M |
| D12 | Wrong PIN in Wellbeing | Fail after the pause | Reshuffle and error as in C04; pause does not restart | M |

### E. Fingerprint

Needs an enrolled fingerprint. On the emulator: Settings > Security > Fingerprint, then `adb emu finger touch 1`.

| ID | Scenario | Steps | Expected | Tag |
|---|---|---|---|---|
| E01 | Card visibility | Home on a device with no sensor / not enrolled / enrolled | Hidden / "Add fingerprint" button / switch | M |
| E02 | Privacy prompt | Enable fingerprint, open a Privacy app | System prompt appears; `adb emu finger touch 1` unlocks | E |
| E03 | Use PIN | Tap "Use PIN" | Keypad usable; "Use fingerprint" button reopens the prompt | E |
| E04 | Wrong finger | `adb emu finger touch 2` | Prompt stays; PIN still works | M |
| E05 | After reboot | Reboot, open a Privacy app | No prompt; PIN required; after a correct PIN the prompt is offered again | M |
| E06 | Policy | n/a | `fingerprintAllowed` only when enabled, not `pinRequired`, sensor ready | U (`UnlockPolicyTest`) |
| E07 | Mode rule | n/a | Fingerprint allowed for Privacy, not Wellbeing | U (`AppLockPolicyTest`) |

### F. Grants and policy (pure logic)

| ID | Scenario | Expected | Tag |
|---|---|---|---|
| F01 | Unlocked app never locks | `shouldLock(null, ...)` is false | U |
| F02 | No grant locks | true | U |
| F03 | Grant boundary | locked at exactly the expiry time | U |
| F04 | Expiry maths | `grantExpiry` adds minutes | U |
| F05 | Pause only for Wellbeing | 10 s vs 0 | U |
| F06 | Screen-off clears only Privacy | per mode | U |
| F07 | Default timing is a valid option for each mode | per mode | U |

### G. Reboot

| ID | Scenario | Steps | Expected | Tag |
|---|---|---|---|---|
| G01 | PIN required after boot | Reboot; open a locked app | Lock appears; no fingerprint until a correct PIN | M |
| G02 | Guard survives reboot | Reboot | Accessibility service running again (no user action); locks work | M |
| G03 | Grants do not survive | Unlock, reboot | Lock appears again | M |
| G04 | Process death | `adb shell am kill` Tumbler after unlock | Next open of the app asks again (grants are in memory) | E |

### H. Security and platform rules (from CLAUDE.md)

| ID | Rule | Check | Tag |
|---|---|---|---|
| H01 | Only `MainActivity` and `BootReceiver` exported | Parse the manifest | S |
| H02 | `allowBackup=false` | Parse the manifest | S |
| H03 | No `QUERY_ALL_PACKAGES`; launcher `<queries>` present | Parse the manifest | S |
| H04 | No notification, foreground-service or full-screen-intent permissions | Parse the manifest | S |
| H05 | Accessibility config minimal: window state events only, `canRetrieveWindowContent=false` | Parse `lock_guard_service.xml` | S |
| H06 | `domain/` has no Android imports | grep | S |
| H07 | No `noHistory`, `ACTION_CLOSE_SYSTEM_DIALOGS`, Lock Task | grep | S |
| H08 | Grants are created only by `LockViewModel.unlock` | grep for `LockState.grant(` | S |
| H09 | Lock activity excluded from Recents, own task | Manifest flags; also C17 | S, E |
| H10 | Guard is the only trigger and sees no content | Dump: `dumpsys accessibility` shows the service without retrieve-content capability | E |
| H11 | Lock UI is secure | C18 | E |
| H12 | Version is never hand-edited | `app/build.gradle.kts` reads `appVersionName`/`appVersionCode` | S |

### I. Landing page (`docs/index.html`)

| ID | Scenario | Expected | Tag |
|---|---|---|---|
| I01 | Hero demo, Privacy | Tapping the group with the next digit advances; wrong group reshuffles; 2·5·8·0 unlocks | M (browser) |
| I02 | Hero demo, Wellbeing | 5 s countdown with keypad disabled, then it works; says "Unlocked for 10 minutes" | M (browser) |
| I03 | No leftover phone-lock claims | No "lock screen", "power button", "system lock", "Notifications" permission text | S |
| I04 | Links | Download, GitHub, cocoexperiments.com, anchors resolve | S |
| I05 | Mobile layout | 390 px wide: no horizontal scroll, cards stack | M (browser) |
| I06 | Product match | Landing page mocks match the real screens (picker, Privacy lock, Wellbeing pause) | M |

## Run log

Record each full run here (newest first): date, build (commit), device, result per ID that is not green, and notes.

| Date | Commit | Device | Result |
|---|---|---|---|
| | | | |
