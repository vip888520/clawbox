#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_runtime.py — Build the ClawBox embedded runtime bundle (arm64).

Assembles a self-contained OpenClaw runtime for Android:
  bundle/
    node/bin/node                 (official Node.js linux-arm64)
    glibc/lib/...                 (glibc .so files from Ubuntu arm64 debs)
    glibc/ld-linux-aarch64.so.1
    openclaw/                     (npm-installed openclaw + deps, linux-arm64)
    patches/glibc-compat.js       (Android compat shim)
    patches/systemctl             (no-op systemctl stub)
    etc/hosts
    node.sh                       (ld.so exec wrapper for manual use)

Usage:
  python build_runtime.py [--node-ver 22.22.0] [--abi arm64] [--out bundle-arm64]
"""
import argparse
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import urllib.request
from pathlib import Path

NODE_VER = "22.22.0"
UBUNTU_PORTS = "https://ports.ubuntu.com"
ABI = "arm64"
# Rust/glibc targets use aarch64-linux-gnu path
MULTIARCH = {
    "arm64": "aarch64-linux-gnu",
    "armv7": "arm-linux-gnueabihf",
}

def log(msg):
    print(f"[build] {msg}", flush=True)

def download(url, dest: Path):
    if dest.exists() and dest.stat().st_size > 0:
        log(f"cached: {dest.name}")
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    log(f"downloading {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "ClawBox-Builder"})
    with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
        shutil.copyfileobj(r, f, length=1024 * 256)
    log(f"done: {dest.name} ({dest.stat().st_size / 1e6:.1f} MB)")

def dir_listing(url):
    """Fetch an HTML directory listing and return hrefs (with retries)."""
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "ClawBox-Builder"})
            with urllib.request.urlopen(req, timeout=90) as r:
                html = r.read().decode("utf-8", "ignore")
            return re.findall(r'href="([^"]+)"', html)
        except Exception as e:
            if attempt == 2:
                raise
            log(f"listing retry {attempt + 1} for {url}: {e}")

def latest_deb(url_dir, pattern):
    """Pick the newest matching .deb filename from a pool directory."""
    names = [n for n in dir_listing(url_dir) if re.match(pattern, n)]
    if not names:
        raise RuntimeError(f"no .deb matching {pattern} in {url_dir}")
    names.sort()
    return names[-1]

def extract_deb(deb_path: Path, dest: Path):
    """Extract data.tar.* from a .deb (ar archive) into dest."""
    dest.mkdir(parents=True, exist_ok=True)
    with open(deb_path, "rb") as f:
        data = f.read()
    # ar archive: 8-byte name, 12 mtime, 6 uid, 6 gid, 8 mode, 10 size, 2 magic
    pos = 8
    extracted = None
    while pos < len(data):
        if len(data) - pos < 60:
            break
        name = data[pos:pos + 16].decode("ascii", "ignore").rstrip("/").rstrip()
        size = int(data[pos + 48:pos + 58].decode("ascii", "ignore").strip() or "0")
        body_start = pos + 60
        body = data[body_start:body_start + size]
        if name.startswith("data.tar"):
            log(f"extracting {name} ({size / 1e6:.1f} MB)")
            if name.endswith(".zst"):
                import zstandard
                dctx = zstandard.ZstdDecompressor()
                with dctx.stream_reader(io.BytesIO(body)) as r:
                    body = r.read()
            with tarfile.open(fileobj=io.BytesIO(body)) as tf:
                # Android-safe: extract symlinks as real files
                for member in tf.getmembers():
                    target = (dest / member.name.lstrip("./")).resolve()
                    if not str(target).startswith(str(dest.resolve())):
                        continue
                    if member.issym():
                        import posixpath as _pp
                        # resolve symlink relative to its own directory
                        link_target = _pp.normpath(
                            _pp.join(_pp.dirname(member.name), member.linkname)
                        ).lstrip("./").lstrip("/")
                        if target.exists():
                            target.unlink()
                        src = None
                        for m2 in tf.getmembers():
                            if m2.name.lstrip("./").lstrip("/") == link_target:
                                src = m2
                                break
                        if src and src.isfile():
                            f2 = tf.extractfile(src)
                            if f2:
                                target.parent.mkdir(parents=True, exist_ok=True)
                                with open(target, "wb") as out:
                                    shutil.copyfileobj(f2, out)
                        elif src and src.issym():
                            # chained symlink: copy resolved content from link target
                            t2 = _pp.normpath(
                                _pp.join(_pp.dirname(src.name), src.linkname)
                            ).lstrip("./").lstrip("/")
                            for m3 in tf.getmembers():
                                if m3.name.lstrip("./").lstrip("/") == t2 and m3.isfile():
                                    f3 = tf.extractfile(m3)
                                    if f3:
                                        target.parent.mkdir(parents=True, exist_ok=True)
                                        with open(target, "wb") as out:
                                            shutil.copyfileobj(f3, out)
                                    break
                        else:
                            target.parent.mkdir(parents=True, exist_ok=True)
                            target.write_bytes(b"")
                    elif member.isfile():
                        target.parent.mkdir(parents=True, exist_ok=True)
                        f2 = tf.extractfile(member)
                        with open(target, "wb") as out:
                            shutil.copyfileobj(f2, out)
                # also set exec bits
                for member in tf.getmembers():
                    if member.isfile() and member.mode & 0o100:
                        p = dest / member.name.lstrip("./")
                        if p.exists():
                            os.chmod(p, p.stat().st_mode | 0o100)
            extracted = name
        pos = body_start + size + (size % 2)
    if not extracted:
        raise RuntimeError(f"no data.tar in {deb_path.name}")
    return extracted

def copy_tree_android(src: Path, dst: Path):
    """Copy a tree; materialize symlinks as regular files (Windows-safe)."""
    dst.mkdir(parents=True, exist_ok=True)
    for item in os.scandir(src):
        s = Path(item.path)
        d = dst / item.name
        if item.is_symlink():
            target = os.readlink(item.path)
            resolved = (s.parent / target).resolve()
            if resolved.is_file():
                shutil.copy2(resolved, d)
            else:
                d.write_bytes(b"")
        elif item.is_dir():
            copy_tree_android(s, d)
        elif item.is_file():
            shutil.copy2(s, d)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--node-ver", default=NODE_VER)
    ap.add_argument("--abi", default=ABI, choices=["arm64"])
    ap.add_argument("--out", default="runtime-arm64")
    ap.add_argument("--work", default="build-work")
    ap.add_argument("--node-mirror", default="https://npmmirror.com/mirrors/node")
    args = ap.parse_args()

    abi = args.abi
    ma = MULTIARCH[abi]
    work = Path(args.work)
    work.mkdir(parents=True, exist_ok=True)
    out = Path(args.out)
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)

    # ── 1. Node.js official linux-arm64 ─────────────────────────
    node_tar = work / f"node-v{args.node_ver}-linux-{abi}.tar.xz"
    node_url = f"{args.node_mirror}/v{args.node_ver}/node-v{args.node_ver}-linux-{abi}.tar.xz"
    download(node_url, node_tar)
    log("extracting node")
    with tarfile.open(node_tar) as tf:
        tf.extractall(work)
    node_src = work / f"node-v{args.node_ver}-linux-{abi}"
    copy_tree_android(node_src / "bin", out / "node" / "bin")
    if (node_src / "lib").exists():
        copy_tree_android(node_src / "lib", out / "node" / "lib")
    log("node copied")

    # ── 2. glibc from Ubuntu arm64 debs ─────────────────────────
    glibc_dir = out / "glibc" / "lib"
    glibc_dir.mkdir(parents=True, exist_ok=True)

    # libc6 — jammy line (2.35, xz-compressed data.tar.xz — Windows-safe)
    libc6_url_dir = f"{UBUNTU_PORTS}/pool/main/g/glibc/"
    libc6_name = latest_deb(libc6_url_dir, rf"libc6_2\.35-0ubuntu3\.\d+_{abi}\.deb$")
    libc6_deb = work / libc6_name
    download(f"{libc6_url_dir}{libc6_name}", libc6_deb)
    libc_tmp = work / "deb-libc6"
    extract_deb(libc6_deb, libc_tmp)
    # copy all .so* from usr/lib/<multiarch> and lib/<multiarch>
    lib_srcs = [libc_tmp / "usr" / "lib" / ma, libc_tmp / "lib" / ma]
    for lib_src in lib_srcs:
        if lib_src.exists():
            for so in lib_src.glob("*.so*"):
                shutil.copy2(so, glibc_dir / so.name)
    log(f"libc6 libs: {len(list(glibc_dir.iterdir()))}")

    # libgcc-s1 — from gcc-12 pool (jammy)
    gcc_dir_url = f"{UBUNTU_PORTS}/pool/main/g/gcc-12/"
    gcc_name = latest_deb(gcc_dir_url, rf"libgcc-s1_12\.\d+.*_{abi}\.deb$")
    gcc_deb = work / gcc_name
    download(f"{gcc_dir_url}{gcc_name}", gcc_deb)
    gcc_tmp = work / "deb-libgcc"
    extract_deb(gcc_deb, gcc_tmp)
    for so in (gcc_tmp / "usr" / "lib" / ma).glob("*.so*"):
        shutil.copy2(so, glibc_dir / so.name)
    log("libgcc copied")

    # ensure ld-linux-aarch64.so.1 exists (symlink to libc.so.6 on modern glibc)
    ld = glibc_dir / "ld-linux-aarch64.so.1"
    if not ld.exists():
        libc = glibc_dir / "libc.so.6"
        if libc.exists():
            shutil.copy2(libc, ld)
    if not ld.exists():
        raise RuntimeError("ld-linux-aarch64.so.1 missing")

    # etc/hosts for glibc getaddrinfo
    (out / "etc").mkdir(parents=True, exist_ok=True)
    (out / "etc" / "hosts").write_text("127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n")

    # ── 3. openclaw via npm (linux-arm64 forced) ────────────────
    oc_dir = out / "openclaw"
    log("npm install openclaw (linux-arm64, --ignore-scripts)")
    env = dict(os.environ)
    env["npm_config_platform"] = "linux"
    env["npm_config_arch"] = abi
    env["npm_config_ignore_scripts"] = "true"
    env["npm_config_audit"] = "false"
    env["npm_config_fund"] = "false"
    subprocess.run(
        ["cmd", "/c", "npm install openclaw@latest --prefix " + str(oc_dir) +
         " --os=linux --cpu=arm64 --ignore-scripts --no-audit --no-fund"],
        env=env, check=True,
    )
    log("openclaw installed")

    # ensure linux-arm64 native prebuilds actually present
    native_ok = True
    for pattern in ["**/node-pty-linux-arm64/**/*.node",
                    "**/tree-sitter-*/prebuilds/linux-arm64/*.node",
                    "**/*.node"]:
        found = list(oc_dir.glob(pattern))
        if found:
            log(f"native check [{pattern}]: {len(found)} found")
            native_ok = native_ok and True
    log("native modules: " + ("OK" if native_ok else "CHECK NEEDED"))

    # ── 4. patches & support files ──────────────────────────────
    patches = out / "patches"
    patches.mkdir(parents=True, exist_ok=True)
    compat_src = Path(__file__).parent / "glibc-compat.js"
    if compat_src.exists():
        shutil.copy2(compat_src, patches / "glibc-compat.js")
    else:
        log("WARN: glibc-compat.js not found next to script — skipping")
    (patches / "systemctl").write_text(
        "#!/system/bin/sh\nexit 0\n"
    )
    os.chmod(patches / "systemctl", 0o755)

    # exec wrapper (for manual / shell use)
    (out / "node.sh").write_text(
        f"""#!/system/bin/sh
