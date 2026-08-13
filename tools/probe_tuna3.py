import urllib.request, re
for url in [
    "https://mirrors.tuna.tsinghua.edu.cn/msys2/msys/x86_64/",
    "https://mirrors.tuna.tsinghua.edu.cn/msys2/mingw/x86_64/",
    "https://mirrors.tuna.tsinghua.edu.cn/msys2/mingw/ucrt64/",
    "https://mirrors.tuna.tsinghua.edu.cn/msys2/mingw/clang64/",
]:
    try:
        html = urllib.request.urlopen(url, timeout=60).read().decode("utf-8", "ignore")
        names = [n for n in re.findall(r'href="([^"]+)"', html) if n.endswith(".db") or n.endswith("desc") or "qemu" in n]
        print(url.split("msys2/")[1], "->", names[:4])
    except Exception as e:
        print(url.split("msys2/")[1], "ERR", str(e)[:60])
