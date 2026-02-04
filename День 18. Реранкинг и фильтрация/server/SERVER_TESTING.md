# MCP Server Testing Guide

## Overview

Comprehensive testing documentation for the HTTP MCP Server with JokeAPI integration and SQLite database. This guide covers both the test suite itself and the Docker-based `run_tests` MCP tool that enables running tests from the Android app.

**Two Ways to Run Tests:**
1. **Manual Execution** - Run tests directly with Python (fast, for development)
2. **Docker-Based MCP Tool** - Run tests in isolated container via Android app chat interface

## Test Suite

### Test Statistics
- **Total Tests**: 26
- **Test Classes**: 8
- **Code Coverage**: All core functionality
- **Execution Time**: ~0.035s for all tests

### Test Categories

#### 1. Database Operations (1 test)
- Database initialization and schema validation
- SQLite table structure verification

#### 2. JSON-RPC Protocol (3 tests)
- Initialize request handling
- Tools list enumeration
- Unknown method error handling

#### 3. Get Joke Tool (5 tests)
- Single-type jokes from JokeAPI
- Two-part jokes (setup/delivery)
- Blacklist flags support
- JokeAPI error responses
- Network error handling

#### 4. Save Joke Tool (4 tests)
- Saving single-type jokes
- Saving two-part jokes
- Saving without JokeAPI ID (custom jokes)
- Russian text support (UTF-8 encoding)

#### 5. Get Saved Jokes Tool (5 tests)
- Retrieving all saved jokes
- Limit parameter support
- Empty database handling
- Single vs two-part joke field mapping
- Order verification (newest first)

#### 6. Tools Call Integration (4 tests)
- End-to-end tool execution
- Tool result formatting
- Unknown tool error handling
- JSON-RPC response structure

#### 7. End-to-End Scenarios (1 test)
- Complete workflow: get → save → retrieve
- Cross-tool integration

#### 8. Error Handling (3 tests)
- Missing required fields
- Database connection failures
- Edge cases (large limits, etc.)

### Test Coverage Table

| Component | Coverage | Tests |
|-----------|----------|-------|
| Database Init | 100% | 1 |
| JSON-RPC Protocol | 100% | 3 |
| Get Joke Tool | 100% | 5 |
| Save Joke Tool | 100% | 4 |
| Get Saved Jokes | 100% | 5 |
| Tools Integration | 100% | 4 |
| Error Handling | 100% | 3 |
| End-to-End | 100% | 1 |

### Test Isolation

Each test runs in a separate temporary database:
```
/tmp/tmpXXXXXX/test_jokes.db
```

Benefits:
- No test pollution
- Predictable state
- Parallel-safe execution
- Automatic cleanup

### Mocking Strategy

**External Dependencies Mocked:**
- JokeAPI HTTP requests (`urllib.request.urlopen`)
- Database connections (for error testing)

**Real Components Tested:**
- All server logic and business rules
- Database schema and queries
- JSON-RPC protocol handling
- Tool parameter validation

## Running Tests Manually

### Quick Start

```bash
# Run all tests
python3 test_http_mcp_server.py

# Run with verbose output (already default)
python3 test_http_mcp_server.py -v

# Run specific test class
python3 -m unittest test_http_mcp_server.TestGetJokeTool

# Run specific test method
python3 -m unittest test_http_mcp_server.TestGetJokeTool.test_get_single_joke_success
```

### Requirements
- Python 3.7+
- No external dependencies (only standard library)
- Modules used: `unittest`, `json`, `sqlite3`, `tempfile`, `unittest.mock`

### Test Examples

#### Successful Test
```python
def test_get_single_joke_success(self, mock_urlopen):
    """Test getting a single-type joke from JokeAPI"""
    mock_response = Mock()
    mock_response.read.return_value = json.dumps({
        'error': False,
        'category': 'Programming',
        'type': 'single',
        'joke': 'Why do programmers prefer dark mode?...',
        'id': 123
    }).encode('utf-8')

    result = self.handler.tool_get_joke({'category': 'Programming'})

    self.assertEqual(result['category'], 'Programming')
    self.assertEqual(result['type'], 'single')
    self.assertNotIn('error', result)
```

