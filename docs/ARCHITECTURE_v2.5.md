# Mauli Barcode Scanner v2.5 Architecture

```
[ Android Mobile App ]
     |
     +---> Sound Preference (PLAY_SOUND) -> ToneGenerator & Haptic Engine
     |
     +---> Camera Scanner / OCR -> Barcode Detection
     |
     +---> Tally Wireless Keyboard Remote
              |
              +---> Wi-Fi HTTP Keystroke Injector (Port 8080)
              |            |
              |            +---> Windows PC Receiver -> SendInput() -> [ TallyPrime ]
              |
              +---> Bluetooth HID Keyboard Profile (Scan codes)
```
