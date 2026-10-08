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

    def __str__(self):
        flags = f" [{','.join(self.flags)}]" if self.flags else ""
        label = f'"{self.label}"' if self.label else "(no label)"
        return f"{label}{flags} @{self.center[0]},{self.center[1]}"


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
    """Returns the screen's entries. uiautomator can't dump while the screen is animating, so retry."""
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

def cmd_launch(args):
    """Opens the app as a launcher does, so it comes back to its task rather than starting another."""
    # monkey turns auto-rotate back on, so put back the orientation a test set.
    rotation = {key: shell(args.serial, f"settings get system {key}").strip()
                for key in ("accelerometer_rotation", "user_rotation")}
    # The QA AVDs have no hardware keys, and monkey refuses to run while it may press them.
    out = shell(args.serial, f"monkey -p {args.package} --pct-syskeys 0 -c android.intent.category.LAUNCHER 1",
                check=False)
    deadline = time.time() + args.timeout
    while args.package not in focused_window(args.serial):
        if time.time() > deadline:
            sys.exit(f"{args.package} didn't come to the front. monkey said:\n{out.strip()}")
        time.sleep(0.5)
    # monkey resets rotation as it finishes, which can be after the app comes up.
    time.sleep(1)
    for key, value in rotation.items():
        if value != "null":
            shell(args.serial, f"settings put system {key} {value}")
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
        print(entry)


def pick(args, entries):
    usable = [e for e in entries if e.interactive and "disabled" not in e.flags]
    # A control comes before text alone with the same words, such as a button over a heading.
    # A field's label is the text typed in it, so any other control that matches, such as the
    # "Chess | In progress" row, comes before a search field holding "chess". A field is still
    # found by its hint, which nothing else has.
    candidates = (find([e for e in usable if "field" not in e.flags], args.text, args.exact)
                  or find([e for e in usable if "field" in e.flags], args.text, args.exact)
                  or find(entries, args.text, args.exact))
    if not candidates:
        return None, candidates
    index = args.nth - 1
    if index >= len(candidates):
        sys.exit(f'only {len(candidates)} match "{args.text}": ' + "; ".join(map(str, candidates)))
    return candidates[index], candidates


def cmd_tap(args):
    entry, candidates = pick(args, read_screen(args.serial))
    if entry is None:
        sys.exit(f'nothing on screen matches "{args.text}". Run "screen" to see what is there.')
    x, y = entry.center
    if args.long:
        shell(args.serial, f"input swipe {x} {y} {x} {y} 800")
    else:
        shell(args.serial, f"input tap {x} {y}")
    note = f" (match {args.nth} of {len(candidates)})" if len(candidates) > 1 else ""
    print(f"tapped {entry}{note}")


def cmd_wait(args):
    deadline = time.time() + args.timeout
    while True:
        present = bool(find(read_screen(args.serial), args.text, args.exact))
        if present != args.gone:
            print(f'"{args.text}" {"gone" if args.gone else "shown"}')
            return
        if time.time() > deadline:
            sys.exit(f'timed out after {args.timeout}s waiting for "{args.text}" to {"go" if args.gone else "show"}')
        time.sleep(0.5)


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


def cmd_scroll_to(args):
    direction = "down" if args.up else "up"
    previous = None
    for _ in range(args.max + 1):
        entries = read_screen(args.serial)
        matches = find(entries, args.text, args.exact)
        if matches:
            print(f"found {matches[0]}")
            return
        labels = [e.label for e in entries]
        if labels == previous:
            sys.exit(f'reached the end without finding "{args.text}"')
        previous = labels
        scrollables = [e for e in entries if "scroll" in e.flags]
        # The largest scrollable area is the page; smaller ones are rows such as the rank trail.
        area = max(scrollables, key=lambda e: (e.bounds[2] - e.bounds[0]) * (e.bounds[3] - e.bounds[1])).bounds \
            if scrollables else None
        swipe(args.serial, direction, area)
        time.sleep(0.6)
    sys.exit(f'"{args.text}" not found after {args.max} swipes')


def cmd_type(args):
    if not args.text.isascii():
        sys.exit("adb's input text takes only ASCII. Paste other text through the clipboard instead.")
    # input text reads %s as a space; the rest is quoted for the device's shell.
    escaped = args.text.replace("%", "\\%").replace(" ", "%s").replace("'", "'\\''")
    shell(args.serial, f"input text '{escaped}'")
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

    sub.add_parser("screen", help="list what's on screen: text, controls and where to tap them")

    def matching(p):
        p.add_argument("text", help="text or screen reader label to match, ignoring case")
        p.add_argument("--exact", action="store_true", help="match the whole label")

    p = sub.add_parser("tap", help="tap the control or text matching TEXT")
    matching(p)
    p.add_argument("--nth", type=int, default=1, help="tap the Nth match")
    p.add_argument("--long", action="store_true", help="long-press instead")

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
    p.add_argument("--crop", help="left,top,right,bottom in screen pixels")
    p.add_argument("--width", type=int, default=540, help="scale to this width (0 keeps full size)")

    p = sub.add_parser("starts", help="list recent activity starts, such as a link opening the browser")
    p.add_argument("--last", type=int, default=5)

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
        "launch": cmd_launch, "clear": cmd_clear, "screen": cmd_screen, "tap": cmd_tap, "wait": cmd_wait, "scroll-to": cmd_scroll_to,
        "swipe": cmd_swipe, "type": cmd_type, "key": cmd_key, "shot": cmd_shot, "starts": cmd_starts,
        "talkback": cmd_talkback, "tb": cmd_tb_gesture, "tb-speech": cmd_tb_speech,
    }[args.command](args)


if __name__ == "__main__":
    main()
