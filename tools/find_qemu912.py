import urllib.request, re
html = urllib.request.urlopen("https://mirrors.tuna.tsinghua.edu.cn/msys2/mingw/ucrt64/", timeout=120).read().decode("utf-8", "ignore")
names = [n for n in re.findall(r'href="([^"]+)"', html) if "qemu-9.1.2" in n and n.endswith(".pkg.tar.zst")]
print(names)
