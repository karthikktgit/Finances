#!/usr/bin/env python3
"""Tap every tappable element in Ledgerline, two levels deep from each tab, and report crashes.

Emulator only: it force-stops the app before every tap. Destructive and app-leaving controls are skipped.

    scripts/ui-crawl.py                         # com.ledgerline.app on emulator-5554
    SERIAL=emulator-5556 PKG=com.ledgerline.app.perf scripts/ui-crawl.py

Load sample data first (onboarding > Explore with sample finances) so every section has something to tap.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SERIAL = os.environ.get('SERIAL', 'emulator-5554')
if not SERIAL.startswith('emulator-'):
    sys.exit(f'Refusing to run on {SERIAL}: emulators only.')
ADB = ['adb', '-s', SERIAL]
PKG = os.environ.get('PKG', 'com.ledgerline.app')
TABS = ['Home', 'Activity', 'Calendar', 'Plan', 'Insights']
SKIP = re.compile(
    r'delete|reset|remove|run setup again|export|open settings|allow|don.t allow|lock|import development|'
    r'stop from here|skip this date|bank fixture|mark .* paid|mark .* received|^paid$|^got it$|dismiss|^add$|count it|duplicate|ignore',
    re.I,
)


def adb(*args, capture=True):
    return subprocess.run(ADB + list(args), capture_output=capture, text=True).stdout


def dump():
    # Delete the old file first: when uiautomator cannot get an idle screen it fails and leaves the previous dump.
    for _ in range(6):
        adb('shell', 'rm', '-f', '/sdcard/ui.xml')
        result = adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml')
        out = adb('exec-out', 'cat', '/sdcard/ui.xml')
        if 'dumped' in result and out.strip().startswith('<?xml'):
            return ET.fromstring(out)
        time.sleep(0.6)
    return None


def label(node):
    text = node.get('text') or ''
    desc = node.get('content-desc') or ''
    if not text and not desc:
        # Compose merges child text into the clickable parent for accessibility; read the first child text.
        for child in node.iter('node'):
            t = child.get('text') or child.get('content-desc')
            if t:
                return t.strip()
    return (text or desc).strip()


def clickables(root):
    found = []
    for node in root.iter('node'):
        if node.get('package') != PKG or node.get('clickable') != 'true' or node.get('enabled') != 'true':
            continue
        name = label(node)
        bounds = list(map(int, re.findall(r'\d+', node.get('bounds'))))
        if bounds[3] - bounds[1] < 10:
            continue
        found.append((name, bounds))
    return found


def signature(root):
    """A rough name for the current screen: the first few texts."""
    if root is None:
        return '?'
    texts = [n.get('text') for n in root.iter('node') if n.get('package') == PKG and n.get('text')]
    return ' / '.join(texts[:3])[:90]


def pid():
    return adb('shell', 'pidof', PKG).strip()


def fresh(tab):
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', f'{PKG}/com.ledgerline.app.MainActivity')
    time.sleep(2.5)
    if tab != 'Home':
        root = dump()
        for name, b in clickables(root):
            if name == tab:
                tap(b)
                time.sleep(1.2)
                break


def tap(b):
    adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))


def crashed():
    log = adb('logcat', '-d', '-b', 'crash')
    return 'FATAL EXCEPTION' in log and PKG in log


report = []
crashes = []
adb('logcat', '-c', '-b', 'crash')
for tab in TABS:
    fresh(tab)
    root = dump()
    base = signature(root)
    targets = [(n, b) for n, b in clickables(root) if n and not SKIP.search(n) and n not in TABS]
    print(f'== {tab}: {len(targets)} controls', flush=True)
    for name, bounds in targets:
        fresh(tab)
        start_pid = pid()
        tap(bounds)
        time.sleep(2.0)
        if crashed() or pid() != start_pid:
            crashes.append(f'{tab} > {name}')
            print(f'   CRASH {tab} > {name}', flush=True)
            adb('logcat', '-c', '-b', 'crash')
            continue
        after = dump()
        where = signature(after)
        report.append((tab, name, where))
        print(f'   ok  {name[:40]:40s} -> {where}', flush=True)
        # One level deeper: tap each control on the screen it opened, returning each time.
        if where != base and after is not None:
            inner = [(n, b) for n, b in clickables(after) if n and not SKIP.search(n) and n not in TABS]
            for inner_name, inner_bounds in inner[:10]:
                fresh(tab)
                tap(bounds)
                time.sleep(2.0)
                p = pid()
                tap(inner_bounds)
                time.sleep(1.6)
                if crashed() or pid() != p:
                    crashes.append(f'{tab} > {name} > {inner_name}')
                    print(f'      CRASH {name} > {inner_name}', flush=True)
                    adb('logcat', '-c', '-b', 'crash')
                else:
                    print(f'      ok  {inner_name[:36]}', flush=True)
                    report.append((tab, f'{name} > {inner_name}', ''))

print(f'\nTapped {len(report) + len(crashes)} controls. Crashes: {len(crashes)}')
for c in crashes:
    print('  ', c)
sys.exit(1 if crashes else 0)
