import json
import urllib.request

data = json.loads(urllib.request.urlopen("https://pypi.org/pypi/pefile/json", timeout=60).read())
ver = data["info"]["version"]
url = data["urls"][0]["url"]
print("pefile", ver, url)
urllib.request.urlretrieve(url, r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools\pefile-pkg.tar.gz")
print("downloaded")
