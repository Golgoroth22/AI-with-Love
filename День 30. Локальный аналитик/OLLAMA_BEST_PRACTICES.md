# Ollama Integration — Best Practices

**Model**: llama3.2:3b
**Last Updated**: February 25, 2026

---

## Applied Best Practices

### 1. `keep_alive` Parameter

Keeps model loaded in RAM between requests.

```kotlin
OllamaChatRequest(
    model = modelName,
    messages = messages,
    stream = false,
    keep_alive = "5m"
)
```

**Impact**:
- First request: ~20s (cold start)
- Subsequent requests (within 5m): ~2-5s (75%+ faster)

---

### 2. Runtime Options

Fine-tuned for the Japan guide persona:

```kotlin
OllamaOptions(
    temperature = 0.7,
    num_predict = 512,
    num_ctx = 4096,
    top_p = 0.9,
    repeat_penalty = 1.1
)
```

---

### 3. Error Handling

Specific HTTP status handling:

```kotlin
when (response.status.value) {
    404 -> throw OllamaClientException(
        "Model '$modelName' not found. Pull it with: ollama pull $modelName",
        errorBody.error
    )
    else -> throw OllamaClientException(
        "Ollama request failed: ${response.status.value}",
        errorBody.error
    )
}
```

---

### 4. NDJSON Response Handling

Ollama returns NDJSON even with `stream: false`. `parseOllamaResponse()` handles both formats:

```kotlin
return if (responseText.contains('\n')) {
    // NDJSON: concatenate content from all lines
    ...
} else {
    // Standard JSON
    json.decodeFromString<OllamaChatResponse>(responseText)
}
```

---

### 5. English-Only LLM Interaction

`llama3.2:3b` performs better in English than Russian. The app uses ML Kit to translate:
- User Russian input → English before sending to LLM
- LLM English response → Russian before showing in UI

This avoids forcing a small model to handle a language it wasn't well trained on.

---

## API Endpoints

```
POST /api/chat     ✅ Used — main chat endpoint
GET  /api/version  — health check
POST /api/tags     — list models (not used in app)
POST /api/generate — simple generation (not used, /api/chat is better)
```

---

## Performance

| Metric | Value |
|--------|-------|
| First request | ~20s (model cold start) |
| Subsequent requests | ~2-5s (keep_alive) |
| Max output tokens | 512 (num_predict) |
| Context window | 4096 tokens |

---

## Checklist

- [x] `/api/chat` endpoint
- [x] Proper message format (role + content)
- [x] Non-streaming for simplicity
- [x] Full conversation history
- [x] `keep_alive` for performance
- [x] HTTP status validation
- [x] Custom exception type
- [x] Runtime options (temperature, num_predict, num_ctx)
- [x] System prompt in English only
- [x] NDJSON response handling
- [ ] Streaming responses
- [ ] Image inputs
- [ ] Tool calling
- [ ] JSON mode
