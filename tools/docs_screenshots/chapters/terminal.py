"""Chapter "Terminal" (docs/guide/terminal.md): the console, the choice of view, Trace and Hex."""
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
    s.shot("console")

    # The view menu, open
    d.tap_text(text="Console")
    time.sleep(0.9)
    s.shot("views")
    d.back()
    time.sleep(0.6)

    # Trace and Hex of a clean session
    flows.disconnect_and_reconnect(d)
    flows.go(d, "Terminal")
    flows.pick_view(d, "Trace")
    send_in_trace(d, "version")
    send_in_trace(d, "hex")
    s.shot("trace")
    flows.pick_view(d, "Hex")
    time.sleep(1)
    s.shot("hex")
