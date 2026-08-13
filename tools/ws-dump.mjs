// ClawBox WS debug: dump all events verbosely
import { createRequire } from "node:module";
const require = createRequire("C:/Users/萧龙/AppData/Roaming/npm/node_modules/openclaw/dist/");
const WebSocket = require("ws");

const TOKEN = "2118bfd510e7439ca2df07b4385e89b0";
const ws = new WebSocket("ws://127.0.0.1:18790");

ws.on("open", () => {
  console.log("[ws] open");
  ws.send(JSON.stringify({
    type: "req", id: "c1", method: "connect",
    params: {
      minProtocol: 4, maxProtocol: 4,
      client: { id: "openclaw-tui", displayName: "ClawBox", version: "1.0.0", platform: "android", mode: "ui", instanceId: "t-1" },
      role: "operator",
      scopes: ["operator.admin"],
      auth: { token: TOKEN },
    },
  }));
});

let n = 0;
ws.on("message", (d) => {
  const m = JSON.parse(String(d));
  n++;
  const s = JSON.stringify(m);
  console.log(`[msg${n}] ${s.slice(0, 600)}`);
  if (n > 8) { console.log("... (stopping dump)"); ws.close(); }
});
ws.on("error", (e) => console.log("[err]", e.message));
setTimeout(() => { console.log("timeout"); process.exit(0); }, 20000);
