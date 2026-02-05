# save_joke Tool Documentation

## Overview

The `save_joke` tool saves jokes to a local SQLite database (`jokes.db`) for persistent storage and later retrieval. It supports both single-line jokes and two-part jokes (setup/delivery format), and can store jokes with or without JokeAPI IDs.

**Tool Type**: Local (uses local SQLite database)
**Emoji**: 💾
**Database**: `jokes.db`
**Table**: `saved_jokes`

---

## Implementation

**File**: `server/http_mcp_server.py`
**Lines**: 478-515
**Method**: `tool_save_joke(args: dict) -> dict`

---

## Parameters

### `type` (required)
- **Type**: String
- **Required**: Yes
- **Description**: Type of joke to save
- **Valid Values**:
  - `"single"` - Single-line joke
  - `"twopart"` - Two-part joke (setup/delivery)

### `joke_api_id` (optional)
- **Type**: Integer
- **Required**: No
- **Description**: Original ID from JokeAPI (if joke came from external API)
- **Example**: `123`

### `category` (optional)
- **Type**: String
- **Required**: No
- **Description**: Joke category (e.g., "Programming", "Misc", "Pun")
- **Example**: `"Programming"`

### `joke_text` (optional, required for single-type)
- **Type**: String
- **Required**: Yes for `type="single"`
- **Description**: Full text of single-line joke
- **Example**: `"Why do programmers prefer dark mode? Light attracts bugs!"`

### `setup` (optional, required for twopart-type)
- **Type**: String
- **Required**: Yes for `type="twopart"`
- **Description**: Setup/question part of two-part joke
- **Example**: `"Why do Java developers wear glasses?"`

### `delivery` (optional, required for twopart-type)
- **Type**: String
- **Required**: Yes for `type="twopart"`
- **Description**: Punchline/answer part of two-part joke
- **Example**: `"Because they can't C#!"`

---

## Returns

### Success Response
```json
{
  "success": true,
  "message": "Joke saved successfully",
  "saved_joke_id": 1
}
```

### Error Response
```json
{
  "success": false,
  "error": "Missing required field: type",
  "saved_joke_id": null
}
```

---

## Database Schema

```sql
CREATE TABLE saved_jokes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    joke_api_id INTEGER,
    category TEXT,
    type TEXT,           -- 'single' or 'twopart'
    joke_text TEXT,      -- For single-type jokes
    setup TEXT,          -- For twopart jokes
    delivery TEXT,       -- For twopart jokes
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Implementation Details

### Save Operation

```python
def tool_save_joke(args: dict) -> dict:
    try:
        # 1. Extract parameters
        joke_type = args.get('type')
        if not joke_type:
            return {
                'success': False,
                'error': 'Missing required field: type',
                'saved_joke_id': None
            }

        joke_api_id = args.get('joke_api_id')
        category = args.get('category')

        # 2. Build SQL query based on joke type
        if joke_type == 'single':
            joke_text = args.get('joke_text', '')
            cursor.execute('''
                INSERT INTO saved_jokes
                (joke_api_id, category, type, joke_text)
                VALUES (?, ?, ?, ?)
            ''', (joke_api_id, category, 'single', joke_text))

        elif joke_type == 'twopart':
            setup = args.get('setup', '')
            delivery = args.get('delivery', '')
            cursor.execute('''
                INSERT INTO saved_jokes
                (joke_api_id, category, type, setup, delivery)
                VALUES (?, ?, ?, ?, ?)
            ''', (joke_api_id, category, 'twopart', setup, delivery))

        # 3. Commit and get inserted ID
        conn.commit()
        saved_joke_id = cursor.lastrowid

        # 4. Return success
        return {
            'success': True,
            'message': 'Joke saved successfully',
            'saved_joke_id': saved_joke_id
        }

    except Exception as e:
        return {
            'success': False,
            'error': str(e),
            'saved_joke_id': None
        }
```

### UTF-8 Support

The database supports Russian and other Unicode text:
```python
conn = sqlite3.connect('jokes.db')
conn.text_factory = str  # UTF-8 encoding
```

---

## Usage Examples

### Example 1: Save Single-Line Joke from JokeAPI

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "save_joke",
      "arguments": {
        "joke_api_id": 123,
        "category": "Programming",
        "type": "single",
        "joke_text": "Why do programmers prefer dark mode? Light attracts bugs!"
      }
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
        "text": "{\"success\":true,\"message\":\"Joke saved successfully\",\"saved_joke_id\":1}"
      }
    ]
  }
}
```

### Example 2: Save Two-Part Joke

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "save_joke",
      "arguments": {
        "joke_api_id": 456,
        "category": "Programming",
        "type": "twopart",
        "setup": "Why do Java developers wear glasses?",
        "delivery": "Because they can'\''t C#!"
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
        "text": "{\"success\":true,\"message\":\"Joke saved successfully\",\"saved_joke_id\":2}"
      }
    ]
  }
}
```

### Example 3: Save Custom Joke (No JokeAPI ID)

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "save_joke",
      "arguments": {
        "category": "Custom",
        "type": "single",
        "joke_text": "My custom joke about developers and coffee"
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
        "text": "{\"success\":true,\"message\":\"Joke saved successfully\",\"saved_joke_id\":3}"
      }
    ]
  }
}
```

### Example 4: Save Russian Joke

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "save_joke",
      "arguments": {
        "category": "Программирование",
        "type": "single",
        "joke_text": "Почему программисты предпочитают тёмную тему? Свет привлекает баги!"
      }
    }
  }'
