$ws = Join-Path $env:USERPROFILE '.openclaw\workspace\clawbox\tools'
$q = Join-Path $ws 'msys64\ucrt64\bin'
$dir = 'C:\Temp\clawbox-arm'
$env:PATH = "$q;" + $env:PATH
$env:QEMU_AUDIO_DRV = 'none'
$serial = 'C:\Temp\clawbox-arm\serial.log'
if (Test-Path $serial) { Remove-Item $serial }
Set-Location $dir
& "$q\qemu-system-aarch64.exe" -M virt -cpu cortex-a53 -smp 1 -m 2048 `
  -kernel "$dir\kernel-ranchu" -initrd "$dir\ramdisk2.img" `
  -drive "if=none,id=system,file=$dir\system.img,format=raw" -device virtio-blk-device,drive=system `
  -drive "if=none,id=vendor,file=$dir\vendor.img,format=raw" -device virtio-blk-device,drive=vendor `
  -drive "if=none,id=data,file=$dir\userdata.qcow2,format=qcow2" -device virtio-blk-device,drive=data `
  -drive "if=none,id=cache,file=$dir\cache.qcow2,format=qcow2" -device virtio-blk-device,drive=cache `
  -netdev "user,id=mynet,hostfwd=tcp::15555-:5555" -device virtio-net-device,netdev=mynet `
  -append "console=ttyAMA0 androidboot.hardware=ranchu qemu=1 androidboot.selinux=permissive" `
  -serial "file:$serial" -no-reboot 2>&1 | Tee-Object -FilePath (Join-Path $ws 'qemu-console.log')
