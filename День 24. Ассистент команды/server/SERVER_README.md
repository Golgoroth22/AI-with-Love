# MCP Server - HTTP JSON-RPC Server with JokeAPI Integration

Python HTTP server implementing the Model Context Protocol (MCP) with JokeAPI integration and SQLite database for persistent joke storage.

> **🔒 Security Notice**: This README uses placeholders (`<SERVER_IP>`, `<USERNAME>`) for sensitive data. Actual credentials are stored in `app/src/main/java/com/example/aiwithlove/util/SecureData.kt` and should **NEVER** be committed to version control or shared publicly.

## Overview

This is a production MCP server deployed on a remote Linux server that provides four tools for joke management and testing:

1. **get_joke** - Fetch random jokes from JokeAPI
2. **save_joke** - Save jokes to SQLite database
3. **get_saved_jokes** - Retrieve saved jokes
4. **run_tests** - Run server tests in isolated Docker container

## Configuration

**Server credentials are stored in**: `app/src/main/java/com/example/aiwithlove/util/SecureData.kt`

```kotlin
object SecureData {
    val serverIp = "YOUR_SERVER_IP"
    val serverPort = 123
    val serverLogin = "YOUR_USERNAME"
    val serverPassword = "YOUR_PASSWORD"
}
```

**⚠️ IMPORTANT**: Never commit `SecureData.kt` with real credentials to version control!

## Quick Start

### Running Locally

```bash
python3 http_mcp_server.py
```

Server will start on `http://0.0.0.0:8080`

### Running Tests

```bash
python3 test_http_mcp_server.py
```

Expected output: `26 tests passed`

## Production Deployment

### Current Deployment

The server is deployed as a Docker container with:
- **Port**: 8080 (configurable)
- **Container**: mcp-jokes-server
- **Status**: Running with automatic restart

### Deployment Architecture

```
Remote Server
├── Docker Container (mcp-jokes-server)
│   ├── Python 3.9-slim
│   ├── http_mcp_server.py
│   └── /app/data/jokes.db (volume mounted)
├── UFW Firewall (port 8080 allowed)
└── Docker Compose orchestration
```

### Connecting to Production Server

From Android app:
```kotlin
// In ServerConfig.kt
const val MCP_SERVER_URL = "http://${SecureData.serverIp}:8080"
```

From command line:
```bash
# Replace <SERVER_IP> with your actual server IP
curl -X POST http://<SERVER_IP>:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

## Architecture

### Server Stack

- **Language**: Python 3.9+
- **Server**: `http.server.HTTPServer`
- **Protocol**: JSON-RPC 2.0
- **Database**: SQLite 3
- **External API**: JokeAPI v2 (https://v2.jokeapi.dev)

### Dependencies

**Runtime**: Python standard library only
- `http.server` - HTTP server
- `json` - JSON parsing
- `sqlite3` - Database
- `urllib` - HTTP client for JokeAPI

**Development**:
- `unittest` - Testing framework
- `unittest.mock` - Mocking for tests

### Database Schema

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
)
```

## API Reference

### JSON-RPC Methods

#### 1. initialize

Get server information.

**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize"
}
```

**Response**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "serverInfo": {
      "name": "Python HTTP MCP Server",
      "version": "2.0.0"
    }
  }
}
```

#### 2. tools/list

Get available tools.

**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list"
}
```

**Response**:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "get_joke",
        "description": "Get a random joke from JokeAPI",
        "inputSchema": { ... }
      },
      {
        "name": "save_joke",
        "description": "Save a joke to the local database",
        "inputSchema": { ... }
      },
      {
        "name": "get_saved_jokes",
        "description": "Get all saved jokes from the database",
        "inputSchema": { ... }
      },
      {
        "name": "run_tests",
        "description": "Run MCP server tests in an isolated Docker container",
        "inputSchema": { ... }
      }
    ]
  }
}
```

#### 3. tools/call

Execute a tool.

**Request - Get Joke**:
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "get_joke",
    "arguments": {
      "category": "Programming",
      "blacklistFlags": "nsfw,racist,sexist"
    }
  }
}
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
        "text": "{\n  \"category\": \"Programming\",\n  \"type\": \"single\",\n  \"id\": 123,\n  \"joke\": \"Why do programmers prefer dark mode? Light attracts bugs!\"\n}"
      }
    ]
  }
}
```

**Request - Save Joke**:
```json
{
  "jsonrpc": "2.0",
  "id": 4,
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
}
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
        "text": "{\n  \"success\": true,\n  \"message\": \"Joke saved successfully\",\n  \"saved_joke_id\": 1\n}"
      }
    ]
  }
}
```

**Request - Get Saved Jokes**:
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "tools/call",
  "params": {
    "name": "get_saved_jokes",
    "arguments": {
      "limit": 10
    }
  }
}
```

