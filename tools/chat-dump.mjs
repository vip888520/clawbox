// dump chat events raw
import { createRequire } from "node:module";
const require = createRequire("C:/Users/萧龙/AppData/Roaming/npm/node_modules/openclaw/dist/");
const WebSocket = require("ws");
const TOKEN = "2118bfd510e7439ca2df07b4385e89b0";
const ws = new WebSocket("ws://127.0.0.1:18790");
let done = false;
function send(method, params) {
  return new Promise((res, rej) => {
    const id = "r" + (Math.random() * 1e9 | 0);
    const h = (d) => {
      const m = JSON.parse(String(d));
      if (m.type === "res" && m.id === id) { ws.off("message", h); m.ok ? res(m.payload) : rej(new Error(JSON.stringify(m.error))); }
    };
    ws.on("message", h);
    ws.send(JSON.stringify({ type: "req", id, method, params }));
  });
}
ws.on("open", async () => {
  await send("connect", { minProtocol: 4, maxProtocol: 4, client: { id: "openclaw-tui", displayName: "ClawBox", version: "1.0.0", platform: "android", mode: "ui", instanceId: "t-1" }, role: "operator", scopes: ["operator.admin"], auth: { token: TOKEN } });
  await send("chat.send", { sessionKey: "main", message: "回复四个字：真机测试", idempotencyKey: "chatdump-" + Date.now() });
  console.log("SENT");
});
ws.on("message", (d) => {
  const m = JSON.parse(String(d));
  if (m.type === "event") {
    if (m.event === "chat") {
      const pl = m.payload ?? {};
      const keys = Object.keys(pl);
      console.log("CHAT-EVENT keys=" + keys.join(","));
      console.log("  " + JSON.stringify(pl).slice(0, 800));
      if (pl.status === "completed" || pl.final || pl.finalText) { done = true; setTimeout(() => process.exit(0), 2000); }
    } else if (m.event === "chat.send_timing") {
      // skip
    } else {
      console.log("EVT", m.event, JSON.stringify(m.payload ?? {}).slice(0, 120));
    }
  }
});
ws.on("error", (e) => { console.log("ERR", e.message); process.exit(3); });
setTimeout(() => { console.log("TIMEOUT done=" + done); process.exit(0); }, 90000);
