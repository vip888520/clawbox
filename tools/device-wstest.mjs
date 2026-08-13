// ClawBox real-device WS test: connect -> chat.history -> chat.send -> stream
import { createRequire } from "node:module";
const require = createRequire("C:/Users/萧龙/AppData/Roaming/npm/node_modules/openclaw/dist/");
const WebSocket = require("ws");

const TOKEN = "2118bfd510e7439ca2df07b4385e89b0";
const URL = "ws://127.0.0.1:18790";
const ws = new WebSocket(URL);

const pending = new Map();
function send(method, params) {
  return new Promise((resolve, reject) => {
    const id = "r" + (Math.random() * 1e9 | 0);
    pending.set(id, { resolve, reject, method });
    ws.send(JSON.stringify({ type: "req", id, method, params }));
  });
}

ws.on("open", async () => {
  console.log("[ws] open", URL);
  const c = await send("connect", {
    minProtocol: 4, maxProtocol: 4,
    client: { id: "openclaw-tui", displayName: "ClawBox", version: "1.0.0", platform: "android", mode: "ui", instanceId: "t-1" },
    role: "operator",
    scopes: ["operator.admin"],
    auth: { token: TOKEN },
  });
  console.log("[connect-res] ok=" + (c !== undefined) + " " + JSON.stringify(c).slice(0, 200));
  connected = true;
  const h = await send("chat.history", { sessionKey: "main", limit: 5 });
  console.log("HISTORY_MESSAGES:", JSON.stringify(h?.messages ?? h).slice(0, 600));
  console.log("SENDING test message...");
  await send("chat.send", {
    sessionKey: "main",
    message: "回复四个字：真机测试",
    idempotencyKey: "dev-test-" + Date.now(),
  });
  setTimeout(() => { console.log("TIMEOUT_WAITING_FOR_STREAM"); process.exit(0); }, 60000);
});

let connected = false;
ws.on("message", (d) => {
  const m = JSON.parse(String(d));
  if (m.type === "res") {
    const p = pending.get(m.id);
    if (p) {
      pending.delete(m.id);
      console.log("[res]", m.id, m.method, "ok=" + m.ok, JSON.stringify(m.payload ?? m.error).slice(0, 300));
      if (m.ok) p.resolve(m.payload); else p.reject(new Error(JSON.stringify(m.error).slice(0, 200)));
    }
  } else if (m.type === "event") {
    const evName = m.event;
    const pl = m.payload ?? m;
    if (evName === "chat") {
      const c = pl;
      const role = c.role ?? "";
      if (c.deltaText) process.stdout.write("[chat-delta] " + c.deltaText + "\n");
      if (c.finalText) console.log("[chat-final] " + c.finalText);
      if (c.content && Array.isArray(c.content)) {
        for (const blk of c.content) {
          if (blk.type === "text" && blk.text) console.log("[chat-block-text] " + blk.text);
          if (blk.type === "thinking" && blk.thinking) console.log("[chat-block-thinking] " + String(blk.thinking).slice(0, 200));
        }
      }
      if (c.status === "completed" || c.final) {
        console.log("[chat-complete]");
        setTimeout(() => process.exit(0), 1500);
      }
      if (c.error) { console.log("[chat-error]", JSON.stringify(c.error).slice(0, 300)); process.exit(2); }
    } else {
      console.log("[event]", evName, JSON.stringify(pl).slice(0, 150));
    }
  }
});

ws.on("error", (e) => { console.log("[ws-error]", e.message); process.exit(3); });
ws.on("close", () => { console.log("[ws-close]"); if (!connected) process.exit(4); });
setTimeout(() => { console.log("GLOBAL_TIMEOUT"); process.exit(5); }, 90000);
