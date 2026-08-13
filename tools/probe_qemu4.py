import urllib.request, re
html = urllib.request.urlopen("https://qemu.weilnetz.de/w64/", timeout=60).read().decode("utf-8", "ignore")
names = re.findall(r'href="([^"]+)"', html)
print("total entries:", len(names))
print(names[:25])
