---
icon: material/clipboard-check-outline
---

# Requirements

What you need to use NexTerm: a phone or tablet, a cable, and an adapter or a board to talk to.

## The phone or tablet

- **Android 8.0** (API 26) or newer.
- **USB host** support: the device must be able to power and talk to a USB device through its port. Most phones and tablets can, and the specifications of the manufacturer usually call it “OTG”.

## The cable

An **OTG cable or adapter** joins the USB port of the phone or tablet to the plug of the adapter. It has the connector of your device on one side (USB-C or micro-USB) and a USB socket on the other.

!!! tip "Use a cable that carries data"
    Some cables only charge and have no data wires: with one of those the adapter is never seen. The phone also powers the adapter through the cable, so a board that needs more current than the phone can give should have its own power supply.

## The adapter or the board

NexTerm talks to **USB serial adapters** and to **boards whose own USB port is a serial port**. It has drivers for five kinds of chip, and picks the right one by itself:

| Driver | Chips |
|---|---|
| CDC-ACM | Boards with native USB, for example the ESP32-S3 |
| CH34x | CH340 and CH341 adapters |
| CP210x | Silicon Labs CP210x adapters |
| FTDI | FTDI adapters |
| PL2303 | Prolific PL2303 adapters |

An adapter that NexTerm does not recognise can still be used: you choose the driver by hand, as described in [Adapters and drivers](../reference/adapters.md).

!!! warning "Tested so far"
    The first version has been tried with a **CH340 adapter** and an **ESP32-S3 board**. The other adapters should work through their drivers, but nobody has tested them yet.

## What NexTerm asks Android for

- **USB access**, for each adapter, when you connect. Android asks for it again every time you plug the adapter back in: see [First connection](first-connection.md#3-connect).
- **Notifications**, on Android 13 and newer, once. They only show an ongoing notification while a session is open, with a button to disconnect. NexTerm works without them.
- **No Internet access.** NexTerm does not ask for the Internet permission, so it cannot send anything off the device.

Next: [Install](install.md).
