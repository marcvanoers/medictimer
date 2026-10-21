#!/usr/bin/env python3
"""Verify the synthetic 91-row PDF produced by ReportsDeviceTest on Android.

Requires Poppler's pdfinfo and pdftotext on the host. This parses the native output,
including exact row counts, instead of testing only for a nonempty file.
"""
import re
import subprocess
import sys

path = sys.argv[1]
info = subprocess.check_output(['pdfinfo', path], text=True)
pages = int(re.search(r'Pages:\s+(\d+)', info).group(1))
assert pages >= 4, f'Expected a multipage report, got {pages} pages'
text = subprocess.check_output(['pdftotext', '-layout', path, '-'], text=True)
for expected in ['Test period', 'Recorded history (91 entries)', '122/81 mmHg', 'mmol/L']:
    assert expected in text, f'Missing {expected}'
assert len(re.findall(r'6\.4 mmol/L', text)) == 90, 'Not all 90 glucose readings were exported'
print(f'PDF verified: {pages} pages, 90 glucose readings and 1 blood pressure reading.')