**Response**:
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\n  \"success\": true,\n  \"count\": 1,\n  \"jokes\": [\n    {\n      \"id\": 1,\n      \"joke_api_id\": 123,\n      \"category\": \"Programming\",\n      \"type\": \"single\",\n      \"saved_at\": \"2026-01-31 21:00:00\",\n      \"joke\": \"Why do programmers prefer dark mode? Light attracts bugs!\"\n    }\n  ]\n}"
      }
    ]
  }
}
```

## Tool Details

### get_joke

Fetches random jokes from JokeAPI v2.

**Parameters**:
- `category` (optional): Any, Programming, Misc, Dark, Pun, Spooky, Christmas (default: "Any")
- `blacklistFlags` (optional): Comma-separated flags to exclude (e.g., "nsfw,racist,sexist")

**Returns**:
- Single-type joke: `{ category, type, id, joke }`
- Two-part joke: `{ category, type, id, setup, delivery }`

**Example**:
```bash
curl -X POST http://<SERVER_IP>:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_joke","arguments":{"category":"Programming"}}}'
```

### save_joke

Saves a joke to the SQLite database.

**Parameters**:
- `type` (required): "single" or "twopart"
- `joke_api_id` (optional): Original ID from JokeAPI
- `category` (optional): Joke category
- `joke_text` (optional): Full joke text for single-type jokes
- `setup` (optional): Setup for two-part jokes
- `delivery` (optional): Punchline for two-part jokes

**Returns**:
```json
{
  "success": true,
  "message": "Joke saved successfully",
  "saved_joke_id": 1
}
```

**Example**:
```bash
curl -X POST http://<SERVER_IP>:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"save_joke","arguments":{"type":"single","joke_text":"My custom joke"}}}'
```

### get_saved_jokes

Retrieves saved jokes from the database.

**Parameters**:
- `limit` (optional): Maximum number of jokes to return (default: 50)

**Returns**:
```json
{
  "success": true,
  "count": 2,
  "jokes": [
    { "id": 2, "type": "single", "joke": "...", "saved_at": "..." },
    { "id": 1, "type": "twopart", "setup": "...", "delivery": "...", "saved_at": "..." }
  ]
}
```

Jokes are ordered by `saved_at DESC` (newest first).

**Example**:
```bash
curl -X POST http://<SERVER_IP>:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_saved_jokes","arguments":{}}}'
```

### run_tests

Executes the complete test suite in an isolated Docker container.

**Prerequisites**:
- Docker must be installed and running on the host
- Docker image `mcp-server-tests` must be built (see Testing section)

**Parameters**: None

**Returns**:
```json
{
  "success": true,
  "tests_run": 26,
  "passed": 26,
  "failed": 0,
  "errors": 0,
  "summary": "26 passed, 0 failed, 0 errors out of 26 tests",
  "output": "...last 1000 chars of test output..."
}
```

**Error Response** (if Docker not available):
```json
{
  "success": false,
  "error": "Docker is not installed or not running",
  "tests_run": 0,
  "passed": 0,
  "failed": 0,
  "errors": 0
}
```

**Features**:
- Runs all 26 unit tests in isolation
- 120-second execution timeout
- Automatic container cleanup (--rm flag)
- Parses test results for summary
- Returns last 1000 characters of output for debugging

**Example**:
```bash
curl -X POST http://<SERVER_IP>:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"run_tests","arguments":{}}}'
```

## Testing

See [TEST_README.md](TEST_README.md) for detailed testing documentation.

### Running Tests Locally

**Quick Test**:
```bash
python3 test_http_mcp_server.py
```

**Test Coverage**:
- 26 tests covering all functionality
- 100% coverage of core logic
- Database operations tested
- JSON-RPC protocol validated
- Error handling verified
- Integration tests included

### Running Tests in Docker (run_tests tool)

The `run_tests` tool allows running the entire test suite in an isolated Docker container. This is useful for:
- Verifying tests work in a clean environment
- CI/CD integration
- Testing from the Android app

**Building the Docker image**:
```bash
cd server
docker build -t mcp-server-tests .
```

**Running tests via Docker manually**:
```bash
docker run --rm mcp-server-tests
```

**Running tests via MCP tool call**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"run_tests","arguments":{}}}'
```

**Expected output**:
```json
{
  "success": true,
  "tests_run": 26,
  "passed": 26,
  "failed": 0,
  "errors": 0,
  "summary": "26 passed, 0 failed, 0 errors out of 26 tests"
}
```

**Test execution details**:
- Runs in isolated Docker container
- Automatic container cleanup after completion
- 120-second timeout
- Returns summary of test results
- Can be triggered from Android app chat interface

## Remote Server Management

### SSH Access

