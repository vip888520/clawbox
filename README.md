# ClawBox v1.0.14 —— 手机版 OpenClaw 主机（真机验证完成版）

> 手机即主机。在 Android 手机上运行完整的 OpenClaw 网关，内置 DeepSeek 模型接入，真实聊天/终端/管理/设置。

## ✅ 真机验证结果（2026-08-13，小米 13 / Android 16 / 无 root）

**全部核心功能在真机上实测通过：**

| 功能 | 状态 | 验证方式 |
|---|---|---|
| 网关进程启动 | ✅ | `openclaw-gateway` 进程运行，端口 18789 监听 |
| OpenClaw 网关 ready | ✅ | 日志 `[gateway] ready` + heartbeat 启动 |
| DeepSeek 插件加载 | ✅ | `plugins.loaded: ["deepseek","memory-core","openai"]` |
| App 连接网关 | ✅ | `connect res ok=true`（WS 协议 v4） |
| 历史会话加载 | ✅ | 聊天页显示网关欢迎消息 |
| 真实消息发送 | ✅ | "hi" → 大龙虾回复"老板好！🦞 有啥事儿尽管说，钳子已就位。" |
| DeepSeek 真实调用 | ✅ | 网关日志 `POST api.deepseek.com/chat/completions → status=200` |
| 流式回复 | ✅ | delta 增量事件逐字显示，final 完成 |
| 状态页实时日志 | ✅ | `[gateway]` 日志实时滚动显示 |
| 设置/终端/管理页 | ✅ | UI 正常，模型/Key/网关配置生效 |

## 🏗 真机适配的技术路线（3 个关键突破）

### 1. SELinux 执行限制 → targetSdk 27
Android 16 禁止新版 targetSdk App 执行自身数据目录的 ELF（`untrusted_app` 域无
`execute_no_trans`，node 直接被杀）。**把 targetSdk 降到 27** → 走 `untrusted_app_27`
域（Termux 同款）→ 可执行。这是"手机秒退网关"的真机终极根因。

### 2. glibc node 被 seccomp 杀 → bionic node
glibc 版 node 在 App 域启动即被 seccomp 杀（SIGSYS，exit 159）。**换 Termux 仓库的
bionic node**（node 26 + 12 个依赖库：libz/libcares/libsqlite3/libffi/libcrypto/libssl/
libicu*/libc++，全部物化 symlink）→ 直接 exec（解释器 /system/bin/linker64）→ 不再
触发 seccomp。外加最小 openssl.cnf（node 默认找 Termux 路径读不到）。

### 3. WS 连接"假死" → 两个 Bug
- OkHttp ping/pong：bionic node 的 ws 服务器不回 pong → 20 秒后 OkHttp 判定连接死 →
  **去掉 pingInterval**（本地回环不需要心跳）。
- connect 响应被吞：`handleResponse` 里 `pending.remove("c1") ?: return` 提前返回，
  `setState(CONNECTED)` 永远执行不到 → **c1 特判移到 pending 查询之前**。

## 📦 交付物

- `clawbox-v1.0.14-release.apk`（1.9MB，正式分发版，签名 keystore/clawbox-release.jks）
- `clawbox-v1.0.14-debug.apk`（18MB，可 run-as 调试，功能与 release 一致）
- `runtime-arm64.tar.xz`（68MB，含 bionic node + glibc node + DeepSeek 插件，sha256=b140e5f7…）
  - 说明：bionic node 由 App 自动优先使用（`bionic/bin/node` 存在即走 bionic 模式），
    glibc node 保留作兜底。当前 tar 包**不含 bionic 目录**——真机测试时 bionic 运行时是
    通过 adb 单独注入的（`/data/local/tmp/bionic-node` → `files/clawbox-runtime/bionic/`）。
    **分发前应把 bionic 目录并入 runtime 包**（见下）。

## 🚨 发布前待办

1. ~~**bionic 并入 runtime 包**~~ ✅ 已完成：`runtime-arm64.tar.xz`（90.7MB，sha256=e087262a…）已含 `runtime/bionic/`（bin/node + lib/* + etc/openssl.cnf）。新装机用户导入一个包即含 bionic 运行时。
2. **重新打包后** 在干净环境验证一次首启向导（导入 → 启动 → 聊天）。
3. 手机当前装的是 debug 版；release 版因签名不同需卸载安装，数据会清空，走首启向导
   重新导入 runtime 即可（`/sdcard/Download/clawbox-runtime.tar.xz` 为旧包，含 bionic 的新包在 workspace 根 `runtime-arm64.tar.xz`）。

## 🔧 技术备注

- 构建：`gradle-8.13 :app:assembleDebug :app:assembleRelease`（无 wrapper）
- 真机注入通道：`adb` + `run-as`（debug 版）；`/data/local/tmp/rt` 是 runtime 副本
- 网关配置：`files/clawbox-runtime/home/.openclaw/openclaw.json`（token/模型/工作区）
- 日志：状态页"最近日志"实时显示；`logcat -s ClawBox-WS/ClawBox-GW/ClawBox-SVC`
- 已知小瑕疵：HomeScreen"App 连接"在网关运行时会显示"重连中…"（connState 被旧
  socket 回调覆盖的显示层问题，不影响实际连接与聊天）

## 🧹 遗留清理

- `clawbox/plugintest3/`、workspace 下旧版 APK（v1.0.1~v1.0.13）可删
- 手机 `/sdcard/Download/` 下的旧测试 APK 可删（保留 clawbox-runtime.tar.xz 与
  clawbox-debug.apk 用于重装）
