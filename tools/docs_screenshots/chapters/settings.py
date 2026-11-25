"""Chapter "Settings" (docs/guide/settings.md): the sections and their pages."""
import time

import flows

NAME = "settings"
SECTION = "guide"


def run(d, s):
    flows.fresh(d)
    flows.go(d, "Settings")
    time.sleep(1)
    s.shot("sections")
    for label in ("Terminal", "Connection", "App"):
        if d.kind == "phone":
            flows.tap_row(d, label)
        else:
            flows.tap_row(d, label)
        time.sleep(1)
        s.shot(label.lower())
        if d.kind == "phone":
            d.back()
            time.sleep(0.8)
