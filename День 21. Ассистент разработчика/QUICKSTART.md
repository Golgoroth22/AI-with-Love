# Quick Start Guide - Day 21

## Prerequisites

### 1. Local Ollama (Required for local processing)

```bash
# Install Ollama
brew install ollama  # macOS
# or visit https://ollama.ai for other platforms

# Start Ollama service
ollama serve

# Pull embedding model (in another terminal)
ollama pull nomic-embed-text

# Verify it works
curl http://localhost:11434/api/embeddings -d '{"model":"nomic-embed-text","prompt":"test"}'
```

### 2. Android Studio

- Android Studio Hedgehog or later
- Kotlin 1.9+
- Android SDK 34

### 3. Remote MCP Server (Optional for testing)

The app connects to remote server at `http://148.253.209.151:8081` by default.

## Running the App

### Option 1: Android Emulator (Recommended)

```bash
# Build and run
./gradlew installDebug

# Or run from Android Studio
# Run > Run 'app'
```

**Important**: Emulator automatically uses `http://10.0.2.2:11434` to access host's Ollama.

### Option 2: Physical Device

1. **Find your machine's IP address:**
   ```bash
   ifconfig | grep "inet "  # macOS/Linux
   ipconfig                  # Windows
   ```

2. **Update ServerConfig.kt:**
   ```kotlin
   const val OLLAMA_API_URL = "http://YOUR_IP:11434"
   ```

3. **Rebuild and install:**
   ```bash
   ./gradlew installDebug
   ```

## Testing Local Processing

### 1. Navigate to Ollama Screen

Launch app → Click "Ollama Screen" button

### 2. Upload a Document

- Click "📎 Upload Document" button
- Select a file:
  - **.md** (Markdown) - e.g., CLAUDE.md
  - **.txt** (Text file)
  - **.pdf** (PDF document)

### 3. Watch the Magic

You'll see real-time progress:

```
Reading CLAUDE.md...
↓
Chunking text locally...
↓
Processing chunk 1/16...
Processing chunk 2/16...
...
↓
✅ CLAUDE.md обработан локально!
📊 Создано фрагментов: 16/16
⏱️ Время обработки: 45s
🔮 Эмбеддинги созданы локально через Ollama!
```

### 4. Test Semantic Search

1. Navigate to Chat Screen
2. Ask: "найди в документах информацию о архитектуре"
3. The AI will use semantic search to find relevant chunks

## Performance Expectations

| File Size | Chunks | Expected Time |
|-----------|--------|---------------|
| 5 KB | ~5 | < 15 seconds |
| 15 KB | ~16 | ~45 seconds |
| 50 KB | ~50 | ~2 minutes |
| 100 KB | ~100 | ~4 minutes |

**Note**: Times assume local Ollama is running and warmed up.

## Troubleshooting

### "Failed to generate embedding: Connection refused"

**Problem**: Can't connect to local Ollama

**Solution**:
```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# If not, start it
ollama serve
```

### "Model 'nomic-embed-text' not found"

**Problem**: Embedding model not downloaded

**Solution**:
```bash
ollama pull nomic-embed-text
```

### Slow Processing

**Problem**: Each chunk takes > 5 seconds

**Possible causes**:
1. Ollama running on CPU (no GPU)
2. Model not loaded in memory (first request is slow)
3. System under heavy load

**Solutions**:
1. Check GPU availability: `nvidia-smi`
2. Pre-warm Ollama:
   ```bash
   curl http://localhost:11434/api/embeddings -d '{"model":"nomic-embed-text","prompt":"warmup"}'
   ```
3. Close other heavy applications

### Emulator Can't Connect to Ollama

**Problem**: `10.0.2.2` not working

**Solution**:
1. Check Ollama listening on all interfaces:
   ```bash
   OLLAMA_HOST=0.0.0.0 ollama serve
   ```

2. Or use `localhost` binding and port forwarding:
   ```bash
   adb forward tcp:11434 tcp:11434
   # Then use http://localhost:11434 in app
   ```

## Development Workflow

### 1. Run Tests

```bash
# Android tests
./gradlew test

# Specific test
./gradlew test --tests ChatViewModelProductionTest

# MCP server tests
cd server
python3 test_http_mcp_server.py
```

### 2. Format Code

```bash
./gradlew formatKotlin
```

### 3. Lint Check

```bash
./gradlew lintDebug
```

### 4. Build Release APK

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## Key Files

| File | Description |
|------|-------------|
| `OllamaViewModel.kt` | Document processing logic |
| `OllamaClient.kt` | Local Ollama API client |
| `TextChunker.kt` | Text chunking utility |
| `ServerConfig.kt` | Server URLs configuration |
| `LOCAL_PROCESSING.md` | Detailed architecture docs |
| `CLAUDE.md` | Full project documentation |

## Next Steps

1. ✅ Upload a test document (CLAUDE.md recommended)
2. ✅ Verify local processing works
3. ✅ Test semantic search in Chat screen
4. ✅ Explore agentic AI patterns
5. ✅ Read `LOCAL_PROCESSING.md` for deep dive

## Support

- **Issues**: Check logcat for error messages
- **Logs**: Filter by "OllamaViewModel", "OllamaClient", "McpClient"
- **Documentation**: See `CLAUDE.md` for architecture details
- **Server**: See `server/` directory for MCP server code

## What's New in Day 21

### Architecture Changes

- **New**: `OllamaClient` for local embedding generation
- **New**: `TextChunker` for client-side text chunking
- **Updated**: `OllamaViewModel` now processes documents locally
- **Updated**: `AppModule` includes OllamaClient DI

### Performance Improvements

- **Before**: 320 seconds for 15KB file (remote processing)
- **After**: 45 seconds for 15KB file (local processing)
- **Improvement**: **7x faster** ⚡

### New Workflow

```
Read File → Chunk Locally → Generate Embeddings (Local Ollama) → Save to Remote Server
```

**Old workflow** (deprecated but still works):
```
Read File → Send to Server → Server Chunks + Embeds → Save to Server
```

## Have Fun!

You now have a powerful local + remote hybrid architecture for document processing with RAG capabilities. Experiment, build, and learn! 🚀
