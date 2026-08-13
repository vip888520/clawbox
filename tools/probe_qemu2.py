import urllib.request, re
for base in ["https://qemu.weilnetz.de/w64/", "https://qemu.weilnetz.de/w64/2024/"]:
    try:
        html = urllib.request.urlopen(base, timeout=60).read().decode("utf-8", "ignore")
        names = re.findall(r'href="([^"]+)"', html)
        zips = [n for n in names if "qemu-w64" in n and n.endswith(".zip")]
        print(base, "->", zips[:6])
    except Exception as e:
        print(base, "ERR", str(e)[:80])