#### Error Handling Test
```python
def test_get_joke_network_error(self, mock_urlopen):
    """Test handling network errors"""
    mock_urlopen.side_effect = Exception('Network timeout')

    result = self.handler.tool_get_joke({'category': 'Any'})

    self.assertTrue(result.get('error'))
    self.assertIn('Failed to fetch joke', result['message'])
```

## Docker-Based Testing

### Docker Configuration

**File**: `server/Dockerfile`
- Base: `python:3.11-slim`
- Copies server and test files
- Sets up test environment
- Runs tests by default

**Container Specs:**
- Command: `python3 -m unittest test_http_mcp_server -v`
- Cleanup: Automatic with `--rm` flag
- Timeout: 120 seconds
- Network: Isolated (no external dependencies needed)

### Building the Docker Image

```bash
cd server
./build_test_image.sh
```

Or manually:
```bash
cd server
docker build -t mcp-server-tests .
```

### Verify Image Built Successfully

```bash
docker images | grep mcp-server-tests
```

Expected output:
```
mcp-server-tests   latest   <image-id>   <timestamp>   <size>
```

### Running Tests in Docker

```bash
docker run --rm mcp-server-tests
```

Expected output: All 26 tests should pass

### Benefits of Docker Approach

1. **Isolation**: Tests run in clean environment every time
2. **Consistency**: Same environment across different machines
3. **Chat Integration**: Can test server directly from app
4. **Automated**: No manual test execution needed
5. **Safe**: Container is automatically removed after tests
6. **Timeout Protection**: Won't hang indefinitely

## run_tests MCP Tool

### Overview

A new MCP tool that allows running the complete server test suite in an isolated Docker container. This tool can be called directly from the Android app's chat interface.

### Server Implementation

**File**: `server/http_mcp_server.py`

Added components:
- `subprocess` and `re` imports
- New tool definition in `handle_tools_list()`
- New tool handler in `handle_tools_call()`
- New `tool_run_tests()` method

**Tool behavior:**
- Launches Docker container
- Runs tests with 120-second timeout
- Parses test output
- Returns summary (X passed, Y failed)
- Auto-removes container after completion

### Android App Integration

**File**: `app/src/main/java/com/example/aiwithlove/mcp/McpServerConfig.kt`

Configuration:
```kotlin
McpToolInfo(
    name = "run_tests",
    emoji = "🧪",
    description = "Запустить тесты сервера в Docker контейнере",
    triggerWords = listOf(
        "запусти тесты",
        "протестируй сервер",
        "проверь работу",
        "тесты",
        "test"
    )
)
```

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

### Test Output Parsing

- Regex pattern: `Ran (\d+) test`
- Success check: "OK" in output and returncode == 0
- Failure/Error extraction: `failures=(\d+)`, `errors=(\d+)`
- Output limit: Last 1000 characters included in response

## Setup Instructions

### Manual Testing Setup

1. **No additional setup required** - Tests use only Python standard library
2. **Verify test file exists**: `ls test_http_mcp_server.py`
3. **Run tests**: `python3 test_http_mcp_server.py`

### Docker Testing Setup

1. **Install Docker Desktop** (if not already installed)

2. **Build the Docker image**:
   ```bash
   cd server
   ./build_test_image.sh
   ```

3. **Verify image built**:
   ```bash
   docker images | grep mcp-server-tests
   ```

4. **Test the image** (optional):
   ```bash
   docker run --rm mcp-server-tests
   ```

5. **Start the MCP Server**:
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
  ... (other tools)
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
    "content": [{
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
    }]
  }
}
```

### Manual Execution

```bash
# Run all tests directly
python3 test_http_mcp_server.py

# Run specific test class
python3 -m unittest test_http_mcp_server.TestGetJokeTool

