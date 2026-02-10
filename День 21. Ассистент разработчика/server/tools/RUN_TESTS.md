# run_tests Tool Documentation

## Overview

The `run_tests` tool executes the complete MCP server test suite in an isolated Docker container. This allows testing the server functionality directly from the Android app chat interface or via API calls. See full testing documentation in [SERVER_TESTING.md](../SERVER_TESTING.md).

**Tool Type**: Local (Docker execution)
**Emoji**: 🧪
**Prerequisites**: Docker must be installed and running
**Docker Image**: `mcp-server-tests`
**Timeout**: 120 seconds

---

## Implementation

**File**: `server/http_mcp_server.py`
**Lines**: 572-719
**Method**: `tool_run_tests(args: dict) -> dict`

---

## Parameters

**None** - This tool requires no parameters.

---

## Returns

### Success Response
```json
{
  "success": true,
  "tests_run": 26,
  "passed": 26,
  "failed": 0,
  "errors": 0,
  "summary": "26 passed, 0 failed, 0 errors out of 26 tests",
  "output": "...(last 1000 characters of test output)..."
}
```

### Failure Response (Some Tests Failed)
```json
{
  "success": false,
  "tests_run": 26,
  "passed": 24,
  "failed": 2,
  "errors": 0,
  "summary": "24 passed, 2 failed, 0 errors out of 26 tests",
  "output": "...(test output with failure details)..."
}
```

### Error Response (Docker Not Available)
```json
{
  "success": false,
  "error": "Docker is not installed or not running",
  "tests_run": 0,
  "passed": 0,
  "failed": 0,
  "errors": 0,
  "output": ""
}
```

### Timeout Response
```json
{
  "success": false,
  "error": "Test execution timed out after 120 seconds",
  "tests_run": 0,
  "passed": 0,
  "failed": 0,
  "errors": 0,
  "output": ""
}
```

---

## Implementation Details

### Docker Execution

```python
import subprocess
import re

def tool_run_tests(args: dict) -> dict:
    try:
        # 1. Run tests in Docker container
        result = subprocess.run(
            ['docker', 'run', '--rm', 'mcp-server-tests'],
            capture_output=True,
            text=True,
            timeout=120  # 2 minutes
        )

        # 2. Parse test output
        output = result.stdout + result.stderr

        # 3. Extract test statistics
        tests_run_match = re.search(r'Ran (\d+) test', output)
        tests_run = int(tests_run_match.group(1)) if tests_run_match else 0

        # 4. Check for failures and errors
        failures = 0
        errors = 0

        if 'FAILED' in output:
            failure_match = re.search(r'failures=(\d+)', output)
            failures = int(failure_match.group(1)) if failure_match else 0

            error_match = re.search(r'errors=(\d+)', output)
            errors = int(error_match.group(1)) if error_match else 0

        passed = tests_run - failures - errors

        # 5. Build summary
        summary = f"{passed} passed, {failures} failed, {errors} errors out of {tests_run} tests"

        # 6. Return results
        return {
            'success': result.returncode == 0,
            'tests_run': tests_run,
            'passed': passed,
            'failed': failures,
            'errors': errors,
            'summary': summary,
            'output': output[-1000:]  # Last 1000 characters
        }

    except subprocess.TimeoutExpired:
        return {
            'success': False,
            'error': 'Test execution timed out after 120 seconds',
            'tests_run': 0,
            'passed': 0,
            'failed': 0,
            'errors': 0,
            'output': ''
        }

    except FileNotFoundError:
        return {
            'success': False,
            'error': 'Docker is not installed or not running',
            'tests_run': 0,
            'passed': 0,
            'failed': 0,
            'errors': 0,
            'output': ''
        }
```

### Test Output Parsing

The tool parses standard Python unittest output:

```
Ran 26 tests in 0.035s

OK
```

Or with failures:
```
Ran 26 tests in 0.042s

FAILED (failures=2)
```

---

## Usage Examples

### Example 1: Run Tests via API

**Request**:
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

**Response (All Tests Passed)**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"success\":true,\"tests_run\":26,\"passed\":26,\"failed\":0,\"errors\":0,\"summary\":\"26 passed, 0 failed, 0 errors out of 26 tests\",\"output\":\"test_get_single_joke_success ... ok\\ntest_save_joke_success ... ok\\n...\\nRan 26 tests in 0.035s\\n\\nOK\"}"
      }
    ]
  }
}
```

### Example 2: Run Tests from Android App

**User Message**: "Запусти тесты" or "Run tests"

**Workflow**:
```
User: "Запусти тесты"
     ↓
ChatViewModel detects keyword "тесты"
     ↓
Sends to Perplexity API with run_tests tool definition
     ↓
API decides to call run_tests
     ↓
ChatViewModel executes run_tests via McpClient
     ↓
MCP Server launches Docker container
     ↓
Container runs 26 tests
     ↓
Returns test summary
     ↓
API generates natural language response
     ↓
User sees: "Тесты выполнены успешно! 26 passed, 0 failed, 0 errors out of 26 tests"
```

### Example 3: Manual Docker Run

Run tests directly without the MCP tool:
```bash
docker run --rm mcp-server-tests
```

**Output**:
```
test_database_initialization (test_http_mcp_server.TestDatabase) ... ok
test_get_joke_network_error (test_http_mcp_server.TestGetJokeTool) ... ok
test_get_single_joke_success (test_http_mcp_server.TestGetJokeTool) ... ok
...
----------------------------------------------------------------------
Ran 26 tests in 0.035s

