import urllib.request
cands = [
    "https://mirrors.nju.edu.cn/msys2/mingw/ucrt64/ucrt64.db",
    "https://mirror.sjtu.edu.cn/msys2/mingw/ucrt64/ucrt64.db",
    "https://mirrors.tuna.tsinghua.edu.cn/msys2/mingw/ucrt64/ucrt64.db",
    "https://mirrors.ustc.edu.cn/msys2/mingw/ucrt64/ucrt64.db",
    "https://mirror.msys2.org/mingw/ucrt64/ucrt64.db",
]
for u in cands:
    try:
        r = urllib.request.urlopen(u, timeout=30)
        print(r.status, u)
    except Exception as e:
        print("ERR", u, str(e)[:50])
