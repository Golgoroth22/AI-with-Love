#!/bin/bash
# Quick deploy script with hardcoded server details
# Deploy updated MCP server to remote machine

SERVER_IP="148.253.209.151"
USERNAME="root"  # Change if different

echo "🚀 Deploying updated MCP server to $SERVER_IP"
echo "================================================"
echo ""
echo "Changes being deployed:"
echo "  - Increased timeouts: 30s → 120s for save_document, search_similar, semantic_search"
echo "  - Increased timeout: 30s → 60s for create_embedding"
echo ""

# Copy updated server file
echo "📦 Copying http_mcp_server.py..."
scp http_mcp_server.py "$USERNAME@$SERVER_IP:/opt/mcp-server/"

if [ $? -ne 0 ]; then
    echo "❌ Failed to copy file!"
    echo ""
    echo "Possible issues:"
    echo "  - SSH key not configured"
    echo "  - Server IP or username incorrect"
    echo "  - Network connectivity problems"
    echo ""
    echo "Try running manually:"
    echo "  scp http_mcp_server.py $USERNAME@$SERVER_IP:/opt/mcp-server/"
    exit 1
fi

echo "✅ File copied successfully"
echo ""

# Restart the server
echo "🔄 Restarting Docker container..."
ssh "$USERNAME@$SERVER_IP" "cd /opt/mcp-server && docker compose restart"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Deployment successful!"
    echo ""
    echo "📊 Verify the server is running:"
    echo "  ssh $USERNAME@$SERVER_IP \"docker logs --tail 50 -f mcp-jokes-server\""
    echo ""
    echo "🧪 Test the server:"
    echo "  curl -X POST http://$SERVER_IP:8080 \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}'"
    echo ""
    echo "Changes deployed:"
    echo "  ✅ save_document timeout: 120 seconds"
    echo "  ✅ search_similar timeout: 120 seconds"
    echo "  ✅ semantic_search timeout: 120 seconds"
    echo "  ✅ create_embedding timeout: 60 seconds"
else
    echo "❌ Failed to restart server!"
    echo ""
    echo "Try manually:"
    echo "  ssh $USERNAME@$SERVER_IP \"cd /opt/mcp-server && docker compose restart\""
    exit 1
fi
