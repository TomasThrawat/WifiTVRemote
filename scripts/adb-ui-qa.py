#!/usr/bin/env python3
import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = "com.tomasthrawat.wifitvremote"
RESULTS = []
REQUIRED = {
    "Power", "Mute", "SOURCE", "EXIT", "CH-LIST", "Back", "Home",
    "PLAY", "BACK", "Volume up", "Volume down", "Channel up",
    "Channel menu", "Channel down", "Numeric keypad",
    "SETUP", "YouTube", "NETFLIX", "prime video"
}

def sh(*args, check=True):
    return subprocess.run(list(args), text=True, capture_output=True, check=check)

def adb(*args, check=True):
    return sh("adb", *args, check=check)

def launch():
    adb("shell", "am", "force-stop", PKG, check=False)
    adb("shell", "monkey", "-p", PKG, "1", check=False)
    time.sleep(1.2)

def dump_xml(path):
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml", check=True)
    out = adb("exec-out", "cat", "/sdcard/window.xml", check=True)
    Path(path).write_text(out.stdout, encoding="utf-8")
    return out.stdout

def screenshot(path):
    with open(path, "wb") as f:
        p = subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, stderr=subprocess.PIPE)
    if p.returncode != 0:
        raise RuntimeError(p.stderr.decode(errors="ignore"))

