# -*- coding: utf-8 -*-
"""Reproduce ConfigManager.writeConfig() output exactly, then validate it
by starting a real gateway with it (host x64 openclaw)."""
import json
import os
import subprocess
import sys
import time
import uuid

STATE = r"C:\Users\萧龙\.openclaw\workspace\clawbox\plugintest3\state"
os.makedirs(STATE, exist_ok=True)

# ---- exact replica of ConfigManager.writeConfig() string building ----
port = 18799  # avoid clashing with any running gateway
token = uuid.uuid4().hex
bind = "loopback"
provider = "deepseek"
api_key = ""  # no key: we only validate config parsing/startup
base_url = "https://api.deepseek.com"
workspace = STATE.replace("\\", "/") + "/workspace"

sb = []
sb.append("{\n")
sb.append('  "gateway": {\n')
sb.append('    "mode": "local",\n')
sb.append('    "port": %d,\n' % port)
sb.append('    "bind": "%s",\n' % bind)
sb.append('    "auth": { "mode": "token", "token": "%s" },\n' % token)
sb.append('    "controlUi": { "allowInsecureAuth": true }\n')
sb.append("  },\n")
sb.append('  "plugins": { "allow": ["deepseek", "openai"] },\n')
sb.append('  "models": {\n')
sb.append('    "providers": {\n')
sb.append('      "%s": {\n' % provider)
sb.append('        "apiKey": "%s",\n' % api_key)
sb.append('        "baseUrl": "%s"\n' % base_url)
sb.append("      }\n")
sb.append("    }\n")
sb.append("  },\n")
sb.append('  "agents": {\n')
sb.append("    \"defaults\": {\n")
sb.append('      "workspace": "%s",\n' % workspace)
sb.append('      "model": { "primary": "deepseek/deepseek-chat" }\n')
sb.append("    }\n")
sb.append("  }\n")
sb.append("}\n")
cfg = "".join(sb)

cfg_path = os.path.join(STATE, "openclaw.json")
with open(cfg_path, "w", encoding="utf-8") as f:
    f.write(cfg)
print("=== config written (exact ConfigManager replica) ===")
print(cfg)
print("=== json validity check ===")
parsed = json.loads(cfg)  # strict JSON parse
print("valid JSON:", parsed["gateway"]["mode"], parsed["gateway"]["auth"]["token"][:8] + "...")

# ---- start real gateway with this config ----
env = dict(os.environ)
env["OPENCLAW_STATE_DIR"] = STATE
env["OPENCLAW_CONFIG_PATH"] = cfg_path
out = os.path.join(STATE, "gw.log")
err = os.path.join(STATE, "gw.err.log")
with open(out, "w") as fo, open(err, "w") as fe:
    proc = subprocess.Popen(
        ["node", r"C:\Users\萧龙\AppData\Roaming\npm\node_modules\openclaw\openclaw.mjs", "gateway", "--port", str(port)],
        env=env, stdout=fo, stderr=fe,
    )
    try:
        deadline = time.time() + 60
        ok = False
        while time.time() < deadline:
            if proc.poll() is not None:
                break
            # try TCP connect
            import socket
            s = socket.socket()
            s.settimeout(1)
            try:
                s.connect(("127.0.0.1", port))
                ok = True
                s.close()
                break
            except Exception:
                s.close()
                time.sleep(2)
        if ok:
            print("=== GATEWAY STARTED AND LISTENING on %d (config OK) ===" % port)
        else:
            print("=== GATEWAY FAILED (see gw.log) ===")
            print(open(out).read()[-2000:])
    finally:
        proc.terminate()
        try:
            proc.wait(5)
        except Exception:
            proc.kill()
