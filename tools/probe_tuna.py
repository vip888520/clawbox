import urllib.request, re
html = urllib.request.urlopen("https://mirrors.tuna.tsinghua.edu.cn/msys2/", timeout=60).read().decode("utf-8", "ignore")
print([n for n in re.findall(r'href="([^"]+)"', html) if not n.startswith("?")][:20])
