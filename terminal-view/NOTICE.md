# Vendored Termux module

Source: https://github.com/termux/termux-app, tag v0.118.3 (same directory name).
License: GPLv3-only, per the termux-app repository LICENSE.md. Portions derive from
Jack Palevich's Android-Terminal-Emulator (Apache-2.0).

Modifications by the NexTerm project (2026-11-20):
- Build file rewritten in Gradle Kotlin DSL; manifest `package` attribute removed (namespace is set in Gradle).
- `TerminalView`: `setAutoScroll(boolean)` (2026-11-25): when off, `onScreenUpdated()` keeps the rows on screen where they are instead of jumping to the newest output (same path as text selection).
