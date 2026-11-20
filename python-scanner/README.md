# MAULI BARCODE — Python Computer Vision Bill Scanner

This directory contains Python libraries and tools to process, benchmark, and train OCR pipelines for handwritten automobile bills and automate entry into **TallyPrime**.

---

## 🚀 Quick Setup

1. **Install Python 3.9+** on your Windows PC.
2. Open PowerShell or Command Prompt:
   ```powershell
   cd python-scanner
   pip install -r requirements.txt
   ```

*(Optional for OCR): Install [Tesseract OCR for Windows](https://github.com/UB-Mannheim/tesseract/wiki).*

---

## 🛠️ Usage

### 1. Scan a Bill Image & Automatically Type into TallyPrime
```powershell
python bill_scanner.py path_to_bill_image.jpg
```
The script will:
1. Apply OpenCV contrast enhancement & Otsu binarization.
2. Extract all **Honda & Hero OEM Part Numbers** (`17211-KUD-900`), **Bajaj/TVS codes** (`DV101048`), and **Indian Barcodes** (`890...`).
3. Offer to automatically type all items into **TallyPrime Sales Voucher** with automatic `Enter` key!

### 2. Generate Synthetic Test Bills for Testing
```powershell
python train_dataset_generator.py
```
Creates `test_bill.png` containing realistic sample bills for testing accuracy.
