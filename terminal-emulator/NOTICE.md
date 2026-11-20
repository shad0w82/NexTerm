# Vendored Termux module

Source: https://github.com/termux/termux-app, tag v0.118.3 (same directory name).
License: GPLv3-only, per the termux-app repository LICENSE.md. Portions derive from
Jack Palevich's Android-Terminal-Emulator (Apache-2.0).

Modifications by the NextTerm project (2026-11-20):
- Build file rewritten in Gradle Kotlin DSL; manifest `package` attribute removed (namespace is set in Gradle).
- `TerminalSession.java` rewritten: the pty/subprocess (JNI) backend was replaced by a
  transport-agnostic byte pipe so the emulator can be driven by a serial port (or, later, SSH/Telnet).
- `JNI.java` and `src/main/jni/` removed (no local subprocess is ever spawned).
