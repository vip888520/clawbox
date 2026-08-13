import sys
sys.path.insert(0, r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools")
import pefile

exe = r"C:\Users\萧龙\AppData\Local\Android\Sdk\emulator\qemu\windows-x86_64\qemu-system-aarch64.exe"
pe = pefile.PE(exe, fast_load=True)
pe.parse_data_directories(directories=[pefile.DIRECTORY_ENTRY["IMAGE_DIRECTORY_ENTRY_IMPORT"]])
dlls = set()
for entry in getattr(pe, "DIRECTORY_ENTRY_IMPORT", []):
    dlls.add(entry.dll.decode())
print("imported DLLs:")
for d in sorted(dlls):
    print(" ", d)
