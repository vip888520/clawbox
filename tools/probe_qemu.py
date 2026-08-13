import urllib.request
for v in ["2024/qemu-w64-8.2.6.zip", "2024/qemu-w64-8.2.5.zip", "2024/qemu-w64-8.2.4.zip", "2023/qemu-w64-8.1.3.zip", "2023/qemu-w64-8.0.4.zip"]:
    url = "https://qemu.weilnetz.de/w64/" + v
    try:
        req = urllib.request.Request(url, method="HEAD")
        with urllib.request.urlopen(req, timeout=30) as r:
            print(r.status, r.headers.get("Content-Length"), url)
    except Exception as e:
        print("MISS", url, str(e)[:40])
