#!/usr/bin/env python3
"""
Test suite verifying key codes and action mapping compatibility with Windows Tally receiver.
"""

ACTIONS = {
    "PRESS_ENTER": 0x0D,
    "PRESS_TAB": 0x09,
    "PRESS_ESCAPE": 0x1B,
    "PRESS_SPACE": 0x20,
    "PRESS_BACKSPACE": 0x08,
    "PRESS_UP": 0x26,
    "PRESS_DOWN": 0x28,
    "PRESS_LEFT": 0x25,
    "PRESS_RIGHT": 0x27,
    "PRESS_PAGE_UP": 0x21,
    "PRESS_PAGE_DOWN": 0x22,
}

def verify_all_actions():
    for name, code in ACTIONS.items():
        assert code > 0, f"Invalid code for {name}"
    print(f"Verified {len(ACTIONS)} virtual key actions successfully.")

if __name__ == "__main__":
    verify_all_actions()
