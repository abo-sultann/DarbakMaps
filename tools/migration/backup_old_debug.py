#!/usr/bin/env python3
"""Create a migration ZIP from the currently installed legacy debug DarbakMaps app.

Requires adb and an installed debuggable package com.abosultan.darbakmaps.debug.
No application uninstall is performed by this tool.
"""
import io
import os
import subprocess
import sys
import tarfile
import zipfile
from pathlib import Path

PACKAGE = "com.abosultan.darbakmaps.debug"
OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "DarbakMaps-Legacy-Backup.zip").resolve()


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def main():
    adb("get-state")
    probe = adb("shell", "run-as", PACKAGE, "sh", "-c", "id && test -d shared_prefs && test -d files", check=False)
    if probe.returncode != 0:
        sys.stderr.write("تعذر استخدام adb run-as. لا تحذف التطبيق القديم.\n")
        sys.stderr.write(probe.stderr.decode("utf-8", "replace"))
        return 2

    proc = subprocess.run(
        ["adb", "exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "shared_prefs", "files/tracks"],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if proc.returncode != 0 or len(proc.stdout) < 1024:
        sys.stderr.write("فشل نسخ بيانات التطبيق. لا تحذف التطبيق القديم.\n")
        sys.stderr.write(proc.stderr.decode("utf-8", "replace"))
        return 3

    allowed = []
    with tarfile.open(fileobj=io.BytesIO(proc.stdout), mode="r:*") as tar:
        for member in tar.getmembers():
            name = member.name.lstrip("./")
            if member.isfile() and (name.startswith("shared_prefs/") or name.startswith("files/tracks/")):
                if ".." in Path(name).parts or name.startswith("/"):
                    raise RuntimeError("Unsafe archive member")
                allowed.append((name, tar.extractfile(member).read()))

    if not any(name == "shared_prefs/darbak_places.xml" for name, _ in allowed):
        sys.stderr.write("لم يتم العثور على darbak_places.xml؛ لن أعتبر النسخ صالحًا.\n")
        return 4

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("migration/version.txt", "1\n")
        z.writestr("migration/package.txt", PACKAGE + "\n")
        for name, data in allowed:
            z.writestr(name, data)

    with zipfile.ZipFile(OUT, "r") as z:
        bad = z.testzip()
        if bad:
            raise RuntimeError(f"Backup verification failed at {bad}")
        tracks = [n for n in z.namelist() if n.startswith("files/tracks/") and n.endswith(".gpx")]
        print(f"تم إنشاء النسخة: {OUT}")
        print(f"عدد ملفات SharedPreferences: {len([n for n in z.namelist() if n.startswith('shared_prefs/')])}")
        print(f"عدد ملفات GPX: {len(tracks)}")
        print("احتفظ بالملف ثم استورده من Darbak Maps الجديد قبل حذف أي نسخة احتياطية.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
