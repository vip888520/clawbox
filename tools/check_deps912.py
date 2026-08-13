import sys
sys.path.insert(0, r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools\pefile-pkg")
import pefile, os

exe = r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools\msys64\ucrt64\bin\qemu-system-aarch64.exe"
bin_dir = r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools\msys64\ucrt64\bin"
pe = pefile.PE(exe, fast_load=True)
pe.parse_data_directories(directories=[pefile.DIRECTORY_ENTRY["IMAGE_DIRECTORY_ENTRY_IMPORT"]])
dlls = sorted(set(e.dll.decode() for e in getattr(pe, "DIRECTORY_ENTRY_IMPORT", [])))
missing = []
for d in dlls:
    if d.lower().startswith(("kernel32", "user32", "advapi32", "shell32", "ole32", "ws2_32", "crypt32", "bcrypt", "winmm", "setupapi", "shlwapi", "rpcrt4", "secur32", "winhttp", "iphlpapi", "mfplat", "d3d9", "api-ms-win", "msvcp", "vcruntime", "concrt")):
        continue
    if not os.path.exists(os.path.join(bin_dir, d)):
        missing.append(d)
print("missing DLLs:", missing)
