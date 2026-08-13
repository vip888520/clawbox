$ws = Join-Path $env:USERPROFILE '.openclaw\workspace\clawbox\tools'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$img = Join-Path $sdk 'system-images\android-28\google_apis\arm64-v8a'
$avd = Join-Path $env:USERPROFILE '.android\avd\clawboxarm.avd'
$q = Join-Path $ws 'msys64\ucrt64\bin'
$env:PATH = "$q;" + $env:PATH
$env:QEMU_AUDIO_DRV = 'none'
$serial = Join-Path $ws 'android-serial.log'
if (Test-Path $serial) { Remove-Item $serial }
Set-Location $q
& "$q\qemu-system-aarch64.exe" -M virt -cpu cortex-a53 -smp 4 -m 2048 `
  -kernel (Join-Path $img 'kernel-ranchu') -initrd (Join-Path $img 'ramdisk.img') `
  -drive "if=none,id=system,file=$(Join-Path $img 'system.img'),format=raw" -device virtio-blk-device,drive=system `
  -drive "if=none,id=vendor,file=$(Join-Path $img 'vendor.img'),format=raw" -device virtio-blk-device,drive=vendor `
  -drive "if=none,id=data,file=$(Join-Path $avd 'userdata-qemu.img'),format=raw" -device virtio-blk-device,drive=data `
  -netdev "user,id=mynet,hostfwd=tcp::15555-:5555" -device virtio-net-device,netdev=mynet `
  -append "console=ttyAMA0 androidboot.hardware=ranchu qemu=1 androidboot.selinux=permissive" `
  -serial "file:$serial" -no-reboot 2>&1 | Tee-Object -FilePath (Join-Path $ws 'qemu-console.log')
