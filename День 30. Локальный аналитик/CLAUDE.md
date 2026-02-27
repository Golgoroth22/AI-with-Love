# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Japan Travel Guide Chat App — An Android application featuring "Yuki", an AI travel guide for Japan. Users type in Russian; the app translates to English before sending to the LLM and translates the English response back to Russian before display. Translation happens on-device via Google ML Kit (no internet required for translation).

**Key features**:
- Yuki persona: enthusiastic Japan travel guide
- On-device Ru↔En translation via Google ML Kit
- Local AI model (llama3.2:3b via Ollama) — no cloud LLM needed
- Full chat history maintained in English for the model

## Build and Run Commands

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew clean build
./gradlew test
./gradlew connectedAndroidTest
```

## Architecture: MVVM + Ollama + ML Kit Translation

```
User types Russian
    ↓
TranslationService.toEnglish()   [ML Kit, on-device]
    ↓
OllamaClient.chat() with English messages
    ↓
llama3.2:3b responds in English
    ↓
TranslationService.toRussian()   [ML Kit, on-device]
    ↓
Russian response shown in chat UI
```

**Conversation history** is stored in English (what the model sees).
**UI messages** show: original Russian user input + translated Russian AI responses.

```
UI Layer (Compose)
  └─ ChatScreen.kt

ViewModel Layer
  └─ ChatViewModel.kt
       ├─ StateFlow<List<Message>>
       ├─ StateFlow<Boolean> - loading state
       ├─ conversationHistory: List<OllamaMessage> (English)
       └─ sendMessage() — translate → LLM → translate back

Translation Layer
  └─ TranslationService.kt
       ├─ toEnglish() — ML Kit Ru→En
       └─ toRussian() — ML Kit En→Ru

Network Layer
  └─ OllamaClient.kt
       └─ Ktor HTTP client for Ollama /api/chat

DI Layer (Koin)
  └─ AppModule.kt
       ├─ OllamaClient (singleton)
       ├─ TranslationService (singleton)
       └─ ChatViewModel (viewModel)
