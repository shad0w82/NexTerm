"""Takes the screenshots of the documentation from the app running on two emulators, a phone and a tablet.

  python3 tools/docs_screenshots/shoot.py [chapter [name-of-one-shot]]      (KIND=phone or KIND=tablet: one emulator only)

Needs the DEBUG build installed on both emulators (it shows a simulated "NexTerm Demo" adapter, only on emulators, so
no real device and no personal data end up in the pictures):

  ./gradlew :app:assembleDebug
  adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk       (and the same for the tablet)

Never use installDebug: it installs on every connected device. The script only drives serials that start with
"emulator-", clears the data of the app on them, and puts the emulator settings it changed back at the end. The
pictures are written to docs/<section>/img/, reduced with sips and optimised with pngquant when they are there.
"""
import importlib
import os
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import lib  # noqa: E402

CHAPTERS = ["first_connection", "terminal", "sending", "interface", "settings"]


def main():
    wanted = sys.argv[1].replace("-", "_") if len(sys.argv) > 1 else ""
    only = sys.argv[2] if len(sys.argv) > 2 else ""
    modules = [importlib.import_module(f"chapters.{name}") for name in CHAPTERS if not wanted or name == wanted]
    if not modules:
        sys.exit(f"no chapter called {wanted!r} ({', '.join(CHAPTERS)})")
    devices = lib.emulators()
    if os.environ.get("KIND"):                      # KIND=phone or KIND=tablet: only that emulator
        devices = [d for d in devices if d.kind == os.environ["KIND"]]
    if not devices:
        sys.exit("no emulator is running")
    for d in devices:
        d.prepare()
    try:
        for mod in modules:
            for d in devices:
                print(f"{mod.NAME} on {d.serial} ({d.kind})")
                shooter = lib.Shooter(d, mod.SECTION, mod.NAME, only)
                shooter.clear_old()
                mod.run(d, shooter)
    finally:
        for d in devices:
            d.restore()


if __name__ == "__main__":
    main()
