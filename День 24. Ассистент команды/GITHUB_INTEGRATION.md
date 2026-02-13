# GitHub MCP Integration - Day 21

## Overview

The Android app now integrates with GitHub's official MCP server to provide git and GitHub operations through the AI assistant. This enables developer workflows like browsing repositories, searching code, managing issues, and viewing commit history.

## Architecture

### Multi-Server System

```
Android App (ChatViewModel)
    ├─→ RAG Server (port 8081) - Document indexing & semantic search
    └─→ GitHub Server (remote) - Git & GitHub operations
```

**Key Components:**
- **McpClientManager**: Routes tool calls to appropriate servers based on tool name
- **McpClient**: HTTP client with authentication support (Bearer token for GitHub)
- **GitHub MCP Server**: Remote service at `https://api.githubcopilot.com/mcp/`

## Available GitHub Operations

### Repository Operations
- **get_repo**: Get repository information (description, stars, forks, languages, topics)
- **get_repo_content**: Read files or directory contents from a repository

### Code Search
- **search_code**: Search code across repositories using GitHub's search syntax

### Issue Management
- **create_issue**: Create new issues with title, body, and optional labels
- **list_issues**: List repository issues (filter by state: open/closed/all)

### Commit History
- **list_commits**: View recent commits on a branch

## Setup Instructions

### 1. Generate GitHub Personal Access Token (PAT)

1. Go to: https://github.com/settings/tokens
2. Click "Generate new token" → "Generate new token (classic)"
3. Name: "AI with Love - MCP Server"
4. Select scopes:
   - ✅ `repo` (Full control of private repositories)
   - ✅ `read:packages` (Download packages from GitHub Package Registry)
   - ✅ `read:org` (Read org and team membership)
