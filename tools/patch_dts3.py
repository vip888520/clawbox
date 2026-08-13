import re

dts_path = r"C:\Temp\clawbox-arm27\virt.dts"
src = open(dts_path, encoding="utf-8", errors="replace").read()
# remove the data subnode block
src = re.sub(r"\n\t\t\t\tdata \{.*?\n\t\t\t\t\};", "", src, count=1, flags=re.S)
open(dts_path, "w", encoding="utf-8").write(src)
print("removed data node; data present:", "data {" in src)
