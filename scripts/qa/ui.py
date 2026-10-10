#!/usr/bin/env python3
"""Drives an emulator over adb for the QA test plan (docs/qa-test-plan.md).

Every command takes the emulator's serial first, so a run never touches an
emulator another session is using:

    scripts/qa/ui.py emulator-5560 screen
    scripts/qa/ui.py emulator-5560 tap "Merit badges"

Run it with no command for the list of commands. It needs only Python 3's
standard library, adb, and macOS's sips (for screenshots).
"""

import argparse
import os
import re
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SDK = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk"))
ADB = os.path.join(SDK, "platform-tools", "adb")
DUMP_PATH = "/sdcard/qa-ui.xml"
PACKAGE = "io.github.bryancassell.bluecard"
TALKBACK = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"


def adb(serial, *args, check=True, binary=False):
    result = subprocess.run([ADB, "-s", serial, *args], capture_output=True)
    if check and result.returncode != 0:
        sys.exit(f"adb {' '.join(args)} failed: {result.stderr.decode(errors='replace').strip()}")
    return result.stdout if binary else result.stdout.decode(errors="replace")


def shell(serial, command, check=True):
    return adb(serial, "shell", command, check=check)


# --- Reading the screen ---------------------------------------------------------------------

class Entry:
    """One thing on screen: a control with the text inside it, or a piece of text on its own."""

    def __init__(self, node, label, interactive):
        self.label = label
        self.interactive = interactive
        self.cls = node.get("class", "").rsplit(".", 1)[-1]
        self.package = node.get("package", "")
        left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "[0,0][0,0]")))
        self.bounds = (left, top, right, bottom)
        self.center = ((left + right) // 2, (top + bottom) // 2)
        flags = []
        if node.get("checkable") == "true":
            flags.append("checked" if node.get("checked") == "true" else "unchecked")
        if self.cls == "EditText":
            flags.append("field")
        elif node.get("clickable") == "true" or node.get("long-clickable") == "true":
            flags.append("tap")
        if node.get("scrollable") == "true":
            flags.append("scroll")
        if node.get("enabled") == "false":
            flags.append("disabled")
        if node.get("focused") == "true":
            flags.append("focused")
        if node.get("selected") == "true":
            flags.append("selected")
        if node.get("password") == "true":
            flags.append("password")
        self.flags = flags

    def describe(self, coords=False):
        flags = f" [{','.join(self.flags)}]" if self.flags else ""
        label = f'"{self.label}"' if self.label else "(no label)"
        return f"{label}{flags}" + (f" @{self.center[0]},{self.center[1]}" if coords else "")

    __str__ = describe


def own_label(node):
    text = node.get("text", "")
    desc = node.get("content-desc", "")
    if text and desc and desc != text:
        return f"{text} (desc: {desc})"
    return text or desc


def is_interactive(node):
    return any(node.get(a) == "true" for a in ("clickable", "long-clickable", "checkable", "scrollable")) \
        or node.get("class", "").endswith("EditText")


def descendant_labels(node):
    labels = []
    for child in node:
        if is_interactive(child):
            continue
        label = own_label(child)
        if label:
            labels.append(label)
        labels.extend(descendant_labels(child))
    return labels


def collect(node, entries):
    if node.tag == "node":
        if is_interactive(node):
            label = own_label(node)
            # A scrollable container's text is listed item by item below, not joined into one label.
            if not label and node.get("scrollable") != "true":
                label = " | ".join(descendant_labels(node))
            entries.append(Entry(node, label, True))
            if node.get("scrollable") != "true":
                for child in node:
                    collect_interactive_only(child, entries)
                return
        elif own_label(node):
            entries.append(Entry(node, own_label(node), False))
    for child in node:
        collect(child, entries)


def collect_interactive_only(node, entries):
    """Lists the controls inside a control, whose text the outer control's label already has."""
    if is_interactive(node):
        collect(node, entries)
    else:
        for child in node:
            collect_interactive_only(child, entries)


def read_screen(serial):
    """Returns the screen's entries. uiautomator can't dump while the screen is animating, so retry.

    The Android CLI's `android layout` reads the screen in about 1 second, against 2 for this,
    but it doesn't say whether a control is disabled, and the helper it leaves running on the
    emulator stops uiautomator working.
    """
    out = ""
    for _ in range(8):
        out = shell(serial, f"uiautomator dump {DUMP_PATH}", check=False)
        if "dumped to" in out:
            try:
                root = ET.fromstring(adb(serial, "exec-out", "cat", DUMP_PATH, binary=True))
                break
            except ET.ParseError:  # An empty or cut-off dump, such as while adb reconnects.
                out = "the dump couldn't be read"
        time.sleep(1)
    else:
        sys.exit(f"uiautomator dump failed: {out.strip()}")
    entries = []
    collect(root, entries)
    return entries


def focused_window(serial):
    # The full dump, since API 26's "dumpsys window displays" leaves the focus out.
    out = shell(serial, "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", check=False)
    match = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^}]+)\}", out)
    return match.group(1) if match else out.strip().splitlines()[0] if out.strip() else "unknown"


