#!/bin/bash
# Start local MCP server with git tools on port 8082

cd "$(dirname "$0")"

echo "🚀 Starting Local Git MCP Server on port 8082..."
echo "📁 Repository: /Users/falin/AndroidStudioProjects/AI-with-Love"

# Set GitHub token from environment or use default
export GITHUB_TOKEN="${GITHUB_TOKEN:-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}"

python3 http_mcp_server.py 8082