```

**Response**:
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"success\":true,\"message\":\"Joke saved successfully\",\"saved_joke_id\":4}"
      }
    ]
  }
}
```

---

## Integration with Android App

### Agentic Workflow

The save_joke tool is typically called as a follow-up to get_joke:

```
User: "Расскажи шутку и сохрани её"
     ↓
API calls get_joke
     ↓
Returns joke (ID: 123, text: "...")
     ↓
ChatViewModel stores joke in lastJokeResult
     ↓
API calls save_joke with joke data
     ↓
ChatViewModel executes save_joke with stored data
     ↓
Returns success: "Joke saved successfully"
     ↓
API response: "Вот шутка про программистов: ... Я сохранил её для вас!"
```

### Tool Execution

**File**: `app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt`
**Lines**: 354-410

```kotlin
"save_joke" -> {
    logD("💾 Calling MCP server save_joke")

    // Use lastJokeResult from previous get_joke call
    val jokeData = lastJokeResult?.let { result ->
        val text = result["content"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.content

        text?.let { Json.parseToJsonElement(it).jsonObject }
    }

    if (jokeData == null) {
        logE("No joke result available to save")
        return buildJsonObject {
            put("error", "No joke available to save")
        }
    }

    // Build save_joke arguments
    val mcpArgs = buildJsonObject {
        put("type", jokeData["type"]?.jsonPrimitive?.content ?: "single")

        jokeData["id"]?.jsonPrimitive?.int?.let {
            put("joke_api_id", it)
        }

        jokeData["category"]?.jsonPrimitive?.content?.let {
            put("category", it)
        }

        if (jokeData["type"]?.jsonPrimitive?.content == "single") {
            jokeData["joke"]?.jsonPrimitive?.content?.let {
                put("joke_text", it)
            }
        } else {
            jokeData["setup"]?.jsonPrimitive?.content?.let {
                put("setup", it)
            }
            jokeData["delivery"]?.jsonPrimitive->content?.let {
                put("delivery", it)
            }
        }
    }

    // Execute tool
    val result = mcpClient.callTool(
        toolName = "save_joke",
        arguments = mcpArgs
    )

    result
}
```

---

## Error Handling

### Missing Required Field
```json
{
  "success": false,
  "error": "Missing required field: type",
  "saved_joke_id": null
}
```

**Cause**: `type` parameter not provided

**Solution**: Always include `type` field with value "single" or "twopart"

### Database Error
```json
{
  "success": false,
  "error": "database is locked",
  "saved_joke_id": null
}
```

**Cause**: Another process is writing to the database

**Solution**: Retry after a short delay

### Invalid Type
```json
{
  "success": false,
  "error": "Invalid joke type",
  "saved_joke_id": null
}
```

**Cause**: `type` field has invalid value (not "single" or "twopart")

**Solution**: Use only "single" or "twopart" as type values

---

## Testing

### Unit Tests

**File**: `server/test_http_mcp_server.py`
**Test Class**: `TestSaveJokeTool`

**Tests**:
1. `test_save_single_joke` - Save single-line joke
2. `test_save_twopart_joke` - Save two-part joke
3. `test_save_joke_without_api_id` - Save custom joke
4. `test_save_joke_russian_text` - Save Russian joke (UTF-8)

**Run tests**:
```bash
cd server
python3 test_http_mcp_server.py TestSaveJokeTool -v
```

### Manual Testing

**Test 1: Save single joke**
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"save_joke","arguments":{"type":"single","joke_text":"Test joke"}}}'
```

**Expected**: Returns success with saved_joke_id

**Test 2: Verify saved in database**
```bash
sqlite3 jokes.db "SELECT * FROM saved_jokes ORDER BY id DESC LIMIT 1;"
```

**Expected**: Shows the just-saved joke

**Test 3: Save without required field**
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"save_joke","arguments":{"joke_text":"Test"}}}'
```

**Expected**: Returns error about missing type field

---

## Database Management

### View All Saved Jokes
```bash
sqlite3 jokes.db "SELECT id, category, type, joke_text, setup, delivery, saved_at FROM saved_jokes ORDER BY saved_at DESC;"
```

### Count Saved Jokes
```bash
sqlite3 jokes.db "SELECT COUNT(*) as total FROM saved_jokes;"
```

### Delete Specific Joke
```bash
sqlite3 jokes.db "DELETE FROM saved_jokes WHERE id = 1;"
```

### Clear All Jokes
```bash
sqlite3 jokes.db "DELETE FROM saved_jokes;"
```

### Backup Database
```bash
cp jokes.db jokes_backup_$(date +%Y%m%d).db
```

---

## Performance

- **Response Time**: < 10ms (local database operation)
- **Database Size**: ~1 KB per joke
- **Concurrency**: SQLite handles multiple reads, serializes writes
- **Max Storage**: Limited by disk space

---

## Related Documentation

- [GET_JOKE.md](GET_JOKE.md) - Fetch jokes from JokeAPI
- [GET_SAVED_JOKES.md](GET_SAVED_JOKES.md) - Retrieve saved jokes
- [CHAT_SCREEN.md](../../docs/CHAT_SCREEN.md) - Chat interface integration
- [SERVER_README.md](../SERVER_README.md) - Database schema details

---

## Version

**Day 18**: Реранкинг и фильтрация

**Last Updated**: 2026-02-04
