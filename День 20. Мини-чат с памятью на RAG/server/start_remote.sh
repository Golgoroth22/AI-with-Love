#!/bin/bash
cd ~/mcp-local-server

# Kill any existing server process
pkill -9 -f "python3.*http_mcp_server.py" 2>/dev/null

# Start the server on port 8081 (accessible from external connections)
nohup python3 -u -c "
import http.server
import sys
import os

# Add current directory to path
sys.path.insert(0, '.')

# Import and initialize
from http_mcp_server import MCPServerHandler, init_database

# Initialize database
init_database()

# Create server bound to all interfaces
server = http.server.HTTPServer(('0.0.0.0', 8081), MCPServerHandler)
print('✅ MCP Local Server started on 0.0.0.0:8081', flush=True)
print('Ready to accept connections...', flush=True)

# Run server
server.serve_forever()
" > server.log 2>&1 &

# Wait a moment for server to start
sleep 2

# Get PID
SERVER_PID=$(pgrep -f "python3.*http_mcp_server.py" | tail -1)

if [ -n "$SERVER_PID" ]; then
    echo "✅ Server started with PID: $SERVER_PID"
    # Show last few lines of log
    tail -5 server.log
else
    echo "❌ Server failed to start"
    tail -10 server.log
    exit 1
fi
