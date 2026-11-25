"""Steps that the chapters share: a fresh app, the demo adapter connected, the tabs, the console."""
import time

from lib import PACKAGE

PROFILE = "Demo board"


def fresh(d):
    """The app as a new user would find it: no profiles, no commands, default settings."""
    d.shell(f"am force-stop {PACKAGE}")
    d.shell(f"pm clear {PACKAGE}")
    d.shell(f"pm grant {PACKAGE} android.permission.POST_NOTIFICATIONS", check=False)
    d.start_app()


def go(d, tab):
    """Taps a tab of the bottom bar (phone) or of the rail (tablet): the word is also on the header of its screen."""
    nodes = [n for n in d.dump() if n.label == tab]
    if not nodes:
        raise TimeoutError(f"no tab {tab!r}")
    nav = min(nodes, key=lambda n: n.bounds[0]) if d.kind == "tablet" else max(nodes, key=lambda n: n.bounds[1])
    d.tap_node(nav)
    time.sleep(0.8)


def fill(d, label, value):
    """Types into the text field whose placeholder or label is `label`."""
    d.tap_text(text=label)
    time.sleep(0.8)
    d.type(value)
    time.sleep(0.4)
    d.hide_keyboard()
    time.sleep(0.4)


def connect_demo(d, name=PROFILE):
    """Home -> Set up… -> a profile called `name` -> Connect. The session ends up open and the screen on Home."""
    go(d, "Home")
    d.tap_text(desc="Set up…")
    d.wait_for(text="Set up connection")
    fill(d, "Profile name", name)
    d.tap_text(text="Connect")
    d.wait_for(text="Active session")
    time.sleep(1.5)


def console_tap(d):
    d.tap(d.width // 2, int(d.height * 0.45))
    time.sleep(0.8)


def console_type(d, *lines, pause=0.8):
    """Types commands into the console and puts the keyboard away."""
    console_tap(d)
    for line in lines:
        d.type(line, enter=True)
        time.sleep(pause)
    d.hide_keyboard()
    time.sleep(0.8)


def ctrl_c(d):
    d.shell("input keycombination KEYCODE_CTRL_LEFT KEYCODE_C")
    time.sleep(0.8)


def pick_view(d, view):
    """Console, Trace or Hex, from the view button of the Terminal header."""
    current = next(v for v in ("Console", "Trace", "Hex") if d.find(text=v))
    d.tap_text(text=current)
    time.sleep(0.8)
    nodes = [n for n in d.dump() if n.label == view]
    d.tap_node(max(nodes, key=lambda n: n.bounds[1]) if len(nodes) > 1 else nodes[0])
    time.sleep(1.0)


def tap_row(d, label):
    """Taps a row of a list whose label is also the name of a tab (Terminal, Settings...): the one that is not in the bar."""
    nodes = [n for n in d.dump() if n.label == label]
    if not nodes:
        raise TimeoutError(f"no {label!r} on the screen")
    row = max(nodes, key=lambda n: n.bounds[0]) if d.kind == "tablet" else min(nodes, key=lambda n: n.bounds[1])
    d.tap_node(row)
    time.sleep(0.9)


def new_command(d, name, command, hex_=False):
    """Commands popup (or dock) -> + New command -> the form -> Save."""
    d.tap_text(text="+ New command")
    d.wait_for(text="New command")
    d.tap_text(text="Name")
    time.sleep(0.6)
    d.type(name)
    d.tap_text(text="Command, e.g. AT+STATUS?")
    time.sleep(0.6)
    d.type(command)
    d.hide_keyboard()
    if hex_:
        d.tap_text(text="Text")
        time.sleep(0.6)
        d.tap_text(text="Hex bytes")
    return None


def save_command(d):
    d.tap_text(text="Save")
    time.sleep(1.0)


def disconnect_and_reconnect(d):
    """A new session, so that Trace and Hex start from an empty log."""
    go(d, "Home")
    d.tap_text(text="Disconnect")
    time.sleep(1.5)
    d.tap_text(text="Reconnect")
    d.wait_for(text="Active session", timeout=15)
    time.sleep(1.5)


def open_commands(d):
    """The list of saved commands: a popup on a phone (opened by the bolt button), a dock that is already open on a tablet."""
    if not d.find(text="+ New command"):
        d.tap_text(desc="Commands")
        time.sleep(0.9)
    d.wait_for(text="+ New command")
