# get_joke Tool Documentation

## Overview

The `get_joke` tool fetches random jokes from JokeAPI v2 (https://v2.jokeapi.dev). It supports filtering by category and blacklist flags, and handles both single-line jokes and two-part jokes (setup/delivery format).

**Tool Type**: Local (no proxying)
**Emoji**: 🎭

---

## Implementation

**File**: `server/http_mcp_server.py`
**Lines**: 428-476
**Method**: `tool_get_joke(args: dict) -> dict`

---

## Parameters

### `category` (optional)
- **Type**: String
- **Default**: "Any"
- **Description**: Joke category to fetch from
- **Valid Values**:
  - `"Any"` - Random category
  - `"Programming"` - Programming-related jokes
  - `"Misc"` - Miscellaneous jokes
  - `"Dark"` - Dark humor (use with caution)
  - `"Pun"` - Puns and wordplay
  - `"Spooky"` - Halloween/spooky jokes
  - `"Christmas"` - Holiday jokes

### `blacklistFlags` (optional)
- **Type**: String (comma-separated)
- **Default**: None
- **Description**: Content flags to exclude from results
- **Valid Values**:
  - `"nsfw"` - Not safe for work content
  - `"religious"` - Religious content
  - `"political"` - Political content
  - `"racist"` - Racist content
  - `"sexist"` - Sexist content
  - `"explicit"` - Explicit language
- **Example**: `"nsfw,racist,sexist"` (exclude multiple flags)

---

## Returns

### Success Response

#### Single-Type Joke
```json
{
  "category": "Programming",
  "type": "single",
  "id": 123,
  "joke": "Why do programmers prefer dark mode? Light attracts bugs!"
}
```

#### Two-Part Joke
```json
{
  "category": "Programming",
  "type": "twopart",
  "id": 456,
  "setup": "Why do Java developers wear glasses?",
  "delivery": "Because they can't C#!"
}
```

### Error Response
```json
{
  "error": true,
  "message": "Failed to fetch joke from JokeAPI",
  "details": "Network timeout"
}
```

---

## Implementation Details

### HTTP Request to JokeAPI

```python
def tool_get_joke(args: dict) -> dict:
    try:
        # 1. Extract parameters
        category = args.get('category', 'Any')
        blacklist_flags = args.get('blacklistFlags', '')

        # 2. Build API URL
        base_url = f"https://v2.jokeapi.dev/joke/{category}"
        params = []

        if blacklist_flags:
            params.append(f"blacklistFlags={blacklist_flags}")

        url = base_url + ("?" + "&".join(params) if params else "")

        # 3. Make HTTP request
        req = urllib.request.Request(url, headers={
            'User-Agent': 'AI-with-Love-MCP-Server/1.0'
        })

        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode('utf-8'))

        # 4. Check for errors
        if data.get('error', False):
            return {
                'error': True,
                'message': data.get('message', 'Unknown error from JokeAPI')
            }

        # 5. Return joke data
        if data['type'] == 'single':
            return {
                'category': data['category'],
                'type': 'single',
                'id': data['id'],
                'joke': data['joke']
            }
        else:  # twopart
            return {
                'category': data['category'],
                'type': 'twopart',
                'id': data['id'],
                'setup': data['setup'],
                'delivery': data['delivery']
            }

    except Exception as e:
        return {
            'error': True,
            'message': f'Failed to fetch joke: {str(e)}'
        }
```

### JokeAPI Response Handling

**Single-line joke response from API**:
```json
{
  "error": false,
  "category": "Programming",
  "type": "single",
  "joke": "Why do programmers prefer dark mode? Light attracts bugs!",
  "flags": {
    "nsfw": false,
    "religious": false,
    "political": false,
    "racist": false,
    "sexist": false,
    "explicit": false
  },
  "id": 123,
  "safe": true,
  "lang": "en"
}
```

**Two-part joke response from API**:
```json
{
  "error": false,
  "category": "Programming",
  "type": "twopart",
  "setup": "Why do Java developers wear glasses?",
  "delivery": "Because they can't C#!",
  "flags": {
    "nsfw": false,
    "religious": false,
    "political": false,
    "racist": false,
    "sexist": false,
    "explicit": false
  },
  "id": 456,
  "safe": true,
  "lang": "en"
}
```

---

## Usage Examples

### Example 1: Get Random Joke (Any Category)

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "get_joke",
      "arguments": {}
    }
  }'
```

**Response**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"category\":\"Misc\",\"type\":\"single\",\"id\":789,\"joke\":\"I told my wife she was drawing her eyebrows too high. She looked surprised.\"}"
      }
    ]
  }
}
```

### Example 2: Get Programming Joke

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "get_joke",
      "arguments": {
        "category": "Programming"
      }
    }
  }'
```

**Response**:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"category\":\"Programming\",\"type\":\"twopart\",\"id\":456,\"setup\":\"Why do Java developers wear glasses?\",\"delivery\":\"Because they can't C#!\"}"
      }
    ]
  }
}
```