def find(entries, text, exact=False):
    """Entries whose label is TEXT, else starts with it, else contains it, ignoring case.

    Taking the closest matches first means "Merit badges" finds the button rather than the
    "Your merit badges" heading, without needing --exact.
    """
    wanted = text.casefold()
    exact_matches = [e for e in entries if e.label.casefold() == wanted]
    if exact:
        return exact_matches
    starting = [e for e in entries if e.label.casefold().startswith(wanted)]
    containing = [e for e in entries if wanted in e.label.casefold()]
    return exact_matches or starting or containing


def screen_size(serial):
    """The screen's size as it's turned now. wm size gives the portrait size even in landscape."""
    out = shell(serial, "dumpsys window displays | grep -oE 'cur=[0-9]+x[0-9]+'", check=False)
    sizes = re.findall(r"(\d+)x(\d+)", out) or re.findall(r"(\d+)x(\d+)", shell(serial, "wm size"))
    width, height = sizes[0] if out.strip() else sizes[-1]  # wm size lists an override last.
    return int(width), int(height)


# --- Commands --------------------------------------------------------------------------------

def launch(serial, package=PACKAGE, timeout=15):
    """Opens the app as a launcher does, so it comes back to its task rather than starting another."""
    # monkey turns auto-rotate back on, so put back the orientation a test set.
    rotation = {key: shell(serial, f"settings get system {key}").strip()
                for key in ("accelerometer_rotation", "user_rotation")}
    # The QA AVDs have no hardware keys, and monkey refuses to run while it may press them.
    out = shell(serial, f"monkey -p {package} --pct-syskeys 0 -c android.intent.category.LAUNCHER 1", check=False)
    deadline = time.time() + timeout
    while package not in focused_window(serial):
        if time.time() > deadline:
            sys.exit(f"{package} didn't come to the front. monkey said:\n{out.strip()}")
        time.sleep(0.5)
    # monkey resets rotation as it finishes, which can be after the app comes up.
    time.sleep(1)
    for key, value in rotation.items():
        if value != "null":
            shell(serial, f"settings put system {key} {value}")


def cmd_launch(args):
    launch(args.serial, args.package, args.timeout)
    print(f"opened {args.package}")


def cmd_clear(args):
    """Deletes the text in the focused field: from the end back, then anything after the cursor."""
    shell(args.serial, "input keyevent KEYCODE_MOVE_END " + "KEYCODE_DEL " * args.max
          + "KEYCODE_FORWARD_DEL " * args.max)
    print("cleared the focused field")


def cmd_screen(args):
    entries = read_screen(args.serial)
    print(f"window: {focused_window(args.serial)}")
    for entry in entries:
        print(entry.describe(args.coords))


