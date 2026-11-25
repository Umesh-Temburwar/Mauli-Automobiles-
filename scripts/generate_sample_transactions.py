#!/usr/bin/env python3
"""
Generates sample spare parts transaction payloads for end-to-end testing.
"""

SAMPLE_ITEMS = [
    {"part_no": "43125-KTC-900", "desc": "Brake Shoe Set Splendor", "qty": 2, "mrp": 210.0},
    {"part_no": "15412-KSP-910", "desc": "Oil Filter Element Shine", "qty": 5, "mrp": 75.0},
    {"part_no": "22102-KTC-900", "desc": "Clutch Plate Splendor", "qty": 1, "mrp": 380.0},
]

def print_samples():
    print(f"Generated {len(SAMPLE_ITEMS)} sample test transactions for Tally.")

if __name__ == "__main__":
    print_samples()
