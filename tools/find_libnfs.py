import urllib.request, re
html = urllib.request.urlopen("https://mirrors.tuna.tsinghua.edu.cn/msys2/mingw/ucrt64/", timeout=120).read().decode("utf-8", "ignore")
names = [n for n in re.findall(r'href="([^"]+)"', html) if "libnfs" in n and n.endswith(".pkg.tar.zst")]
print(len(names))
for n in names[:12]:
    print(n)
