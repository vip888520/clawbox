import urllib.request, re
url = "https://mirrors.tuna.tsinghua.edu.cn/ubuntu/pool/main/g/glibc/"
html = urllib.request.urlopen(url, timeout=60).read().decode("utf-8", "ignore")
names = re.findall(r'href="([^"]+)"', html)
cands = [n for n in names if "libc6_2.35" in n and "amd64" in n]
print(len(cands))
print(cands[:10])
