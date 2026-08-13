import { createRequire } from "node:module";
const require = createRequire("C:/Users/萧龙/AppData/Roaming/npm/node_modules/openclaw/dist/");
const WebSocket = require("ws");

const ws = new WebSocket("ws://127.0.0.1:18999");

ws.on("open", () => {
  console.log("[ws] open");
  ws.send(JSON.stringify({
    type: "req", id: "c1", method: "connect",
    params: {
      minProtocol: 4, maxProtocol: 4,
      client: { id: "openclaw-tui", displayName: "ClawBox", version: "1.0.0", platform: "android", mode: "ui", instanceId: "t-1" },
      role: "operator",
      scopes: ["operator.admin"],
      auth: { token: "test-token-123" },
    },
  }));
});

ws.on("message", (d) => {
  const m = JSON.parse(String(d));
  if (m.type === "res") {
    console.log("[res]", m.id, "ok=" + m.ok, JSON.stringify(m.payload ?? m.error).slice(0, 400));
    if (m.id === "c1" && m.ok) {
      console.log("[hello-auth]", JSON.stringify(m.payload?.auth ?? {}));
      ws.send(JSON.stringify({ type: "req", id: "h1", method: "chat.history", params: { sessionKey: "main", limit: 10 } }));
    } else if (m.id === "h1" && m.ok) {
      console.log("[history] sessionId=", m.payload?.sessionId, "entries=", Array.isArray(m.payload?.history) ? m.payload.history.length : JSON.stringify(m.payload).slice(0, 200));
      ws.send(JSON.stringify({ type: "req", id: "s1", method: "chat.send", params: { sessionKey: "main", message: "你好，测试一下 ClawBox", idempotencyKey: "test-run-2" } }));
    } else if (m.id === "s1") {
      console.log("[send] runId=", m.payload?.runId, "status=", m.payload?.status);
    } else if (m.id === "q1") {
      process.exit(0);
    }
  } else if (m.type === "event") {
    if (m.event === "chat") {
      console.log("[chat-event]", m.payload?.state, "runId=", m.payload?.runId?.slice(0, 8), "delta=", (m.payload?.deltaText ?? "").slice(0, 100));
      if (m.payload?.state === "final") {
        console.log("[chat-final message]", JSON.stringify(m.payload.message ?? {}).slice(0, 900));
      }
      if (m.payload?.state === "error") {
        console.log("[chat-error]", m.payload?.errorMessage, m.payload?.errorKind);
      }
    } else if (m.event === "health") {
      // ignore periodic health
    }
  }
});

setTimeout(() => { console.log("[timeout] exiting"); process.exit(2); }, 45000);
