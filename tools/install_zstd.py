import urllib.request, re, zipfile, sys, os, pathlib

html = urllib.request.urlopen('https://pypi.org/simple/zstandard/', timeout=60).read().decode()
urls = re.findall(r'href="([^"]+\.whl[^"]*)"', html)
cand = [u for u in urls if 'cp311' in u and 'win_amd64' in u]
print('candidates:', len(cand))
u = cand[-1]
print('using:', u.split('/')[-1])
data = urllib.request.urlopen(u, timeout=120).read()
whl = os.path.join(os.environ['TEMP'], 'zstd.whl')
open(whl, 'wb').write(data)
sp = pathlib.Path(sys.executable).parent / 'Lib' / 'site-packages'
print('site-packages:', sp)
with zipfile.ZipFile(whl) as z:
    z.extractall(sp)
import zstandard
print('zstandard OK', zstandard.__version__)
