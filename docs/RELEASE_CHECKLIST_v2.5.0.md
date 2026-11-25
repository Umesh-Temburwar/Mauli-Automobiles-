# Release Verification Checklist: v2.5.0

- [x] PLAY_SOUND defaults to true in PreferenceStore.
- [x] 1-Tap SoundToggleAction present on Dashboard, Scanner, Batch Scanner, Daily Scan, and Keyboard.
- [x] Unwanted clutter cards removed from Dashboard.
- [x] Tally Wireless Keyboard screen accessible and responsive.
- [x] Directional arrows (Up, Down, Left, Right, Page Up, Page Down) mapped correctly.
- [x] Numpad, arithmetic operators, and decimal working.
- [x] Tally shortcuts (Alt+A, Alt+C, Ctrl+A, Esc, Enter, Tab) tested.
- [x] F1-F12 function keys tested.
- [x] Receiver serves `/apk` with HTTP 200 and Content-Length.
- [x] Receiver responds to `/api/tally/ping` with JSON status.
- [x] Gradle assembleDebug completes with 0 errors.
