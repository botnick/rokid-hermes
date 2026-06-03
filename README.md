# 🥽 Rokid Hermes

**Rokid AI Glasses + [Hermes Agent](https://github.com/NousResearch/hermes-agent) — glasses-direct, voice-first wearable AI**

The Rokid AI Glasses connect **directly** to a Hermes Agent gateway over WiFi — no phone bridge needed. Tap to talk, your speech is streamed to Hermes, and the reply appears on the monochrome green HUD and is spoken back. Because Hermes is model-agnostic and self-hosted, you choose the model and keep the data.

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🎤 **Voice input** | On-device speech recognition (glasses mic array) turns speech into text |
| 🖥️ **HUD display** | Streaming reply, token-by-token, on the 480×640 monochrome green micro-LED |
| 🔊 **TTS replies** | Hermes' answer is spoken aloud through the built-in speakers |
| 🧠 **Memory that grows** | A stable per-install memory key (`X-Hermes-Session-Key`) lets Hermes remember you across conversations |
| 🔗 **Session continuity** | Each conversation carries an `X-Hermes-Session-Id`; **New** starts fresh |
| ⏹ **Stop & retry** | Cancel a long reply mid-stream; one tap to retry after an error |
| 📡 **Honest status** | The header dot reflects a real reachability probe, not just "fields filled in" |
| 🧪 **Test & save** | Setup verifies the URL + API key against the gateway before it lets you in |

---

## 🏗️ Architecture

The glasses run Android internally and can run sideloaded Jetpack Compose APKs.
They talk to Hermes' OpenAI-compatible `api_server` platform over plain HTTP.

```
┌─────────────────────────────────────────────┐
│              Rokid AI Glasses                │
│  🎤 mic ─► on-device speech-to-text          │
│  🖥️ micro-LED ◄─ streaming HUD chat          │
│  🔊 speakers ◄─ text-to-speech               │
│  📡 WiFi ─► HTTPS/HTTP client                │
└─────────────────────────────────────────────┘
                     │  POST /v1/chat/completions (stream)
                     ▼
┌─────────────────────────────────────────────┐
│        Hermes Agent — api_server (:8642)     │
│  • OpenAI-compatible Chat Completions         │
│  • Auth: Authorization: Bearer <API_SERVER_KEY>│
│  • Memory + skills + tools + sessions          │
│  • Any model (Nous Portal / OpenRouter / …)    │
└─────────────────────────────────────────────┘
```

- **`glasses-app/`** — the standalone glasses app (Kotlin / Jetpack Compose)
- **`phone-app/`** — reserved for a future companion (not used; glasses are direct)
- **`shared/`** — reserved for shared protocol/data models

---

## 📋 Requirements

- A running **Hermes Agent** with the `api_server` platform enabled and an `API_SERVER_KEY` set
  (any OpenAI-compatible client can reach it at `http://<host>:8642/v1`)
- The glasses and the Hermes host on the **same reachable WiFi network**
- To build: [Android Studio](https://developer.android.com/studio) (open `glasses-app/`),
  **or** the CLI with a JDK 17 + Android SDK on `ANDROID_HOME`: `./gradlew assembleDebug`

> WiFi note: the app does not join WiFi itself (Android restricts that). Connect the
> glasses to WiFi from the system settings, then point the app at your Hermes URL.

---

## 🚀 Getting started

1. Clone this repo
2. Build the APK — Android Studio (open `glasses-app/`) **or** `./gradlew assembleDebug`
3. Sideload onto the glasses (`adb install -r glasses-app/build/outputs/apk/debug/glasses-app-debug.apk`)
4. Open the app → **⚙ SET UP** → enter your **Hermes URL** (include `/v1`) and **API key** → **TEST & SAVE** (it only drops you into chat once the gateway answers)
5. Tap **🎤 TAP TO TALK**, speak, then tap **SEND**

### Using it

- **🎤 TAP TO TALK** → **● LISTENING — TAP TO SEND**: tap once to start, tap again to send your utterance.
- The reply streams onto the HUD token-by-token and is read aloud; tap **✕ STOP** to cancel a long answer.
- **NEW** clears the conversation (and rotates the session id); the header dot shows connection status: `○` not set up · `◌` checking · `●` connected · `⚠` last attempt failed.
- On an error you get an inline card with **RETRY** and **SET UP** — no dead-ends.

---

## 🔄 Self-update

On launch, `LoaderActivity` checks this repo's GitHub releases for a newer
version. If a release with a higher version and an attached `.apk` asset exists,
it downloads the APK and hands it to the system package installer (this needs the
"install unknown apps" permission, `REQUEST_INSTALL_PACKAGES`); otherwise it
launches the installed app.

To ship an update: publish a GitHub release tagged with a higher semver
(e.g. `v0.2.0`) and attach the built `.apk`. The APK's **`versionCode` must be
strictly higher** than the installed one — the installer compares `versionCode`,
not the version name — so build it with a bumped code, e.g.
`./gradlew assembleRelease -PappVersionCode=2 -PappVersionName=0.2.0`.

---

## 🙏 Credits

- **[Hermes Agent](https://github.com/NousResearch/hermes-agent)** by Nous Research — the brain
- **[Clawsses](https://github.com/dweddepohl/clawsses)** by [@dweddepohl](https://github.com/dweddepohl) — proved the glasses-AI concept

## 📄 License

MIT — build on it, hack it, make it yours.
