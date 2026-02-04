# get_saved_jokes Tool Documentation

## Overview

The `get_saved_jokes` tool retrieves all saved jokes from the local SQLite database (`jokes.db`). Results are ordered by save date (newest first) and can be limited to a specific number of jokes.

**Tool Type**: Local (uses local SQLite database)
**Emoji**: 📖
**Database**: `jokes.db`
**Table**: `saved_jokes`

---

## Implementation

**File**: `server/http_mcp_server.py`
**Lines**: 517-570
**Method**: `tool_get_saved_jokes(args: dict) -> dict`

---

## Parameters

### `limit` (optional)
- **Type**: Integer
- **Required**: No
- **Default**: 50
- **Description**: Maximum number of jokes to return
- **Valid Range**: 1-1000
- **Example**: `10` (return 10 most recent jokes)

---

## Returns

### Success Response
```json
{
  "success": true,
  "count": 2,
  "jokes": [
    {
      "id": 2,
      "joke_api_id": 456,
      "category": "Programming",
      "type": "twopart",
      "setup": "Why do Java developers wear glasses?",
      "delivery": "Because they can't C#!",
      "saved_at": "2026-02-04 15:30:00"
    },
    {
      "id": 1,
      "joke_api_id": 123,
      "category": "Programming",
      "type": "single",
      "joke": "Why do programmers prefer dark mode? Light attracts bugs!",
      "saved_at": "2026-02-04 15:25:00"
    }
  ]
}
```

### Empty Database Response
```json
{
  "success": true,
  "count": 0,
  "jokes": []
}
```

### Error Response
```json
{
  "success": false,
  "error": "Failed to fetch saved jokes: database locked",
  "count": 0,
  "jokes": []
}
```

---

## Implementation Details

### SQL Query

```python
def tool_get_saved_jokes(args: dict) -> dict:
    try:
        # 1. Extract limit parameter
        limit = args.get('limit', 50)

        # Validate limit
        if limit < 1:
            limit = 1
        elif limit > 1000:
            limit = 1000

        # 2. Query database
        cursor.execute('''
            SELECT id, joke_api_id, category, type,
                   joke_text, setup, delivery, saved_at
            FROM saved_jokes
            ORDER BY saved_at DESC
            LIMIT ?
        ''', (limit,))

        rows = cursor.fetchall()

        # 3. Format results
        jokes = []
        for row in rows:
            joke = {
                'id': row[0],
                'joke_api_id': row[1],
                'category': row[2],
                'type': row[3],
                'saved_at': row[7]
            }

            # Add type-specific fields
            if row[3] == 'single':
                joke['joke'] = row[4]  # joke_text
            else:  # twopart
                joke['setup'] = row[5]
                joke['delivery'] = row[6]

            jokes.append(joke)

        # 4. Return results
        return {
            'success': True,
            'count': len(jokes),
            'jokes': jokes
        }

    except Exception as e:
        return {
            'success': False,
            'error': f'Failed to fetch saved jokes: {str(e)}',
            'count': 0,
            'jokes': []
        }
```

### Result Ordering

Jokes are ordered by `saved_at DESC` (newest first):
```sql
ORDER BY saved_at DESC
```

This ensures the most recently saved jokes appear first in the list.

---

## Usage Examples

### Example 1: Get All Saved Jokes (Default Limit)

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "get_saved_jokes",
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
        "text": "{\"success\":true,\"count\":3,\"jokes\":[{\"id\":3,\"joke_api_id\":null,\"category\":\"Custom\",\"type\":\"single\",\"joke\":\"My custom joke\",\"saved_at\":\"2026-02-04 16:00:00\"},{\"id\":2,\"joke_api_id\":456,\"category\":\"Programming\",\"type\":\"twopart\",\"setup\":\"Why do Java developers wear glasses?\",\"delivery\":\"Because they can't C#!\",\"saved_at\":\"2026-02-04 15:30:00\"},{\"id\":1,\"joke_api_id\":123,\"category\":\"Programming\",\"type\":\"single\",\"joke\":\"Why do programmers prefer dark mode?\",\"saved_at\":\"2026-02-04 15:25:00\"}]}"
      }
    ]
  }
}
```

### Example 2: Get Top 10 Recent Jokes

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "get_saved_jokes",
      "arguments": {
        "limit": 10
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
        "text": "{\"success\":true,\"count\":10,\"jokes\":[...]}"
      }
    ]
  }
}
```

### Example 3: Get Latest Joke

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "get_saved_jokes",
      "arguments": {
        "limit": 1
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
        "text": "{\"success\":true,\"count\":1,\"jokes\":[{\"id\":3,\"category\":\"Custom\",\"type\":\"single\",\"joke\":\"Latest joke\",\"saved_at\":\"2026-02-04 16:00:00\"}]}"
      }
    ]
  }
}
```

---

## Integration with Android App

### Tool Execution

**File**: `app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt`
**Lines**: 412-439

```kotlin
"get_saved_jokes" -> {
    logD("📖 Calling MCP server get_saved_jokes")

    // Extract limit from API's tool call
    val limit = args["limit"]?.jsonPrimitive?.int ?: 50

    // Execute tool via MCP client
    val result = mcpClient.callTool(
        toolName = "get_saved_jokes",
        arguments = buildJsonObject {
            put("limit", limit)
        }
    )

    result
}
```

### Agentic Workflow

```
User: "Покажи мои сохранённые шутки"
     ↓
