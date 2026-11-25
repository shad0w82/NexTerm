---
icon: material/alert-circle-outline
---

# Troubleshooting

What to do when something does not work. The sections follow what you see; the first ones are about getting connected, the last ones about what happens in a session.

## The adapter is not listed

Home says “No adapter connected”, or the adapter you expect is missing. NexTerm lists every USB device that Android reports, so if it is not there, Android does not see it.

- **The cable.** Some cables only charge. Try a cable that carries data, and an OTG adapter or cable that is made for it.
- **USB host.** The phone or tablet must support USB host. Plug a USB keyboard in through the same cable: if it types in any app, the device can do it.
- **Power.** The phone powers the adapter. A board that needs more current than the phone gives, or that has its own power switch, may have to be powered on its own.
- **The list.** Tap **Refresh list**, or the refresh button at the top, or unplug and plug again.

## “no driver detected”

The adapter is listed in red as “no driver detected” and has a **Choose driver…** button. NexTerm does not know its chip. If you know it, choose the driver by hand: see [Adapters and drivers](adapters.md#when-the-driver-is-not-detected).

## “Couldn't connect”

A window with this title says why. The messages that you may see:

| Message | What to do |
|---|---|
| USB permission was denied. | You tapped Cancel on Android's window. Connect again and tap **OK**. |
| No attached device matches … Is the adapter plugged in? | The adapter of the profile is not there. Plug it in. |
| No driver for … Pick one under Driver. | See “no driver detected” above. |
| A message from the port | The port did not open. If you forced a driver, choose **Automatic**: a driver that does not match the chip may not open the port. |

Two short messages appear at the bottom of the screen when you press **Connect** on a profile: “Plug in the adapter first” and “Disconnect the active session first”. The second one is because NexTerm keeps one session at a time.

## Android asks for the permission every time

It does: Android drops the USB permission when the adapter is unplugged. NexTerm cannot ask for it once and for all because it does not start by itself when an adapter is plugged in, on purpose. See [First connection](../start/first-connection.md#3-connect).

## The text is garbage

Boxes, question marks or random characters mean that the two sides do not use the same settings.

- Check the **baud rate** first. 115200 is common, 9600 is another, and the right one is the one the device is set to.
- Then the **data bits**, the **parity** and the **stop bits**: “8N1” (8 bits, no parity, 1 stop bit) is the usual.
- Change them in the [profile](../guide/profiles.md), then connect again.

## Nothing appears

- Press **Enter**: a device that waits for a command may print nothing until it gets one.
- Check the settings as above, and the wires: the transmit of one side goes to the receive of the other.
- Look at **Trace** or **Hex**: they show every byte that arrives, so you can tell a silent device from text you cannot read.
- The **flow control** of the profile must match the device. With RTS/CTS and a device that does not drive CTS, what NexTerm sends does not get out: it gives up on it, counts an error and says “TX timeout … Is flow control holding the line?” in the Console and in Trace. Set it to **None**.

## The text steps diagonally down the screen

The device ends its lines with a line feed only. In the profile, turn on **Received LF starts a new line**.

## I cannot see what I type

The device does not echo what it receives. Turn on **Local echo** in the [Settings](../guide/settings.md#terminal).

## Typing does nothing

Tap the console to bring the keyboard up. If the session has ended, the Terminal is a read-only transcript: tap **Reconnect**. And look at **Enter sends** in the profile: a device that expects CR+LF does not react to a plain CR.

## The board resets when I connect

DTR and RTS change when the port opens, and many boards use them to restart. In the Settings, Connection, turn off the **Initial DTR and RTS** that restarts yours (DTR is on at first), or change the lines from the bar of the Terminal after connecting. See [Control lines and BREAK](../guide/control-lines.md).

## The session ended by itself

Home says why, at the top of the session:

- “The … adapter was unplugged.” The cable or the contact came loose, or the board lost power or reset its USB port.
- “The connection was lost: …” The link failed with the adapter still plugged in.

The statistics and the last transcript stay on the screen. **Reconnect** opens the session again. With **Auto-reconnect** on, NexTerm resumes by itself after an unplug: only after an unplug, not after you tap Disconnect, and when you open the app if it was in the background or the screen was locked.

## The Errors counter goes up

It counts the writes that timed out: NexTerm gave up on part of what it was sending. The usual cause is flow control holding the line. See “Nothing appears” above.

## The text is too small or too large

Pinch the Console, or use **Text size** in the Settings (7 to 24 sp).

## Something else

NexTerm is a young program and this list will grow. If you find a problem that is not here, or an adapter that works, or one that does not, tell us on the [issues page](https://github.com/shad0w82/NexTerm/issues), with the version of NexTerm (it is in Settings, About), the adapter and what you did. Please leave out anything private: there is no need for it.