### Example 3: Get Joke with Blacklist Flags

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "get_joke",
      "arguments": {
        "category": "Any",
        "blacklistFlags": "nsfw,racist,sexist,explicit"
      }
    }
  }'
```

**Response**:
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"category\":\"Pun\",\"type\":\"single\",\"id\":321,\"joke\":\"I'm reading a book about anti-gravity. It's impossible to put down!\"}"
      }
    ]
  }
}
```

---

## Integration with Android App

### Tool Detection

**File**: `app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt`
**Lines**: ~80-106

```kotlin
private fun userMentionsJokes(message: String): Boolean {
    val lowerMessage = message.lowercase()
    val keywords = listOf(
        "шутк",      // Russian: joke
        "анекдот",   // Russian: anecdote
        "смешн",     // Russian: funny
        "joke",
        "funny"
    )
    return keywords.any { lowerMessage.contains(it) }
}
```

### Tool Execution

**File**: `app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt`
**Lines**: 298-351

```kotlin
"get_joke" -> {
    logD("🎭 Calling MCP server get_joke")

    // Extract parameters from Perplexity API's tool call
    val category = args["category"]?.jsonPrimitive?.contentOrNull
    val blacklistFlags = args["blacklistFlags"]?.jsonPrimitive?.contentOrNull

    // Build arguments for MCP tool call
    val mcpArgs = buildJsonObject {
        category?.let { put("category", it) }
        blacklistFlags?.let { put("blacklistFlags", it) }
    }

    // Execute tool via MCP client
    val result = mcpClient.callTool(
        toolName = "get_joke",
        arguments = mcpArgs
    )

    // Store result for potential save operation
    lastJokeResult = result

    result
}
```

### Agentic Workflow

```
User: "Расскажи шутку про программистов"
     ↓
ChatViewModel detects keyword "шутк"
     ↓
Sends to Perplexity API with get_joke tool definition
     ↓
API decides to call get_joke with category="Programming"
     ↓
ChatViewModel executes get_joke via McpClient
     ↓
MCP Server calls JokeAPI
     ↓
Returns joke to ChatViewModel
     ↓
ChatViewModel submits result back to API
     ↓
API generates natural language response with joke
     ↓
User sees: "Вот шутка про программистов: Why do Java developers wear glasses? Because they can't C#!"
```

---

## Error Handling

### Network Timeout
```json
{
  "error": true,
  "message": "Failed to fetch joke: timed out"
}
```

**Cause**: JokeAPI didn't respond within 10 seconds

**Solution**: Retry the request or check internet connectivity

### Invalid Category
```json
{
  "error": true,
  "message": "No matching joke found",
  "causedBy": ["No jokes available in this category with the given filters"]
}
```

**Cause**: Category doesn't exist or no jokes match the blacklist filters

**Solution**: Use a different category or relax blacklist flags

### JokeAPI Rate Limit
```json
{
  "error": true,
  "message": "Too many requests. Please try again later."
}
```

**Cause**: Exceeded JokeAPI rate limit (120 requests per minute for free tier)

**Solution**: Wait 60 seconds before retrying

---

## Testing

### Unit Tests

**File**: `server/test_http_mcp_server.py`
**Test Class**: `TestGetJokeTool`

**Tests**:
1. `test_get_single_joke_success` - Single-line joke fetching
2. `test_get_twopart_joke_success` - Two-part joke fetching
3. `test_get_joke_with_blacklist` - Blacklist flags support
4. `test_get_joke_api_error` - JokeAPI error handling
5. `test_get_joke_network_error` - Network error handling

**Run tests**:
```bash
cd server
python3 test_http_mcp_server.py TestGetJokeTool -v
```

### Manual Testing

**Test 1: Basic functionality**
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_joke","arguments":{}}}'
```

**Expected**: Returns a random joke from any category

**Test 2: Category filtering**
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_joke","arguments":{"category":"Programming"}}}'
```

**Expected**: Returns a programming-related joke

**Test 3: Blacklist flags**
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_joke","arguments":{"blacklistFlags":"nsfw,racist,sexist"}}}'
```

**Expected**: Returns a safe joke without NSFW, racist, or sexist content

---

## Performance

- **Response Time**: 200-500ms (depends on JokeAPI response time)
- **Timeout**: 10 seconds
- **Caching**: No caching (always fetches fresh jokes)
- **Rate Limit**: JokeAPI allows 120 requests/minute

---

## Related Documentation

- [SAVE_JOKE.md](SAVE_JOKE.md) - Save jokes to database
- [GET_SAVED_JOKES.md](GET_SAVED_JOKES.md) - Retrieve saved jokes
- [CHAT_SCREEN.md](../../docs/CHAT_SCREEN.md) - Chat interface integration
- [SERVER_README.md](../SERVER_README.md) - MCP server overview

---

## External Resources

- **JokeAPI Documentation**: https://v2.jokeapi.dev/
- **JokeAPI Categories**: https://v2.jokeapi.dev/categories
- **JokeAPI Flags**: https://v2.jokeapi.dev/flags

---

## Version

**Day 18**: Реранкинг и фильтрация

**Last Updated**: 2026-02-04
