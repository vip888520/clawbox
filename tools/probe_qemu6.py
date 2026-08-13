import urllib.request, re
html = urllib.request.urlopen("https://qemu.weilnetz.de/w64/2024/", timeout=60).read().decode("utf-8", "ignore")
names = re.findall(r'href="([^"]+)"', html)
print([n for n in names if "qemu" in n.lower()][:20])
