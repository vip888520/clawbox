# ClawBox emulator test: install APK, provision runtime, start gateway, verify chat.
# Run from PowerShell after the emulator reports BOOT COMPLETED.

$ErrorActionPreference = 'Stop'
$adb = 'C:\Users\萧龙\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$ws  = 'C:\Users\萧龙\.openclaw\workspace'
$apk = "$ws\clawbox-v1.0.4-debug.apk"
$rt  = "$ws\runtime-x64.tar.xz"
$pkg = 'com.lobster.clawbox'

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

Step '0. device check'
& $adb devices
$boot = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
Write-Host "boot_completed=$boot"
if ($boot -ne '1') { throw 'emulator not booted yet' }

Step '1. install APK'
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'install failed' }

Step '2. adb root (google_apis image)'
& $adb root
Start-Sleep -Seconds 4
& $adb wait-for-device

Step '3. push runtime + extract'
& $adb push $rt /data/local/tmp/runtime.tar.xz
if ($LASTEXITCODE -ne 0) { throw 'push failed' }
& $adb shell "cd /data/local/tmp && mkdir -p rx && tar -xJf runtime.tar.xz -C rx"
if ($LASTEXITCODE -ne 0) { throw 'tar extract failed (toybox xz unsupported?)' }
& $adb shell "ls /data/local/tmp/rx/runtime | head -20"

Step '4. move runtime into app data'
& $adb shell "mkdir -p /data/data/$pkg/files"
& $adb shell "rm -rf /data/data/$pkg/files/clawbox-runtime"
& $adb shell "mv /data/local/tmp/rx/runtime /data/data/$pkg/files/clawbox-runtime"
& $adb shell "chmod -R 755 /data/data/$pkg/files/clawbox-runtime"
& $adb shell "ls /data/data/$pkg/files/clawbox-runtime"

Step '5. set prefs (runtime_installed=true, setup_done=true)'
& $adb shell "mkdir -p /data/data/$pkg/shared_prefs"
& $adb shell "cat > /data/data/$pkg/shared_prefs/clawbox.xml <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name=\"initialized\" value=\"true\" />
    <boolean name=\"setup_done\" value=\"true\" />
    <boolean name=\"runtime_installed\" value=\"true\" />
    <boolean name=\"boot_start\" value=\"false\" />
    <boolean name=\"lan_access\" value=\"false\" />
    <boolean name=\"pair_require\" value=\"true\" />
    <int name=\"gateway_port\" value=\"18789\" />
    <string name=\"model_provider\">deepseek</string>
    <string name=\"model_name\">deepseek-chat</string>
    <string name=\"base_url\">https://api.deepseek.com</string>
    <string name=\"session_key\">main</string>
    <string name=\"api_key\"></string>
</map>
EOF"
& $adb shell "chown -R $(($( & $adb shell 'dumpsys package com.lobster.clawbox | grep -o "userId=[0-9]*" | head -1').Split('=')[1]) :$(($( & $adb shell 'dumpsys package com.lobster.clawbox | grep -o "userId=[0-9]*" | head -1').Split('=')[1])) /data/data/$pkg/shared_prefs"

Step '6. start gateway service'
& $adb shell "am start-foreground-service -n $pkg/.gateway.GatewayService -a com.lobster.clawbox.action.START"
Start-Sleep -Seconds 5

Step '7. read gateway token from prefs'
$token = (& $adb shell "cat /data/data/$pkg/shared_prefs/clawbox.xml").Trim() -match 'gateway_token[^>]*>([^<]+)<'
Write-Host "token found: $($matches[1])"

Step '8. forward port + wait for gateway'
& $adb forward tcp:18789 tcp:18789
Start-Sleep -Seconds 10

Step '9. gateway process check'
& $adb shell "ps -A | grep -E 'node|openclaw' | head -5"
& $adb shell "tail -20 /data/data/$pkg/files/clawbox-runtime/logs/gateway.log 2>/dev/null"

Write-Host "`nDONE. Next: run gateway-wstest.mjs against ws://127.0.0.1:18789 with the token above."