5. Click "Generate token"
6. **Copy the token** (you won't see it again!)

### 2. Add Token to App

Edit `app/src/main/java/com/example/aiwithlove/util/SecureData.kt`:

```kotlin
object SecureData {
    val apiKey = "pplx-..."
    // ... existing fields ...

    // Replace with your actual token
    val githubPersonalAccessToken = "ghp_YOUR_TOKEN_HERE"
}
```

**⚠️ Security Note**: Never commit real tokens to version control!

### 3. Enable GitHub Server

1. Build and run the app
2. Open chat screen
3. Tap the MCP tools button (wrench icon) in the top bar
4. Toggle "GitHub Assistant" to enabled
5. The switch will turn blue/active

### 4. Test GitHub Integration

Try these example queries (use "GitWithLove" keyword to activate):

```
"GitWithLove покажи информацию о репозитории facebook/react"
"GitWithLove найди код с использованием Jetpack Compose в android/compose-samples"
"GitWithLove создать issue в owner/repo: Bug in authentication flow"
"GitWithLove список открытых issues в owner/repo"
"GitWithLove последние 5 коммитов в main ветке owner/repo"
"GitWithLove прочитай README.md из owner/repo"
```

## Configuration Options

### Remote vs Local Deployment

**Default: Remote (Recommended)**

```kotlin
// ServerConfig.kt
const val GITHUB_MCP_SERVER_URL = "https://api.githubcopilot.com/mcp/"
const val GITHUB_MCP_USE_LOCAL = false  // Use remote
```

**Pros:**
- Zero setup complexity
- Maintained by GitHub
- Always up-to-date
- OAuth support

**Cons:**
- Requires internet connection
- GitHub API rate limits apply
- Token must have appropriate permissions

**Alternative: Local Docker Deployment**

If you need offline operation or enhanced privacy:

```kotlin
const val GITHUB_MCP_USE_LOCAL = true
const val GITHUB_MCP_LOCAL_URL = "http://10.0.2.2:8082"
```

Then run:

```bash
docker pull ghcr.io/github/github-mcp-server:latest
docker run -d \
  -p 8082:8080 \
  -e GITHUB_PERSONAL_ACCESS_TOKEN=ghp_YOUR_TOKEN \
  ghcr.io/github/github-mcp-server:latest
```

## Usage Examples

### Example 1: Get Repository Info

**User Query:** "GitWithLove покажи информацию о репозитории facebook/react"

**What Happens:**
1. ChatViewModel detects GitHub keywords ("репозиторий")
2. Enables `get_repo` tool in the request to Perplexity
3. Perplexity calls `get_repo` with `owner=facebook`, `repo=react`
4. McpClientManager routes to GitHub server
5. GitHub MCP server returns repository details
6. AI formats response in Russian with stats

**Sample Response:**
```
📦 facebook/react

React - это библиотека JavaScript для создания пользовательских интерфейсов.

Статистика:
⭐ Stars: 226k
🔱 Forks: 46k
🏷️ Язык: JavaScript
📝 Описание: The library for web and native user interfaces
```

### Example 2: Search Code

**User Query:** "GitWithLove найди функцию authenticate в коде репозитория myorg/myapp"

**What Happens:**
1. Keywords "найди" + "функцию" trigger GitHub tools
2. AI constructs search query: `authenticate repo:myorg/myapp`
3. Calls `search_code` tool
4. Returns code snippets with file paths and line numbers

### Example 3: Create Issue

**User Query:** "GitWithLove создать issue в myorg/myapp: Add dark mode support"

**What Happens:**
1. Keywords "создать issue" trigger GitHub tools
2. AI parses: owner=myorg, repo=myapp, title="Add dark mode support"
3. Calls `create_issue` tool
4. Confirms issue created with link

## Keyword Detection

The app automatically enables GitHub tools when it detects the magic keyword:

**Activation Keyword**: `GitWithLove` (case-insensitive)

Simply include "GitWithLove" anywhere in your message to activate GitHub tools.

**Examples:**
- ✅ "GitWithLove покажи репозиторий facebook/react"
- ✅ "gitwithlove список issues"
- ✅ "Gitwithlove найди код compose"
- ✅ "покажи репозиторий facebook/react GitWithLove"

## Troubleshooting

### Issue: "No enabled server found for tool: get_repo"

**Cause**: GitHub server is not enabled in the MCP dialog

**Solution**:
1. Open chat screen
2. Tap MCP tools button (wrench icon)
3. Enable "GitHub Assistant"

### Issue: Authentication Failed (401)

**Cause**: Invalid or missing GitHub PAT

**Solution**:
1. Verify token in `SecureData.kt` is correct
2. Check token hasn't expired (go to GitHub settings → Tokens)
3. Ensure token has required scopes (`repo`, `read:packages`, `read:org`)
4. Rebuild the app after updating the token

### Issue: Rate Limit Exceeded

**Cause**: GitHub API rate limits reached

**Solution**:
- Authenticated requests: 5000/hour
- Wait for rate limit reset (shown in error message)
- Consider using local Docker deployment for unlimited requests

### Issue: Repository Not Found

**Cause**: Repository doesn't exist or is private without access

**Solution**:
- Verify repository name: `owner/repo` format
- For private repos: ensure PAT has `repo` scope
- Check repository visibility settings on GitHub

## Technical Details

### Authentication Flow

```
1. App makes request to GitHub MCP server
2. McpClient adds Bearer token to request headers
   - Header: "Authorization: Bearer ghp_YOUR_TOKEN"
3. GitHub MCP server validates token with GitHub API
4. On success: returns requested data
5. On failure: returns 401/403 error
```

### Tool Routing Logic

```kotlin
// In McpClientManager
suspend fun callTool(
    toolName: String,
    arguments: Map<String, Any>,
    enabledServers: List<String>
): String {
    // Find which server provides this tool
    val serverConfig = serverConfigs.find { config ->
        config.isEnabled &&
        enabledServers.contains(config.id) &&
        config.tools.any { it.name == toolName }
    }

    // Route to appropriate client
    val client = clients[serverConfig.id]
    return client.callTool(toolName, arguments)
}
```

### Tool Detection Logic

```kotlin
// In ChatViewModel
private fun userMentionsGitHub(message: String): Boolean {
    val keywords = listOf(
        "репозиторий", "repo", "github",
        "создать issue", "коммиты", ...
    )
    return keywords.any { message.lowercase().contains(it) }
}
```

## Performance Considerations

### Request Latency

| Operation | Typical Time |
|-----------|--------------|
| get_repo | 500-1000ms |
| search_code | 1-2 seconds |
| create_issue | 800-1200ms |
| list_commits | 600-1000ms |

**Factors:**
- GitHub API response time
- Network latency to GitHub servers
- Size of repository
- Number of results returned

### Token Usage

Each GitHub operation adds to your Perplexity API token usage:
- Tool definition in request: ~200 tokens
- Tool call result: varies (500-2000 tokens)
- AI response generation: ~300-800 tokens

**Estimated cost per query**: $0.002-0.005 (depending on complexity)

## Hybrid Workflows

The power of multi-server architecture is combining GitHub with RAG:

### Example: Document-Enhanced Issue Creation

**User:** "найди документацию об архитектуре, затем GitWithLove создай issue с предложениями по улучшению"

**Flow:**
1. **Semantic search** finds architecture docs from local database
2. AI reads and summarizes findings
3. **GitHub create_issue** creates issue with detailed suggestions
4. Response includes both documentation citations and GitHub issue link

### Example: Code Discovery with Context

**User:** "GitWithLove найди в github примеры использования Compose Navigation, затем найди наши документы по навигации"

**Flow:**
1. **GitHub search_code** finds public examples
2. **Semantic search** finds internal documentation
3. AI provides comprehensive answer with both sources

## Future Enhancements

### Planned Features

1. **Pull Request Support**
   - `create_pull_request`: Create PRs with title, body, base, head
   - `list_pull_requests`: List PRs with filters
   - `merge_pull_request`: Merge approved PRs

2. **Branch Management**
   - `list_branches`: View all branches
   - `create_branch`: Create new branches
   - `get_branch`: Get branch details

3. **GitHub Actions**
   - `list_workflow_runs`: View CI/CD runs
   - `get_workflow_run`: Get run details
   - `trigger_workflow`: Manually trigger workflows

4. **Repository Context Persistence**
   - Remember last accessed repository
   - Quick repo switching
   - Favorite repositories list

### Wishlist

- Comment on issues/PRs
- Review pull requests
- Manage labels and milestones
- Access GitHub Discussions
- View security alerts

## API Reference

### get_repo

**Parameters:**
- `owner` (string, required): Repository owner
- `repo` (string, required): Repository name

**Returns:** Repository information object

### search_code

**Parameters:**
- `query` (string, required): Search query (GitHub syntax)
- `max_results` (integer, optional): Max results (default: 5)

**Returns:** Array of code search results

### create_issue

**Parameters:**
- `owner` (string, required): Repository owner
- `repo` (string, required): Repository name
- `title` (string, required): Issue title
- `body` (string, required): Issue description (markdown)

**Returns:** Created issue object with URL

### list_issues

**Parameters:**
- `owner` (string, required): Repository owner
- `repo` (string, required): Repository name
- `state` (string, optional): Filter by state (default: "open")

**Returns:** Array of issues

### list_commits

**Parameters:**
- `owner` (string, required): Repository owner
- `repo` (string, required): Repository name
- `max_results` (integer, optional): Max commits (default: 10)

**Returns:** Array of commit objects

### get_repo_content

**Parameters:**
- `owner` (string, required): Repository owner
- `repo` (string, required): Repository name
- `path` (string, required): File or directory path

**Returns:** File content or directory listing

## Resources

- **GitHub MCP Server**: https://github.com/github/github-mcp-server
- **GitHub API Docs**: https://docs.github.com/en/rest
- **MCP Protocol**: https://github.com/modelcontextprotocol
- **Rate Limits**: https://docs.github.com/en/rest/overview/resources-in-the-rest-api#rate-limiting

## Summary

GitHub MCP integration transforms the AI assistant into a powerful developer tool that bridges documentation (RAG) and repository operations (GitHub). With simple keyword detection and multi-server routing, users can seamlessly query both local knowledge and live GitHub data in natural language.

**Key Benefits:**
- 🔧 **Developer-Friendly**: No CLI commands, just natural language
- 🔀 **Multi-Source**: Combine GitHub + RAG for comprehensive answers
- 🔐 **Secure**: Your token never leaves your device
- ⚡ **Fast**: Direct GitHub API access via MCP
- 🎯 **Smart**: Automatic tool detection from keywords
