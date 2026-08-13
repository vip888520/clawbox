import re

dts_path = r"C:\Temp\clawbox-arm27\virt.dts"
src = open(dts_path, encoding="utf-8", errors="replace").read()

node = """
	firmware {
		android {
			compatible = "android,firmware";
			fstab {
				compatible = "android,fstab";
				system {
					compatible = "android,system";
					dev = "/dev/block/vda";
					type = "ext4";
					mnt_flags = "ro";
					fs_mgr_flags = "wait,first_stage_mount";
				};
				vendor {
					compatible = "android,vendor";
					dev = "/dev/block/vdd";
					type = "ext4";
					mnt_flags = "ro";
					fs_mgr_flags = "wait,first_stage_mount";
				};
				cache {
					compatible = "android,cache";
					dev = "/dev/block/vdb";
					type = "ext4";
					mnt_flags = "noatime,nosuid,nodev,nomblk_io_submit,errors=panic";
					fs_mgr_flags = "wait";
				};
				data {
					compatible = "android,data";
					dev = "/dev/block/vdc";
					type = "ext4";
					mnt_flags = "noatime,nosuid,nodev,nomblk_io_submit,errors=panic";
					fs_mgr_flags = "wait,check,first_stage_mount";
				};
			};
		};
	};
"""

# remove existing firmware node
src = re.sub(r"\n\tfirmware \{.*?\n\t\};", "", src, count=1, flags=re.S)

# insert before the first top-level subnode (psci {) which follows root properties
idx = src.find("\n\tpsci {")
assert idx >= 0, "psci node not found"
out = src[:idx] + "\n" + node + src[idx:]
open(dts_path, "w", encoding="utf-8").write(out)
print("firmware node relocated before psci")
