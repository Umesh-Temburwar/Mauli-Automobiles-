"""
MAULI BARCODE — Python Computer Vision Bill Scanner & TallyPrime Automation
Author: Umesh Temburwar | Mauli Automobiles (Ambajogai)
"""

import re
import sys
import os
import time
import cv2
import numpy as np
from PIL import Image

try:
    import pytesseract
except ImportError:
    pytesseract = None

try:
    import pyautogui
except ImportError:
    pyautogui = None


class AutomobileBillScanner:
    """
    OpenCV and Computer Vision engine for extracting handwritten
    automobile spare parts numbers and barcodes from customer bills.
    """

    # Indian 2-Wheeler Spare Parts Regex Patterns
    HONDA_HERO_OEM = re.compile(r'\b([0-9]{5}-[0-9A-Za-z]{3}-[0-9A-Za-z]{3,4})\b')
    CONTINUOUS_OEM = re.compile(r'\b([0-9]{5}[0-9A-Za-z]{6,7})\b')
    BAJAJ_TVS_OEM = re.compile(r'\b([A-Za-z]{2}[0-9]{6}|[0-9]{2}[A-Za-z]{2}[0-9]{4}|[A-Za-z][0-9]{7})\b')
    INDIAN_EAN13 = re.compile(r'\b(890[0-9]{10})\b')
    GENERAL_BARCODE = re.compile(r'\b([0-9]{12,14})\b')

    # Noise Patterns
    PRICE_PATTERN = re.compile(r'(?:₹|rs\.?|inr)\s*[0-9]+(?:\.[0-9]+)?|[0-9]+(?:\.[0-9]+)?\s*/-', re.IGNORECASE)
    QTY_PATTERN = re.compile(r'\b[0-9]+\s*(?:pc|pcs|nos|no|pkt|pkts|set|sets|qty|ltr|ml|box)\b', re.IGNORECASE)
    DATE_PATTERN = re.compile(r'\b[0-9]{1,2}[/\-.][0-9]{1,2}[/\-.][0-9]{2,4}\b')

    def __init__(self, tesseract_cmd=None):
        if tesseract_cmd and pytesseract:
            pytesseract.pytesseract.tesseract_cmd = tesseract_cmd

    def preprocess_image(self, image_path: str):
        """
        OpenCV image preprocessing pipeline for faint handwriting on paper bills.
        """
        img = cv2.imread(image_path)
        if img is None:
            raise FileNotFoundError(f"Could not load image: {image_path}")

        # 1. Grayscale
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

        # 2. Contrast Limited Adaptive Histogram Equalization (CLAHE)
        clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
        contrast_boost = clahe.apply(gray)

        # 3. Bilateral Filter to remove paper grain while preserving sharp pen edges
        denoised = cv2.bilateralFilter(contrast_boost, 9, 75, 75)

        # 4. Otsu's Adaptive Thresholding (turns handwriting pure black, background pure white)
        _, thresh = cv2.threshold(denoised, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

        return gray, contrast_boost, thresh

    def extract_part_numbers(self, image_path: str):
        """
        Extracts verified OEM part numbers and barcodes from a bill image.
        """
        if not pytesseract:
            print("[Warning] pytesseract is not installed. Run: pip install -r requirements.txt")
            return []

        _, contrast_boost, thresh = self.preprocess_image(image_path)

        # OCR configuration optimized for alphanumeric part numbers
        custom_config = r'--oem 3 --psm 6'

        # Multi-pass OCR: Pass 1 on contrast boost, Pass 2 on Otsu threshold
        text1 = pytesseract.image_to_string(contrast_boost, config=custom_config)
        text2 = pytesseract.image_to_string(thresh, config=custom_config)

        combined_text = f"{text1}\n{text2}"
        return self.parse_text_for_parts(combined_text)

    def parse_text_for_parts(self, raw_text: str):
        """
        Filters and normalizes spare parts numbers.
        """
        results = []
        seen = set()

        for line in raw_text.splitlines():
            cleaned = line.strip()
            if not cleaned:
                continue

            # Strip prices, quantities, dates
            cleaned = self.PRICE_PATTERN.sub(' ', cleaned)
            cleaned = self.QTY_PATTERN.sub(' ', cleaned)
            cleaned = self.DATE_PATTERN.sub(' ', cleaned)

            # Match Honda/Hero OEM
            for match in self.HONDA_HERO_OEM.finditer(cleaned):
                code = match.group(1).upper()
                if code not in seen:
                    seen.add(code)
                    results.append({"type": "Honda/Hero OEM", "code": code})

            # Match Continuous OEM (17211KUD900 -> 17211-KUD-900)
            for match in self.CONTINUOUS_OEM.finditer(cleaned):
                raw = match.group(1).upper()
                fmt = f"{raw[:5]}-{raw[5:8]}-{raw[8:]}"
                if fmt not in seen:
                    seen.add(fmt)
                    results.append({"type": "Formatted OEM", "code": fmt})

            # Match Bajaj/TVS
            for match in self.BAJAJ_TVS_OEM.finditer(cleaned):
                code = match.group(1).upper()
                if code not in seen:
                    seen.add(code)
                    results.append({"type": "Bajaj/TVS OEM", "code": code})

            # Match Indian EAN-13
            for match in self.INDIAN_EAN13.finditer(cleaned):
                code = match.group(1)
                if code not in seen:
                    seen.add(code)
                    results.append({"type": "Indian Barcode (890)", "code": code})

        return results

    def send_to_tally_via_keystrokes(self, part_numbers, delay_seconds=1.2):
        """
        Automatically types extracted barcode numbers into TallyPrime with Enter key.
        """
        if not pyautogui:
            print("[Warning] pyautogui is not installed. Run: pip install pyautogui")
            return

        print(f"\n[Ready] Please click into TallyPrime Sales Voucher item field within 5 seconds...")
        for i in range(5, 0, -1):
            print(f"Starting in {i}...")
            time.sleep(1)

        print("\n[Typing into TallyPrime...]")
        for idx, item in enumerate(part_numbers, 1):
            code = item["code"]
            print(f"Product {idx}/{len(part_numbers)}: Typing '{code}' + ENTER")
            pyautogui.write(code, interval=0.04)
            pyautogui.press('enter')
            time.sleep(delay_seconds)

        print("\n[Finished] All products successfully entered into TallyPrime!")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python bill_scanner.py <path_to_bill_image>")
        print("Example: python bill_scanner.py bill_sample.jpg")
        sys.exit(1)

    scanner = AutomobileBillScanner()
    image_file = sys.argv[1]

    print(f"Analyzing bill: {image_file}...")
    parts = scanner.extract_part_numbers(image_file)

    print(f"\nFound {len(parts)} Spare Part Numbers / Barcodes:")
    for p in parts:
        print(f"  • [{p['type']}] {p['code']}")

    if parts and pyautogui:
        resp = input("\nDo you want to enter these into TallyPrime now? (y/n): ")
        if resp.lower() == 'y':
            scanner.send_to_tally_via_keystrokes(parts)
