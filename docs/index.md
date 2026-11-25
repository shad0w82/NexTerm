---
hide:
  - navigation
  - toc
---

# NexTerm

**A USB serial terminal for Android.** Plug a serial adapter or a development board into your phone or tablet with an OTG cable, then read what it prints, send commands and watch the control lines.

[Get started](start/requirements.md){ .md-button .md-button--primary }
[Install the app](start/install.md){ .md-button }

<div class="grid cards" markdown>

-   :material-rocket-launch-outline: **Getting started**

    ---

    What you need, how to install the app and how to make your first connection.

    [:octicons-arrow-right-24: Requirements](start/requirements.md)

-   :material-book-open-variant: **User guide**

    ---

    The screens of the app, one by one: adapters, profiles, the terminal, sending data, control lines and settings.

    [:octicons-arrow-right-24: Interface tour](guide/interface.md)

-   :material-book-search-outline: **Reference**

    ---

    The adapters and drivers, the syntax for sending raw bytes and what to do when something does not work.

    [:octicons-arrow-right-24: Adapters and drivers](reference/adapters.md)

-   :material-information-outline: **About**

    ---

    The license, the libraries NexTerm builds on and what it cannot do yet.

    [:octicons-arrow-right-24: About NexTerm](about.md)

</div>

## What it does

- **Lists the adapters** that are plugged in, with their VID:PID and driver, and follows you plugging and unplugging. It has drivers for CDC-ACM (for example the native USB of an ESP32-S3), CH340/CH341, CP210x, FTDI and PL2303.
- **Shows the traffic three ways** over the same data: Console (VT100/ANSI), Trace, with the direction and the time of each message, and Hex.
- **Sends text or raw bytes**, such as `01 03 "OK" 0D 0A`, and keeps the commands you use most one tap away.
- **Handles the control lines**: toggle DTR and RTS, watch CTS, DSR, CD and RI, send a BREAK.
- **Remembers each adapter** with a profile (baud rate, data bits, parity, stop bits, flow control, line ending), and can resume a session by itself when an unplugged adapter comes back.
- **Fits phones and tablets**, in portrait and landscape, with statistics and a traffic chart for the session.

!!! warning "First version"
    NexTerm 0.1.0 is the first public build. It has been tried with a CH340 adapter and an ESP32-S3 board; other adapters should work through their drivers but have not been tested. Logging a session to a file is not there yet.

NexTerm is free software (GNU GPL v3) and its code is on [GitHub](https://github.com/shad0w82/NexTerm). The names of chips and boards belong to their respective owners and are used here only to say what NexTerm works with.
