# GGUF Model Setup Guide

## First Launch

1. Launch app on Android device (minSdk 26)
2. App shows **GGUFModelScreen** (model selection)
3. 5 models displayed with name, size, description
4. Default selected: **Llama 3.2 1B** (869MB)
5. Tap **"Скачать модель"** button
6. Download progress bar shows percentage
7. Download completes → automatic navigation to ChatScreen
8. Start chatting!

## Model Selection Guide

**Recommended for most users:**
- **Llama 3.2 1B Q4_K_M** (869MB) - Best balance of quality and speed

**For older/slower devices:**
- **TinyLlama 1.1B Q4_K_M** (669MB) - Smallest model, fastest inference

**For better quality:**
- **Gemma 2 2B Q4_K_M** (1.7GB) - Highest quality responses
- **Phi-3 Mini Q4_K_M** (2.4GB) - Good quality, optimized for mobile

**For programming tasks:**
- **Qwen 2.5 1.5B Q4_K_M** (1.1GB) - Best for code generation

## Storage Requirements

| Model | Size | RAM Required | Storage Needed |
|-------|------|--------------|----------------|
| TinyLlama 1.1B | 669MB | 2GB+ | 1GB |
| Llama 3.2 1B | 869MB | 2-3GB | 1.5GB |
| Qwen 2.5 1.5B | 1.1GB | 3-4GB | 2GB |
| Gemma 2 2B | 1.7GB | 4GB+ | 2.5GB |
| Phi-3 Mini | 2.4GB | 4-6GB | 3GB |

**Recommendation:** Device with 4GB+ RAM and 3GB+ free storage

## Download Details

**Source:** HuggingFace (bartowski, TheBloke repos)

**Method:** Direct HTTPS download via Ktor

**Storage location:** `{app_data}/files/models/`
- Example: `/data/data/com.example.aiwithlove/files/models/llama-3.2-1b-q4.gguf`

**Resume capability:** Currently not implemented (downloads start from beginning if interrupted)

**Progress tracking:** Updates every 100KB downloaded

## Troubleshooting

### Download Fails with 404 Error

**Symptom:** "Client request invalid: 404 Not Found"

**Cause:** HuggingFace model URL changed or model removed

**Fix:**
1. Check ModelCatalog.kt for correct URL
2. Verify model exists on HuggingFace
3. Update downloadUrl in ModelCatalog

### App Crashes During Model Load

**Symptom:** App closes when trying to load model

**Cause:** Insufficient RAM

**Fix:**
1. Use smaller model (TinyLlama 669MB)
2. Close other apps to free RAM
3. Restart device before launching

### Model Downloaded But App Asks to Download Again

**Symptom:** Model file exists but app shows download screen

**Cause:** GGUFModelViewModel not detecting file

**Fix:**
1. Check file exists: `adb shell ls /data/data/com.example.aiwithlove/files/models/`
2. Verify filename matches ModelCatalog.kt
3. Clear app data → re-download if needed

## Performance Expectations

### Model Load Time
- **TinyLlama 1.1B:** 2-5 seconds
- **Llama 3.2 1B:** 5-10 seconds
- **Gemma 2 2B:** 10-15 seconds

### Inference Speed
- **First message:** 5-15 seconds
- **Subsequent messages:** 2-5 seconds
- **Token generation:** 15-25 tokens/sec

**Depends on:**
- Device CPU (ARM Cortex-A75+ recommended)
- Available RAM
- Model size
- Context length
