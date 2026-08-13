#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build the ClawBox embedded runtime for x86_64 (emulator testing).

Mirrors build_runtime.py but targets linux-x64 so it can run inside an
x86_64 Android emulator (arm64 bundles can't execute there). Validates the
exact same Android-specific risks: exec from app dir, SELinux /proc limits,
glibc + node + openclaw startup, pre-bundled DeepSeek plugin, lib deps.
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
import urllib.parse
import urllib.request
from pathlib import Path

NODE_VER = "22.22.0"
UBUNTU = "https://mirrors.tuna.tsinghua.edu.cn/ubuntu"
NODE_MIRROR = "https://npmmirror.com/mirrors/node"
ABI = "x64"
DEB_ARCH = "amd64"
MULTIARCH = "x86_64-linux-gnu"


def log(msg):
    print(f"[build] {msg}", flush=True)


def download(url, dest: Path):
    if dest.exists() and dest.stat().st_size > 0:
        log(f"cached: {dest.name}")
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    log(f"downloading {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "ClawBox-Builder"})
    with urllib.request.urlopen(req, timeout=180) as r, open(dest, "wb") as f:
        shutil.copyfileobj(r, f, length=1024 * 256)
    log(f"done: {dest.name} ({dest.stat().st_size / 1e6:.1f} MB)")


def dir_listing(url):
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "ClawBox-Builder"})
            with urllib.request.urlopen(req, timeout=90) as r:
                html = r.read().decode("utf-8", "ignore")
            return [urllib.parse.unquote(n) for n in re.findall(r'href="([^"]+)"', html)]
        except Exception as e:
            if attempt == 2:
                raise
            log(f"listing retry {attempt + 1} for {url}: {e}")


def latest_deb(url_dir, pattern):
    names = [n for n in dir_listing(url_dir) if re.match(pattern, n)]
    if not names:
        raise RuntimeError(f"no .deb matching {pattern} in {url_dir}")
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
            log(f"extracting {name} ({size / 1e6:.1f} MB)")
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
                for member in tf.getmembers():
                    if member.isfile() and member.mode & 0o100:
                        p = dest / member.name.lstrip("./")
                        if p.exists():
                            os.chmod(p, p.stat().st_mode | 0o100)
            break
        pos = body_start + size + (size % 2)