def pick(text, entries, exact=False, nth=1):
    usable = [e for e in entries if e.interactive and "disabled" not in e.flags]
    # A control comes before text alone with the same words, such as a button over a heading.
    # A field's label is the text typed in it, so any other control that matches, such as the
    # "Chess | In progress" row, comes before a search field holding "chess". A field is still
    # found by its hint, which nothing else has.
    candidates = (find([e for e in usable if "field" not in e.flags], text, exact)
                  or find([e for e in usable if "field" in e.flags], text, exact)
                  or find(entries, text, exact))
    if not candidates:
        return None, candidates
    if nth > len(candidates):
        sys.exit(f'only {len(candidates)} match "{text}": ' + "; ".join(map(str, candidates)))
    return candidates[nth - 1], candidates


def tap(serial, text, exact=False, nth=1, long=False):
    """Taps the control or text matching TEXT, and returns a line saying what was tapped."""
    entry, candidates = pick(text, read_screen(serial), exact, nth)
    if entry is None:
        sys.exit(f'nothing on screen matches "{text}". Run "screen" to see what is there.')
    x, y = entry.center
    shell(serial, f"input swipe {x} {y} {x} {y} 800" if long else f"input tap {x} {y}")
    note = f" (match {nth} of {len(candidates)})" if len(candidates) > 1 else ""
    return f"tapped {entry}{note}"


def wait_for(serial, text, exact=False, gone=False, timeout=10):
    deadline = time.time() + timeout
    while True:
        present = bool(find(read_screen(serial), text, exact))
        if present != gone:
            return f'"{text}" {"gone" if gone else "shown"}'
        if time.time() > deadline:
            sys.exit(f'timed out after {timeout}s waiting for "{text}" to {"go" if gone else "show"}')
        time.sleep(0.5)


def cmd_tap(args):
    line = tap(args.serial, args.text, args.exact, args.nth, args.long)
    if args.then:
        line += "; " + wait_for(args.serial, args.then, timeout=args.timeout)
    print(line)


def cmd_wait(args):
    print(wait_for(args.serial, args.text, args.exact, args.gone, args.timeout))


EXPECT_FLAGS = {"disabled", "enabled", "checked", "unchecked", "focused", "selected", "tap", "field"}


def cmd_expect(args):
    """Checks that each TEXT is on screen, in one read, and prints only what isn't as expected.

    TEXT=flag (or TEXT=flag,flag) also checks the control's state, such as "Get started=disabled"
    or "Completed=checked". With --gone, checks that each TEXT is not on screen.
    """
    entries = read_screen(args.serial)
    problems = []
    for item in args.items:
        text, _, flag_text = item.rpartition("=")
        flags = set(flag_text.split(","))
        if not text or not flags <= EXPECT_FLAGS:
            text, flags = item, set()
        matches = find(entries, text, args.exact)
        if args.gone:
            if matches:
                problems.append(f"still shown: {matches[0]}")
            continue
        if not matches:
            problems.append(f'missing: "{text}"')
            continue
        def has(entry, flag):
            return "disabled" not in entry.flags if flag == "enabled" else flag in entry.flags
        if flags and not any(all(has(e, f) for f in flags) for e in matches):
            problems.append(f"not {','.join(sorted(flags))}: " + "; ".join(map(str, matches[:3])))
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    print(f"ok: all {len(args.items)} {'gone' if args.gone else 'as expected'}")


def swipe(serial, direction, bounds=None):
    width, height = screen_size(serial)
    left, top, right, bottom = bounds or (0, int(height * 0.15), width, int(height * 0.85))
    x = (left + right) // 2
    y = (top + bottom) // 2
    dy = int((bottom - top) * 0.3)
    dx = int((right - left) * 0.35)
    ends = {
        "up": (x, y + dy, x, y - dy),       # Moves the content up, to see what's below.
        "down": (x, y - dy, x, y + dy),
        "left": (x + dx, y, x - dx, y),
        "right": (x - dx, y, x + dx, y),
    }[direction]
    shell(serial, "input swipe {} {} {} {} 400".format(*ends))


