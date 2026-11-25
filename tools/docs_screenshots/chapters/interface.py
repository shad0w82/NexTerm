"""Chapter "Interface tour" (docs/guide/interface.md): the details of an adapter and the list of profiles."""
import time

import flows

NAME = "interface"
SECTION = "guide"


def run(d, s):
    flows.fresh(d)
    # Home with the details of the adapter open
    d.tap_text(text="NexTerm Demo")
    time.sleep(1)
    s.shot("adapter-details")

    # A profile, then the list of profiles with its details open
    d.tap_text(desc="Set up…")
    d.wait_for(text="Set up connection")
    flows.fill(d, "Profile name", flows.PROFILE)
    d.tap_text(text="Connect")
    d.wait_for(text="Active session")
    time.sleep(1)
    flows.disconnect_and_reconnect(d)
    flows.go(d, "Home")
    d.tap_text(text="Disconnect")
    time.sleep(1.2)
    flows.go(d, "Profiles")
    time.sleep(1)
    s.shot("profiles")
    d.tap_text(text=flows.PROFILE)
    time.sleep(1)
    s.shot("profile-details")
