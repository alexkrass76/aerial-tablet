#!/usr/bin/env python3
"""Install on an explicitly selected tablet and preserve a reversible HOME transition."""
import argparse
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("serial", help="Serial reported by adb devices")
parser.add_argument("--port", default="5037")
parser.add_argument("--restore", action="store_true")
args = parser.parse_args()
project = Path(__file__).resolve().parent
state = project / ".local-state" / "before.json"
adb = ["adb", "-P", args.port, "-s", args.serial]
home = "app.aerial.tablet/.HomeActivity"
kid_service = "hk.ithkservice.kidlock/.KidModeProtectionService"

def run(*parts):
    return subprocess.check_output(adb + list(parts), text=True).strip()

def services():
    value = run("shell", "settings", "get", "secure", "enabled_accessibility_services")
    return [] if value in ("null", "") else value.split(":")

if args.restore:
    old = json.loads(state.read_text())
    if old["serial"] != args.serial:
        raise SystemExit("Backup belongs to a different tablet")
    print(run("shell", "cmd", "package", "set-home-activity", old["home"]))
    current = services()
    if kid_service in old["accessibility"] and kid_service not in current:
        current.append(kid_service)
        print(run("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(current)))
        print(run("shell", "settings", "put", "secure", "accessibility_enabled", "1"))
    print(run("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"))
    raise SystemExit(0)

apk = project / "build" / "aerial-tablet.apk"
if not apk.exists():
    raise SystemExit("Build the APK first: ./build.sh")
if not state.exists():
    previous_home = run("shell", "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME").splitlines()[-1]
    if "/" not in previous_home or previous_home == home:
        raise SystemExit("Cannot identify the previous HOME; refusing to replace it without a backup")
    state.parent.mkdir(exist_ok=True)
    state.write_text(json.dumps({"serial": args.serial, "home": previous_home, "accessibility": services()}, indent=2))
    state.chmod(0o600)
else:
    if json.loads(state.read_text())["serial"] != args.serial:
        raise SystemExit("Existing backup belongs to a different tablet")
print(run("install", "-r", str(apk)))
print(run("shell", "cmd", "package", "set-home-activity", home))
current = services()
if kid_service in current:
    current.remove(kid_service)
    if current:
        print(run("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(current)))
    else:
        print(run("shell", "settings", "delete", "secure", "enabled_accessibility_services"))
print(run("shell", "am", "start", "-n", home))
print("Backup:", state)
