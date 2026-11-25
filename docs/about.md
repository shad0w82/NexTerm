---
icon: material/information-outline
---

# About

NexTerm is a **USB serial terminal for Android**. Version 0.1.0 is the first public build.

## Free software

NexTerm is free software, released under the **GNU General Public License v3**. You can use it, study it, change it and share it under the terms of that license, which you can read in the [LICENSE](https://github.com/shad0w82/NexTerm/blob/main/LICENSE) file. The source code, the README and the issues are on [GitHub](https://github.com/shad0w82/NexTerm).

## Your data

- NexTerm does **not ask for the Internet permission**, so it cannot send anything off the device.
- Your profiles, saved commands and settings stay on the device. NexTerm keeps no backup of them, so uninstalling the app loses them for good.
- It needs the permission to use each USB adapter, and, on Android 13 and newer, to show a notification while a session is open: see [Requirements](start/requirements.md#what-nexterm-asks-android-for).

## What it cannot do yet

- **Log a session to a file.** The switch is in the Settings, switched off, waiting for a later version.
- **Light and system themes.** The app is dark.
- **Other kinds of link.** The terminal does not depend on the serial port, so others could be added, but today there is only USB serial.
- **Adapters nobody has tried.** Five drivers are there, and only a CH340 adapter and an ESP32-S3 board have been tried: see [Adapters and drivers](reference/adapters.md).

## Credits

NexTerm is built on the work of others:

- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) 3.12.0, by mik3y (MIT): the drivers for the adapters.
- The `terminal-emulator` and `terminal-view` of [Termux](https://github.com/termux/termux-app) v0.118.3 (GPL v3), which in turn build on Jack Palevich's Android-Terminal-Emulator (Apache 2.0): the Console.
- Roboto Mono (Apache 2.0): the font of values such as the VID:PID in the app.

This site is made with [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/), and uses the fonts Inter and JetBrains Mono (SIL Open Font License 1.1), which it serves itself.

## Names

CH340, CH341, CP210x, FTDI, PL2303, ESP32, Arduino, STM32 and the other names of chips and boards are the property of their respective owners, and are used here only to say what NexTerm works with. NexTerm is not affiliated with or endorsed by them.
