import urllib.request, re
for sub in ["mingw/", "msys/", "mingw/ucrt64/", "mingw/clang64/"]:
    try:
        html = urllib.request.urlopen("https://mirrors.tuna.tsinghua.edu.cn/msys2/" + sub, timeout=60).read().decode("utf-8", "ignore")
        names = [n for n in re.findall(r'href="([^"]+)"', html) if n not in ("../", "?C=N;O=D", "?C=M;O=A", "?C=S;O=A", "?C=D;O=A")]
        print(sub, "->", names[:6])
    except Exception as e:
        print(sub, "ERR", str(e)[:60])
