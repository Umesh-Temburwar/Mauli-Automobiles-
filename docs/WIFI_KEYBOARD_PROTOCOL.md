# Wi-Fi Keystroke Injection Protocol Specification

## Endpoint: `/api/tally/scan` (POST)
Direct key events and strings are transmitted over HTTP JSON payloads to the PC receiver running on port 8080.

### Single Key Event Payload
```json
{
  "barcode": "",
  "profile": {
    "id": "keyboard_direct",
    "name": "KeyboardDirect",
    "steps": [
      {
        "type": "PRESS_UP",
        "delayMs": 30
      }
    ]
  }
}
```

### Direct Text String Payload
```json
{
  "barcode": "43125-KTC-900",
  "profile": {
    "id": "keyboard_text",
    "name": "KeyboardText",
    "steps": [
      {
        "type": "TYPE_TEXT",
        "textPayload": "43125-KTC-900",
        "delayMs": 30
      },
      {
        "type": "PRESS_ENTER",
        "delayMs": 30
      }
    ]
  }
}
```
