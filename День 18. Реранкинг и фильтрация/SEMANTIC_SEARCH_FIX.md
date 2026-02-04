# Semantic Search Tool - Implementation Fix

## Problem Identified

The semantic_search tool was not being included in Agentic API requests even though it was implemented. The logs showed:

```
📤 Sending Agentic request with 4 tools
```

Instead of 5 tools (4 joke tools + semantic_search).

## Root Cause

The semantic search keywords were incorrectly added to the `userMentionsJokes()` function, causing them to trigger joke tools instead of the semantic_search tool.

**Before:**
```kotlin
private fun userMentionsJokes(message: String): Boolean {
    val keywords = listOf(
        "шутк", "анекдот",
        // ... joke keywords ...
        "расскажи о",  // ❌ Wrong! This should trigger semantic search
        "что такое",   // ❌ Wrong!
        "объясни"      // ❌ Wrong!
    )
}
```

This meant when the user said "Расскажи о bakemono", it triggered `useJokeTools = true`, but the tools list only included 4 joke tools.

## Solution Implemented

### 1. Separated Keyword Detection (ChatViewModel.kt)

**Created new function `userMentionsSemanticSearch()`:**
```kotlin
private fun userMentionsSemanticSearch(message: String): Boolean {
    val lowerMessage = message.lowercase()
    val keywords = listOf(
        "найди в документах",
        "поиск в базе",
        "что говорится в документах",
        "информация о",
        "расскажи о",  // ✅ Now correctly triggers semantic search
        "что такое",
        "как работает",
        "объясни",
        "документ"
    )
    return keywords.any { lowerMessage.contains(it) }
}
```

**Cleaned up `userMentionsJokes()`:**
- Removed semantic search keywords
- Kept only joke and test-related keywords

### 2. Updated Tool Detection Logic (ChatViewModel.kt:651-655)

**Before:**
```kotlin
val useJokeTools = isJokeServerEnabled() && userMentionsJokes(userMessage)
logD("🎭 Use Agentic API with all joke tools: $useJokeTools")
sendWithAgenticApi(userMessage, thinkingMessageIndex, useJokeTools)
```

**After:**
```kotlin
val useJokeTools = isJokeServerEnabled() && userMentionsJokes(userMessage)
val useSemanticSearch = isJokeServerEnabled() && userMentionsSemanticSearch(userMessage)
logD("🎭 Use Agentic API with joke tools: $useJokeTools, semantic search: $useSemanticSearch")
sendWithAgenticApi(userMessage, thinkingMessageIndex, useJokeTools, useSemanticSearch)
```

### 3. Modified sendWithAgenticApi Function (ChatViewModel.kt)

**Updated signature:**
```kotlin
private suspend fun sendWithAgenticApi(
    userMessage: String,
    thinkingMessageIndex: Int,
    useJokeTools: Boolean = false,
    useSemanticSearch: Boolean = false  // ✅ New parameter
)
```

**Dynamic tool list building:**
```kotlin
val tools = buildList {
    if (useJokeTools) {
        add(buildAgenticJokeTool())
        add(buildSaveJokeTool())
        add(buildGetSavedJokesTool())
        add(buildRunTestsTool())
    }
    if (useSemanticSearch) {
        add(buildSemanticSearchTool())  // ✅ Added conditionally
    }
}.takeIf { it.isNotEmpty() }
```

This means:
- If only joke keywords detected: 4 tools sent
- If only semantic search keywords detected: 1 tool sent
- If both detected: 5 tools sent
- If neither: 0 tools (null)

### 4. Created buildSemanticSearchTool() (ChatViewModel.kt:248-277)

```kotlin
private fun buildSemanticSearchTool(): AgenticTool {
    val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Question or search text to find relevant document chunks")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of relevant chunks to return")
                put("default", 3)
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("query"))  // ✅ Fixed: Use JsonPrimitive, not String
        }
    }

    return AgenticTool(
        type = "function",
        name = "semantic_search",
        description = "Search for relevant document chunks from indexed documents using semantic similarity...",
        parameters = parameters
    )
}
```

### 5. Updated buildInstructions() (ChatViewModel.kt:547-581)

**Updated signature:**
```kotlin
private fun buildInstructions(useJokeTools: Boolean, useSemanticSearch: Boolean): String
```

**Added semantic search instructions:**
```kotlin
if (useSemanticSearch) {
    instructions.add("""
When user asks a question or requests information about a topic, FIRST use the semantic_search tool to find relevant document chunks.
After receiving document chunks, use them as context to answer the user's question accurately.
Include the information from the retrieved documents in your answer.
If no relevant documents are found, answer based on your general knowledge and mention that no specific documents were found.""".trimIndent())
}
```

## Testing the Fix

### Expected Behavior Now

**Query: "Расскажи о bakemono"**
```
Expected logs:
🎭 Use Agentic API with joke tools: false, semantic search: true
📤 Sending Agentic request with 1 tools
🌐 Calling MCP server semantic_search
```

**Query: "Расскажи шутку"**
```
Expected logs:
🎭 Use Agentic API with joke tools: true, semantic search: false
📤 Sending Agentic request with 4 tools
🎭 Calling MCP server get_joke
```

**Query: "Расскажи шутку о документах"** (hypothetical - both keywords)
```
Expected logs:
🎭 Use Agentic API with joke tools: true, semantic search: true
📤 Sending Agentic request with 5 tools
```

### Manual Test

1. **Start local MCP server:**
```bash
cd server
python3 http_mcp_server.py
```

2. **Install updated app:**
```bash
./gradlew installDebug
```

3. **Test queries:**
- "Расскажи о MCP протоколе" → Should trigger semantic_search
- "Что такое Ollama?" → Should trigger semantic_search
- "Объясни embeddings" → Should trigger semantic_search
- "Расскажи шутку" → Should trigger get_joke (joke tools only)

4. **Check logcat:**
```bash
adb logcat | grep -E "ChatViewModel|semantic_search|Sending Agentic"
```

## Files Modified

1. **ChatViewModel.kt**
   - Line ~120: Added `userMentionsSemanticSearch()` function
   - Line ~80-106: Removed semantic search keywords from `userMentionsJokes()`
   - Line ~248-277: Added `buildSemanticSearchTool()` function
   - Line ~547-581: Updated `buildInstructions()` with semantic search parameter
   - Line ~652-655: Updated tool detection logic
   - Line ~658-662: Updated `sendWithAgenticApi()` signature
   - Line ~681-691: Dynamic tools list building

## Verification

**Compilation:** ✅ BUILD SUCCESSFUL
```bash
./gradlew compileDebugKotlin
```

**Expected Result:**
- Semantic search keywords now correctly trigger `semantic_search` tool
- Tool count in logs should match enabled features
- No interference between joke tools and semantic search

## Next Steps

1. **Deploy to remote server** (if needed):
```bash
cd server
./deploy_to_remote.sh
# Enter: 148.253.209.151
# Enter: root
```

2. **Test real queries** in the Android app
3. **Monitor logs** to verify correct tool selection
4. **Check remote server** has indexed documents ready
5. **Tune keyword detection** if needed based on usage patterns
