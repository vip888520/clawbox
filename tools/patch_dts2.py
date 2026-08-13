import re

dts_path = r"C:\Temp\clawbox-arm27\virt.dts"
src = open(dts_path, encoding="utf-8", errors="replace").read()
src = src.replace('fs_mgr_flags = "wait,check,first_stage_mount";', 'fs_mgr_flags = "wait,first_stage_mount";')
open(dts_path, "w", encoding="utf-8").write(src)
print("removed check flag from data")
