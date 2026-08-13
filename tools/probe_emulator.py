import urllib.request

candidates = [
    "https://dl.google.com/android/repository/emulator-windows_x64-13890635.zip",  # 36.2.13?
    "https://dl.google.com/android/repository/emulator-windows_x64-13685632.zip",  # 36.2.11?
    "https://dl.google.com/android/repository/emulator-windows_x64-13496837.zip",  # 36.1.x?
    "https://dl.google.com/android/repository/emulator-windows_x64-12457405.zip",  # 35.1.4
    "https://dl.google.com/android/repository/emulator-windows_x64-12314180.zip",  # 35.1.3?
    "https://dl.google.com/android/repository/emulator-windows_x64-11237101.zip",  # 34.1.x?
    "https://dl.google.com/android/repository/emulator-windows_x64-10696886.zip",  # 33.x?
]
for u in candidates:
    try:
        req = urllib.request.Request(u, method="HEAD")
        with urllib.request.urlopen(req, timeout=30) as r:
            print(r.status, r.headers.get("Content-Length"), u)
    except Exception as e:
        print("MISS", u, str(e)[:60])
