import urllib.request, re
for year in ["2023", "2024", "2025", "2026"]:
    try:
        html = urllib.request.urlopen(f"https://qemu.weilnetz.de/w64/{year}/", timeout=60).read().decode("utf-8", "ignore")
        names = re.findall(r'href="([^"]+)"', html)
        zips = [n for n in names if "qemu-w64" in n and n.endswith(".zip")]
        print(year, "->", zips[:6])
    except Exception as e:
        print(year, "ERR", str(e)[:60])
