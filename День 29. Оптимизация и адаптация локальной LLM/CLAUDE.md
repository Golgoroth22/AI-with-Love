# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Chat App - Android application with on-device LLM inference using llama.cpp + GGUF models. Users download GGUF models (Llama 3.2, Gemma 2, etc.) and chat completely offline.

**Key features:**
- On-device inference (no server required)
- GGUF model download with progress tracking
- 5 pre-configured models in catalog
- 100% offline operation
- Modern Material Design 3 UI

## Build and Run Commands

### Build the app
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Install to device
```bash
./gradlew installDebug
# Or manually:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Clean build
```bash
./gradlew clean build
```

### Run tests
```bash
# Unit tests
./gradlew test

# Instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

## Architecture: MVVM + llama.cpp

```
UI Layer (Compose)
  └─ ChatScreen.kt - Chat UI with messages
  └─ GGUFModelScreen.kt - Model selection and download UI

ViewModel Layer
  └─ ChatViewModel.kt
       ├─ StateFlow<List<Message>> - message list
       ├─ StateFlow<Boolean> - loading state
       └─ sendMessage() - calls LLMClient
  └─ GGUFModelViewModel.kt
       ├─ StateFlow<GGUFModel> - selected model
       ├─ StateFlow<DownloadProgress> - download state
       ├─ StateFlow<Boolean> - model ready
       └─ startDownload() - initiates download

Data Layer
  └─ GGUFModelRepository.kt
       ├─ downloadModel() - HTTP download from HuggingFace
       ├─ getDownloadedModels() - file system check
       └─ deleteModel() - cleanup
  └─ LlamaCppClient.kt
       └─ Llamatik library wrapper for GGUF inference

Utilities
  └─ ModelCatalog.kt - 5 pre-configured GGUF models

DI Layer (Koin)
  └─ AppModule.kt
       ├─ HttpClient (for downloads)
       ├─ GGUFModelRepository
       ├─ LLMClient → LlamaCppClient
       ├─ ChatViewModel
       └─ GGUFModelViewModel
```

**Initialization**: `MainActivity.onCreate()` calls `startKoin()` before setting content.

## Model Management

### GGUF Model Catalog

**File:** `util/ModelCatalog.kt`

Defines 5 pre-configured GGUF models:
1. Llama 3.2 1B Q4_K_M (869MB) - DEFAULT
2. Gemma 2 2B Q4_K_M (1.7GB)
3. Qwen 2.5 1.5B Q4_K_M (1.1GB)
4. Phi-3 Mini Q4_K_M (2.4GB)
5. TinyLlama 1.1B Q4_K_M (669MB)

### Download Flow

1. User selects model from catalog → `GGUFModelViewModel.selectModel()`
2. User taps download → `startDownload()`
3. GGUFModelRepository downloads from HuggingFace
4. Progress updates via `Flow<DownloadProgress>`
5. File saved to `context.filesDir/models/`
6. On completion → `isModelReady = true`
7. App auto-navigates to ChatScreen

### Model Storage

**Location:** `{app_data}/files/models/`
- Example: `/data/data/com.example.aiwithlove/files/models/llama-3.2-1b-q4.gguf`

**Auto-detection:** On app restart, GGUFModelViewModel scans for any .gguf files and sets as selected model.

## llama.cpp Integration

**Library:** Llamatik (llama.cpp Kotlin wrapper)

**Implementation:** `llm/LlamaCppClient.kt`

**Key features:**
- Lazy model initialization (load on first chat)
- ChatML prompt formatting
- Streaming token generation
- Response cleaning (remove special tokens)
- Proper resource cleanup

**Inference flow:**
1. ChatViewModel.sendMessage() → llmClient.chat(messages)
2. LlamaCppClient formats prompt (ChatML format)
3. LlamaBridge.predict() → generates response
4. Tokens streamed via SharedFlow
5. Response cleaned and returned to ViewModel
6. UI updates with new message

## Key Files and Their Responsibilities

**ChatViewModel.kt** (98 lines)
- Manages conversation state
- Calls LLMClient for inference
- Error handling for model failures
- Maintains conversation history

**LlamaCppClient.kt** (167 lines)
- Implements LLMClient interface
- Wraps Llamatik library
- Lazy model initialization
- ChatML prompt formatting
- Resource cleanup on close()

**GGUFModelViewModel.kt** (126 lines)
- Model selection logic
- Download orchestration
- Persistent model detection (checks on app restart)
- Auto-navigation when ready

**GGUFModelRepository.kt** (196 lines)
- HTTP download from HuggingFace
- Progress tracking (8KB chunks, emit every 100KB)
- File system storage
- Model deletion

**ModelCatalog.kt** (119 lines)
- Defines 5 GGUF models
- Metadata: name, size, URL, quantization
- Helper methods: getModelById, formatSize

**GGUFModelScreen.kt** (UI)
- Model selection cards
- Download progress bar
- Auto-navigation to chat when ready

**ChatScreen.kt** (UI)
- Message list with auto-scroll
- Input field with send button
- "Новый чат" button clears history
- Loading indicator during inference

**AppModule.kt** (DI configuration)
- HttpClient for downloads
- GGUFModelRepository (singleton)
- LLMClient → LlamaCppClient (auto-detects first .gguf file)
- ChatViewModel
- GGUFModelViewModel

## Testing Strategy