```

**Initialization**: `MainActivity.onCreate()` calls `startKoin()` before `setContent`.

## Ollama Communication Pattern

### Request Flow
1. User types Russian → `ChatViewModel.sendMessage()`
2. `TranslationService.toEnglish()` translates user text
3. ViewModel calls `OllamaClient.chat(conversationHistory)` with English-only history
4. OllamaClient sends HTTP POST to `/api/chat`:
   ```json
   {
     "model": "llama3.2:3b",
     "messages": [...],
     "system": "You are Yuki...",
     "stream": false,
     "keep_alive": "5m",
     "options": {"temperature": 0.7, "num_predict": 512, "num_ctx": 4096, "top_p": 0.9, "repeat_penalty": 1.1}
   }
   ```
5. `TranslationService.toRussian()` translates response
6. UI updated with Russian response

### Response Parsing

Ollama can return **NDJSON** even when `stream: false`. `OllamaClient.parseOllamaResponse()` handles both:

- **Standard JSON**: decoded directly
- **NDJSON**: splits on `\n`, concatenates all `message.content` chunks, trims result

### ML Kit Translation

`TranslationService.translatePreservingStructure()` splits text on `\n`, translates each non-blank line individually, then rejoins. This prevents ML Kit from collapsing multi-line responses (bullet lists, numbered lists) into a single line.

On first use, ML Kit downloads translation models (~10 MB each). Subsequent calls are instant and work offline.

## Key Files and Their Responsibilities

**`viewmodel/ChatViewModel.kt`**:
- Manages chat state (`_messages`, `_isLoading`)
- Translates Ru→En before LLM, En→Ru after LLM
- Maintains `conversationHistory` in English
- Greeting shown in Russian (UI), seeded in English (history)

**`ollama/OllamaClient.kt`**:
- Ktor HTTP client, 5-minute timeouts
- Parses both JSON and NDJSON Ollama responses
- `OllamaClientException` with user-friendly messages

**`ollama/OllamaModels.kt`**:
- `OllamaMessage`, `OllamaOptions`, `OllamaChatRequest`, `OllamaChatResponse`, `OllamaError`
- `OllamaChatResponse` only contains `message: OllamaMessage`

**`util/TranslationService.kt`**:
- ML Kit Ru↔En wrapper
- `toEnglish()` / `toRussian()` — download model if needed, then translate line-by-line

**`ui/ChatScreen.kt`**:
- LazyColumn for messages, auto-scroll on new message and content change
- FAB send button with loading indicator
- "Новый чат" button (hidden when keyboard is visible)

**`di/AppModule.kt`**:
- Provides `OllamaClient` with Yuki system prompt and `OllamaOptions`
- Provides `TranslationService` singleton
- Provides `ChatViewModel`

**`data/model/Message.kt`**:
- `text: String`, `isFromUser: Boolean`

**`util/ServerConfig.kt`**:
- Exposes `OLLAMA_SERVER_URL` from `SecureData.kt` (gitignored)

**`util/ILoggable.kt`**:
- `logD()` / `logE()` interface backed by `android.util.Log`

## Ollama Server Setup

```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama pull llama3.2:3b
ollama serve
curl http://localhost:11434/api/version
```

For remote access:
```bash
sudo systemctl edit ollama.service
# Add: Environment="OLLAMA_HOST=0.0.0.0:11434"
sudo systemctl daemon-reload && sudo systemctl restart ollama
```

**Default port**: 11434 — configured in `util/SecureData.kt` (gitignored).

## Dependency Management

Version catalog: `gradle/libs.versions.toml`

**Active dependencies**:
- `ktor-client-*` (3.0.0) — HTTP client
- `koin-*` (3.5.6) — DI
- `kotlinx-serialization-json` (1.7.3) — JSON
- `androidx-compose-*` (BOM 2024.09.00) — UI
- `mlkit-translate` (17.0.3) — on-device translation
- `kotlinx-coroutines-play-services` (1.9.0) — ML Kit coroutine bridge

## Common Development Tasks

### Update server URL
Edit `util/SecureData.kt` (gitignored):
```kotlin
object SecureData {
    const val SERVER_IP = "10.0.2.2"   // emulator → host localhost
    const val SERVER_PORT = 11434
    val OLLAMA_SERVER_URL = "http://$SERVER_IP:$SERVER_PORT"
}
```

### Switch model
Edit `di/AppModule.kt`, change `modelName = "llama3.2:3b"` to desired model. Pull on server first:
```bash
ollama pull <model-name>
```

### Tune model parameters
Edit `OllamaOptions` in `di/AppModule.kt`:
- `temperature` (0.0–1.0): creativity
- `num_predict`: max output tokens
- `num_ctx`: context window size
- `top_p`: nucleus sampling
- `repeat_penalty`: penalise repetition

### Change Yuki's persona / system prompt
Edit `systemPrompt` string in `di/AppModule.kt`.

## Security Considerations

- **Cleartext HTTP**: configured via `res/xml/network_security_config.xml`
- **SecureData.kt**: gitignored — contains server IP/port
- **No auth**: Ollama has no built-in auth — use VPN/firewall for remote access
- **On-device translation**: ML Kit processes text locally, nothing sent to Google

## Performance Considerations

- **keep_alive "5m"**: keeps model in RAM — first request ~20s, subsequent ~2-5s
- **ML Kit models**: downloaded once (~10 MB each), then offline forever
- **num_predict 512**: limits response length for faster replies
- **num_ctx 4096**: context window — longer chats increase latency

## Troubleshooting

### "Cleartext HTTP traffic not permitted"
Configure `network_security_config.xml` — already done for 10.0.2.2.

### NDJSON / NoTransformationFoundException
Handled by `OllamaClient.parseOllamaResponse()`.

### ML Kit translation model not downloading
Requires internet on first use. After download, works offline.

### Response is one long line (no formatting)
`TranslationService` translates line-by-line to preserve `\n`. If broken, check `translatePreservingStructure()`.

### Cannot connect from emulator
Use `10.0.2.2` in `SecureData.kt`. Test with:
```bash
adb shell curl http://10.0.2.2:11434/api/version
```

### Slow first response
Normal — model cold start. `keep_alive: "5m"` keeps it warm for subsequent requests.
