"""Chapter "Terminal" (docs/guide/terminal.md): the console, full screen, the choice of view, Trace and Hex."""
import time

import flows

NAME = "terminal"
SECTION = "guide"


def send_in_trace(d, text):
    d.tap_text(text="Send text")
    time.sleep(0.6)
    d.type(text)
    d.hide_keyboard()
    d.tap_text(text="Send")
    time.sleep(1.2)


def run(d, s):
    flows.fresh(d)
    flows.connect_demo(d)
    flows.go(d, "Terminal")
    time.sleep(1)
    flows.console_type(d, "help")
    s.shot("console", n=1)

    # Full screen: the round button hides the header and the bars, and the same button brings them back.
    # The device runs a full-screen program (`top` of the demo device), which shows the whole window at work.
    flows.console_type(d, "top")
    time.sleep(2.5)
    d.tap_text(desc="Full screen")
    time.sleep(1.5)
    s.shot("fullscreen", n=5)
    d.tap_text(desc="Exit full screen")
    time.sleep(1.0)
    flows.console_type(d, "q")                    # leaves top, and the console shows what it showed before

    # The view menu, open
    d.tap_text(text="Console")
    time.sleep(0.9)
    s.shot("views", n=2)
    d.back()
    time.sleep(0.6)

    # Trace and Hex of a clean session
    flows.disconnect_and_reconnect(d)
    flows.go(d, "Terminal")
    flows.pick_view(d, "Trace")
    send_in_trace(d, "version")
    send_in_trace(d, "hex")
    s.shot("trace", n=3)
    flows.pick_view(d, "Hex")
    time.sleep(1)
    s.shot("hex", n=4)
