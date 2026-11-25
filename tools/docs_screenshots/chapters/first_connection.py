"""Chapter "First connection" (docs/start/first-connection.md): the adapter on Home, the form, the session, the console."""
import time

import flows

NAME = "first-connection"
SECTION = "start"


def run(d, s):
    flows.fresh(d)

    # 1. Home with the adapter that has no profile yet
    d.wait_for(desc="Set up…")
    s.shot("adapters")

    # 2. The form, with a name for the profile
    d.tap_text(desc="Set up…")
    d.wait_for(text="Set up connection")
    flows.fill(d, "Profile name", flows.PROFILE)
    s.shot("setup")

    # The console first (shot 4), then some traffic for the chart on Home (shot 3)
    d.tap_text(text="Connect")
    d.wait_for(text="Active session")
    time.sleep(1)
    flows.go(d, "Terminal")
    flows.console_type(d, "help", "colors")
    s.shot("console", n=4)
    flows.console_type(d, "stream")
    time.sleep(9)
    flows.ctrl_c(d)
    flows.go(d, "Home")
    time.sleep(1.5)
    s.shot("session", n=3)
