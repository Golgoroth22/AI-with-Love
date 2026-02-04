#!/bin/bash
# Deploy updated MCP server to remote machine

# Load credentials from SecureData.kt (you'll need to set these manually)
read -p "Enter server IP: " SERVER_IP
read -p "Enter username: " USERNAME

echo "🚀 Deploying updated MCP server to $SERVER_IP"
echo "================================================"

# Copy updated server file
echo "📦 Copying http_mcp_server.py..."
scp http_mcp_server.py "$USERNAME@$SERVER_IP:/opt/mcp-server/"

if [ $? -ne 0 ]; then
    echo "❌ Failed to copy file!"
    exit 1
fi

echo "✅ File copied successfully"

# Restart the server
echo "🔄 Restarting server..."
ssh "$USERNAME@$SERVER_IP" "cd /opt/mcp-server && docker compose restart"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Deployment successful!"
    echo ""
    echo "Verify the server is running:"
    echo "  ssh $USERNAME@$SERVER_IP \"docker logs -f mcp-jokes-server\""
    echo ""
    echo "Test the server:"
    echo "  curl -X POST http://$SERVER_IP:8080 \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}'"
else
    echo "❌ Failed to restart server!"
    exit 1
fi
