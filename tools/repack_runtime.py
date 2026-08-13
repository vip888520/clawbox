#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Repack the ClawBox runtime bundle into runtime-arm64.tar.xz (+ sha256)."""
import hashlib
import os
import shutil
import sys
import tarfile
from pathlib import Path

BUNDLE = Path(r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools\runtime-arm64")
OUT = Path(r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools\runtime-arm64.tar.xz")
SHA = Path(r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools\runtime-arm64.sha256")

def main():
    if OUT.exists():
        OUT.unlink()
    print(f"packaging {BUNDLE} -> {OUT}", flush=True)
    with tarfile.open(OUT, "w:xz") as tf:
        tf.add(BUNDLE, arcname="runtime")
    size = OUT.stat().st_size
    sha = hashlib.sha256(OUT.read_bytes()).hexdigest()
    SHA.write_text(f"{sha}  {OUT.name}\n")
    print(f"DONE: {OUT.name} ({size/1e6:.1f} MB) sha256={sha[:16]}...", flush=True)

if __name__ == "__main__":
    main()
