# New Feature: run_tests Tool

## Overview

A new MCP tool has been added that allows running the complete server test suite in an isolated Docker container. This tool can be called directly from the Android app's chat interface.

## What Was Added

### 1. Docker Configuration
**File**: `server/Dockerfile`
- Python 3.11-slim base image
- Copies server and test files
- Sets up test environment
- Runs tests by default

### 2. Server Implementation
**File**: `server/http_mcp_server.py`
- Added `subprocess` and `re` imports
- New tool definition in `handle_tools_list()`
- New tool handler in `handle_tools_call()`
- New `tool_run_tests()` method that:
  - Launches Docker container
  - Runs tests with 120-second timeout
  - Parses test output
  - Returns summary (X passed, Y failed)
  - Auto-removes container after completion

### 3. Android App Integration
**File**: `app/src/main/java/com/example/aiwithlove/mcp/McpServerConfig.kt`
- Added `run_tests` tool configuration
- Emoji: 🧪
- Russian description and trigger words
- Trigger words: "запусти тесты", "протестируй сервер", "проверь работу", "тесты", "test"

### 4. Documentation
**Files**:
- `server/README.md` - Updated with Docker testing section
- `server/build_test_image.sh` - Helper script to build Docker image
- `NEW_FEATURE_RUN_TESTS.md` - This file

## Setup Instructions

### Step 1: Build the Docker Image

```bash
cd server
./build_test_image.sh
```

Or manually:
```bash
cd server
docker build -t mcp-server-tests .
```

### Step 2: Verify Image Built Successfully

```bash
docker images | grep mcp-server-tests
```

You should see:
```
mcp-server-tests   latest   <image-id>   <timestamp>   <size>
```

### Step 3: Test the Image Manually (Optional)

```bash
docker run --rm mcp-server-tests
```

Expected output: All 26 tests should pass

### Step 4: Start the MCP Server

```bash
cd server
python3 http_mcp_server.py
```

Server should show:
```
Available Tools:
  🎭 get_joke       - Get random jokes from JokeAPI
  💾 save_joke      - Save a joke to local database
  📖 get_saved_jokes - Get all saved jokes from database
  🧪 run_tests       - Run server tests in Docker container
```

## Usage

### From Android App

1. Open the app
2. In the chat, type any of these phrases:
   - "Запусти тесты"
   - "Протестируй сервер"
   - "Проверь работу сервера"
   - "Run tests"
   - "Тесты"

3. The AI will automatically call the `run_tests` tool

4. You'll receive a response with test results:
   ```
   Тесты выполнены успешно!
   26 passed, 0 failed, 0 errors out of 26 tests
   ```

### Via API Call

```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "run_tests",
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
        "text": "{
  \"success\": true,
  \"tests_run\": 26,
  \"passed\": 26,
  \"failed\": 0,
  \"errors\": 0,
  \"summary\": \"26 passed, 0 failed, 0 errors out of 26 tests\",
  \"output\": \"...\"
}"
      }
    ]
  }
}
```

## How It Works

### Workflow

1. **User Request**: User asks to run tests via chat
2. **Tool Detection**: ChatViewModel detects trigger words
3. **API Call**: App sends request to Perplexity API with tool definition
4. **Tool Execution**: API decides to call `run_tests` tool
5. **Docker Launch**: Server launches Docker container via subprocess
6. **Test Execution**: Tests run in isolated container (max 120 seconds)
7. **Result Parsing**: Server parses test output to extract summary
8. **Container Cleanup**: Docker auto-removes container (--rm flag)
9. **Response**: Server returns summary to app
10. **AI Response**: Perplexity API generates natural language response with results

### Technical Details

**Docker Container**:
- Image: `mcp-server-tests`
- Base: `python:3.11-slim`
- Command: `python3 -m unittest test_http_mcp_server -v`
- Cleanup: Automatic with `--rm` flag
- Timeout: 120 seconds
- Network: Isolated (no external dependencies needed for tests)

**Test Parsing**:
- Regex pattern: `Ran (\d+) test`
- Success check: "OK" in output and returncode == 0
- Failure/Error extraction: `failures=(\d+)`, `errors=(\d+)`
- Output limit: Last 1000 characters included in response

## Benefits

1. **Isolation**: Tests run in clean environment every time
2. **Consistency**: Same environment across different machines
3. **Chat Integration**: Can test server directly from app
4. **Automated**: No manual test execution needed
5. **Safe**: Container is automatically removed after tests
6. **Timeout Protection**: Won't hang indefinitely

## Requirements

- Docker must be installed and running
- Docker image must be built before first use
- Server must have permissions to execute Docker commands

## Troubleshooting

### Error: "Docker is not installed or not running"

**Solution**:
1. Install Docker Desktop
2. Start Docker Desktop
3. Verify: `docker --version`

### Error: "Timeout after 120 seconds"

**Possible Causes**:
- Tests are stuck in infinite loop
- System is very slow
- Docker is having issues

**Solution**:
- Run tests manually to diagnose: `docker run --rm mcp-server-tests`
- Check Docker logs: `docker logs <container-id>`

### Error: "No such image: mcp-server-tests"

**Solution**:
```bash
cd server
./build_test_image.sh
```

### Tests Fail in Docker

**Debugging**:
1. Run container interactively:
   ```bash
   docker run --rm -it mcp-server-tests /bin/bash
   ```

2. Inside container, run tests manually:
   ```bash
   python3 test_http_mcp_server.py
   ```

3. Check for environment differences

## Future Enhancements

Potential improvements:
- Add parameters (test verbosity, specific test selection)
- Support for different test frameworks
- Parallel test execution
- Test coverage reporting
- Integration with CI/CD pipelines
- Save test results to database
- Historical test result tracking

## Files Changed

```
server/
├── Dockerfile                    # NEW - Docker image for tests
├── build_test_image.sh          # NEW - Build helper script
├── http_mcp_server.py           # MODIFIED - Added run_tests tool
├── README.md                    # MODIFIED - Added Docker testing docs
└── NEW_FEATURE_RUN_TESTS.md     # NEW - This file

app/src/main/java/com/example/aiwithlove/mcp/
└── McpServerConfig.kt           # MODIFIED - Added run_tests tool config
```

## Testing the Feature

### Test Scenario 1: Manual Docker Run
```bash
docker run --rm mcp-server-tests
```
Expected: All tests pass

### Test Scenario 2: API Call
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"run_tests","arguments":{}}}'
```
Expected: JSON response with test summary

### Test Scenario 3: Android App Chat
1. Type: "Запусти тесты"
2. Expected: AI calls run_tests tool and reports results

### Test Scenario 4: Error Handling
1. Stop Docker Desktop
2. Type: "Run tests"
3. Expected: Error message about Docker not running

## Success Criteria

✅ Docker image builds successfully
✅ Tests run in Docker container
✅ Container auto-removes after completion
✅ Server correctly parses test results
✅ API returns proper JSON response
✅ Android app can trigger tests via chat
✅ Timeout works (120 seconds)
✅ Error handling for missing Docker

## Conclusion

The `run_tests` tool successfully integrates Docker-based testing into the MCP server, making it accessible via the Android app's chat interface. This demonstrates the power of MCP tool composition and agentic workflows.