def copy_tree_android(src: Path, dst: Path):
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
    work = Path("build-work-x64")
    work.mkdir(parents=True, exist_ok=True)
    out = Path("runtime-x64")
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)

    # 1. Node.js official linux-x64
    node_tar = work / f"node-v{NODE_VER}-linux-x64.tar.xz"
    download(f"{NODE_MIRROR}/v{NODE_VER}/node-v{NODE_VER}-linux-x64.tar.xz", node_tar)
    log("extracting node")
    with tarfile.open(node_tar) as tf:
        tf.extractall(work)
    node_src = work / f"node-v{NODE_VER}-linux-x64"
    copy_tree_android(node_src / "bin", out / "node" / "bin")
    if (node_src / "lib").exists():
        copy_tree_android(node_src / "lib", out / "node" / "lib")
    log("node copied")

    # 2. glibc from Ubuntu amd64 debs
    glibc_dir = out / "glibc" / "lib"
    glibc_dir.mkdir(parents=True, exist_ok=True)

    libc6_url_dir = f"{UBUNTU}/pool/main/g/glibc/"
    libc6_name = latest_deb(libc6_url_dir, rf"libc6_2\.35-0ubuntu3\.\d+_{DEB_ARCH}\.deb$")
    libc6_deb = work / libc6_name
    download(f"{libc6_url_dir}{libc6_name}", libc6_deb)
    libc_tmp = work / "deb-libc6"
    extract_deb(libc6_deb, libc_tmp)
    for lib_src in (libc_tmp / "lib" / MULTIARCH,):
        if lib_src.exists():
            for so in lib_src.glob("*.so*"):
                shutil.copy2(so, glibc_dir / so.name)
    log(f"libc6 libs: {len(list(glibc_dir.iterdir()))}")

    # 3. libgcc-s1 + libstdc++6 (gcc-12)
    gcc_url_dir = f"{UBUNTU}/pool/main/g/gcc-12/"
    for pat, label in (
        (rf"libgcc-s1_12\.\d+.*_{DEB_ARCH}\.deb$", "libgcc-s1"),
        (rf"libstdc\+\+6_12\.\d+.*_{DEB_ARCH}\.deb$", "libstdc++6"),
    ):
        name = latest_deb(gcc_url_dir, pat)
        deb = work / name
        download(f"{gcc_url_dir}{name}", deb)
        tmp = work / f"deb-{label}"
        extract_deb(deb, tmp)
        for lib_src in (tmp / "usr" / "lib" / MULTIARCH, tmp / "lib" / MULTIARCH):
            if lib_src.exists():
                for so in lib_src.glob("*.so*"):
                    if so.is_file():
                        shutil.copy2(so, glibc_dir / so.name)
                        log(f"copied {so.name}")
    # materialize libstdc++.so.6 symlink
    real = glibc_dir / "libstdc++.so.6.0.30"
    if real.exists() and not (glibc_dir / "libstdc++.so.6").exists():
        shutil.copy2(real, glibc_dir / "libstdc++.so.6")
        log("materialized libstdc++.so.6")

    ld = glibc_dir / "ld-linux-x86-64.so.2"
    if not ld.exists():
        libc = glibc_dir / "libc.so.6"
        if libc.exists():
            shutil.copy2(libc, ld)
    if not ld.exists():
        raise RuntimeError("ld-linux-x86-64.so.2 missing")

    (out / "etc").mkdir(parents=True, exist_ok=True)
    (out / "etc" / "hosts").write_text("127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n")

    # 4. openclaw via npm (linux x64)
    oc_dir = out / "openclaw"
    log("npm install openclaw (linux-x64, --ignore-scripts)")
    env = dict(os.environ)
    env["npm_config_platform"] = "linux"
    env["npm_config_arch"] = "x64"
    env["npm_config_ignore_scripts"] = "true"
    env["npm_config_audit"] = "false"
    env["npm_config_fund"] = "false"
    subprocess.run(
        ["cmd", "/c", "npm install openclaw@2026.7.1 --prefix " + str(oc_dir) +
         " --os=linux --cpu=x64 --ignore-scripts --no-audit --no-fund"],
        env=env, check=True,
    )
    log("openclaw installed")

    # 5. pre-bundle the DeepSeek provider as a bundled plugin (offline startup)
    src_plugin = Path(os.environ.get(
        "DEEPSEEK_PLUGIN_SRC",
        r"C:\Users\萧龙\.openclaw\npm\projects\openclaw-deepseek-provider-2481ed984b\node_modules\@openclaw\deepseek-provider",
    ))
    if src_plugin.exists():
        dst_plugin = oc_dir / "node_modules" / "openclaw" / "dist" / "extensions" / "deepseek"
        dst_plugin.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src_plugin / "package.json", dst_plugin / "package.json")
        shutil.copy2(src_plugin / "openclaw.plugin.json", dst_plugin / "openclaw.plugin.json")
        copy_tree_android(src_plugin / "dist", dst_plugin / "dist")
        log("deepseek provider pre-bundled")
    else:
        log("WARN: deepseek provider source not found; plugin NOT pre-bundled")

    # 6. patches & support files
    patches = out / "patches"
    patches.mkdir(parents=True, exist_ok=True)
    compat_src = Path(__file__).parent / "glibc-compat.js"
    if compat_src.exists():
        shutil.copy2(compat_src, patches / "glibc-compat.js")
    (patches / "systemctl").write_text("#!/system/bin/sh\nexit 0\n")
    os.chmod(patches / "systemctl", 0o755)
    (out / "node.sh").write_text(
        f"""#!/system/bin/sh
DIR=$(dirname "$0")
export LD_LIBRARY_PATH="$DIR/glibc/lib"
export TMPDIR="$DIR/tmp"
exec "$DIR/glibc/lib/ld-linux-x86-64.so.2" --library-path "$DIR/glibc/lib" "$DIR/node/bin/node" --require "$DIR/patches/glibc-compat.js" "$@"
"""
    )
    os.chmod(out / "node.sh", 0o755)
    (out / "tmp").mkdir(exist_ok=True)
    (out / "manifest.json").write_text(json.dumps({
        "abi": "x86_64",
        "node": NODE_VER,
        "glibc": libc6_name,
        "openclaw": "2026.7.1",
        "built": __import__("datetime").datetime.utcnow().isoformat() + "Z",
    }, indent=2))

    # 7. package tar.xz + sha256
    log("packaging tar.xz")
    tar_path = Path("runtime-x64.tar.xz")
    if tar_path.exists():
        tar_path.unlink()
    with tarfile.open(tar_path, "w:xz") as tf:
        tf.add(out, arcname="runtime")
    sha = hashlib.sha256(tar_path.read_bytes()).hexdigest()
    Path("runtime-x64.sha256").write_text(sha + "  " + tar_path.name + "\n")
    log(f"DONE: {tar_path} ({tar_path.stat().st_size / 1e6:.1f} MB) sha256={sha[:16]}...")


if __name__ == "__main__":
    main()
