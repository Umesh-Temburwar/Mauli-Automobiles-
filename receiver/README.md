# Mauli Tally Receiver

The PC-side receiver runs silently in the Windows system tray and injects keystrokes directly into TallyPrime.

## Highlights
- **Zero Config**: Auto-discovered by Android phone via UDP port 8888.
- **Port 8080**: Serves `/api/tally/scan`, `/api/tally/ping`, and `/apk`.
- **Keyboard Injection**: Uses Windows `SendInput` API for millisecond-accurate key simulation.
- **Windows Autostart**: Runs automatically on Windows login via registry entry.
