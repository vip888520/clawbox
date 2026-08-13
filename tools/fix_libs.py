#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Add missing libstdc++.so.6 / libgcc_s.so.1 to the ClawBox runtime bundle.

Node.js official linux-arm64 binaries need libstdc++ (V8) and libgcc_s.
The original build_runtime.py only pulled libc6 + libgcc-s1 but extracted
libgcc_s from the wrong path, so both were missing -> node crashed on device.
"""
import io
import re
import shutil
import sys
import tarfile
import urllib.request
from pathlib import Path

PORTS = "https://ports.ubuntu.com"
WORK = Path(r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools\build-work-fix")
GLIBC_LIB = Path(r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools\runtime-arm64\glibc\lib")
MULTIARCH = "aarch64-linux-gnu"


def download(url, dest: Path):
    if dest.exists() and dest.stat().st_size > 0:
        print(f"cached: {dest.name}", flush=True)
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    print(f"downloading {url}", flush=True)
    req = urllib.request.Request(url, headers={"User-Agent": "ClawBox-Builder"})
    with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
        shutil.copyfileobj(r, f, length=1024 * 256)
    print(f"done: {dest.name} ({dest.stat().st_size/1e6:.1f} MB)", flush=True)


def dir_listing(url):
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "ClawBox-Builder"})
            with urllib.request.urlopen(req, timeout=90) as r:
                html = r.read().decode("utf-8", "ignore")
            return re.findall(r'href="([^"]+)"', html)
        except Exception as e:
            if attempt == 2:
                raise
            print(f"retry {attempt+1}: {e}", flush=True)


def latest_deb(url_dir, pattern):
    names = [n for n in dir_listing(url_dir) if re.match(pattern, n)]
    if not names:
        raise RuntimeError(f"no deb matching {pattern}")
    names.sort()
    return names[-1]


def extract_deb(deb_path: Path, dest: Path):
    dest.mkdir(parents=True, exist_ok=True)
    with open(deb_path, "rb") as f:
        data = f.read()
    pos = 8
    while pos < len(data):
        if len(data) - pos < 60:
            break
        name = data[pos:pos + 16].decode("ascii", "ignore").rstrip("/").rstrip()
        size = int(data[pos + 48:pos + 58].decode("ascii", "ignore").strip() or "0")
        body_start = pos + 60
        body = data[body_start:body_start + size]
        if name.startswith("data.tar"):
            print(f"extracting {name} ({size/1e6:.1f} MB)", flush=True)
            if name.endswith(".zst"):
                import zstandard
                dctx = zstandard.ZstdDecompressor()
                with dctx.stream_reader(io.BytesIO(body)) as r:
                    body = r.read()
            with tarfile.open(fileobj=io.BytesIO(body)) as tf:
                for member in tf.getmembers():
                    if not member.isfile():
                        continue
                    target = (dest / member.name.lstrip("./")).resolve()
                    if not str(target).startswith(str(dest.resolve())):
                        continue
                    target.parent.mkdir(parents=True, exist_ok=True)
                    f2 = tf.extractfile(member)
                    if f2:
                        with open(target, "wb") as out:
                            shutil.copyfileobj(f2, out)
            break
        pos = body_start + size + (size % 2)


def main():
    # libstdc++6 (jammy, gcc-12)
    d1 = f"{PORTS}/pool/main/g/gcc-12/"
    n1 = latest_deb(d1, r"libstdc\+\+6_12\.\d+.*_arm64\.deb$")
    download(f"{d1}{n1}", WORK / n1)
    t1 = WORK / "deb-libstdcxx"
    extract_deb(WORK / n1, t1)
    src1 = t1 / "usr" / "lib" / MULTIARCH
    for so in src1.glob("libstdc++.so.6*"):
        if so.is_file():
            shutil.copy2(so, GLIBC_LIB / so.name)
            print(f"copied {so.name} ({so.stat().st_size/1e3:.0f} KB)", flush=True)
    # deb ships libstdc++.so.6 as a symlink; materialize it as a real file
    real = GLIBC_LIB / "libstdc++.so.6.0.30"
    if real.exists() and not (GLIBC_LIB / "libstdc++.so.6").exists():
        shutil.copy2(real, GLIBC_LIB / "libstdc++.so.6")
        print("materialized libstdc++.so.6 -> libstdc++.so.6.0.30", flush=True)

    # libgcc-s1 (jammy, gcc-12)
    n2 = latest_deb(d1, r"libgcc-s1_12\.\d+.*_arm64\.deb$")
    download(f"{d1}{n2}", WORK / n2)
    t2 = WORK / "deb-libgcc"
    extract_deb(WORK / n2, t2)
    for cand in (t2 / "usr" / "lib" / MULTIARCH, t2 / "lib" / MULTIARCH):
        if cand.exists():
            for so in cand.glob("libgcc_s.so.1*"):
                shutil.copy2(so, GLIBC_LIB / so.name)
                print(f"copied {so.name} ({so.stat().st_size/1e3:.0f} KB)", flush=True)

    # verify
    for name in ("libstdc++.so.6", "libgcc_s.so.1"):
        p = GLIBC_LIB / name
        print(f"{'OK ' if p.exists() else 'MISSING'} {name}", flush=True)
        if not p.exists():
            sys.exit(1)


if __name__ == "__main__":
    main()
