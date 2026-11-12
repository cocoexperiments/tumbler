#!/usr/bin/env python3
"""Tumbler test driver. Scenario IDs match testing/PLAYBOOK.md.

    python3 testing/e2e/tumbler_e2e.py static
    python3 testing/e2e/tumbler_e2e.py run                 # all E scenarios
    python3 testing/e2e/tumbler_e2e.py run C01 C03 D01     # some of them
    python3 testing/e2e/tumbler_e2e.py list

Needs `adb` (ANDROID_HOME/platform-tools or PATH) and one booted device/emulator for `run`.
Standard library only. Each `run` starts from a fresh install (pm clear), so it wipes Tumbler's data.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PKG = "com.cocoexperiments.tumbler"
APK = os.path.join(ROOT, "app/build/outputs/apk/debug/app-debug.apk")
PIN = "6248"
GUARD = f"{PKG}/{PKG}.service.LockGuardService"

SCENARIOS = {}  # id -> function(ctx)


def scenario(sid, title):
    def wrap(fn):
        fn.sid, fn.title = sid, title
        SCENARIOS[sid] = fn
        return fn
    return wrap


class Fail(AssertionError):
    pass


def check(cond, msg):
    if not cond:
        raise Fail(msg)


# ----------------------------------------------------------------------------- device

class Node:
    def __init__(self, el):
        self.text = el.get("text", "")
        self.desc = el.get("content-desc", "")
        self.pkg = el.get("package", "")
        self.clickable = el.get("clickable") == "true"
        self.enabled = el.get("enabled") == "true"
        self.checked = el.get("checked") == "true"
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", el.get("bounds", "[0,0][0,0]"))
        self.l, self.t, self.r, self.b = map(int, m.groups())

    @property
    def cx(self):
        return (self.l + self.r) // 2

    @property
    def cy(self):
        return (self.t + self.b) // 2

    def __repr__(self):
        return f"<{self.text or self.desc!r} {self.l},{self.t},{self.r},{self.b}>"


class Device:
    def __init__(self, serial=None):
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
        adb = os.path.join(sdk, "platform-tools", "adb")
        self.adb_bin = adb if os.path.exists(adb) else "adb"
        self.serial = serial
        self._tmp = tempfile.mkdtemp(prefix="tumbler-e2e-")

    def adb(self, *args, check_rc=True, timeout=120):
        cmd = [self.adb_bin] + (["-s", self.serial] if self.serial else []) + list(args)
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        if check_rc and p.returncode != 0:
            raise RuntimeError(f"{' '.join(cmd)} failed: {p.stderr.strip() or p.stdout.strip()}")
        return p.stdout

    def sh(self, cmd, **kw):
        return self.adb("shell", cmd, **kw)

    # input
    def tap(self, x, y):
        self.sh(f"input tap {x} {y}")

    def key(self, name):
        self.sh(f"input keyevent {name}")

    def home(self):
        self.key("KEYCODE_HOME")

    def back(self):
        self.key("KEYCODE_BACK")

    def screen_off(self):
        self.key("KEYCODE_SLEEP")

    def screen_on(self):
        self.key("KEYCODE_WAKEUP")

    def launch(self, pkg):
        self.sh(f"monkey -p {pkg} -c android.intent.category.LAUNCHER 1", check_rc=False)

    def force_stop(self, pkg):
        self.sh(f"am force-stop {pkg}")

    # screen
    def nodes(self):
        path = "/sdcard/tumbler_e2e_dump.xml"
        for _ in range(3):
            out = self.sh(f"uiautomator dump {path}", check_rc=False)
            if "dumped" in out:
                break
            time.sleep(0.5)
        local = os.path.join(self._tmp, "dump.xml")
        self.adb("pull", path, local)
        return [Node(e) for e in ET.parse(local).getroot().iter("node")]

    def foreground(self):
        out = self.sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", check_rc=False)
        m = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([\w.]+)/", out)
        return m.group(1) if m else ""

    def texts(self):
        return [n.text or n.desc for n in self.nodes() if (n.text or n.desc)]

    def find(self, text=None, regex=None, desc=None):
        for n in self.nodes():
            if text is not None and n.text == text:
                return n
            if regex is not None and re.search(regex, n.text or ""):
                return n
            if desc is not None and n.desc == desc:
                return n
        return None

    def wait_text(self, text=None, regex=None, timeout=10, gone=False):
        end = time.time() + timeout
        while time.time() < end:
            n = self.find(text=text, regex=regex)
            if (n is None) == gone:
                return n
            time.sleep(0.4)
        return None if not gone else self.find(text=text, regex=regex)

    def tap_text(self, text=None, regex=None, timeout=8):
        n = self.wait_text(text=text, regex=regex, timeout=timeout)
        check(n is not None, f"could not find {text or regex!r} on screen")
        self.tap(n.cx, n.cy)
        return n

    def screenshot_dark(self):
        """True if a screenshot is (almost) all black, which is what FLAG_SECURE produces."""
        local = os.path.join(self._tmp, "shot.png")
        with open(local, "wb") as f:
            p = subprocess.run(
                [self.adb_bin] + (["-s", self.serial] if self.serial else []) + ["exec-out", "screencap", "-p"],
                stdout=f, stderr=subprocess.PIPE,
            )
        return os.path.getsize(local) < 20_000 if p.returncode == 0 else False

    def label_of(self, pkg):
        """Display name of an installed app, from its launcher entry."""
        out = self.sh(f"cmd package resolve-activity --brief -c android.intent.category.LAUNCHER {pkg}", check_rc=False)
        return out.strip().splitlines()[-1] if out.strip() else pkg


# ----------------------------------------------------------------------------- Tumbler helpers

class Ctx:
    def __init__(self, dev, app_a, app_b):
        self.dev, self.app_a, self.app_b = dev, app_a, app_b
        self.label_a = ""
        self.label_b = ""


def digit_node(dev, digit):
    for n in dev.nodes():
        if n.text == str(digit) and (n.clickable or n.enabled):
            return n
    return None


def enter_pin_on_pad(dev, pin):
    """Plain numeric pad (PIN setup)."""
    for ch in pin:
        n = digit_node(dev, ch)
        check(n is not None, f"digit {ch} not on screen")
        dev.tap(n.cx, n.cy)
        time.sleep(0.25)


def groups_on_screen(dev):
    return [n for n in dev.nodes() if re.fullmatch(r"\d{3}", n.text or "")]


def tap_group_with(dev, digit):
    for n in groups_on_screen(dev):
        if str(digit) in n.text:
            dev.tap(n.cx, n.cy)
            return n
    raise Fail(f"no keypad button contains {digit}")


def tap_group_without(dev, digit):
    for n in groups_on_screen(dev):
        if str(digit) not in n.text:
            dev.tap(n.cx, n.cy)
            return n
    raise Fail(f"every keypad button contains {digit}")


def unlock_with_pin(dev, pin=PIN):
    """Digits stay fixed while correct, so one dump per digit is enough."""
    for ch in pin:
        tap_group_with(dev, ch)
        time.sleep(0.35)


def lock_visible(dev):
    return len(groups_on_screen(dev)) == 9


def wait_lock(dev, timeout=6):
    end = time.time() + timeout
    while time.time() < end:
        if lock_visible(dev):
            return True
        time.sleep(0.3)
    return False


def wait_no_lock(dev, timeout=4):
    end = time.time() + timeout
    while time.time() < end:
        if not lock_visible(dev):
            return True
        time.sleep(0.3)
    return False


def open_app(ctx, pkg):
    ctx.dev.launch(pkg)


def set_guard(dev, on):
    cur = dev.sh("settings get secure enabled_accessibility_services").strip()
    parts = [p for p in ("" if cur == "null" else cur).split(":") if p and p != GUARD]
    if on:
        parts.append(GUARD)
    dev.sh(f"settings put secure enabled_accessibility_services '{':'.join(parts)}'" if parts
           else "settings delete secure enabled_accessibility_services")
    dev.sh(f"settings put secure accessibility_enabled {1 if on or parts else 0}")


def fresh_install(ctx):
    dev = ctx.dev
    check(os.path.exists(APK), f"build the APK first: {APK}")
    set_guard(dev, False)
    dev.sh(f"pm uninstall {PKG}", check_rc=False)
    dev.adb("install", "-r", APK)
    dev.sh(f"appops set {PKG} SYSTEM_ALERT_WINDOW allow")
    dev.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS", check_rc=False)
    for pkg in (ctx.app_a, ctx.app_b):
        dev.force_stop(pkg)
    dev.sh("input keyevent KEYCODE_WAKEUP")
    dev.sh("wm dismiss-keyguard", check_rc=False)
    ctx.label_a = ctx.label_a or app_label(dev, ctx.app_a)
    ctx.label_b = ctx.label_b or app_label(dev, ctx.app_b)


def app_label(dev, pkg):
    """The label the picker shows. Reads it from the picker itself would be slow, so ask the package manager."""
    out = dev.sh(f"dumpsys package {pkg} | grep -m1 -E 'applicationInfo|labelRes|nonLocalizedLabel'", check_rc=False)
    m = re.search(r"nonLocalizedLabel=([^\s]+)", out)
    if m and m.group(1) != "null":
        return m.group(1)
    return PKG_LABELS.get(pkg, pkg.split(".")[-1].capitalize())


PKG_LABELS = {
    "com.google.android.calendar": "Calendar",
    "com.google.android.youtube": "YouTube",
    "com.google.android.apps.messaging": "Messages",
    "com.android.settings": "Settings",
}


def create_pin(ctx):
    dev = ctx.dev
    dev.launch(PKG)
    check(dev.wait_text(text="Create your PIN", timeout=15), "PIN creation screen not shown")
    enter_pin_on_pad(dev, PIN)
    check(dev.wait_text(text="Confirm your PIN", timeout=5), "confirm step not shown")
    enter_pin_on_pad(dev, PIN)
    check(dev.wait_text(text="Enable app lock", timeout=8), "home screen not shown after PIN")


def open_picker(ctx):
    dev = ctx.dev
    dev.launch(PKG)
    dev.wait_text(text="Enable app lock", timeout=10)
    dev.tap_text(regex=r"^(Choose|Edit)$")
    check(dev.wait_text(text="Locked apps", timeout=5), "picker not shown")
    time.sleep(1.5)  # icons load


def scroll_to(dev, label, max_swipes=14):
    for _ in range(max_swipes):
        n = dev.find(text=label)
        if n:
            return n
        dev.sh("input swipe 540 1700 540 700 300")
        time.sleep(0.4)
    return None


def row_switch(dev, row):
    """The switch is the clickable node to the right of the row label, on the same line."""
    for n in dev.nodes():
        if n.clickable and n.l > row.cx and abs(n.cy - row.cy) < 140 and n.r > 900:
            return n
    return None


def lock_app(ctx, pkg, label, mode=None, minutes=None):
    dev = ctx.dev
    open_picker(ctx)
    row = scroll_to(dev, label)
    check(row is not None, f"{label} not in the picker")
    sw = row_switch(dev, row)
    check(sw is not None, f"switch for {label} not found")
    dev.tap(sw.cx, sw.cy)
    time.sleep(0.8)
    if mode:
        dev.tap_text(text=mode)
        time.sleep(0.5)
    if minutes:
        dev.tap_text(text=f"{minutes} min")
        time.sleep(0.5)
    dev.back()
    time.sleep(0.5)


def enable_lock(ctx):
    dev = ctx.dev
    set_guard(dev, True)
    dev.launch(PKG)
    dev.wait_text(text="Enable app lock", timeout=10)
    time.sleep(1)
    sw = None
    for n in dev.nodes():
        if n.clickable and n.r > 900 and 150 < n.cy < 700 and not n.checked:
            sw = n
            break
    if sw:
        dev.tap(sw.cx, sw.cy)
    check(dev.wait_text(text="App lock is on", timeout=5), "app lock did not turn on")


def standard_setup(ctx, modes=None):
    """PIN, locked app A (Privacy), locked app B (optional Wellbeing), lock enabled. Leaves the launcher in front."""
    fresh_install(ctx)
    create_pin(ctx)
    set_guard(ctx.dev, True)
    lock_app(ctx, ctx.app_a, ctx.label_a)
    if modes == "both":
        lock_app(ctx, ctx.app_b, ctx.label_b, mode="Wellbeing", minutes=5)
    enable_lock(ctx)
    ctx.dev.home()
    time.sleep(0.5)


# ----------------------------------------------------------------------------- scenarios

@scenario("A01", "Fresh install shows PIN creation")
def a01(ctx):
    fresh_install(ctx)
    ctx.dev.launch(PKG)
    check(ctx.dev.wait_text(text="Create your PIN", timeout=15), "no PIN creation screen")
    check(ctx.dev.find(text="Enable app lock") is None, "home screen visible before a PIN exists")


@scenario("A02", "PIN mismatch is rejected")
def a02(ctx):
    dev = ctx.dev
    fresh_install(ctx)
    dev.launch(PKG)
    dev.wait_text(text="Create your PIN", timeout=15)
    enter_pin_on_pad(dev, PIN)
    dev.wait_text(text="Confirm your PIN", timeout=5)
    enter_pin_on_pad(dev, "1111")
    check(dev.wait_text(regex=r"didn.t match", timeout=5), "mismatch message not shown")


@scenario("A03", "PIN is created")
def a03(ctx):
    fresh_install(ctx)
    create_pin(ctx)


@scenario("A04", "Enable switch is gated until permissions are granted")
def a04(ctx):
    dev = ctx.dev
    fresh_install(ctx)
    dev.sh(f"appops set {PKG} SYSTEM_ALERT_WINDOW deny")
    create_pin(ctx)
    check(dev.find(text="Grant the permissions below to turn it on"), "setup hint missing")
    sw = [n for n in dev.nodes() if n.clickable and n.r > 900 and 150 < n.cy < 700]
    check(sw and not sw[0].enabled, "enable switch should be disabled")


@scenario("A05", "Permission rows reflect state")
def a05(ctx):
    dev = ctx.dev
    fresh_install(ctx)
    dev.sh(f"appops set {PKG} SYSTEM_ALERT_WINDOW deny")
    create_pin(ctx)
    check(len([n for n in dev.nodes() if n.text == "Grant"]) == 2, "expected two Grant buttons")
    dev.sh(f"appops set {PKG} SYSTEM_ALERT_WINDOW allow")
    set_guard(dev, True)
    dev.home()
    dev.launch(PKG)
    time.sleep(1.5)
    check(len([n for n in dev.nodes() if n.text == "Granted"]) >= 2, "rows should read Granted")


@scenario("B01", "Picker lists apps, not Tumbler")
def b01(ctx):
    fresh_install(ctx)
    create_pin(ctx)
    open_picker(ctx)
    check(ctx.dev.find(text="Tumbler") is None, "Tumbler must not list itself")
    check(scroll_to(ctx.dev, ctx.label_a), f"{ctx.label_a} not listed")


@scenario("B02", "Locking defaults to Privacy, 1 min")
def b02(ctx):
    fresh_install(ctx)
    create_pin(ctx)
    lock_app(ctx, ctx.app_a, ctx.label_a)
    open_picker(ctx)
    check(ctx.dev.find(text="1 apps protected") or ctx.dev.find(regex=r"^1 apps? protected"), "count should be 1")
    scroll_to(ctx.dev, ctx.label_a)
    check(ctx.dev.find(regex=r"PIN each time . re-lock after 1 min"), "default summary wrong")


@scenario("B03", "Wellbeing mode defaults to 10 s pause, 10 min")
def b03(ctx):
    fresh_install(ctx)
    create_pin(ctx)
    lock_app(ctx, ctx.app_a, ctx.label_a, mode="Wellbeing")
    open_picker(ctx)
    scroll_to(ctx.dev, ctx.label_a)
    check(ctx.dev.find(regex=r"10 s pause . open for 10 min"), "wellbeing summary wrong")
    for m in ("5 min", "10 min", "15 min", "30 min"):
        check(ctx.dev.find(text=m), f"missing {m} option")


@scenario("B04", "Back to Privacy resets timing")
def b04(ctx):
    fresh_install(ctx)
    create_pin(ctx)
    lock_app(ctx, ctx.app_a, ctx.label_a, mode="Wellbeing")
    open_picker(ctx)
    scroll_to(ctx.dev, ctx.label_a)
    ctx.dev.tap_text(text="Privacy")
    time.sleep(0.6)
    check(ctx.dev.find(regex=r"PIN each time . re-lock after 1 min"), "privacy summary wrong after switching back")


@scenario("B05", "Timing chip updates the summary")
def b05(ctx):
    fresh_install(ctx)
    create_pin(ctx)
    lock_app(ctx, ctx.app_a, ctx.label_a, minutes=5)
    open_picker(ctx)
    scroll_to(ctx.dev, ctx.label_a)
    check(ctx.dev.find(regex=r"re-lock after 5 min"), "5 min not applied")


@scenario("B06", "Settings persist across a restart")
def b06(ctx):
    fresh_install(ctx)
    create_pin(ctx)
    lock_app(ctx, ctx.app_a, ctx.label_a, mode="Wellbeing", minutes=15)
    ctx.dev.force_stop(PKG)
    open_picker(ctx)
    scroll_to(ctx.dev, ctx.label_a)
    check(ctx.dev.find(regex=r"10 s pause . open for 15 min"), "settings lost after restart")


@scenario("B07", "Unlocking an app removes the lock")
def b07(ctx):
    standard_setup(ctx)
    lock_app(ctx, ctx.app_a, ctx.label_a)  # toggles it off
    ctx.dev.home()
    open_app(ctx, ctx.app_a)
    time.sleep(2)
    check(wait_no_lock(ctx.dev), "app still locked after switching it off")
    check(ctx.dev.foreground() == ctx.app_a, "app did not open")


@scenario("C01", "Locked app is covered by the lock")
def c01(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "lock did not appear")
    check(ctx.dev.find(text=f"{ctx.label_a} is locked"), "lock does not name the app")


@scenario("C02", "Apps that are not locked are untouched")
def c02(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_b)
    time.sleep(2.5)
    check(wait_no_lock(ctx.dev), "lock appeared for an unlocked app")
    check(ctx.dev.foreground() == ctx.app_b, "unlocked app did not open")


@scenario("C03", "Correct PIN reveals the app and it stays")
def c03(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    unlock_with_pin(ctx.dev)
    time.sleep(1.5)
    check(ctx.dev.foreground() == ctx.app_a, f"expected {ctx.app_a} in front, got {ctx.dev.foreground()}")
    time.sleep(2.5)
    check(wait_no_lock(ctx.dev), "lock came back while using the app")


@scenario("C04", "Wrong tap reshuffles and resets the sequence")
def c04(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    tap_group_with(ctx.dev, PIN[0])
    time.sleep(0.4)
    check(ctx.dev.find(text="Digit 2 of 4"), "step should advance")
    before = sorted(n.text for n in groups_on_screen(ctx.dev))
    tap_group_without(ctx.dev, PIN[1])
    time.sleep(1.2)
    check(ctx.dev.find(text="Digit 1 of 4"), "sequence should reset after a wrong tap")
    after = sorted(n.text for n in groups_on_screen(ctx.dev))
    check(before != after, "board did not reshuffle")


@scenario("C05", "Correct digits keep the board")
def c05(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    before = sorted(n.text for n in groups_on_screen(ctx.dev))
    tap_group_with(ctx.dev, PIN[0])
    time.sleep(0.5)
    after = sorted(n.text for n in groups_on_screen(ctx.dev))
    check(before == after, "board changed after a correct digit")


@scenario("C07", "Five wrong taps start a lockout")
def c07(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    for _ in range(5):
        tap_group_without(ctx.dev, PIN[0])
        time.sleep(0.9)
    check(ctx.dev.wait_text(regex=r"Too many wrong attempts", timeout=3), "no lockout message after 5 wrong taps")


@scenario("C09", "Lockout survives leaving the lock")
def c09(ctx):
    c07(ctx)
    ctx.dev.home()
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock on reopen")
    check(ctx.dev.wait_text(regex=r"Too many wrong attempts", timeout=3), "lockout was reset by leaving")


@scenario("C10", "Grace window: no re-prompt within 1 min")
def c10(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    unlock_with_pin(ctx.dev)
    time.sleep(1.5)
    ctx.dev.home()
    time.sleep(1)
    open_app(ctx, ctx.app_a)
    time.sleep(2.5)
    check(wait_no_lock(ctx.dev), "locked again inside the grace window")


@scenario("C11", "Grace expiry: lock returns after 1 min")
def c11(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    unlock_with_pin(ctx.dev)
    time.sleep(1.5)
    ctx.dev.home()
    time.sleep(65)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock after the grace window ended")


@scenario("C12", "Screen off clears a Privacy grant")
def c12(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    unlock_with_pin(ctx.dev)
    time.sleep(1.5)
    ctx.dev.screen_off()
    time.sleep(1.5)
    ctx.dev.screen_on()
    ctx.dev.sh("wm dismiss-keyguard", check_rc=False)
    check(wait_lock(ctx.dev, timeout=8), "lock not in front after screen off/on")


@scenario("C13", "Lock retargets when another locked app opens")
def c13(ctx):
    standard_setup(ctx, modes="both")
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock for A")
    check(ctx.dev.find(text=f"{ctx.label_a} is locked"), "wrong app named for A")
    ctx.dev.sh(f"monkey -p {ctx.app_b} -c android.intent.category.LAUNCHER 1", check_rc=False)
    time.sleep(3)
    texts = ctx.dev.texts()
    check(any(ctx.label_b in t for t in texts) or any("Take a breath" in t for t in texts),
          f"lock did not retarget to {ctx.label_b}: {texts[:8]}")


@scenario("C15", "Back on the lock goes to the launcher")
def c15(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    ctx.dev.back()
    time.sleep(1.5)
    check(wait_no_lock(ctx.dev), "lock still showing after Back")
    check(ctx.dev.foreground() != ctx.app_a, "Back revealed the locked app")
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "lock did not return when the app was reopened")


@scenario("C16", "Home on the lock goes to the launcher")
def c16(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    ctx.dev.home()
    time.sleep(1.5)
    check(wait_no_lock(ctx.dev), "lock still showing after Home")
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "lock did not return when the app was reopened")


@scenario("C17", "Lock is not in Recents")
def c17(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    out = ctx.dev.sh("dumpsys activity recents | grep -c 'LockScreenActivity'", check_rc=False).strip()
    check(out in ("0", ""), f"LockScreenActivity found in recents ({out})")


@scenario("C18", "Lock is hidden from screenshots")
def c18(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    check(ctx.dev.screenshot_dark(), "screenshot of the lock was not blank")


@scenario("C19", "Cover speed (informational: prints timings)")
def c19(ctx):
    standard_setup(ctx)
    ctx.dev.sh("logcat -c")
    ctx.dev.launch(ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    out = ctx.dev.sh("logcat -d -b events | grep -E 'wm_activity_launch_time|am_activity_launch_time'", check_rc=False)
    print("      launch times:\n      " + "\n      ".join(out.strip().splitlines()[-4:]))


@scenario("C20", "Master switch off disables locking")
def c20(ctx):
    dev = ctx.dev
    standard_setup(ctx)
    dev.launch(PKG)
    dev.wait_text(text="App lock is on", timeout=8)
    sw = [n for n in dev.nodes() if n.clickable and n.r > 900 and 150 < n.cy < 700][0]
    dev.tap(sw.cx, sw.cy)
    check(dev.wait_text(text="App lock is off", timeout=5), "switch did not turn off")
    dev.home()
    open_app(ctx, ctx.app_a)
    time.sleep(2.5)
    check(wait_no_lock(dev), "lock appeared with the master switch off")


@scenario("C21", "Guard off disables locking (known limit)")
def c21(ctx):
    standard_setup(ctx)
    set_guard(ctx.dev, False)
    time.sleep(1)
    open_app(ctx, ctx.app_a)
    time.sleep(2.5)
    check(wait_no_lock(ctx.dev), "lock appeared without the accessibility service")


@scenario("D01", "Wellbeing pause screen")
def d01(ctx):
    standard_setup(ctx, modes="both")
    ctx.dev.home()
    open_app(ctx, ctx.app_b)
    check(ctx.dev.wait_text(text="Take a breath", timeout=6), "no pause screen")
    check(ctx.dev.find(regex=r"Keypad opens in \d+ s"), "no countdown")


@scenario("D02", "Taps are ignored during the pause")
def d02(ctx):
    d01(ctx)
    for n in groups_on_screen(ctx.dev)[:3]:
        ctx.dev.tap(n.cx, n.cy)
    check(ctx.dev.find(text="Digit 1 of 4"), "a tap during the pause changed the step")
    check(ctx.dev.find(regex=r"Too many wrong") is None, "taps during the pause counted as failures")


@scenario("D03", "Keypad opens after the pause with duration chips")
def d03(ctx):
    d01(ctx)
    check(ctx.dev.wait_text(regex=r"Keypad opens in", timeout=14, gone=True) is None, "countdown never ended")
    check(ctx.dev.find(text="Open for how long?"), "duration prompt missing")
    for m in ("5 min", "10 min", "15 min", "30 min"):
        check(ctx.dev.find(text=m), f"missing {m} chip")


@scenario("D04", "Open count text")
def d04(ctx):
    standard_setup(ctx, modes="both")
    for _ in range(2):
        ctx.dev.home()
        open_app(ctx, ctx.app_b)
        check(ctx.dev.wait_text(regex=r"Keypad opens in", timeout=6), "no pause")
        ctx.dev.wait_text(regex=r"Keypad opens in", timeout=14, gone=True)
        unlock_with_pin(ctx.dev)
        time.sleep(1.5)
        ctx.dev.sh("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS", check_rc=False)
        ctx.dev.force_stop(ctx.app_b)
        time.sleep(0.5)
    ctx.dev.home()
    open_app(ctx, ctx.app_b)
    check(ctx.dev.wait_text(regex=r"opened .* 2 times today", timeout=6) or ctx.dev.find(regex=r"2 times today"),
          "open count text not shown")


@scenario("D05", "Unlocking a Wellbeing app grants the window")
def d05(ctx):
    standard_setup(ctx, modes="both")
    ctx.dev.home()
    open_app(ctx, ctx.app_b)
    check(ctx.dev.wait_text(regex=r"Keypad opens in", timeout=6), "no pause")
    ctx.dev.wait_text(regex=r"Keypad opens in", timeout=14, gone=True)
    unlock_with_pin(ctx.dev)
    time.sleep(1.5)
    check(ctx.dev.foreground() == ctx.app_b, "app not in front after unlock")
    ctx.dev.home()
    time.sleep(1)
    open_app(ctx, ctx.app_b)
    time.sleep(2.5)
    check(wait_no_lock(ctx.dev) and ctx.dev.find(text="Take a breath") is None, "re-locked inside the window")


@scenario("D07", "Window ends while the app is open (waits 5 min)")
def d07(ctx):
    d05(ctx)  # unlocked with the 5 min window set in setup
    time.sleep(5 * 60 + 8)
    check(ctx.dev.wait_text(text="Take a breath", timeout=10) or lock_visible(ctx.dev),
          "lock did not return when the window ran out")


@scenario("D09", "Screen off keeps a Wellbeing window")
def d09(ctx):
    standard_setup(ctx, modes="both")
    ctx.dev.home()
    open_app(ctx, ctx.app_b)
    ctx.dev.wait_text(regex=r"Keypad opens in", timeout=6)
    ctx.dev.wait_text(regex=r"Keypad opens in", timeout=14, gone=True)
    unlock_with_pin(ctx.dev)
    time.sleep(1.5)
    ctx.dev.screen_off()
    time.sleep(1.5)
    ctx.dev.screen_on()
    ctx.dev.sh("wm dismiss-keyguard", check_rc=False)
    time.sleep(2.5)
    check(wait_no_lock(ctx.dev) and ctx.dev.find(text="Take a breath") is None,
          "Wellbeing window was cleared by screen off")


@scenario("G04", "Process death asks for the PIN again")
def g04(ctx):
    standard_setup(ctx)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "no lock")
    unlock_with_pin(ctx.dev)
    time.sleep(1.5)
    ctx.dev.home()
    ctx.dev.sh(f"am kill {PKG}", check_rc=False)
    ctx.dev.force_stop(PKG)
    set_guard(ctx.dev, False)
    set_guard(ctx.dev, True)
    time.sleep(2)
    open_app(ctx, ctx.app_a)
    check(wait_lock(ctx.dev), "grant survived process death")


@scenario("H10", "Accessibility service does not retrieve window content")
def h10(ctx):
    standard_setup(ctx)
    out = ctx.dev.sh("dumpsys accessibility", check_rc=False)
    check("LockGuardService" in out, "guard service not listed")
    block = re.search(r"LockGuardService.*?(?=\n\s*\n|\Z)", out, re.S)
    check(block is not None, "no service block found")
    check("flagRetrieveInteractiveWindows" not in block.group(0), "service retrieves interactive windows")


# ----------------------------------------------------------------------------- static checks

def read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def kotlin_files(sub):
    base = os.path.join(ROOT, "app/src/main/java/com/cocoexperiments/tumbler", sub)
    for dp, _, fs in os.walk(base):
        for f in fs:
            if f.endswith(".kt"):
                yield os.path.join(dp, f)


def static_checks():
    results = []

    def rec(sid, title, ok, detail=""):
        results.append((sid, title, ok, detail))

    ns = {"a": "http://schemas.android.com/apk/res/android"}
    A = "{http://schemas.android.com/apk/res/android}"
    man = ET.parse(os.path.join(ROOT, "app/src/main/AndroidManifest.xml")).getroot()
    app = man.find("application")

    exported = sorted(
        e.get(A + "name") for e in list(app) if e.get(A + "exported") == "true"
    )
    rec("H01", "Only MainActivity and BootReceiver are exported",
        exported == [".MainActivity", ".service.BootReceiver"], str(exported))
    rec("H02", "allowBackup=false", app.get(A + "allowBackup") == "false")

    perms = [p.get(A + "name") for p in man.findall("uses-permission")]
    qs = man.find("queries")
    rec("H03", "No QUERY_ALL_PACKAGES; launcher <queries> present",
        "android.permission.QUERY_ALL_PACKAGES" not in perms and qs is not None and qs.find("intent") is not None)
    banned = [p for p in perms if any(k in p for k in ("POST_NOTIFICATIONS", "FOREGROUND_SERVICE", "USE_FULL_SCREEN_INTENT"))]
    rec("H04", "No notification, foreground-service or full-screen-intent permissions", not banned, str(banned))

    cfg = ET.parse(os.path.join(ROOT, "app/src/main/res/xml/lock_guard_service.xml")).getroot()
    rec("H05", "Accessibility config minimal",
        cfg.get(A + "accessibilityEventTypes") == "typeWindowStateChanged"
        and cfg.get(A + "canRetrieveWindowContent") == "false")

    bad = []
    for f in kotlin_files("domain"):
        if re.search(r"^import (android|androidx)\.", open(f, encoding="utf-8").read(), re.M):
            bad.append(os.path.relpath(f, ROOT))
    rec("H06", "domain/ has no Android imports", not bad, str(bad))

    hits = []
    for sub in ("", "service", "ui", "util", "data"):
        for f in kotlin_files(sub):
            s = open(f, encoding="utf-8").read()
            for token in ("noHistory", "ACTION_CLOSE_SYSTEM_DIALOGS", "startLockTask"):
                if token in s:
                    hits.append(f"{os.path.relpath(f, ROOT)}:{token}")
    manifest_text = read("app/src/main/AndroidManifest.xml")
    if "noHistory" in manifest_text:
        hits.append("AndroidManifest.xml:noHistory")
    rec("H07", "No noHistory / CLOSE_SYSTEM_DIALOGS / Lock Task", not hits, str(sorted(set(hits))))

    callers = sorted({os.path.relpath(f, ROOT) for f in kotlin_files("") if "LockState.grant(" in open(f, encoding="utf-8").read()})
    rec("H08", "Grants are created only by LockViewModel.unlock",
        callers == ["app/src/main/java/com/cocoexperiments/tumbler/ui/lock/LockViewModel.kt"], str(callers))

    lock = next((e for e in app.findall("activity") if e.get(A + "name") == ".LockScreenActivity"), None)
    rec("H09", "Lock activity: excluded from Recents, own task, not exported",
        lock is not None and lock.get(A + "excludeFromRecents") == "true"
        and lock.get(A + "taskAffinity") == "" and lock.get(A + "exported") == "false")

    gradle = read("app/build.gradle.kts")
    rec("H12", "Version comes from -PappVersionName/-PappVersionCode",
        "appVersionName" in gradle and "appVersionCode" in gradle)

    site = read("docs/index.html")
    body = re.sub(r"<style.*?</style>|<script.*?</script>", "", site, flags=re.S)
    text = re.sub(r"<[^>]+>", " ", body).lower()
    stale = [p for p in ("power button", "system lock", "full-screen alerts", "lock screen is on", "enable lock screen")
             if p in text]
    rec("I03", "Landing page has no phone-lock claims", not stale, str(stale))

    ids = set(re.findall(r'id="([^"]+)"', site))
    broken = [h for h in re.findall(r'href="#([^"]+)"', site) if h not in ids]
    rec("I04", "Landing page anchors resolve", not broken, str(broken))

    width = 24
    failed = 0
    for sid, title, ok, detail in results:
        mark = "PASS" if ok else "FAIL"
        failed += 0 if ok else 1
        print(f"{mark}  {sid:<4} {title}" + (f"  [{detail}]" if not ok and detail else ""))
    return failed


# ----------------------------------------------------------------------------- main

def run(args):
    dev = Device(args.serial)
    devices = dev.adb("devices").strip().splitlines()[1:]
    check(any("\tdevice" in d for d in devices), "no booted device: start an emulator first")
    ctx = Ctx(dev, args.app_a, args.app_b)
    wanted = args.ids or [s for s in SCENARIOS if s not in SLOW or args.slow]
    unknown = [w for w in wanted if w not in SCENARIOS]
    check(not unknown, f"unknown scenario(s): {unknown}")
    failed = 0
    for sid in wanted:
        fn = SCENARIOS[sid]
        t0 = time.time()
        try:
            fn(ctx)
            print(f"PASS  {sid:<4} {fn.title}  ({time.time() - t0:.0f}s)")
        except Exception as e:  # noqa: BLE001 - report and continue with the next scenario
            failed += 1
            print(f"FAIL  {sid:<4} {fn.title}  ({time.time() - t0:.0f}s)\n      {type(e).__name__}: {e}")
            try:
                shot = os.path.join(tempfile.gettempdir(), f"tumbler-e2e-{sid}.xml")
                dev.adb("pull", "/sdcard/tumbler_e2e_dump.xml", shot, check_rc=False)
                print(f"      last UI dump: {shot}")
            except Exception:  # noqa: BLE001
                pass
    print(f"\n{len(wanted) - failed}/{len(wanted)} passed")
    return failed


SLOW = {"C11", "D07"}  # need real waiting: run with --slow or by ID


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("cmd", choices=["static", "run", "list"])
    p.add_argument("ids", nargs="*")
    p.add_argument("--serial")
    p.add_argument("--app-a", default="com.google.android.calendar", help="app locked in Privacy mode")
    p.add_argument("--app-b", default="com.google.android.youtube", help="app locked in Wellbeing mode / left unlocked")
    p.add_argument("--slow", action="store_true", help="include scenarios that wait minutes (C11, D07)")
    args = p.parse_args()
    if args.cmd == "list":
        for sid, fn in SCENARIOS.items():
            print(f"{sid:<4} {fn.title}{'  (slow)' if sid in SLOW else ''}")
        return 0
    if args.cmd == "static":
        return 1 if static_checks() else 0
    return 1 if run(args) else 0


if __name__ == "__main__":
    sys.exit(main())
