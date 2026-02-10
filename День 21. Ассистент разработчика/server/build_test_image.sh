#!/bin/bash
# Build Docker image for MCP server tests

echo "🐳 Building MCP Server Test Docker Image"
echo "=========================================="

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running"
    echo "Please start Docker Desktop and try again"
    exit 1
fi

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Build the Docker image
echo "📦 Building image 'mcp-server-tests'..."
docker build -t mcp-server-tests "$SCRIPT_DIR"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Docker image built successfully!"
    echo ""
    echo "You can now:"
    echo "  1. Run tests manually:"
    echo "     docker run --rm mcp-server-tests"
    echo ""
    echo "  2. Use the run_tests tool from the Android app"
    echo "     (Ask: 'Запусти тесты' or 'Run tests')"
    echo ""
    echo "  3. Call via API:"
    echo "     curl -X POST http://localhost:8080 \\"
    echo "       -H 'Content-Type: application/json' \\"
    echo "       -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"run_tests\",\"arguments\":{}}}'"
else
    echo ""
    echo "❌ Build failed!"
    exit 1
fi