def cmd_swipe(args):
    swipe(args.serial, args.direction)
    print(f"swiped {args.direction}")


def scroll_to(serial, text, exact=False, up=False, max_swipes=15):
    direction = "down" if up else "up"
    previous = None
    for _ in range(max_swipes + 1):
        entries = read_screen(serial)
        matches = find(entries, text, exact)
        if matches:
            return f"found {matches[0]}"
        labels = [e.label for e in entries]
        if labels == previous:
            sys.exit(f'reached the end without finding "{text}"')
        previous = labels
        scrollables = [e for e in entries if "scroll" in e.flags]
        # The largest scrollable area is the page; smaller ones are rows such as the rank trail.
        area = max(scrollables, key=lambda e: (e.bounds[2] - e.bounds[0]) * (e.bounds[3] - e.bounds[1])).bounds \
            if scrollables else None
        swipe(serial, direction, area)
        time.sleep(0.6)
    sys.exit(f'"{text}" not found after {max_swipes} swipes')


def cmd_scroll_to(args):
    print(scroll_to(args.serial, args.text, args.exact, args.up, args.max))


def type_text(serial, text):
    if not text.isascii():
        sys.exit("adb's input text takes only ASCII. Paste other text through the clipboard instead.")
    # input text reads %s as a space; the rest is quoted for the device's shell.
    escaped = text.replace("%", "\\%").replace(" ", "%s").replace("'", "'\\''")
    shell(serial, f"input text '{escaped}'")


def cmd_type(args):
    type_text(args.serial, args.text)
    print(f"typed {args.text!r}")


def cmd_key(args):
    codes = [k if k.isdigit() else "KEYCODE_" + k.upper().removeprefix("KEYCODE_") for k in args.keys]
    shell(args.serial, "input keyevent " + " ".join(codes * args.repeat))
    print("pressed " + " ".join(args.keys) + (f" x{args.repeat}" if args.repeat > 1 else ""))


def screen_png(serial):
    png = adb(serial, "exec-out", "screencap", "-p", binary=True)
    if png.startswith(b"\x89PNG"):
        return png
    # A foldable has a display for each screen, and screencap then asks which one: take the one
    # that's on.
    displays = shell(serial, "dumpsys display | grep 'DisplayDeviceInfo{'", check=False)
    on = re.findall(r'uniqueId="local:(\d+)".*?, state ON,', displays)
    if not on:
        sys.exit("screencap didn't return a PNG, and no display is on: " + png[:200].decode(errors="replace"))
    return adb(serial, "exec-out", "screencap", "-d", on[0], "-p", binary=True)


def cmd_shot(args):
    png = screen_png(args.serial)
    with open(args.out, "wb") as f:
        f.write(png)
    if args.crop:
        left, top, right, bottom = map(int, args.crop.split(","))
        width, height = int.from_bytes(png[16:20], "big"), int.from_bytes(png[20:24], "big")
        # sips ignores an offset of 0,0 and crops around the middle instead, and leaves the image
        # whole when the box reaches its far edge, so the box stays a pixel inside the image.
        left, top = max(left, 1), max(top, 1)
        right, bottom = min(right, width - 1), min(bottom, height - 1)
        subprocess.run(["sips", "-c", str(bottom - top), str(right - left), "--cropOffset", str(top), str(left),
                        args.out, "--out", args.out], capture_output=True, check=True)
    if args.width:
        subprocess.run(["sips", "--resampleWidth", str(args.width), args.out, "--out", args.out],
                       capture_output=True, check=True)
    print(f"saved {args.out}")