OK
```

---

## Integration with Android App

### Tool Execution

**File**: `app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt`
**Lines**: 242-256

```kotlin
"run_tests" -> {
    logD("🧪 Calling MCP server run_tests")

    // Execute tool (no arguments needed)
    val result = mcpClient.callTool(
        toolName = "run_tests",
        arguments = buildJsonObject { }
    )

    // Extract server logs for file saving
    val testOutput = result["content"]
        ?.jsonArray?.firstOrNull()
        ?.jsonObject?.get("text")
        ?.jsonPrimitive?.content
        ?.let { Json.parseToJsonElement(it).jsonObject }
        ?.get("output")
        ?.jsonPrimitive?.content

    if (testOutput != null) {
        saveLogFile("server_tests_${timestamp}.log", testOutput)
    }

    result
}
```

### Log File Saving

**File**: `app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt`
**Lines**: 502-527

Test results are automatically saved to a log file:
```kotlin
private fun saveLogFile(filename: String, content: String) {
    val file = File(context.filesDir, filename)

    // Delete previous log files
    context.filesDir.listFiles()
        ?.filter { it.name.startsWith("server_tests_") }
        ?.forEach { it.delete() }

    // Save new log
    file.writeText(content)

    logD("💾 Saved test log: ${file.absolutePath}")
}
```

Users can then click the log file card in the chat to open it.

---

## Test Suite Overview

### 26 Tests Across 8 Categories

1. **Database Operations** (1 test)
   - Database initialization
   - Schema validation

2. **JSON-RPC Protocol** (3 tests)
   - Initialize request
   - Tools list
   - Unknown method handling

3. **Get Joke Tool** (5 tests)
   - Single-type jokes
   - Two-part jokes
   - Blacklist flags
   - API errors
   - Network errors

4. **Save Joke Tool** (4 tests)
   - Single-type saving
   - Two-part saving
   - Custom jokes
   - Russian text support

5. **Get Saved Jokes Tool** (5 tests)
   - Retrieve all jokes
   - Limit parameter
   - Empty database
   - Field mapping
   - Ordering

6. **Tools Call Integration** (4 tests)
   - End-to-end execution
   - Result formatting
   - Unknown tool handling
   - Response structure

7. **End-to-End Scenarios** (1 test)
   - Complete workflow: get → save → retrieve

8. **Error Handling** (3 tests)
   - Missing fields
   - Database errors
   - Edge cases

---

## Docker Setup

### Build Docker Image

**Dockerfile** (server/Dockerfile):
```dockerfile
FROM python:3.11-slim

WORKDIR /app

# Copy server and test files
COPY http_mcp_server.py .
COPY test_http_mcp_server.py .

# Run tests
CMD ["python3", "-m", "unittest", "test_http_mcp_server", "-v"]
```

**Build command**:
```bash
cd server
docker build -t mcp-server-tests .
```

### Verify Image

```bash
docker images | grep mcp-server-tests
```

Expected output:
```
mcp-server-tests   latest   abc123def456   2 minutes ago   180MB
```

---

## Error Handling

### Docker Not Installed
```json
{
  "success": false,
  "error": "Docker is not installed or not running"
}
```

**Solutions**:
1. Install Docker Desktop: https://www.docker.com/products/docker-desktop
2. Start Docker Desktop
3. Verify: `docker --version`

### Image Not Found
```json
{
  "success": false,
  "error": "Unable to find image 'mcp-server-tests:latest' locally"
}
```

**Solution**:
```bash
cd server
docker build -t mcp-server-tests .
```

### Timeout After 120 Seconds
```json
{
  "success": false,
  "error": "Test execution timed out after 120 seconds"
}
```

**Possible Causes**:
- Tests stuck in infinite loop
- System very slow
- Docker performance issues

**Solutions**:
1. Run tests manually to diagnose: `docker run --rm mcp-server-tests`
2. Check Docker logs: `docker logs <container-id>`
3. Increase timeout in `tool_run_tests()` if needed

### Some Tests Failed
```json
{
  "success": false,
  "tests_run": 26,
  "passed": 24,
  "failed": 2
}
```

**Solution**:
1. Check test output for failure details
2. Run tests manually for debugging:
   ```bash
   docker run --rm mcp-server-tests
   ```
3. Fix failing tests in `http_mcp_server.py` or `test_http_mcp_server.py`

---

## Performance

- **Execution Time**: ~2-5 seconds (including Docker startup)
- **Container Startup**: ~1 second
- **Test Suite Runtime**: ~0.035 seconds
- **Timeout**: 120 seconds (2 minutes)
- **Memory Usage**: ~50 MB (container)

---

## Testing

### Manual Test of run_tests Tool

```bash
# 1. Build Docker image
cd server
docker build -t mcp-server-tests .

# 2. Start MCP server
python3 http_mcp_server.py

# 3. Call run_tests tool
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"run_tests","arguments":{}}}'
```

**Expected**: Returns JSON with 26 passed tests

### Verify Docker Integration

```bash
# Run container manually
docker run --rm mcp-server-tests

# Should output all test results
# Should exit with code 0 if all tests pass
echo $?
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: MCP Server Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2

      - name: Build Docker image
        run: |
          cd server
          docker build -t mcp-server-tests .

      - name: Run tests in Docker
        run: docker run --rm mcp-server-tests

      - name: Upload test results
        if: failure()
        uses: actions/upload-artifact@v2
        with:
          name: test-results
          path: server/test_results.xml
```

---

## Related Documentation

- [SERVER_TESTING.md](../SERVER_TESTING.md) - **Complete testing guide**
  - Detailed test suite breakdown
  - Test coverage table
  - Docker setup instructions
  - Troubleshooting guide
- [CHAT_SCREEN.md](../../docs/CHAT_SCREEN.md) - Chat interface integration
- [SERVER_README.md](../SERVER_README.md) - MCP server overview

---

## Version

**Day 18**: Реранкинг и фильтрация

**Last Updated**: 2026-02-04
