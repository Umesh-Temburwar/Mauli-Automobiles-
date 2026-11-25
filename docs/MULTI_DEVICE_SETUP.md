# Multi-Device Wi-Fi Setup Guide

The Mauli Barcode Scanner system supports multiple Android mobile phones communicating with a single TallyPrime Windows PC over local Wi-Fi.

## How It Works
- The PC receiver broadcasts UDP beacons on port 8888.
- Any Android phone running Mauli Barcode Scanner automatically detects the PC IP.
- Each scan or keyboard action is sent as an atomic HTTP POST.
- The receiver queues concurrent incoming requests and injects keystrokes sequentially.