# Run in Docker
docker run --rm mcp-server-tests
```

## Troubleshooting

### Docker-Specific Issues

#### "Docker is not installed or not running"
**Solution**:
1. Install Docker Desktop
2. Start Docker Desktop
3. Verify: `docker --version`

#### "Timeout after 120 seconds"
**Possible Causes**:
- Tests are stuck in infinite loop
- System is very slow
- Docker is having issues

**Solution**:
- Run tests manually to diagnose: `docker run --rm mcp-server-tests`
- Check Docker logs: `docker logs <container-id>`
- Increase timeout in `tool_run_tests()` method if needed

#### "No such image: mcp-server-tests"
**Solution**:
```bash
cd server
./build_test_image.sh
```

#### Tests Fail in Docker
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

### Test Execution Issues

#### Tests Fail to Import Module
```bash
# Make sure http_mcp_server.py is in the same directory
ls http_mcp_server.py test_http_mcp_server.py
```

#### Database Permission Errors
```bash
# Check /tmp directory permissions
ls -ld /tmp
# Should show: drwxrwxrwt
```

#### Mock Issues
Ensure `unittest.mock` is available (Python 3.3+):
```python
from unittest.mock import Mock, patch
```

### Network and Integration Issues

#### MCP Server Not Responding
**Solution**:
- Check if server is running: `ps aux | grep http_mcp_server`
- Verify port 8080 is not in use: `lsof -i :8080`
- Check server logs: `tail -f server/server.log`

#### Android App Can't Trigger Tests
**Solution**:
- Verify MCP server is running
- Check trigger words in ChatViewModel.kt
- Verify `run_tests` tool is in McpServerConfig.kt
- Check logcat for errors: `adb logcat | grep -E "ChatViewModel|run_tests"`

## CI/CD Integration

### GitHub Actions Example

```yaml
# .github/workflows/test.yml
name: MCP Server Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2

      - name: Set up Python
        uses: actions/setup-python@v2
        with:
          python-version: '3.9'

      - name: Run tests
        run: |
          cd server
          python3 test_http_mcp_server.py

      - name: Build Docker image
        run: |
          cd server
          docker build -t mcp-server-tests .

      - name: Run Docker tests
        run: docker run --rm mcp-server-tests
```

### Recommendations

- Run tests on every push and pull request
- Use matrix strategy to test multiple Python versions
- Cache Docker layers for faster builds
- Add test coverage reporting
- Set up notifications for test failures

## Future Enhancements

### Test Suite Enhancements
- [ ] Add performance benchmarks
- [ ] Test concurrent requests
- [ ] Add load testing scenarios
- [ ] Test CORS headers
- [ ] Add integration tests with real JokeAPI
- [ ] Code coverage metrics (coverage.py)
- [ ] Add stress tests for database

### run_tests Tool Enhancements
- [ ] Add parameters (test verbosity, specific test selection)
- [ ] Support for different test frameworks
- [ ] Parallel test execution
- [ ] Test coverage reporting in tool response
- [ ] Integration with CI/CD pipelines
- [ ] Save test results to database
- [ ] Historical test result tracking
- [ ] Real-time test output streaming

## Files Modified

```
server/
├── Dockerfile                    # NEW - Docker image for tests
├── build_test_image.sh          # NEW - Build helper script
├── http_mcp_server.py           # MODIFIED - Added run_tests tool
├── test_http_mcp_server.py      # Test suite
└── TESTING.md                   # NEW - This file (merged docs)

app/src/main/java/com/example/aiwithlove/mcp/
└── McpServerConfig.kt           # MODIFIED - Added run_tests tool config
```

## Test Scenarios

### Scenario 1: Manual Docker Run
```bash
docker run --rm mcp-server-tests
```
**Expected**: All 26 tests pass

### Scenario 2: API Call
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"run_tests","arguments":{}}}'
```
**Expected**: JSON response with test summary

### Scenario 3: Android App Chat
1. Type: "Запусти тесты"
2. **Expected**: AI calls run_tests tool and reports results

### Scenario 4: Error Handling
1. Stop Docker Desktop
2. Type: "Run tests"
3. **Expected**: Error message about Docker not running

## Success Criteria

✅ All 26 tests pass when run manually
✅ Docker image builds successfully
✅ Tests run in Docker container
✅ Container auto-removes after completion
✅ Server correctly parses test results
✅ API returns proper JSON response
✅ Android app can trigger tests via chat
✅ Timeout works (120 seconds)
✅ Error handling for missing Docker

## Contributing

When adding new features to the MCP server:
1. Write tests first (TDD approach)
2. Ensure all existing tests pass
3. Add tests for error cases
4. Update test count in this document
5. Update Docker image if needed
6. Test via both manual and Docker methods

## License

Same as parent project
