import sys
sys.path.insert(0, r"C:\Users\萧龙\.openclaw\workspace\clawbox\tools\pefile-pkg")
import pefile

exe = r"C:\Users\萧龙\AppData\Local\Android\Sdk\emulator\qemu\windows-x86_64\qemu-system-aarch64.exe"
pe = pefile.PE(exe, fast_load=True)
pe.parse_data_directories(directories=[pefile.DIRECTORY_ENTRY["IMAGE_DIRECTORY_ENTRY_IMPORT"]])
dlls = sorted(set(e.dll.decode() for e in getattr(pe, "DIRECTORY_ENTRY_IMPORT", [])))
print("imported DLLs (%d):" % len(dlls))
for d in dlls:
    print(" ", d)
