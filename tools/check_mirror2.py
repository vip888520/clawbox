import urllib.request, re
url = "https://mirrors.tuna.tsinghua.edu.cn/ubuntu/pool/main/g/gcc-12/"
html = urllib.request.urlopen(url, timeout=60).read().decode("utf-8", "ignore")
names = re.findall(r'href="([^"]+)"', html)
cands = [n for n in names if "libstdc" in n and "amd64" in n]
print(len(cands))
print(cands[:10])