def cmd_starts(args):
    """Lists the activities started recently, to check what a link, Share or a phone number opened."""
    out = adb(args.serial, "logcat", "-d", "-v", "time", "ActivityTaskManager:I", "ActivityManager:I", "*:S")
    lines = [line for line in out.splitlines() if "START u0" in line]
    print("\n".join(lines[-args.last:]) or "no activity starts in the log")
    # Newer Android versions shorten a link in the log to its host. The intents that apps are
    # holding, such as the link Chrome keeps while it shows its first-run screens, have it whole.
    held = shell(args.serial, "dumpsys activity intents; dumpsys activity activities", check=False)
    links = sorted(set(re.findall(r"dat=((?:https?|tel|mailto):\S+)", held)))
    links = [link for link in links if not link.endswith("...")]
    if links:
        print("full links in held intents: " + " ".join(links))


# --- Starting states ------------------------------------------------------------------------

SEED_FILE = "bluecard-qa-seed.json"


def cmd_seed(args):
    """Starts from the seed: clears the app, finishes Onboarding, and imports the seed with Replace all."""
    s = args.serial
    shell(s, f"pm clear {PACKAGE}")
    launch(s)
    wait_for(s, "Welcome to BlueCard", timeout=20)
    # Each field is filled with the keyboard closed again after it: the first time the keyboard
    # opens on API 26, a Gboard tip covers the next field, and the keyboard can cover Get started.
    for field, text in (("Name", "QA Scout"), ("Unit number", "Troop 1")):
        tap(s, field)
        type_text(s, text)
        shell(s, "input keyevent KEYCODE_BACK")
    fields = [e.label for e in read_screen(s) if "field" in e.flags]
    if fields != ["QA Scout", "Troop 1"]:
        sys.exit(f"Onboarding's fields didn't take the text: {fields}")
    tap(s, "Get started")
    wait_for(s, "Your rank", timeout=15)
    scroll_to(s, "Manage data", exact=True)
    tap(s, "Manage data", exact=True)
    wait_for(s, "Import data")
    # At large text sizes the button is below the fold; "Import data", its heading, isn't it.
    scroll_to(s, "Import", exact=True)
    tap(s, "Import", exact=True)
    deadline = time.time() + 15
    while "documentsui" not in focused_window(s):
        if time.time() > deadline:
            sys.exit("the file picker didn't open")
        time.sleep(0.5)
    # A fresh emulator's file picker opens at Recent files, which is empty.
    if not find(read_screen(s), SEED_FILE):
        tap(s, "Show roots")
        tap(s, "Downloads", exact=True)
        wait_for(s, SEED_FILE)
    # In landscape the picker shows a grid, where only the file's preview button is labeled.
    if not [e for e in read_screen(s) if e.label.startswith(SEED_FILE)]:
        tap(s, "List view", exact=True)
        wait_for(s, SEED_FILE)
    tap(s, SEED_FILE)
    wait_for(s, "Replace all")
    tap(s, "Replace all")
    wait_for(s, "Data imported.")
    shell(s, "input keyevent KEYCODE_BACK")
    # Home comes back at the top, or scrolled to where Manage data was, so either shows it's Home.
    deadline = time.time() + 10
    while not any(find(read_screen(s), text, exact=True) for text in ("Manage data", "Your merit badges")):
        if time.time() > deadline:
            sys.exit("Home didn't come back after the import")
        time.sleep(0.5)
    print("seeded: Home shows the QA seed (see the plan's \"The seed\")")


def cmd_reset(args):
    """Puts the emulator back as it started, so the next tester can use it without a restart."""
    s = args.serial
    for command in [
        "settings delete secure enabled_accessibility_services",
        "settings put secure accessibility_enabled 0",
        "settings put system font_scale 1.0",
        "wm density reset",
        "cmd uimode night no",
        "settings put system user_rotation 0",
        "settings put system accelerometer_rotation 1",
        "cmd locale set-device-locale en-US",    # API 34 and higher.
        "cmd device_state state reset",          # Foldables.
        f"pm clear {PACKAGE}",
        "am force-stop com.android.chrome",
        "am force-stop com.google.android.gm",
        # Files a tester saved, such as exports and reports, but not the seed.
        f"cd /sdcard/Download && ls | grep -vx '{SEED_FILE}' | while read f; do rm -rf \"$f\"; done",
        "logcat -c",
        "logcat -b crash -c",
        "input keyevent KEYCODE_HOME",
    ]:
        shell(s, command, check=False)
    print("reset: default settings, BlueCard cleared, Downloads holds only the seed, logs cleared")