### Model Download Test
1. Launch app (first time)
2. Should show GGUFModelScreen
3. Select Llama 3.2 1B
4. Tap "Скачать модель"
5. Progress bar should update
6. Download completes → auto-navigate to chat

### Chat Inference Test
1. With model downloaded
2. Type "Hello, how are you?"
3. Tap send
4. Should see "Думаю..." indicator
5. AI response appears within 5-15 seconds
6. Response quality should be coherent

### Persistent Model Test
1. Download model
2. Close app
3. Relaunch app
4. Should auto-navigate to chat (skip download screen)
5. Model should load and respond

### Multi-turn Conversation
1. Send message "My name is Alex"
2. Send message "What's my name?"
3. AI should reference "Alex" (context maintained)

### Error Handling
1. Delete model file manually
2. Try to send message
3. Should show error message
4. Navigate back to download screen

## Common Development Tasks

### Add a new GGUF model

Edit `util/ModelCatalog.kt`:

```kotlin
GGUFModel(
    id = "new-model-id",
    name = "Display Name",
    description = "Описание на русском",
    sizeBytes = 1_000_000_000L,
    downloadUrl = "https://huggingface.co/user/repo/resolve/main/model.gguf",
    sha256 = "",
    quantization = "Q4_K_M",
    parameters = "1B",
    filename = "model-filename.gguf"
)
```

### Change default model

Edit `util/ModelCatalog.kt`:

```kotlin
val DEFAULT_MODEL = RECOMMENDED_MODELS[index]  // Change index
```

### Adjust context size

Edit `di/AppModule.kt`:

```kotlin
LlamaCppClient(
    context = androidContext(),
    modelPath = modelPath,
    contextSize = 4096  // Increase for longer conversations
)
```

**Note:** Higher context = more memory usage. Default 2048 is balanced for mobile.

### Update welcome message

Edit `viewmodel/ChatViewModel.kt`:

```kotlin
Message(
    text = "Your new welcome message here!",
    isFromUser = false
)
```

## Troubleshooting

### Error: Model not found
**Symptom:** "❌ Ошибка: Model file not found"
**Fix:** Check that model downloaded successfully. Navigate to GGUFModelScreen and re-download.

### Error: Out of memory
**Symptom:** App crashes during inference
**Fix:** Use smaller model (TinyLlama) or reduce context size to 1024.

### Error: Download fails
**Symptom:** "Client request invalid: 404 Not Found"
**Fix:** Verify HuggingFace URL in ModelCatalog.kt. Model may have been moved/renamed.

### App always shows download screen
**Symptom:** Model downloaded but app doesn't detect it
**Fix:** Check GGUFModelViewModel.checkForDownloadedModels() - should scan for any .gguf file.

### Slow inference
**Expected:** First inference after load: 5-15 seconds. Subsequent: 2-5 seconds.
**If slower:** Device may have insufficient RAM. Try smaller model.

## Dependency Management

This project uses Gradle version catalogs (`gradle/libs.versions.toml`).

**Major dependencies:**
- `llamatik` (0.15.0) - llama.cpp wrapper for GGUF inference
- `ktor-client-*` (3.0.0) - HTTP client for model downloads
- `koin-*` (3.5.6) - Dependency injection
- `kotlinx-serialization-json` (1.7.3) - JSON parsing
- `androidx-compose-*` (BOM 2024.09.00) - UI framework

**Removed dependencies** (no longer needed):
- Room (database) - Uses file system instead
- WorkManager - Direct Ktor downloads
- MediaPipe - Replaced by Llamatik

## Performance Considerations

**Model Load Time:**
- Cold start (first load): 5-10 seconds
- Depends on device CPU and model size

**Inference Time:**
- First message: 5-15 seconds
- Subsequent messages: 2-5 seconds
- Token generation: 15-25 tokens/sec (device-dependent)

**Memory Usage:**
- App baseline: ~85MB
- With model loaded: 1.5-3GB (depends on model)
- Recommendation: Device with 4GB+ RAM

**Storage:**
- App APK: ~50MB
- Model files: 669MB - 2.4GB
- Recommendation: 3GB+ free storage

## Security Considerations

**Network Security:**
- App downloads models over HTTPS
- No cleartext HTTP (unlike old Ollama setup)

**Data Privacy:**
- 100% offline operation after model download
- No data sent to external servers
- All inference happens on-device

**Model Integrity:**
- SHA256 checksum verification currently disabled
- Models downloaded from trusted HuggingFace repos (bartowski, TheBloke)

## Documentation Index

This project has comprehensive documentation across multiple files:

- **README.md**: User-facing quick start guide
- **CLAUDE.md** (this file): AI assistant development guide
- **GGUF_SETUP.md**: Detailed GGUF model setup
- **LLM_BEST_PRACTICES.md**: llama.cpp best practices
- **IMPLEMENTATION_SUMMARY.md**: Migration history (Days 25-27)
- **DEPLOYMENT_GUIDE.md**: Production deployment strategies
- **VERIFICATION_REPORT.md**: Testing results and compliance

When to read which file:
- 🚀 **Start the project?** → README.md → GGUF_SETUP.md
- 🏗️ **Understand architecture?** → CLAUDE.md → IMPLEMENTATION_SUMMARY.md
- 🚢 **Deploy to production?** → DEPLOYMENT_GUIDE.md
- ⚡ **Optimize performance?** → LLM_BEST_PRACTICES.md
- ✅ **Verify implementation?** → VERIFICATION_REPORT.md
