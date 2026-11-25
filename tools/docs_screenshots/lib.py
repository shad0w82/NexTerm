"""Helpers to drive NexTerm on an Android emulator and to take the screenshots of the documentation.

The screens are read with uiautomator (text and content descriptions) and operated with `adb input`, so the scripts
in chapters/ say "tap Set up..." and not "tap at 540, 1200". Only emulators are ever touched: a serial that does not
start with "emulator-" is refused.
"""
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

PACKAGE = "dev.nexterm"
ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"

# Width of the saved images: a phone is shown about 300 px wide and a tablet about 700 px wide, doubled for sharp screens.
WIDTHS = {"phone": 600, "tablet": 1400}


def find_adb():
    candidates = [os.environ.get("ADB")]
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(var):
            candidates.append(os.path.join(os.environ[var], "platform-tools", "adb"))
    candidates.append(shutil.which("adb"))
    for c in candidates:
        if c and os.path.exists(c):
            return c
    sys.exit("adb not found: put it in PATH or set ADB")


ADB = find_adb()


@dataclass
class Node:
    text: str
    desc: str
    bounds: tuple  # left, top, right, bottom
    clickable: bool
    cls: str

    @property
    def center(self):
        l, t, r, b = self.bounds
        return (l + r) // 2, (t + b) // 2

    @property
    def label(self):
        return self.text or self.desc


