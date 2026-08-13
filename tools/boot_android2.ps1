$img = 'C:\Users\萧龙\AppData\Local\Android\Sdk\system-images\android-28\google_apis\arm64-v8a'
$avd = "$env:USERPROFILE\.android\avd\clawboxarm.avd"
$q = 'C:\Users\萧龙\.openclaw\workspace\clawbox\tools\msys64\ucrt64\bin'
$env:PATH = "$q;" + $env:PATH
$env:QEMU_AUDIO_DRV = 'none'
$serial = 'C:\Users\萧龙\.openclaw\workspace\clawbox\tools\android-serial.log'
if (Test-Path $serial) { Remove-Item $serial }

$cmd = "`"$q\qemu-system-aarch64.exe`" -M virt -cpu cortex-a53 -smp 4 -m 2048 " +
  "-kernel `"$img\kernel-ranchu`" -initrd `"$img\ramdisk.img`" " +
  "-drive `"if=none,id=system,file=$img\system.img,format=raw`" -device virtio-blk-device,drive=system " +
  "-drive `"if=none,id=vendor,file=$img\vendor.img,format=raw`" -device virtio-blk-device,drive=vendor " +
  "-drive `"if=none,id=data,file=$avd\userdata-qemu.img,format=raw`" -device virtio-blk-device,drive=data " +
  "-netdev `"user,id=mynet,hostfwd=tcp::15555-:5555`" -device virtio-net-device,netdev=mynet " +
  "-append `"console=ttyAMA0 androidboot.hardware=ranchu qemu=1 androidboot.selinux=permissive`" " +
  "-serial file:$serial -no-reboot"
cmd /c $cmd
