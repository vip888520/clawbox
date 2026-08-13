import urllib.request, re, collections
html = urllib.request.urlopen("https://mirrors.tuna.tsinghua.edu.cn/msys2/mingw/ucrt64/", timeout=120).read().decode("utf-8", "ignore")
names = [n for n in re.findall(r'href="([^"]+)"', html) if "qemu" in n]
print("qemu files:", len(names))
vers = collections.Counter()
for n in names:
    m = re.search(r"qemu-(\d+\.\d+\.\d+)", n)
    if m:
        vers[m.group(1)] += 1
for v in sorted(vers):
    print(v, vers[v])
