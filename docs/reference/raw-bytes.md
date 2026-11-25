---
icon: material/hexadecimal
---

# Raw bytes syntax

Raw bytes are for devices that speak a binary protocol. You write a line that mixes **hexadecimal bytes** and **text in quotes**, and NexTerm sends exactly the bytes it describes, with **nothing added**: no line ending, no checksum. It is used by the **raw** mode of the send field in [Hex](../guide/terminal.md#hex), and by the saved commands whose “Send as” is **Hex bytes** ([Sending data](../guide/sending.md#saved-commands)).

```text
01 03 "OK" 0D 0A
```

## The rules

**Hexadecimal bytes.** Digits `0` to `9` and letters `A` to `F`, in either case, two to a byte. Spaces between them are free: `0103`, `01 03` and `0 1 0 3` are the same two bytes. A group of digits that does not make whole bytes is an error. No `0x` prefix.

**Text in quotes.** Between `"` and `"`, or between `'` and `'`: the quote that opens is the one that closes, so the other kind can be used inside. The text goes out as **UTF-8**. Inside quotes, a backslash starts an escape:

| Write | You get |
|---|---|
| `\r` | A carriage return, 0D |
| `\n` | A line feed, 0A |
| `\t` | A tab, 09 |
| `\\` | A backslash, 5C |
| `\"` | A double quote, 22 |
| `\'` | A single quote, 27 |

Any other letter after a backslash is an error.

**Nothing else.** Anything that is not a hex digit, a space or a quote, outside the quotes, is an error, and so is a line with no bytes at all.

## Examples

| You write | The bytes sent |
|---|---|
| `01 03 "OK" 0D 0A` | `01 03 4F 4B 0D 0A` |
| `"AT" 0D` | `41 54 0D` |
| `"AT\r\n"` | `41 54 0D 0A` |
| `"café"` | `63 61 66 C3 A9` |
| `'say "hi"'` | `73 61 79 20 22 68 69 22` |
| `FF fe 00` | `FF FE 00` |
| `02 "data" 03` | `02 64 61 74 61 03` |

## The messages of errors

| Message | What went wrong |
|---|---|
| Use hex pairs like 01 03, or "text" in quotes. | Digits that do not come in pairs, before a quote or at the end, or a character that is not allowed outside the quotes (such as the `x` of `0x01`). |
| A quote is not closed. | A `"` or a `'` with no matching one after it. |
| Unknown escape. Use \r \n \t \\ or \". | A backslash followed by a letter that is not in the table. |
| Enter bytes, or "text" in quotes. | The line is empty. |

!!! tip "To send text with a line ending"
    If you only want to send text and the line ending of the profile, you do not need raw bytes: use the **text** mode of the field, or a saved command whose “Send as” is Text.
