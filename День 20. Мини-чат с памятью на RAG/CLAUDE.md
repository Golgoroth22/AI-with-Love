# AI with Love - Features Documentation Index

This document provides a comprehensive index of all features in the AI with Love application (День 18: Реранкинг и фильтрация).

## Table of Contents

1. [MCP Server Features](#1-mcp-server-features)
2. [Android App Features](#2-android-app-features)
3. [Quick Links](#3-quick-links)

---

## 1. MCP Server Features

### Core Server Documentation

- **[SERVER_README.md](server/SERVER_README.md)** - Complete MCP server overview and deployment guide
  - Server architecture and stack
  - Production deployment with Docker
  - Remote server management
  - Monitoring and troubleshooting

- **[SERVER_TESTING.md](server/SERVER_TESTING.md)** - Comprehensive testing guide
  - Test suite overview (26 tests)
  - Docker-based testing with `run_tests` tool
  - CI/CD integration
  - Troubleshooting test failures

### MCP Tools (10 Total)

#### 🎭 Joke Management Tools (3)

Tools for fetching, saving, and retrieving jokes from JokeAPI.

- **[GET_JOKE.md](server/tools/GET_JOKE.md)** - Fetch random jokes from JokeAPI v2
  - Parameters: category, blacklistFlags
  - Supports single-line and two-part jokes
  - Integration with https://v2.jokeapi.dev

- **[SAVE_JOKE.md](server/tools/SAVE_JOKE.md)** - Save jokes to local SQLite database
  - Parameters: joke_api_id, category, type, joke_text, setup, delivery
  - Persistent storage in `jokes.db`
  - Russian and English text support

- **[GET_SAVED_JOKES.md](server/tools/GET_SAVED_JOKES.md)** - Retrieve saved jokes from database
  - Parameters: limit (default: 50)
  - Returns jokes ordered by save date (newest first)
  - Includes full joke metadata

#### 🧪 Testing Tool (1)

Tool for running automated tests in isolated Docker container.

- **[RUN_TESTS.md](server/tools/RUN_TESTS.md)** - Run MCP server tests in Docker container
  - No parameters required
  - Executes all 26 unit tests
  - 120-second timeout
  - Parses and returns test summary
  - Callable from Android app chat interface

#### 🔍 RAG & Embedding Tools (6)

Tools for document indexing, embedding generation, and semantic search.

- **[SEMANTIC_SEARCH.md](server/SEMANTIC_SEARCH.md)** - **⭐ Day 18 Main Feature** - Threshold-filtered semantic search
  - Parameters: query, limit (default: 3), threshold (default: 0.7), compare_mode
  - Filters results by similarity threshold (0.3-0.95 range)
  - Compare mode shows both filtered and unfiltered results
  - RAG implementation with reranking
  - Critical bug fixes documented

- **[CREATE_EMBEDDING.md](server/tools/CREATE_EMBEDDING.md)** - Generate text embeddings using Ollama
  - Parameters: text (required)
  - Uses nomic-embed-text model (768 dimensions)
  - Direct integration with Ollama API (localhost:11434)
  - Returns embedding array

- **[SAVE_DOCUMENT.md](server/tools/SAVE_DOCUMENT.md)** - Save document with embedding to database
  - Parameters: content (required)
  - Proxied to remote MCP server (148.253.209.151:8080)
  - Stores in `embeddings.db` database
  - Returns document ID on success

- **[SEARCH_SIMILAR.md](server/tools/SEARCH_SIMILAR.md)** - Search for similar documents using cosine similarity
  - Parameters: query, limit (default: 5)
  - Proxied to remote MCP server
  - Returns documents with similarity scores
  - Uses vector embeddings for search

- **[PROCESS_PDF.md](server/tools/PROCESS_PDF.md)** - Process PDF files: extract text, chunk, and save with embeddings
  - Parameters: pdf_base64, filename, chunk_size (default: 1000), chunk_overlap (default: 200)
  - Proxied to remote MCP server
  - 120-second timeout for large PDFs
  - Returns chunks saved count

- **[PROCESS_TEXT_CHUNKS.md](server/tools/PROCESS_TEXT_CHUNKS.md)** - Process extracted text into chunks and save with embeddings
  - Parameters: text, filename, chunk_size (default: 1000), chunk_overlap (default: 200)
  - Proxied to remote MCP server
  - 300-second timeout
  - Client-side PDF extraction, server-side processing

---

## 2. Android App Features

### 💬 ChatScreen - Main Chat Interface

- **[CHAT_SCREEN.md](docs/CHAT_SCREEN.md)** - Primary chat interface with agentic tool calling

  **Key Features:**
  - **Message Management** - Chat bubbles, auto-scroll, message filtering
  - **Agentic Tool System** - Automatic tool detection and iterative calling (max 5 iterations)
  - **Semantic Search** - Dynamic threshold filtering (0.3-0.95), comparison mode UI
  - **Token Counting** - Prompt and completion token display with metrics
  - **Dialog Compression** - Auto-summarization every 5 user messages
  - **Typewriter Effect** - Character-by-character animation (3 chars per 30ms)
  - **Log File Management** - Test result capture and file saving
  - **Database Integration** - Message persistence and history loading
  - **MCP Server Management** - Server toggle with badge display

  **Tool Integration:**
  - JokeAPI tools (get_joke, save_joke, get_saved_jokes)
  - Testing tool (run_tests)
  - Semantic search with threshold controls

### 📚 OllamaScreen - Document Indexing Interface

- **[OLLAMA_SCREEN.md](docs/OLLAMA_SCREEN.md)** - Document indexing and embedding generation interface

  **Key Features:**
  - **Document Indexing** - Text message indexing with automatic embedding generation
  - **PDF Upload & Processing** - PDF selection, text extraction (PDFBox), intelligent chunking
  - **Semantic Search Integration** - Query indexed documents with threshold filtering
  - **Message-Based UI** - Chat-like interface with document count tracking
  - **Typewriter Animation** - 10ms delay per character for responses

  **Technical Details:**
  - Uses PDFBox Android library for PDF extraction
  - Integrates with Ollama nomic-embed-text model
  - Stores embeddings in SQLite database
  - Supports overlapping text chunks for better context

---

## 3. Quick Links

### For Developers

- **Architecture Overview** → [CLAUDE.md](CLAUDE.md)
  - Project structure and layer architecture
  - MVI pattern with Koin DI
  - Core agentic tool loop pattern
  - Key files and their responsibilities

- **Server Setup** → [SERVER_README.md](server/SERVER_README.md)
  - Local development setup
  - Remote deployment with Docker
  - SSH access and management commands

- **Testing** → [SERVER_TESTING.md](server/SERVER_TESTING.md)
  - Manual test execution
  - Docker-based testing
  - Test suite details (26 tests)

### For Day 18 (Current Day) - Reranking & Filtering

**Main Feature**: Threshold-filtered semantic search for RAG applications

- **[SEMANTIC_SEARCH.md](server/SEMANTIC_SEARCH.md)** - Complete implementation guide
  - **Threshold Filtering**: 0.3-0.95 range with default 0.7 (70% similarity)
  - **Comparison Mode**: Side-by-side view of filtered vs. unfiltered results
  - **Multi-stage Pipeline**: Fetch raw → filter by threshold → apply limit
  - **RAG Implementation**: Retrieve relevant context for AI responses
  - **Bug Fixes**: Keyword detection separation, connection architecture fix

**Related Tools:**
- [CREATE_EMBEDDING.md](server/tools/CREATE_EMBEDDING.md) - Generate embeddings for documents
- [SAVE_DOCUMENT.md](server/tools/SAVE_DOCUMENT.md) - Store documents with embeddings
- [SEARCH_SIMILAR.md](server/tools/SEARCH_SIMILAR.md) - Base similarity search

**UI Integration:**
- [CHAT_SCREEN.md](docs/CHAT_SCREEN.md) - Semantic search section
  - Dynamic threshold slider in UI
  - Comparison mode toggle
  - Similarity score visualization

---

## Feature Summary by Category

### Joke Management
- Get jokes → [GET_JOKE.md](server/tools/GET_JOKE.md)
- Save jokes → [SAVE_JOKE.md](server/tools/SAVE_JOKE.md)
- View saved jokes → [GET_SAVED_JOKES.md](server/tools/GET_SAVED_JOKES.md)

### Testing & Quality Assurance
- Run automated tests → [RUN_TESTS.md](server/tools/RUN_TESTS.md)
- Test documentation → [SERVER_TESTING.md](server/SERVER_TESTING.md)

### Document Indexing & RAG
- Index documents → [OLLAMA_SCREEN.md](docs/OLLAMA_SCREEN.md)
- Generate embeddings → [CREATE_EMBEDDING.md](server/tools/CREATE_EMBEDDING.md)
- Save documents → [SAVE_DOCUMENT.md](server/tools/SAVE_DOCUMENT.md)
- Search similar → [SEARCH_SIMILAR.md](server/tools/SEARCH_SIMILAR.md)
- Semantic search → [SEMANTIC_SEARCH.md](server/SEMANTIC_SEARCH.md)
- Process PDFs → [PROCESS_PDF.md](server/tools/PROCESS_PDF.md)
- Process text chunks → [PROCESS_TEXT_CHUNKS.md](server/tools/PROCESS_TEXT_CHUNKS.md)

### Chat Interface
- Main chat → [CHAT_SCREEN.md](docs/CHAT_SCREEN.md)
- Document indexing → [OLLAMA_SCREEN.md](docs/OLLAMA_SCREEN.md)

---

## Architecture Diagrams

### Three-Tier Architecture

```
┌─────────────────┐
│  Android App    │  - ChatScreen: Agentic tool calling
│   (Emulator)    │  - OllamaScreen: Document indexing
└────────┬────────┘
         │ http://10.0.2.2:8080
         ↓
┌─────────────────────────────┐
│  LOCAL MCP Server           │  - 10 MCP tools
│  (Your Computer)            │  - JSON-RPC 2.0 protocol
│  Port: 8080                 │  - SQLite databases
└────────┬────────────────────┘
         │ http://148.253.209.151:8080
         │ (for proxied tools)
         ↓
┌─────────────────────────────┐
│  REMOTE MCP Server          │  - Embeddings database
│  (148.253.209.151)          │  - Ollama integration
│  Port: 8080                 │  - Vector search
└─────────────────────────────┘
```

### Tool Call Flow

```
User Input → ChatScreen → Keyword Detection → Tool Selection
     ↓
Perplexity API (with tool definitions)
     ↓
Tool Call Decision → Execute Tool → MCP Server
     ↓
Tool Result → Back to API → Final Response → User
```

---

## File Count

- **Total Documentation Files**: 15
  - Core server docs: 3 (SERVER_README.md, SERVER_TESTING.md, SEMANTIC_SEARCH.md)
  - Individual tool docs: 9 (in server/tools/)
  - App screen docs: 2 (in docs/)
  - Index: 1 (INFO.md)

---

## Contributing

When adding new features:
1. Create feature-specific documentation file
2. Update this INFO.md with link and description
3. Add cross-references to related documentation
4. Include code examples and usage instructions
5. Add troubleshooting section

---

## Version

**Day 18**: Реранкинг и фильтрация (Reranking and Filtering)

**Last Updated**: 2026-02-04

**Repository**: AI with Love - Educational Android Project
