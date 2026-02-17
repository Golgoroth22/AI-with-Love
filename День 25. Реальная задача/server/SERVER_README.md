# MCP Server - Webpage Creator

Python HTTP server implementing the Model Context Protocol (MCP) with webpage creation tool.

## Overview

This MCP server provides a single tool for creating web pages from text:

**create_webpage** - Creates HTML pages from text with automatic deployment

## Quick Start

### Running Locally

```bash
python3 http_mcp_server.py
```

Server will start on `http://0.0.0.0:8080`

### Testing

```bash
# Test create_webpage tool
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "create_webpage",
      "arguments": {
        "text": "Hello World!",
        "title": "My Page"
      }
    }
  }'
```

## Production Deployment

### Prerequisites

- Docker & Docker Compose installed
- nginx configured for serving static files
- Port 8080 open

### Deploy to Remote Server

```bash
./deploy_quick.sh
```

This script will:
1. Copy server files to remote server
2. Build and start Docker container
3. Server will be available at `http://<SERVER_IP>:8080`

### Manual Deployment

```bash
# 1. Copy files to server
scp -r * root@<SERVER_IP>:/opt/mcp-server/

# 2. SSH to server
ssh root@<SERVER_IP>

# 3. Build and run with Docker
cd /opt/mcp-server
docker compose up -d --build

# 4. Check logs
docker compose logs -f
```

## create_webpage Tool

### Description
Creates an HTML webpage from provided text and deploys it to `/var/www/html/webpages/`

### Input Parameters
- `text` (required): Content for the webpage (max 10,000 chars)
- `title` (optional): Page title (defaults to "My Page")

### Output
```json
{
  "success": true,
  "url": "http://<SERVER_IP>:8080/webpages/page_20260217_123456_abc12345.html",
  "filename": "page_20260217_123456_abc12345.html",
  "filepath": "/var/www/html/webpages/page_20260217_123456_abc12345.html"
}
```

### Features
- 🔐 XSS Prevention (HTML escaping)
- 🆔 Unique filenames (timestamp + UUID)
- 🎨 Beautiful gradient design
- 🌍 UTF-8 encoding (emoji support)
- 📱 Responsive design

## nginx Configuration

Add to `/etc/nginx/sites-available/default`:

```nginx
location /webpages/ {
    alias /var/www/html/webpages/;
    autoindex off;
    add_header 'Access-Control-Allow-Origin' '*';
}
```

Then reload nginx:
```bash
sudo nginx -t
sudo systemctl reload nginx
```

## Directory Structure

```
/var/www/html/webpages/  # Created HTML pages (755 permissions)
├── page_20260217_120001_abc12345.html
├── page_20260217_120015_def67890.html
└── ...
```

## Troubleshooting

**Issue**: Files not accessible via HTTP
**Solution**: Check nginx configuration and `/var/www/html/webpages/` permissions (should be 755)

**Issue**: "Failed to create webpage"
**Solution**: Ensure `/var/www/html/webpages/` directory exists and has correct permissions

**Issue**: Docker container won't start
**Solution**: Check logs with `docker compose logs` and ensure port 8080 is not in use

## Security

- HTML content is escaped to prevent XSS attacks
- File paths use UUID to prevent traversal attacks
- Input validation (max 10,000 characters)
- Files created with 644 permissions (read-only for others)