# --- TalkBack --------------------------------------------------------------------------------
# input tap and input swipe bypass TalkBack. Touches sent through the emulator console reach it
# as a finger would, but only when they arrive quickly, so each gesture is sent over one socket.

def console(serial, commands, pause=0.016):
    port = int(serial.rsplit("-", 1)[1])
    with open(os.path.expanduser("~/.emulator_console_auth_token")) as f:
        token = f.read().strip()
    with socket.create_connection(("localhost", port), timeout=5) as sock:
        sock.recv(4096)
        sock.sendall(f"auth {token}\n".encode())
        time.sleep(0.1)
        sock.recv(4096)
        for command in commands:
            if isinstance(command, float):
                time.sleep(command)
                continue
            sock.sendall((command + "\n").encode())
            time.sleep(pause)
        sock.sendall(b"quit\n")


def touch_path(points):
    commands = [f"event mouse {x} {y} 0 1" for x, y in points]
    x, y = points[-1]
    commands.append(f"event mouse {x} {y} 0 0")
    return commands


def cmd_talkback(args):
    if args.state == "on":
        shell(args.serial, f"settings put secure enabled_accessibility_services {TALKBACK}")
        shell(args.serial, "settings put secure accessibility_enabled 1")
    else:
        shell(args.serial, "settings delete secure enabled_accessibility_services")
        shell(args.serial, "settings put secure accessibility_enabled 0")
    print(f"TalkBack {args.state}")


def cmd_tb_gesture(args):
    width, height = screen_size(args.serial)
    x, y = width // 2, height // 2
    if args.gesture == "double-tap":
        commands = touch_path([(x, y)]) + [0.08] + touch_path([(x, y)])
    elif args.gesture == "tap":
        commands = touch_path([(args.x, args.y)])
    else:
        dx, dy = {"right": (1, 0), "left": (-1, 0), "down": (0, 1), "up": (0, -1)}[args.gesture]
        steps = 8
        span = width * 0.4 if dx else height * 0.25
        start = (x - dx * span / 2, y - dy * span / 2)
        commands = touch_path([(int(start[0] + dx * span * i / steps), int(start[1] + dy * span * i / steps))
                               for i in range(steps + 1)])
    console(args.serial, commands)
    print(f"TalkBack gesture {args.gesture}")


