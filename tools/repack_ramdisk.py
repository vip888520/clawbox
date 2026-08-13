#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Repack Android ramdisk cpio (newc) preserving unix modes; inject fstab.ranchu."""
import gzip
import io
import os
import struct

SRC_CPIO = r"C:\Temp\clawbox-arm\ramdisk.cpio"
OUT_IMG = r"C:\Temp\clawbox-arm\ramdisk2.img"

FSTAB = """# Android fstab for ClawBox QEMU virt (arm64)
/dev/block/vda /system ext4 ro wait,first_stage_mount
/dev/block/vdb /vendor ext4 ro wait,first_stage_mount
/dev/block/vdc /data ext4 wait,check,first_stage_mount
/dev/block/vdd /cache ext4 wait,check
"""


def parse_newc(data):
    entries = []  # (name, mode, data)
    pos = 0
    n = len(data)
    while pos + 110 <= n:
        hdr = data[pos:pos + 110]
        if hdr[0:6] != b"070701":
            break
        vals = [int(hdr[6 + i * 8:14 + i * 8], 16) for i in range(13)]
        namesize = vals[11]
        filesize = vals[6]
        mode = vals[1]
        name = data[pos + 110:pos + 110 + namesize - 1].decode("utf-8", "replace")
        data_start = pos + 110 + namesize
        if data_start % 2:
            data_start += 1
        filedata = data[data_start:data_start + filesize]
        entries.append((name, mode, filedata))
        pos = data_start + filesize
        if pos % 4:
            pos += 4 - (pos % 4)
        if name == "TRAILER!!!":
            break
    return entries


def build_newc(entries):
    out = bytearray()
    for name, mode, filedata in entries:
        nameb = name.encode("utf-8") + b"\x00"
        namesize = len(nameb)
        filesize = len(filedata)
        vals = [0, mode, 0, 0, 1, 0, filesize, 0, 0, 0, 0, namesize, 0]
        hdr = b"070701" + b"".join(b"%08x" % v for v in vals)
        out += hdr
        out += nameb
        if len(nameb) % 2:
            out += b"\x00"
        out += filedata
        out += b"\x00" * ((4 - filesize % 4) % 4)
    nameb = b"TRAILER!!!\x00"
    vals = [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, len(nameb), 0]
    hdr = b"070701" + b"".join(b"%08x" % v for v in vals)
    out += hdr
    out += nameb
    out += b"\x00" * ((4 - len(nameb) % 4) % 4)
    return bytes(out)


def main():
    with open(SRC_CPIO, "rb") as f:
        data = f.read()
    entries = parse_newc(data)
    print("parsed entries:", len(entries))

    # normalize modes: dirs 755, files: init 755, others 644
    for i, (name, mode, filedata) in enumerate(entries):
        if name == "TRAILER!!!":
            continue
        if mode & 0o170000 == 0o040000:  # dir
            mode = 0o40755
        elif name in ("init",):
            mode = 0o100755
        else:
            mode = 0o100644
        entries[i] = (name, mode, filedata)

    # inject fstab.ranchu
    if not any(n == "fstab.ranchu" for n, _, _ in entries):
        entries.insert(1, ("fstab.ranchu", 0o100644, FSTAB.encode()))
        print("injected fstab.ranchu")

    cpio = build_newc(entries)
    with open(OUT_IMG, "wb") as f:
        f.write(gzip.compress(cpio, compresslevel=1))
    print("wrote", OUT_IMG, os.path.getsize(OUT_IMG), "bytes")


if __name__ == "__main__":
    main()
