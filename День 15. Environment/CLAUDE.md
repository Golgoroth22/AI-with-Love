# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is "День 14: Композиция MCP-инструментов" (Day 14: MCP Tools Composition) - an educational Android project demonstrating how to compose multiple MCP (Model Context Protocol) tools into a unified pipeline through an agentic API loop.

**Key Components**:
- **Android App** (Kotlin/Compose): Chat interface that orchestrates tool calls
- **Python MCP Server**: HTTP server exposing 3 tools via JSON-RPC 2.0 protocol

**Project Goal**: Demonstrate automatic tool chaining where an AI agent detects when tools should be used and chains them together to fulfill user requests.

**Educational Context**: Part of a 14-day "AI with Love" course. Video walkthrough: https://drive.google.com/file/d/15mKIBtjsTjEYTPcnNFl7AwA492Cex5QK/view?usp=sharing

## Development Commands

### Android App

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device/emulator
./gradlew installDebug

# Run tests
./gradlew test

# Run lint checks
./gradlew lint
```

### MCP Server (Required for Tool Functionality)

The MCP server must be running for the app's tool features to work.

```bash
# Start server (runs on port 8080)
cd mcp_server
./start_server.sh

# Stop server
./stop_server.sh

# Check server status
./status_server.sh

# Restart server
./restart_server.sh

# View server logs
tail -f mcp_server/server.log

# Manual start with direct logging
cd mcp_server
python3 http_mcp_server.py
```

**Server Endpoints**:
- Emulator: `http://10.0.2.2:8080`
- Physical device: `http://<your-local-ip>:8080`

## High-Level Architecture

### Android App (MVI + Koin DI)

**Layer Structure**:
- **View**: Jetpack Compose UI in [ChatScreen.kt](app/src/main/java/com/example/aiwithlove/ui/ChatScreen.kt)
- **ViewModel**: [ChatViewModel.kt](app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt) (870 lines) - Core orchestration logic
- **Data Layer**:
  - [PerplexityApiServiceImpl.kt](app/src/main/java/com/example/aiwithlove/data/PerplexityApiServiceImpl.kt) - Agentic API client (Ktor)
  - [McpClient.kt](app/src/main/java/com/example/aiwithlove/mcp/McpClient.kt) - MCP server HTTP client
- **Database**: Room ORM for persistent message storage
- **DI**: Koin modules configured in [AppModule.kt](app/src/main/java/com/example/aiwithlove/di/AppModule.kt)

**Key Dependencies**:
- Ktor for HTTP networking
- Kotlinx.serialization for JSON
- Room for local database
- Koin for dependency injection
- Jetpack Compose for UI

### MCP Server (Python)

