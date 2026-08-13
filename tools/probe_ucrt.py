import urllib.request, re
url = "https://mirrors.tuna.tsinghua.edu.cn/msys2/mingw/ucrt64/"
html = urllib.request.urlopen(url, timeout=60).read().decode("utf-8", "ignore")
names = [n for n in re.findall(r'href="([^"]+)"', html) if not n.startswith("?") and n != "../"]
print("total:", len(names))
print(names[:12])
print("db files:", [n for n in names if n.endswith(".db")])
