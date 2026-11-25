"""Chapter "Sending data" (docs/guide/sending.md): saved commands, their editor, raw bytes in Hex."""
import time

import flows

NAME = "sending"
SECTION = "guide"


def run(d, s):
    flows.fresh(d)
    flows.connect_demo(d)
    flows.go(d, "Terminal")
    time.sleep(1)

    # Three saved commands; the editor is shot with the first one filled in
    flows.open_commands(d)
    flows.new_command(d, "Version", "version")
    s.shot("editor")
    flows.save_command(d)
    for name, command in (("Colours", "colors"), ("Sensor stream", "stream")):
        flows.open_commands(d)
        flows.new_command(d, name, command)
        flows.save_command(d)
    flows.open_commands(d)
    s.shot("commands")

    # Tapping a command sends it
    d.tap_text(text="Version")
    time.sleep(1.2)

    # Raw bytes in the Hex view
    flows.pick_view(d, "Hex")
    d.tap_text(text="01 03 \"OK\" 0D")
    time.sleep(0.6)
    d.type('01 03 "OK" 0D')
    d.hide_keyboard()
    time.sleep(0.6)
    s.shot("raw")
