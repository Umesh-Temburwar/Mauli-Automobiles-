#!/usr/bin/env python3
"""
Keystroke latency measurement script over local Wi-Fi.
"""
import time
import urllib.request
import json

def measure_ping_latency(host="127.0.0.1", port=8080, samples=5):
    latencies = []
    url = f"http://{host}:{port}/api/tally/ping"
    for i in range(samples):
        t0 = time.perf_counter()
        try:
            req = urllib.request.urlopen(url, timeout=1)
            _ = req.read()
            dt = (time.perf_counter() - t0) * 1000
            latencies.append(dt)
        except Exception:
            pass
    if latencies:
        avg = sum(latencies) / len(latencies)
        print(f"Average latency over {len(latencies)} samples: {avg:.2f} ms")
        return avg
    print("Unable to measure latency.")
    return None

if __name__ == "__main__":
    measure_ping_latency()
