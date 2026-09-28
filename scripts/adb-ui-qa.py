#!/usr/bin/env python3
import json, re, subprocess, time
from pathlib import Path

PKG="com.tomasthrawat.wifitvremote"
RESULTS=[]

def adb(*args, check=True, timeout=12):
    return subprocess.run(["adb",*args],text=True,capture_output=True,check=check,timeout=timeout)

def launch():
    adb("shell","am","force-stop",PKG,check=False)
    adb("shell","monkey","-p",PKG,"1",check=False)
    time.sleep(0.8)

def dump(path):
    p=adb("exec-out","uiautomator","dump","/dev/tty",check=True)
    xml=p.stdout
    Path(path).write_text(xml,encoding="utf-8")
    return xml

def nodes(xml):
    out=[]
    for tag in re.findall(r"<node\b[^>]*>",xml):
        if 'clickable="true"' not in tag or 'enabled="true"' not in tag:
            continue
        desc=re.search(r'content-desc="([^"]*)"',tag)
        text=re.search(r'text="([^"]*)"',tag)
        bounds=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',tag)
        if not bounds: continue
        label=(desc.group(1) if desc else "") or (text.group(1) if text else "")
        if not label: continue
        x1,y1,x2,y2=map(int,bounds.groups())
        out.append((label,(x1+x2)//2,(y1+y2)//2))
    return out

def find(xml,label):
    for n in nodes(xml):
        if n[0]==label: return n
    for n in nodes(xml):
        if label.lower() in n[0].lower(): return n
    return None

def tap(n):
    adb("shell","input","tap",str(n[1]),str(n[2]))
    time.sleep(0.25)

def long_press(n):
    adb("shell","input","swipe",str(n[1]),str(n[2]),str(n[1]),str(n[2]),"900")
    time.sleep(0.35)

def assert_text(label, path="ui-source-main.xml"):
    xml=dump(path)
    if not find(xml,label): raise AssertionError("Not visible: "+label)

def check(name,fn):
    try: fn(); RESULTS.append({"name":name,"ok":True})
    except Exception as e: RESULTS.append({"name":name,"ok":False,"error":str(e)})

def click_restart(name,label):
    launch()
    xml=dump("ui-source-main.xml")
    n=find(xml,label)
    if not n: RESULTS.append({"name":name,"ok":False,"error":"Not found: "+label}); return
    check(name,lambda:tap(n))

# Versions and initial screenshots.
subprocess.run(["appium","--version"],text=True,stdout=open("appium-version.txt","w"),stderr=subprocess.STDOUT)
subprocess.run(["agent-device","--version"],text=True,stdout=open("agent-device-version.txt","w"),stderr=subprocess.STDOUT)
launch()
adb("shell","cmd","uimode","night","yes",check=False)
launch()
subprocess.run(["adb","exec-out","screencap","-p"],stdout=open("ui-dark.png","wb"),check=True)
xml=dump("ui-source-main.xml")

main_labels=[
"Power","Mute","SETUP","SOURCE","▲","◀","▶","▼","OK",
"EXIT","CH-LIST","Back","Home","PLAY","BACK",
"Volume up","Mute","Channel menu","Volume down","Channel up","Channel down",
"Numeric keypad","Empty app shortcut 1","Empty app shortcut 2","Empty app shortcut 3"
]
# Tap each required main control. Duplicate Mute is intentional: it is the center volume control in this UI.
seen=set()
for label in main_labels:
    if label.startswith("Empty app shortcut"):
        continue
    if (label) in seen and label=="Mute": continue
    seen.add(label)
    click_restart("main:"+label,label)

for index in range(1,4):
    label=f"Empty app shortcut {index}"
    launch(); xml=dump("ui-source-main.xml"); n=find(xml,label)
    if not n:
        RESULTS.append({"name":f"shortcut:{index}:visible","ok":False,"error":"Not found: "+label})
        continue
    check(f"shortcut:{index}:tap opens editor",lambda n=n:tap(n))
    check(f"shortcut:{index}:editor visible",lambda index=index:assert_text(f"App shortcut {index}"))
    cancel=find(dump("ui-source-main.xml"),"Cancel")
    if cancel: tap(cancel)
    else: RESULTS.append({"name":f"shortcut:{index}:cancel","ok":False,"error":"Cancel not found"})

    launch(); xml=dump("ui-source-main.xml"); n=find(xml,label)
    check(f"shortcut:{index}:long press opens editor",lambda n=n:long_press(n))
    check(f"shortcut:{index}:long press editor visible",lambda index=index:assert_text(f"App shortcut {index}"))
    cancel=find(dump("ui-source-main.xml"),"Cancel")
    if cancel: tap(cancel)
    else: RESULTS.append({"name":f"shortcut:{index}:long-press-cancel","ok":False,"error":"Cancel not found"})

# Explicit keypad flow.
launch(); xml=dump("ui-source-main.xml"); toggle=find(xml,"Numeric keypad")
if not toggle: RESULTS.append({"name":"keypad:open","ok":False,"error":"Numeric keypad not found"})
else:
    tap(toggle); time.sleep(0.4); kxml=dump("ui-source-keypad.xml")
    for label in ["Key 1","Key 2","Key 3","Key 4","Key 5","Key 6","Key 7","Key 8","Key 9","Key *","Key 0","Key #"]:
        n=find(kxml,label)
        if not n: RESULTS.append({"name":"numeric:"+label[4:],"ok":False,"error":"Not found"})
        else:
            check("numeric:"+label[4:],lambda n=n:tap(n))
    n=find(kxml,"Close numeric keypad")
    if n: check("numeric:Close",lambda:tap(n))
    else: RESULTS.append({"name":"numeric:Close","ok":False,"error":"Not found"})

# SETUP -> Settings and bottom navigation.
for label in ["SETUP","Remote","Apps","Cast","Settings"]:
    click_restart("navigation:"+label,label)

# Disconnect from Settings.
launch(); xml=dump("ui-source-main.xml"); n=find(xml,"Settings")
if n:
    tap(n); time.sleep(0.4); sx=dump("ui-source-settings.xml"); d=find(sx,"Disconnect TV")
    if d: check("navigation:Disconnect TV",lambda:tap(d))
    else: RESULTS.append({"name":"navigation:Disconnect TV","ok":False,"error":"Not found"})
else: RESULTS.append({"name":"navigation:Disconnect TV","ok":False,"error":"Settings nav not found"})

# Light screenshot.
adb("shell","cmd","uimode","night","no",check=False); launch()
subprocess.run(["adb","exec-out","screencap","-p"],stdout=open("ui-light.png","wb"),check=True)

failed=[r for r in RESULTS if not r["ok"]]
report={"totalChecks":len(RESULTS),"passed":len(RESULTS)-len(failed),"failed":len(failed),"allChecksPassed":not failed,"results":RESULTS}
Path("ui-qa-report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
print(json.dumps(report,ensure_ascii=False,indent=2))
if failed: raise SystemExit(1)