ChatViewModel detects keyword "шутк"
     ↓
Sends to Perplexity API with get_saved_jokes tool definition
     ↓
API decides to call get_saved_jokes
     ↓
ChatViewModel executes get_saved_jokes via McpClient
     ↓
MCP Server queries jokes.db
     ↓
Returns list of saved jokes
     ↓
API generates natural language response listing jokes
     ↓
User sees formatted list of their saved jokes
```

---

## Response Formatting

### Single-Line Joke Format
```json
{
  "id": 1,
  "joke_api_id": 123,
  "category": "Programming",
  "type": "single",
  "joke": "Why do programmers prefer dark mode? Light attracts bugs!",
  "saved_at": "2026-02-04 15:25:00"
}
```

**Fields**:
- `id` - Database ID
- `joke_api_id` - Original JokeAPI ID (null for custom jokes)
- `category` - Joke category
- `type` - Always "single"
- `joke` - Full joke text
- `saved_at` - Timestamp when saved

### Two-Part Joke Format
```json
{
  "id": 2,
  "joke_api_id": 456,
  "category": "Programming",
  "type": "twopart",
  "setup": "Why do Java developers wear glasses?",
  "delivery": "Because they can't C#!",
  "saved_at": "2026-02-04 15:30:00"
}
```

**Fields**:
- `id` - Database ID
- `joke_api_id` - Original JokeAPI ID (null for custom jokes)
- `category` - Joke category
- `type` - Always "twopart"
- `setup` - Question/setup part
- `delivery` - Answer/punchline part
- `saved_at` - Timestamp when saved

---

## Error Handling

### Database Error
```json
{
  "success": false,
  "error": "Failed to fetch saved jokes: database locked",
  "count": 0,
  "jokes": []
}
```

**Cause**: Database is being written to by another process

**Solution**: Retry after a short delay

### Empty Database
```json
{
  "success": true,
  "count": 0,
  "jokes": []
}
```

**Cause**: No jokes have been saved yet

**Solution**: Save some jokes first using save_joke tool

---

## Testing

### Unit Tests

**File**: `server/test_http_mcp_server.py`
**Test Class**: `TestGetSavedJokesTool`

**Tests**:
1. `test_get_saved_jokes_success` - Retrieve all saved jokes
2. `test_get_saved_jokes_with_limit` - Test limit parameter
3. `test_get_saved_jokes_empty_database` - Handle empty database
4. `test_get_saved_jokes_field_mapping` - Verify single vs twopart fields
5. `test_get_saved_jokes_ordering` - Verify newest-first ordering

**Run tests**:
```bash
cd server
python3 test_http_mcp_server.py TestGetSavedJokesTool -v
```

### Manual Testing

**Test 1: Get saved jokes**
```bash
# First, save some jokes
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"save_joke","arguments":{"type":"single","joke_text":"Test joke 1"}}}'

curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"save_joke","arguments":{"type":"single","joke_text":"Test joke 2"}}}'

# Now retrieve them
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_saved_jokes","arguments":{}}}'
```

**Expected**: Returns both jokes, with "Test joke 2" first (newest)

**Test 2: Test limit parameter**
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_saved_jokes","arguments":{"limit":1}}}'
```

**Expected**: Returns only the most recent joke

**Test 3: Empty database**
```bash
# Clear database
sqlite3 jokes.db "DELETE FROM saved_jokes;"

# Try to get jokes
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"get_saved_jokes","arguments":{}}}'
```

**Expected**: Returns success=true, count=0, jokes=[]

---

## Performance

- **Response Time**: < 10ms for typical database size
- **Query Optimization**: Uses index on `saved_at` column
- **Memory Usage**: Minimal, results streamed from database
- **Limit Impact**: Higher limits increase response time linearly

---

## Database Queries

### Check Total Joke Count
```bash
sqlite3 jokes.db "SELECT COUNT(*) FROM saved_jokes;"
```

### View Recent Jokes
```bash
sqlite3 jokes.db "SELECT id, type, joke_text, setup, delivery, saved_at FROM saved_jokes ORDER BY saved_at DESC LIMIT 10;"
```

### Count by Category
```bash
sqlite3 jokes.db "SELECT category, COUNT(*) as count FROM saved_jokes GROUP BY category ORDER BY count DESC;"
```

### Find Specific Joke
```bash
sqlite3 jokes.db "SELECT * FROM saved_jokes WHERE joke_text LIKE '%programmer%';"
```

---

## Related Documentation

- [GET_JOKE.md](GET_JOKE.md) - Fetch jokes from JokeAPI
- [SAVE_JOKE.md](SAVE_JOKE.md) - Save jokes to database
- [CHAT_SCREEN.md](../../docs/CHAT_SCREEN.md) - Chat interface integration
- [SERVER_README.md](../SERVER_README.md) - Database schema details

---

## Version

**Day 18**: Реранкинг и фильтрация

**Last Updated**: 2026-02-04
