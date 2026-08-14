# DSH Android Client

A native Kotlin (Jetpack Compose) companion app for [deepseek-harness](https://github.com/aqiyoung/deepseek-harness).
It connects to a `dsh web` instance exposed behind a reverse proxy (e.g. Caddy) and lets you
chat with the agent, watch streamed replies, and **approve or answer** the agent's tool calls / questions
from your phone — the same role OpenClaw's Android app plays against its Gateway.

> ⚠️ deepseek-harness is a **developer preview**: the wire protocol can change. This client was
> written against the `aqiyoung/deepseek-harness@master` source (`packages/client/connection`,
> `packages/host/apiproxy`). If a call stops working, capture one WebSocket frame from your browser
> DevTools and re-align the fields in `HarnessClient.kt` / `ChatViewModel.kt`.

## Protocol (verified against source)

| Channel | Shape |
|---|---|
| Unary RPC | `POST /api/<method>` with body `{type:"client-request", rpcId, method, payload}` → `{type:"server-response", rpcId, result:{ok, value\|error}}` |
| Downlink | two downlink-only WebSockets `wss://<host>/api/events.mux` and `/api/events.host`; frames are `{type:"server-request", rpcId, method, payload}` (client never sends on them) |
| Respond | `POST /api/respond` with body `{type:"client-response", rpcId:<echo of request frame>, result:{ok:true, value:<answer>}}` |

Key methods: `session.create` → `{sessionId}`, `session.prompt` → `{sessionId, mode:"queue", content:[{type:"text", text}]}`.
Streamed assistant text arrives as `session/event` frames with `event.type == "assistant/chunk"`.
Approvals arrive as `approval/requested` and are answered via `POST /api/respond` with
`{sessionId, approvalId, outcome:"allowed-once"|"rejected"}`.
Questions arrive as `question/requested` and are answered via `POST /api/respond` with
`{sessionId, answer:{answers:[{id, selected:[labels], custom?}]}}`.

Auth is expected to be a Basic-auth layer in front of `dsh web` (see Caddy snippet below); the app
sends the `Authorization: Basic …` header on every HTTP request **and** the WebSocket upgrade.

## Server side (one-time)

`dsh web` ships with **no TLS/auth**, so put it behind a reverse proxy. Caddy example:

```caddy
yourdomain.example.com {
    basicauth {
        you $2a$14$xxxxxxxxxxxxxxxxxxxx   # `caddy hash-password`
    }
    reverse_proxy localhost:3080
}
```

```sh
dsh web --trusted-host yourdomain.example.com
```

## Build

```sh
# On the `feature/android-dsh` branch the Android client lives at the repo root.
# Needs Android SDK (compileSdk 34) + JDK 17; Android Studio handles the wrapper automatically.
./gradlew assembleDebug      # or open in Android Studio and Run
```

CI (`.github/workflows/android.yml`) builds a debug APK on every push to `feature/android-dsh`
and uploads it as an artifact.

## Use

1. Open the app, tap **设置**, enter `https://yourdomain.example.com` + the Basic-auth user/pass.
2. Tap **保存并连接**.
3. Type a message; streamed replies render live. When the agent asks for approval or a question,
   a card appears — answer it inline.

## Known verification points (harness developer preview)

- `assistant/chunk` text is read from `delta` then `text` (the data field is a wide passthrough in
  the schema). If streaming text is blank, capture a chunk frame and confirm the field name.
- Question `selected` sends the **option labels** (the schema only exposes `label`, not an option id).
  If the host rejects answers, capture a `question/requested` frame and check the expected identifier.