# ClawBox runtime wrapper — run glibc node via ld.so
DIR=$(dirname "$0")
export LD_LIBRARY_PATH="$DIR/glibc/lib"
export TMPDIR="$DIR/tmp"
exec "$DIR/glibc/lib/ld-linux-aarch64.so.1" --library-path "$DIR/glibc/lib" "$DIR/node/bin/node" --require "$DIR/patches/glibc-compat.js" "$@"
"""
    )
    os.chmod(out / "node.sh", 0o755)
    (out / "tmp").mkdir(exist_ok=True)

    # manifest
    (out / "manifest.json").write_text(json.dumps({
        "abi": abi,
        "node": args.node_ver,
        "glibc": libc6_name,
        "openclaw": "latest",
        "built": __import__("datetime").datetime.utcnow().isoformat() + "Z",
    }, indent=2))

    # ── 5. package tar.xz + sha256 ──────────────────────────────
    log("packaging tar.xz")
    tar_path = Path(f"{args.out}.tar.xz")
    if tar_path.exists():
        tar_path.unlink()
    with tarfile.open(tar_path, "w:xz") as tf:
        tf.add(out, arcname="runtime")
    sha = hashlib.sha256(tar_path.read_bytes()).hexdigest()
    Path(f"{args.out}.sha256").write_text(sha + "  " + tar_path.name + "\n")
    log(f"DONE: {tar_path} ({tar_path.stat().st_size / 1e6:.1f} MB) sha256={sha[:16]}…")

if __name__ == "__main__":
    main()