def parse_nodes(xml):
    root = ET.fromstring(xml)
    nodes = []
    for n in root.iter("node"):
        if n.attrib.get("clickable") != "true" or n.attrib.get("enabled") != "true":
            continue
        if n.attrib.get("visible-to-user") == "false":
            continue
        desc = (n.attrib.get("content-desc") or "").strip()
        text = (n.attrib.get("text") or "").strip()
        label = desc or text
        bounds = n.attrib.get("bounds") or ""
        m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        if x2 <= x1 or y2 <= y1:
            continue
        nodes.append({
            "label": label,
            "text": text,
            "content_desc": desc,
            "bounds": bounds,
            "center": [(x1 + x2) // 2, (y1 + y2) // 2],
            "resource_id": n.attrib.get("resource-id") or ""
        })
    uniq = {}
    for x in nodes:
        key = (x["label"], x["bounds"])
        uniq[key] = x
    return list(uniq.values())

def find_node(nodes, label):
    for n in nodes:
        if n["label"] == label:
            return n
    low = label.lower()
    for n in nodes:
        if low and low in n["label"].lower():
            return n
    return None

def tap_node(n):
    x, y = n["center"]
    adb("shell", "input", "tap", str(x), str(y), check=True)
    time.sleep(0.35)

def record(name, ok, error=None):
    item = {"name": name, "ok": ok}
    if error:
        item["error"] = str(error)
    RESULTS.append(item)

def click_and_restart(name, node):
    try:
        tap_node(node)
        record(name, True)
    except Exception as e:
        record(name, False, e)
    finally:
        launch()

def set_night(value):
    adb("shell", "cmd", "uimode", "night", value, check=False)
    launch()

# Infrastructure versions
with open("appium-version.txt", "w", encoding="utf-8") as f:
    p = subprocess.run(["appium", "--version"], text=True, stdout=f, stderr=subprocess.STDOUT)
with open("agent-device-version.txt", "w", encoding="utf-8") as f:
    subprocess.run(["agent-device", "--version"], text=True, stdout=f, stderr=subprocess.STDOUT)

# Ensure debug emulator preview is visible.
launch()
set_night("yes")
screenshot("ui-dark.png")
xml_main_dark = dump_xml("ui-source-main.xml")
nodes_main = parse_nodes(xml_main_dark)

present = {n["label"] for n in nodes_main if n["label"]}
missing = sorted(REQUIRED - present)
record("main:required-controls-visible", not missing, "Missing: " + ", ".join(missing) if missing else None)

# Every visible main-screen clickable control is exercised.
for n in nodes_main:
    if n["label"]:
        click_and_restart("main:" + n["label"], n)

# Explicit setup -> Settings validation.
launch()
nodes = parse_nodes(dump_xml("ui-source-main.xml"))
setup = find_node(nodes, "SETUP")
if setup:
    try:
        tap_node(setup)
        time.sleep(0.6)
        xml_settings = dump_xml("ui-source-settings.xml")
        record("flow:SETUP-opens-Settings", bool(find_node(parse_nodes(xml_settings), "Settings")) or "Settings" in xml_settings)
    except Exception as e:
        record("flow:SETUP-opens-Settings", False, e)
else:
    record("flow:SETUP-opens-Settings", False, "SETUP not found")

# Numeric keypad and all 12 number controls.
launch()
nodes = parse_nodes(dump_xml("ui-source-main.xml"))
toggle = find_node(nodes, "Numeric keypad")
if toggle:
    try:
        tap_node(toggle)
        time.sleep(0.5)
        keypad_xml = dump_xml("ui-source-keypad.xml")
        keypad_nodes = parse_nodes(keypad_xml)
        expected = ["Key 1","Key 2","Key 3","Key 4","Key 5","Key 6","Key 7","Key 8","Key 9","Key *","Key 0","Key #"]
        for label in expected:
            n = find_node(keypad_nodes, label)
            if not n:
                record("numeric:" + label[4:], False, "Not found")
            else:
                click_and_restart("numeric:" + label[4:], n)
                launch()
                keypad_nodes = parse_nodes(dump_xml("ui-source-keypad.xml"))
                # Reopen the keypad after each restart.
                t = find_node(keypad_nodes, "Numeric keypad")
                if t:
                    tap_node(t)
                    time.sleep(0.4)
                    keypad_nodes = parse_nodes(dump_xml("ui-source-keypad.xml"))
        close = find_node(keypad_nodes, "Close numeric keypad")
        if close:
            tap_node(close)
            record("numeric:Close", True)
        else:
            record("numeric:Close", False, "Not found")
    except Exception as e:
        record("flow:numeric-keypad", False, e)
else:
    record("flow:numeric-keypad", False, "Numeric keypad button not found")

# Bottom navigation and screenshots.
for label, image in [("Remote", None), ("Apps", "ui-apps.png"), ("Cast", "ui-cast.png"), ("Settings", "ui-settings.png")]:
    launch()
    nodes = parse_nodes(dump_xml("ui-source-main.xml"))
    n = find_node(nodes, label)
    if not n:
        record("navigation:" + label, False, "Not found")
        continue
    try:
        tap_node(n)
        time.sleep(0.6)
        xml = dump_xml("ui-source-main.xml")
        ok = label in xml
        record("navigation:" + label, ok, None if ok else "Target page label not found")
        if image:
            screenshot(image)
    except Exception as e:
        record("navigation:" + label, False, e)

# Settings -> Disconnect TV.
launch()
nodes = parse_nodes(dump_xml("ui-source-main.xml"))
settings = find_node(nodes, "Settings")
if settings:
    tap_node(settings)
    time.sleep(0.5)
    settings_nodes = parse_nodes(dump_xml("ui-source-settings.xml"))
    disconnect = find_node(settings_nodes, "Disconnect TV")
    if disconnect:
        click_and_restart("navigation:Disconnect TV", disconnect)
    else:
        record("navigation:Disconnect TV", False, "Not found")
else:
    record("navigation:Disconnect TV", False, "Settings nav button not found")

# Light-mode screenshot.
set_night("no")
screenshot("ui-light.png")

failed = [x for x in RESULTS if not x["ok"]]
summary = {
    "totalChecks": len(RESULTS),
    "passed": len(RESULTS) - len(failed),
    "failed": len(failed),
    "allChecksPassed": not failed,
    "missingRequiredMainControls": missing,
    "results": RESULTS
}
Path("ui-qa-report.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
print(json.dumps(summary, indent=2, ensure_ascii=False))
if failed:
    raise SystemExit(1)