class Device:
    def __init__(self, serial):
        if not serial.startswith("emulator-"):
            sys.exit(f"refusing to drive {serial!r}: only emulators are allowed")
        self.serial = serial
        size = self.shell("wm size").split(":")[-1].strip()
        self.width, self.height = (int(v) for v in size.split("x"))
        self.density = int(self.shell("wm density").split(":")[-1].strip())
        self.dp_width = self.width * 160 // self.density
        self.kind = "tablet" if self.dp_width >= 600 else "phone"

    # ---- adb ----
    def run(self, *args, check=True, binary=False):
        out = subprocess.run([ADB, "-s", self.serial, *args], capture_output=True, check=check)
        return out.stdout if binary else out.stdout.decode("utf-8", "replace")

    def shell(self, cmd, check=True):
        return self.run("shell", cmd, check=check).replace("\r", "").strip()

    # ---- the state of the emulator ----
    def prepare(self, clear_data=True):
        """A clean, predictable emulator: no animations, dark mode, a tidy status bar, and the app with no data."""
        for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.shell(f"settings put global {key} 0")
        self.shell("cmd uimode night yes")
        self.shell("settings put system font_scale 1.0")
        self.shell("settings put secure show_ime_with_hard_keyboard 0")
        # Gesture navigation: a thin bar at the bottom instead of three buttons. Changing it restarts the system UI,
        # so it comes before the status bar is tidied.
        self.shell("cmd overlay enable com.android.internal.systemui.navbar.gestural", check=False)
        time.sleep(3)
        if clear_data:
            self.shell(f"pm clear {PACKAGE}")
        self.shell(f"pm grant {PACKAGE} android.permission.POST_NOTIFICATIONS", check=False)
        self.shell("settings put global sysui_demo_allowed 1")
        for extra in (
            "-e command enter",
            "-e command clock -e hhmm 1000",
            "-e command battery -e level 100 -e plugged false",
            "-e command network -e wifi show -e level 4 -e fully true",
            "-e command network -e mobile hide",
            "-e command notifications -e visible false",
        ):
            self.shell(f"am broadcast -a com.android.systemui.demo {extra}")

    def restore(self):
        self.shell("am broadcast -a com.android.systemui.demo -e command exit", check=False)
        self.shell("cmd overlay disable com.android.internal.systemui.navbar.gestural", check=False)
        for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.shell(f"settings put global {key} 1")

    def start_app(self):
        self.shell(f"am force-stop {PACKAGE}")
        self.shell(f"monkey -p {PACKAGE} -c android.intent.category.LAUNCHER 1")
        self.wait_for(text="Home", timeout=20)

    # ---- reading the screen ----
    def dump(self):
        self.shell("uiautomator dump /sdcard/nx_ui.xml", check=False)
        xml = self.run("exec-out", "cat", "/sdcard/nx_ui.xml", check=False)
        nodes = []
        try:
            root = ET.fromstring(xml)
        except ET.ParseError:
            return nodes
        for el in root.iter("node"):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", el.get("bounds", ""))
            if not m:
                continue
            nodes.append(Node(
                text=el.get("text", ""), desc=el.get("content-desc", ""),
                bounds=tuple(int(v) for v in m.groups()), clickable=el.get("clickable") == "true",
                cls=el.get("class", ""),
            ))
        return nodes

    @staticmethod
    def _match(n, text, desc, contains):
        """Compose puts some labels in `text` and some in the content description: either one matches."""
        for want in (text, desc):
            if want is None:
                continue
            for have in (n.text, n.desc):
                if (want in have) if contains else (want == have):
                    return True
        return False

    def find(self, text=None, desc=None, contains=False, nodes=None, index=0):
        found = [n for n in (nodes if nodes is not None else self.dump()) if self._match(n, text, desc, contains)]
        return found[index] if len(found) > index else None

    def wait_for(self, text=None, desc=None, contains=False, timeout=10, index=0):
        end = time.time() + timeout
        while True:
            n = self.find(text, desc, contains, index=index)
            if n:
                return n
            if time.time() > end:
                raise TimeoutError(f"{self.serial}: no {text or desc!r} on the screen")
            time.sleep(0.4)

    def gone(self, text=None, desc=None, contains=False, timeout=10):
        end = time.time() + timeout
        while self.find(text, desc, contains):
            if time.time() > end:
                raise TimeoutError(f"{self.serial}: {text or desc!r} is still on the screen")
            time.sleep(0.4)

    def texts(self):
        return [n.label for n in self.dump() if n.label]

    # ---- input ----
    def tap(self, x, y):
        self.shell(f"input tap {x} {y}")
        time.sleep(0.5)

    def tap_node(self, node):
        self.tap(*node.center)

    def tap_text(self, text=None, desc=None, contains=False, timeout=10, index=0):
        self.tap_node(self.wait_for(text, desc, contains, timeout, index))

    def swipe(self, x1, y1, x2, y2, ms=300):
        self.shell(f"input swipe {x1} {y1} {x2} {y2} {ms}")
        time.sleep(0.5)

    def key(self, name):
        self.shell(f"input keyevent {name}")
        time.sleep(0.4)

    def type(self, text, enter=False):
        quoted = "'" + text.replace(" ", "%s").replace("'", "'\\''") + "'"
        self.shell("input text " + quoted)
        if enter:
            self.key("KEYCODE_ENTER")

    def back(self):
        self.key("KEYCODE_BACK")

    def keyboard_shown(self):
        return "mInputShown=true" in self.shell("dumpsys input_method | grep mInputShown", check=False)

    def hide_keyboard(self):
        if self.keyboard_shown():
            self.back()

    # ---- pictures ----
    def screenshot(self, out_path):
        """Saves the screen as a PNG of the width meant for the documentation, optimised when pngquant is there."""
        out_path = Path(out_path)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        raw = out_path.with_suffix(".raw.png")
        raw.write_bytes(self.run("exec-out", "screencap", "-p", binary=True))
        subprocess.run(["sips", "--resampleWidth", str(WIDTHS[self.kind]), str(raw), "--out", str(out_path)],
                       check=True, capture_output=True)
        raw.unlink()
        if shutil.which("pngquant"):
            subprocess.run(["pngquant", "--quality", "70-95", "--force", "--output", str(out_path), str(out_path)],
                           check=False, capture_output=True)
        return out_path


def emulators():
    out = subprocess.run([ADB, "devices"], capture_output=True, check=True).stdout.decode()
    return [Device(line.split()[0]) for line in out.splitlines()[1:] if line.startswith("emulator-")]


class Shooter:
    """Takes the shots of one chapter on one device and names the files `<chapter>-<n>-<name>[-tablet].png`."""

    def __init__(self, device, section, chapter, only=""):
        self.device, self.section, self.chapter, self.only = device, section, chapter, only
        self.count = 0
        self.dir = DOCS / section / "img"

    def clear_old(self):
        for f in self.dir.glob(f"{self.chapter}-*.png"):
            if f.name.endswith("-tablet.png") == (self.device.kind == "tablet"):
                if not self.only:
                    f.unlink()

    def shot(self, name, n=None):
        """The number in the file name is the order of the shots in the chapter, or `n` when the order of taking
        them is not the order of showing them."""
        self.count += 1
        if self.only and self.only not in name:
            return None
        suffix = "-tablet" if self.device.kind == "tablet" else ""
        path = self.dir / f"{self.chapter}-{n or self.count}-{name}{suffix}.png"
        self.device.screenshot(path)
        print("  shot", path.relative_to(ROOT))
        return path
