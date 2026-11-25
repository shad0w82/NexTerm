---
icon: material/usb-port
---

# Adapters and drivers

NexTerm talks to a USB serial adapter through a **driver** that knows its chip. It has five of them, from the library [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android), and it picks the right one by itself from the VID:PID of the adapter.

## The drivers

| Driver | Chips and boards |
|---|---|
| CDC-ACM | Arduino, ESP32-S2 and S3 native USB, STM32 |
| CH34x | CH340, CH341 |
| CP210x | CP2102, CP2104 |
| FTDI | FT232R, FT2232, FT231X |
| PL2303 | Prolific PL2303 |

!!! warning "Tested so far"
    The first version has been tried with a **CH340 adapter** (1A86:7522) and an **ESP32-S3 board** (its native USB, which is CDC-ACM). The other drivers come from the library and should work, but nobody has tried them with NexTerm yet. If you do, a note on the [issues page](https://github.com/shad0w82/NexTerm/issues) helps.

## How NexTerm picks the driver

The driver comes from a table of VID:PID pairs. NexTerm uses the table of the library, adds the pairs that it lacks (for now 1A86:7522, a variant of the CH340), and then your own choices, which win over the others. The details of an adapter say where its driver came from: “Detected: CH34x”, “added by NexTerm” for a pair NexTerm added, or “chosen by you”.

## When the driver is not detected

An adapter that no driver recognises still appears in the list of [Home](../guide/home.md), with “no driver detected” in red and a **Choose driver…** button, and so does any other USB device that is plugged in. If you know what chip it has, you can tell NexTerm:

1. Tap **Choose driver…** (or **Change driver…** in the details of the adapter).
2. In the **Driver** window, choose one of the five drivers. **Automatic** uses what NexTerm detects, and is how you go back.
3. Tap **Save**.

The choice is kept for the **model** of the adapter, and applies to **all** its profiles. It cannot be changed while a session is open.

!!! warning "A wrong driver may not open the port"
    If the driver does not match the chip, the port may fail to open, and NexTerm says so when you connect. Choose **Automatic**, or another driver, and try again.

## Adapters with more than one port

Some chips, such as the FT2232 and FT4232, give several serial ports through one USB connection. The profile of such an adapter has a **Port** choice (“Port 1”, “Port 2”…) that says which one to open.

## What each chip can do

What the control lines and the flow control can do depends on the chip. The details of the adapter on Home list, under **Control lines** and **Flow control**, what its driver offers, so you can see it before you connect. A chip that has no hardware flow control, for instance, offers only None.

## Android and the USB permission

Android asks you for the permission to use each adapter, and asks again every time you plug it back in: see [First connection](../start/first-connection.md#3-connect).