Located in [mcp_server/http_mcp_server.py](mcp_server/http_mcp_server.py) (421 lines):
- HTTP server implementing JSON-RPC 2.0 protocol
- SQLite database (`jokes.db`) for persistent joke storage
- Integration with JokeAPI v2 (https://jokeapi.dev/)
- Comprehensive logging to `server.log`

**Three Tools**:
1. `get_joke`: Fetch random joke from JokeAPI
2. `save_joke`: Persist joke to local database
3. `get_saved_jokes`: Retrieve all saved jokes

### Core Pattern: Agentic Tool Loop

The central architectural pattern is an **agentic tool-calling loop** that automatically detects when tools should be used and chains multiple tool calls together:

1. **User Input**: User sends message mentioning jokes (Russian keywords: "шутк", "анекдот", "смешн")
2. **Tool Detection**: [ChatViewModel.kt:347-354](app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt) detects keywords and enables joke tools
3. **API Request**: App sends message + tool definitions to Perplexity Agentic API
4. **Tool Decision**: API analyzes message and decides to call tool(s) (e.g., `get_joke`)
5. **Tool Execution**: App executes tool via [McpClient.kt](app/src/main/java/com/example/aiwithlove/mcp/McpClient.kt) HTTP call to local server
6. **Result Submission**: App sends tool result back to API with `tool_use_id`
7. **Response Generation**: API uses tool result to generate response (may call more tools)
8. **Loop**: Process repeats until API returns final text response without tool calls

**Implementation Details**:
- Tool detection: [ChatViewModel.kt:347-354](app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt)
- Tool execution: [ChatViewModel.kt:542-602](app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt)
- Tool definitions: [McpServerConfig.kt](app/src/main/java/com/example/aiwithlove/mcp/McpServerConfig.kt)

### Dialog Compression Feature

**Purpose**: Save tokens by summarizing conversation history after every 5 user messages

**Implementation** ([ChatViewModel.kt:614-652](app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt)):
1. Counts user messages in current session
2. After 5 messages, sends all messages to Perplexity for summarization
3. Replaces old messages with summary in database and UI
4. Resets counter and continues conversation

**Trigger**: Automatic after every 5th user message

## Configuration

### SDK Versions
- `compileSdk`: 36
- `minSdk`: 25
- `targetSdk`: 36
- `jvmTarget`: 11

### MCP Server Connection
Configured in [McpClient.kt](app/src/main/java/com/example/aiwithlove/mcp/McpClient.kt):
- **Emulator**: `http://10.0.2.2:8080` (Android emulator's localhost alias)
- **Physical Device**: Update to your machine's local IP address

### API Authentication
Perplexity API key stored in [SecureData.kt](app/src/main/java/com/example/aiwithlove/util/SecureData.kt). Update this file with your API key.

### Russian Language Context
- UI labels and prompts are in Russian
- Tool detection uses Russian keywords: "шутк" (joke), "анекдот" (anecdote), "смешн" (funny)
- System prompts instruct API to respond in Russian
- When modifying tool detection or prompts, maintain Russian language support

## MCP Tools Reference

Tool definitions are in [McpServerConfig.kt](app/src/main/java/com/example/aiwithlove/mcp/McpServerConfig.kt).

### get_joke
**Purpose**: Fetch random joke from JokeAPI
**Parameters**:
- `category` (optional): Joke category (e.g., "Programming", "Misc")

**Returns**: Joke text in Russian (translation happens server-side)

**Example Call**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "get_joke",
    "arguments": {"category": "Programming"}
  }
}
```

### save_joke
**Purpose**: Save joke to server's SQLite database
**Parameters**:
- `joke_text` (required): The joke text to save

**Returns**: Success confirmation message

**Example Call**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "save_joke",
    "arguments": {"joke_text": "Why do programmers prefer dark mode?..."}
  }
}
```

### get_saved_jokes
**Purpose**: Retrieve all saved jokes from database
**Parameters**: None

**Returns**: List of previously saved jokes with timestamps

**Example Call**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "get_saved_jokes",
    "arguments": {}
  }
}
```

## Key Files for Understanding

These files contain the core logic that requires understanding multiple components:

1. **[ChatViewModel.kt](app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt)** (870 lines)
   - Orchestrates entire agentic loop
   - Tool detection and enablement logic
   - Tool execution and result handling
   - Dialog compression implementation
   - Message state management

2. **[http_mcp_server.py](mcp_server/http_mcp_server.py)** (421 lines)
   - All three tool implementations
   - JSON-RPC 2.0 protocol handling
   - SQLite database operations
   - JokeAPI integration with caching

3. **[McpClient.kt](app/src/main/java/com/example/aiwithlove/mcp/McpClient.kt)**
   - HTTP client for MCP server communication
   - JSON-RPC request/response handling
   - Error handling for server connectivity

4. **[McpServerConfig.kt](app/src/main/java/com/example/aiwithlove/mcp/McpServerConfig.kt)**
   - Tool schema definitions
   - Available servers configuration
   - Tool parameter specifications

5. **[PerplexityApiServiceImpl.kt](app/src/main/java/com/example/aiwithlove/data/PerplexityApiServiceImpl.kt)**
   - Ktor HTTP client for Perplexity Agentic API
   - Request/response serialization
   - Streaming response handling