```bash
# Use credentials from SecureData.kt
ssh <USERNAME>@<SERVER_IP>
```

> **Note**: Server credentials (IP, username, password) are configured in `app/src/main/java/com/example/aiwithlove/util/SecureData.kt`

### Docker Commands

**View container status**:
```bash
ssh <USERNAME>@<SERVER_IP> "docker ps --filter name=mcp-jokes-server"
```

**View logs**:
```bash
ssh <USERNAME>@<SERVER_IP> "docker logs -f mcp-jokes-server"
```

**Restart container**:
```bash
ssh <USERNAME>@<SERVER_IP> "docker restart mcp-jokes-server"
```

**Stop container**:
```bash
ssh <USERNAME>@<SERVER_IP> "cd /opt/mcp-server && docker compose down"
```

**Update deployment** (after code changes):
```bash
# Copy new code to server
scp http_mcp_server.py <USERNAME>@<SERVER_IP>:/opt/mcp-server/

# Rebuild and restart
ssh <USERNAME>@<SERVER_IP> "cd /opt/mcp-server && docker compose up -d --build"
```

### Database Access

**View database on server**:
```bash
ssh <USERNAME>@<SERVER_IP> "sqlite3 /opt/mcp-server/data/jokes.db 'SELECT * FROM saved_jokes;'"
```

**Backup database**:
```bash
scp <USERNAME>@<SERVER_IP>:/opt/mcp-server/data/jokes.db ./jokes_backup_$(date +%Y%m%d).db
```

**Clear database**:
```bash
ssh <USERNAME>@<SERVER_IP> "sqlite3 /opt/mcp-server/data/jokes.db 'DELETE FROM saved_jokes;'"
```

## Monitoring

### Health Check

```bash
curl -X POST http://<SERVER_IP>:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize"}'
```

Expected: 200 OK with server info

### Resource Usage

```bash
ssh <USERNAME>@<SERVER_IP> "docker stats mcp-jokes-server --no-stream"
```

### Log Monitoring

```bash
ssh <USERNAME>@<SERVER_IP> "docker logs --tail 100 mcp-jokes-server"
```

## Troubleshooting

### Server Not Responding

1. Check if container is running:
   ```bash
   ssh <USERNAME>@<SERVER_IP> "docker ps"
   ```

2. Check logs for errors:
   ```bash
   ssh <USERNAME>@<SERVER_IP> "docker logs mcp-jokes-server"
   ```

3. Restart container:
   ```bash
   ssh <USERNAME>@<SERVER_IP> "docker restart mcp-jokes-server"
   ```

### Database Issues

1. Check if database file exists:
   ```bash
   ssh <USERNAME>@<SERVER_IP> "ls -lh /opt/mcp-server/data/jokes.db"
   ```

2. Verify database integrity:
   ```bash
   ssh <USERNAME>@<SERVER_IP> "sqlite3 /opt/mcp-server/data/jokes.db 'PRAGMA integrity_check;'"
   ```

### JokeAPI Not Working

1. Test JokeAPI directly:
   ```bash
   curl https://v2.jokeapi.dev/joke/Any
   ```

2. Check server internet access:
   ```bash
   ssh <USERNAME>@<SERVER_IP> "curl -I https://v2.jokeapi.dev"
   ```

## Security Notes

### Current Security Posture

- **HTTP Only**: No HTTPS/SSL (cleartext traffic)
- **No Authentication**: Open access to all endpoints
- **No Rate Limiting**: Could be subject to abuse
- **Public Exposure**: Port 8080 open to internet

### Recommended Production Enhancements

1. **Add SSL/TLS**:
   - Use nginx reverse proxy
   - Let's Encrypt certificate
   - Redirect HTTP to HTTPS

2. **Add Authentication**:
   - API key header validation
   - Token-based auth
   - IP whitelisting

3. **Add Rate Limiting**:
   - Per-IP request limits
   - Tool-specific quotas

4. **Monitoring & Alerts**:
   - Uptime monitoring
   - Error rate alerts
   - Resource usage alerts

## Development

### Project Structure

```
server/
├── http_mcp_server.py      # Main server code
├── test_http_mcp_server.py # Test suite
├── TEST_README.md           # Test documentation
└── README.md                # This file
```

### Making Changes

1. Edit `http_mcp_server.py`
2. Add/update tests in `test_http_mcp_server.py`
3. Run tests: `python3 test_http_mcp_server.py`
4. Update remote server (see "Remote Server Management")

### Code Style

- Python 3.9+ compatible
- Standard library only (no external dependencies)
- Follow existing patterns
- Add logging for debugging
- Include docstrings for functions

## License

Same as parent project (AI with Love educational series)

## Support

For issues or questions, refer to the main project documentation or the "День 15: Environment" lesson materials.
