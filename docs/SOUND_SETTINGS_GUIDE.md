# Sound & Audio Feedback Settings

## Default Configuration
Starting in v2.5.0, scan beep sound is enabled by default (`PLAY_SOUND = true`).

## 1-Tap Toggle
Every active scanning screen (Barcode Scanner, Batch Scanner, Daily Customer Scan) and the Dashboard feature a 1-tap volume icon in the TopAppBar:
- **Volume Up (Green/Primary)**: Sound is enabled. Beep sounds on barcode scan and keyboard clicks.
- **Volume Off (Grey/Disabled)**: Muted. All scans and keyboard taps operate silently with vibration only.

## Toast Confirmation
Tapping the icon displays an immediate feedback toast:
- `Sound ON (Beep enabled)`
- `Sound OFF (Muted)`
