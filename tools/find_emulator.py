import re
import urllib.request

url = "https://dl.google.com/android/repository/repository2-3.xml"
data = urllib.request.urlopen(url, timeout=120).read().decode("utf-8", "ignore")
# find emulator packages: <remotePackage path="emulator"> ... <revision><major>36</major>...
blocks = re.findall(r'<remotePackage[^>]*path="emulator"[^>]*>(.*?)</remotePackage>', data, re.S)
print("emulator packages:", len(blocks))
for b in blocks:
    rev = re.search(r"<revision>(.*?)</revision>", b, re.S)
    revtxt = "".join(re.findall(r"<(\w+)>([^<]+)</\1>", rev.group(1)) and [m[1] for m in re.findall(r"<(\w+)>([^<]+)</\1>", rev.group(1))]) if rev else "?"
    urlm = re.search(r'<url>([^<]+)</url>', b)
    print(revtxt, "->", urlm.group(1) if urlm else "?")