def cmd_tb_speech(args):
    """Prints what TalkBack spoke since the log was last cleared. Needs TalkBack's verbose log."""
    if args.clear:
        adb(args.serial, "logcat", "-c")
        print("log cleared")
        return
    out = adb(args.serial, "logcat", "-d", "-v", "time")
    spoken = re.findall(r"^(\S+ \S+) .*SpeechControllerImpl.*Speaking fragment text=\"(.*?)\", utteranceId",
                        out, re.MULTILINE)
    print("\n".join(f"{time_} {text}" for time_, text in spoken)
          or "nothing spoken (is TalkBack's log output level Verbose?)")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("serial", help="the emulator's serial, such as emulator-5560")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("launch", help="open the app as a launcher does, and wait for it")
    p.add_argument("--package", default=PACKAGE)
    p.add_argument("--timeout", type=float, default=15)

    p = sub.add_parser("screen", help="list what's on screen: text and controls, with their state")
    p.add_argument("--coords", action="store_true", help="also give where each is, for adb input commands")

    def matching(p):
        p.add_argument("text", help="text or screen reader label to match, ignoring case")
        p.add_argument("--exact", action="store_true", help="match the whole label")

    p = sub.add_parser("tap", help="tap the control or text matching TEXT")
    matching(p)
    p.add_argument("--nth", type=int, default=1, help="tap the Nth match")
    p.add_argument("--long", action="store_true", help="long-press instead")
    p.add_argument("--then", help="then wait until this text shows, as on the next page")
    p.add_argument("--timeout", type=float, default=10, help="how long --then waits")

    p = sub.add_parser("expect", help="check that each TEXT (or TEXT=disabled, =checked…) is on screen; "
                                      "prints only what isn't")
    p.add_argument("items", nargs="+", metavar="TEXT")
    p.add_argument("--exact", action="store_true", help="match whole labels")
    p.add_argument("--gone", action="store_true", help="check that each TEXT is not on screen")

    p = sub.add_parser("wait", help="wait until TEXT shows, or with --gone, until it's gone")
    matching(p)
    p.add_argument("--gone", action="store_true")
    p.add_argument("--timeout", type=float, default=10)

    p = sub.add_parser("scroll-to", help="scroll the page down until TEXT shows")
    matching(p)
    p.add_argument("--up", action="store_true", help="scroll back up instead")
    p.add_argument("--max", type=int, default=15)

    p = sub.add_parser("swipe", help="swipe the page: up shows what's below")
    p.add_argument("direction", choices=["up", "down", "left", "right"])

    p = sub.add_parser("type", help="type ASCII text into the focused field")
    p.add_argument("text")

    p = sub.add_parser("clear", help="delete all the text in the focused field")
    p.add_argument("--max", type=int, default=120, help="characters to delete each way")

    p = sub.add_parser("key", help="press keys, such as BACK, HOME, ENTER, TAB, DEL, MOVE_END")
    p.add_argument("keys", nargs="+")
    p.add_argument("--repeat", type=int, default=1)

    p = sub.add_parser("shot", help="save a screenshot, optionally cropped and scaled down")
    p.add_argument("out")
    p.add_argument("--crop", help="left,top,right,bottom in the screen's pixels, as screen --coords gives them")
    p.add_argument("--width", type=int, default=360, help="scale to this width (0 keeps full size)")

    p = sub.add_parser("starts", help="list recent activity starts, such as a link opening the browser")
    p.add_argument("--last", type=int, default=5)

    sub.add_parser("seed", help="start from the seed: clear the app, finish Onboarding, import the seed")
    sub.add_parser("reset", help="put the emulator back to default settings with BlueCard cleared")

    p = sub.add_parser("talkback", help="turn TalkBack on or off")
    p.add_argument("state", choices=["on", "off"])

    p = sub.add_parser("tb", help="send a TalkBack gesture: swipe right/left/up/down, double-tap, tap X Y")
    p.add_argument("gesture", choices=["right", "left", "up", "down", "double-tap", "tap"])
    p.add_argument("x", type=int, nargs="?")
    p.add_argument("y", type=int, nargs="?")

    p = sub.add_parser("tb-speech", help="print what TalkBack spoke since --clear")
    p.add_argument("--clear", action="store_true", help="clear the log, to start listening")

    args = parser.parse_args()
    if args.command == "tb" and args.gesture == "tap" and args.y is None:
        parser.error("tb tap needs X and Y")
    {
        "launch": cmd_launch, "clear": cmd_clear, "screen": cmd_screen, "tap": cmd_tap, "expect": cmd_expect,
        "wait": cmd_wait, "scroll-to": cmd_scroll_to, "seed": cmd_seed, "reset": cmd_reset,
        "swipe": cmd_swipe, "type": cmd_type, "key": cmd_key, "shot": cmd_shot, "starts": cmd_starts,
        "talkback": cmd_talkback, "tb": cmd_tb_gesture, "tb-speech": cmd_tb_speech,
    }[args.command](args)


if __name__ == "__main__":
    main()
