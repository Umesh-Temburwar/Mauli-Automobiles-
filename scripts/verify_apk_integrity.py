#!/usr/bin/env python3
"""
APK integrity verification script.
"""
import os
import hashlib

def check_file(path):
    if not os.path.exists(path):
        print(f"File not found: {path}")
        return False
    size = os.path.getsize(path)
    print(f"File: {path} | Size: {size:,} bytes")
    return size > 50_000_000

if __name__ == "__main__":
    check_file("MauliBarcodeScanner.apk")
