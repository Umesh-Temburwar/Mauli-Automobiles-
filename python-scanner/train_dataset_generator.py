"""
Synthetic Dataset Generator and OCR Training/Evaluation Benchmark
Generates sample handwritten Indian spare parts bills and benchmarks recognition accuracy.
"""

import os
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFont

SAMPLE_PARTS = [
    "17211-KUD-900",  # Honda Activa Air Filter
    "22102-KWP-D00",  # Honda Drive Face
    "06455-KRE-G01",  # Honda Brake Pad
    "23100-KWP-D01",  # Honda Drive Belt
    "18350-AAH-000",  # Hero Splendor Silencer
    "31600-KTR-941",  # Hero Regulator Rectifier
    "22201-KGF-900",  # Clutch Friction Disc
    "15412-MGS-D21",  # Oil Filter Element
    "DV101048",       # Bajaj Pulsar Filter
    "PA541212",       # TVS Apache Spark Plug
    "36JZ0001",       # Bajaj CDI Unit
    "8901030381734",  # Castrol Activ 4T 1L Barcode
    "8901425000000"   # Motul Engine Oil Barcode
]

ITEMS = [
    "Air Filter", "Drive Belt", "Brake Shoe", "Clutch Plate",
    "Spark Plug", "Engine Oil 1L", "Front Fork Seal", "Chain Sprocket Kit"
]


def generate_synthetic_bill(output_path="test_bill.png", num_items=6):
    """
    Creates a realistic synthetic handwritten bill image for testing.
    """
    img = Image.new("RGB", (800, 1000), color=(250, 248, 242))
    draw = ImageDraw.Draw(img)

    # Header
    draw.text((220, 30), "MAULI AUTOMOBILES", fill=(30, 30, 30))
    draw.text((200, 55), "Honda & Hero Genuine Spares • Ambajogai", fill=(80, 80, 80))
    draw.line([(50, 90), (750, 90)], fill=(120, 120, 120), width=2)

    # Columns
    draw.text((60, 105), "Sr.", fill=(50, 50, 50))
    draw.text((120, 105), "Part Code / Barcode", fill=(50, 50, 50))
    draw.text((420, 105), "Item Description", fill=(50, 50, 50))
    draw.text((650, 105), "Rate", fill=(50, 50, 50))
    draw.line([(50, 130), (750, 130)], fill=(150, 150, 150), width=1)

    y = 150
    selected_parts = random.sample(SAMPLE_PARTS, min(num_items, len(SAMPLE_PARTS)))

    for idx, part in enumerate(selected_parts, 1):
        item_desc = random.choice(ITEMS)
        price = random.randint(80, 850)

        # Draw row with slight vertical jitter to simulate handwriting
        jitter = random.randint(-2, 2)
        draw.text((65, y + jitter), f"{idx}.", fill=(15, 20, 80))
        draw.text((120, y + jitter), part, fill=(10, 15, 70))
        draw.text((420, y + jitter), item_desc, fill=(40, 40, 40))
        draw.text((650, y + jitter), f"{price}/-", fill=(20, 20, 20))

        y += 55

    draw.line([(50, y + 20), (750, y + 20)], fill=(120, 120, 120), width=2)
    draw.text((500, y + 35), "Total: Cash Paid", fill=(30, 30, 30))

    img.save(output_path)
    print(f"Generated synthetic test bill: {output_path}")
    return selected_parts


if __name__ == "__main__":
    generate_synthetic_bill("test_bill.png", num_items=8)
