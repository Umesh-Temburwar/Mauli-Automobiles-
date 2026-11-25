#!/usr/bin/env python3
"""
Integration test script for PC receiver HTTP endpoints.
"""
import urllib.request
import json
import sys

def test_receiver_ping(host="127.0.0.1", port=8080):
    url = f"http://{host}:{port}/api/tally/ping"
    try:
        req = urllib.request.urlopen(url, timeout=2)
        data = json.loads(req.read().decode())
        assert data.get("success") is True
        print(f"Receiver ping success: {data}")
        return True
    except Exception as e:
        print(f"Receiver ping failed (is receiver running?): {e}")
        return False

if __name__ == "__main__":
    test_receiver_ping()
