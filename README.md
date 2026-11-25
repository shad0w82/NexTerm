# NexTerm

A USB serial terminal for Android. Plug a serial adapter or a development board into your phone or tablet
with an OTG cable and talk to it: read what it prints, send commands, watch the control lines.

NexTerm is free software, licensed under the GNU General Public License v3.

## Features

**Connect**
- Lists every USB device that is plugged in, with VID:PID, driver and path, and refreshes on plug and unplug.
- Drivers for CDC-ACM (for example the native USB of an ESP32-S3), CH340/CH341, CP210x, FTDI and PL2303 (through
  [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android)). A driver can be forced on an unknown
  VID:PID.
- Profiles per adapter: baud rate, data bits, parity, stop bits, flow control, and what Enter sends.
- Optional Auto-reconnect: after an unplug the session waits and resumes when the adapter comes back (Android asks
  for the USB permission again each time).

**Terminal**
- Console (VT100/ANSI, based on the Termux terminal emulator), Trace (every message with its direction and the
  time since the one before) and Hex, all over the same byte log.
- Pinch to zoom (7 to 24 sp), scrollback up to 50,000 lines, Autoscroll on or off, local echo, a configurable row
  of extra keys.
- Send text with a chosen line ending, or send raw bytes: hex pairs mixed with `"quoted text"`, for example
  `01 03 "OK" 0D 0A`.
- Saved commands, shared by all profiles: tap to send, pencil to edit.
- Control lines: DTR and RTS toggles, CTS, DSR, CD and RI indicators, BREAK, and the initial DTR/RTS state.
- Session statistics and a traffic chart on the Home screen.
- Layouts for phones and tablets, in portrait and landscape.

Not there yet: logging a session to a file, and transports other than USB serial (the terminal itself does not
depend on the serial port, so others can be added).

## Requirements

- Android 8.0 (API 26) or newer.
- A device with USB host support and an OTG cable or adapter.

## Building

You need a JDK 17 or newer and the Android SDK (platform 37). The Gradle wrapper does the rest.

```bash
./gradlew :app:assembleDebug      # debug build
./gradlew :app:assembleRelease    # release build
./gradlew :app:testDebugUnitTest  # unit tests
```

The release build is signed with the local debug key, so it can be installed for testing and replaces a debug
install in place. A build to publish needs its own signing configuration.

The debug build also has a simulated serial device that shows up on emulators only (they have no USB host), which
is handy for trying the interface without hardware.

## Design

`design/mockup/index.html` is an interactive mockup of the whole interface, at the real sizes of a phone and a
tablet. Open it in a browser. The app and the mockup are kept in step.

## Project layout

- `app/`: the Android app (Kotlin, Jetpack Compose).
- `terminal-emulator/`, `terminal-view/`: the Termux terminal modules, with the changes listed in each `NOTICE.md`.
- `design/mockup/`: the interface mockup.

## License and credits

NexTerm is released under the GNU General Public License v3 (see `LICENSE`).

- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) 3.12.0, by mik3y (MIT).
- Termux `terminal-emulator` and `terminal-view`, from [termux-app](https://github.com/termux/termux-app) v0.118.3
  (GPL v3), which in turn builds on Jack Palevich's Android-Terminal-Emulator (Apache 2.0).
- Roboto Mono (Apache 2.0).
