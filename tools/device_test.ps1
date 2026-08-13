# ClawBox 真机全流程测试脚本（adb 无线/USB 连接后运行）
$ErrorActionPreference = 'Continue'
$adb = 'C:\Users\萧龙\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$ws  = 'C:\Users\萧龙\.openclaw\workspace'
$apk = "$ws\clawbox-v1.0.4-debug.apk"   # debug 版支持 run-as
$rt  = "$ws\runtime-arm64.tar.xz"
$pkg = 'com.lobster.clawbox'

function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

Step '0. 设备确认'
& $adb devices -l
$ser = (& $adb devices | Select-String 'device$' | Select-Object -First 1).ToString().Split("`t")[0]
if (-not $ser) { Write-Host 'NO DEVICE - exit'; exit 1 }
Write-Host "serial: $ser"
Write-Host ('abi: ' + (& $adb -s $ser shell getprop ro.product.cpu.abi))
Write-Host ('sdk: ' + (& $adb -s $ser shell getprop ro.build.version.sdk))
Write-Host ('root: ' + (& $adb -s $ser shell "su -c id" 2>&1))

Step '1. 安装 APK'
& $adb -s $ser install -r $apk

Step '2. 尝试 root 通道'
$rooted = (& $adb -s $ser shell "su -c id" 2>&1) -match 'uid=0'
Write-Host "rooted: $rooted"

Step '3. 准备 App 数据目录（root 或 run-as）'
if ($rooted) {
    & $adb -s $ser shell "su -c 'mkdir -p /data/data/$pkg/files'"
    & $adb -s $ser push $rt /data/local/tmp/runtime.tar.xz
    & $adb -s $ser shell "su -c 'cd /data/local/tmp && tar -xJf runtime.tar.xz -C /data/local/tmp && rm -rf /data/data/$pkg/files/clawbox-runtime && mv /data/local/tmp/runtime /data/data/$pkg/files/clawbox-runtime && chmod -R 755 /data/data/$pkg/files/clawbox-runtime'"
} else {
    & $adb -s $ser shell "run-as $pkg mkdir -p files"
    & $adb -s $ser push $rt /data/local/tmp/runtime.tar.xz
    & $adb -s $ser shell "run-as $pkg sh -c 'cd /data/local/tmp && tar -xJf runtime.tar.xz'"
    # run-as 无法解压到 files（/data/local/tmp 权限），改为：先解压到 /data/local/tmp 再复制
    & $adb -s $ser shell "tar -xJf /data/local/tmp/runtime.tar.xz -C /data/local/tmp"
    & $adb -s $ser shell "run-as $pkg sh -c 'rm -rf files/clawbox-runtime && cp -r /data/local/tmp/runtime files/clawbox-runtime && chmod -R 755 files/clawbox-runtime'"
}
& $adb -s $ser shell "run-as $pkg ls files/clawbox-runtime" 2>&1 | Select-Object -First 5

Step '4. 写入 prefs（跳过向导 + 标记运行时已装）'
$prefs = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><boolean name=`"initialized`" value=`"true`" /><boolean name=`"setup_done`" value=`"true`" /><boolean name=`"runtime_installed`" value=`"true`" /><boolean name=`"boot_start`" value=`"false`" /><boolean name=`"lan_access`" value=`"false`" /><string name=`"model_provider`">deepseek</string><string name=`"model_name`">deepseek-chat</string><string name=`"base_url`">https://api.deepseek.com</string><string name=`"session_key`">main</string></map>"
$prefs | Out-File -FilePath "$env:TEMP\clawbox-prefs.xml" -Encoding utf8
& $adb -s $ser push "$env:TEMP\clawbox-prefs.xml" /data/local/tmp/prefs.xml
& $adb -s $ser shell "run-as $pkg sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/prefs.xml shared_prefs/clawbox.xml && chmod 660 shared_prefs/clawbox.xml'"

Step '5. 启动 App（首页）'
& $adb -s $ser shell "am start -n $pkg/.MainActivity"
Start-Sleep -Seconds 6
& $adb -s $ser shell "screencap -p /sdcard/s1.png"
& $adb -s $ser pull /sdcard/s1.png "$ws\test-shot-1.png"

Step '6. 启动网关服务'
& $adb -s $ser shell "am start-foreground-service -n $pkg/.gateway.GatewayService -a com.lobster.clawbox.action.START"
Start-Sleep -Seconds 20

Step '7. 网关日志'
& $adb -s $ser shell "run-as $pkg sh -c 'tail -30 files/clawbox-runtime/logs/gateway.log 2>/dev/null || ls files/clawbox-runtime/logs'"

Step '8. 转发端口 + 协议测试'
& $adb -s $ser forward tcp:18789 tcp:18789
$token = (& $adb -s $ser shell "run-as $pkg cat shared_prefs/clawbox.xml" 2>&1 | Select-String 'gateway_token' | Select-Object -First 1)
Write-Host "token line: $token"

Step '9. 进程检查'
& $adb -s $ser shell "ps -A | grep -E 'node|openclaw' | head -3"

Write-Host "`nDONE. 截图在 test-shot-1.png，网关日志见上。"
