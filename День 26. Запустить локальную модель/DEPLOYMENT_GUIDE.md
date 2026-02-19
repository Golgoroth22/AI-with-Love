# Deployment and Testing Guide

## Prerequisites

- SSH access to server: root@148.253.209.151
- nginx installed on server
- Docker and docker-compose installed on server
- Android Studio installed locally

## Part 1: Server Infrastructure Setup

### Step 1: Set up webpages directory

```bash
# SSH to server
ssh root@148.253.209.151

# Create webpages directory
mkdir -p /var/www/html/webpages
chmod 755 /var/www/html/webpages

# Verify directory was created
ls -la /var/www/html/ | grep webpages
# Expected output: drwxr-xr-x ... webpages
```

### Step 2: Configure nginx

```bash
# Check if nginx is installed and running
systemctl status nginx

# Edit nginx configuration
nano /etc/nginx/sites-available/default
```

Add this location block inside the `server { }` block (after existing location blocks):

```nginx
location /webpages/ {
    alias /var/www/html/webpages/;
    autoindex off;

    # Enable CORS if needed for cross-origin access
    add_header 'Access-Control-Allow-Origin' '*';
    add_header 'Access-Control-Allow-Methods' 'GET, OPTIONS';
}
```

Save and exit (Ctrl+X, Y, Enter).

```bash
# Test nginx configuration
sudo nginx -t
# Expected: nginx: configuration file /etc/nginx/nginx.conf test is successful

# Reload nginx
sudo systemctl reload nginx

# Verify nginx is serving the directory
curl -I http://localhost/webpages/
# Expected: HTTP/1.1 200 OK (or 404 if empty, which is fine)

# Exit SSH
exit
```

### Step 3: Deploy updated MCP server

```bash
# On your local machine, navigate to server directory
cd "/Users/falin/AndroidStudioProjects/AI-with-Love/День 24. Ассистент команды/server"

# Deploy using the quick deploy script
./deploy_quick.sh
```

If the script fails due to SSH key issues, deploy manually:

```bash
# Copy updated server file
scp http_mcp_server.py root@148.253.209.151:/opt/mcp-server/

# SSH to server and restart
ssh root@148.253.209.151 "cd /opt/mcp-server && docker compose restart"
```

### Step 4: Verify MCP server deployment

```bash
# Test tools/list to see if create_webpage is available
curl -X POST http://148.253.209.151:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | jq '.result.tools[] | select(.name=="create_webpage")'
```

Expected output:
```json
{
  "name": "create_webpage",
  "description": "Create a simple HTML webpage with provided text content and deploy it to the web server",
  "inputSchema": {
    "type": "object",
    "properties": {
      "text": {
        "type": "string",
        "description": "The text content to display on the webpage"
      },
      "title": {
        "type": "string",
        "description": "Optional page title (defaults to first 50 chars of text)"
      }
    },
    "required": ["text"]
  }
}
```

## Part 2: Test MCP Server Tool

### Test 1: Basic webpage creation

```bash
curl -X POST http://148.253.209.151:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "create_webpage",
      "arguments": {
        "text": "Hello World! This is my first webpage created via MCP."
      }
    }
  }' | jq '.'
```

Expected response:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\n  \"success\": true,\n  \"url\": \"http://148.253.209.151/webpages/page_20260217_123456_abc12345.html\",\n  \"filename\": \"page_20260217_123456_abc12345.html\",\n  \"filepath\": \"/var/www/html/webpages/page_20260217_123456_abc12345.html\",\n  \"timestamp\": \"2026-02-17T12:34:56.789\"\n}"
      }
    ]
  }
}
```

### Test 2: Verify webpage is accessible

```bash
# Extract URL from previous response and test
curl -I http://148.253.209.151/webpages/page_20260217_123456_abc12345.html
# Expected: HTTP/1.1 200 OK

# View the webpage content
curl http://148.253.209.151/webpages/page_20260217_123456_abc12345.html | head -20
```

### Test 3: Test with special characters (XSS prevention)

```bash
curl -X POST http://148.253.209.151:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "create_webpage",
      "arguments": {
        "text": "<script>alert(\"XSS\")</script>This should be escaped!"
      }
    }
  }' | jq '.result.content[0].text | fromjson'
```

Verify that the returned URL shows the script tags as text (escaped), not executed.

### Test 4: Test with long text

```bash
curl -X POST http://148.253.209.151:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "create_webpage",
      "arguments": {
        "text": "'"$(python3 -c "print('A' * 5000)")"'"
      }
    }
  }' | jq '.result.content[0].text | fromjson'
```

### Test 5: Test with emoji and Unicode

```bash
curl -X POST http://148.253.209.151:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "create_webpage",
      "arguments": {
        "text": "🚀 Привет, мир! 🎉 Hello World! 💻",
        "title": "Unicode Test Page"
      }
    }
  }' | jq '.result.content[0].text | fromjson'
```

## Part 3: Android App Testing

### Step 1: Open project in Android Studio

```bash
open -a "Android Studio" "/Users/falin/AndroidStudioProjects/AI-with-Love/День 25. Реальная задача"
```

Or manually:
1. Open Android Studio
2. File → Open
3. Navigate to `/Users/falin/AndroidStudioProjects/AI-with-Love/День 25. Реальная задача`
4. Click "Open"

### Step 2: Sync Gradle

1. Click "Sync Now" when prompted
2. Or: File → Sync Project with Gradle Files
3. Wait for dependencies to download (may take 2-5 minutes)

Expected: Build should succeed with no errors.

### Step 3: Fix any import errors

If you see red underlines in code:
1. File → Invalidate Caches → Invalidate and Restart
2. Build → Clean Project
3. Build → Rebuild Project

### Step 4: Run the app

1. **On Emulator:**
   - Tools → Device Manager
   - Create a new device (Pixel 5, API 34 recommended)
   - Click "Run" (green play button) or press Shift+F10
   - Select the emulator

2. **On Physical Device:**
   - Enable Developer Mode on your Android device
   - Enable USB Debugging
   - Connect device via USB
   - Click "Run" and select your device

### Step 5: Test the app

#### Test 1: Basic Flow
1. App opens to chat screen
2. You see welcome message: "Привет! Отправь мне любой текст..."
3. Type: "Hello, this is my first webpage!"
4. Click send button
5. Wait for response (2-3 seconds)
6. Verify you see:
   - ✅ Веб-страница создана!
   - 🔗 URL: http://148.253.209.151/webpages/page_...
   - 📄 Файл: page_...
7. Click "Открыть страницу →"
8. Browser opens and displays the webpage with your text

#### Test 2: Long Text
1. Type a long message (500+ characters)
2. Send and verify page is created
3. Open URL and verify all text displays correctly

#### Test 3: Special Characters
1. Type: `<b>Bold text</b> & special chars: < > " '`
2. Send and verify
3. Open URL and verify characters are escaped (shown as text, not HTML)

#### Test 4: Emoji
1. Type: "🚀 Hello World 🎉 Testing emojis 💻"
2. Send and verify
3. Open URL and verify emojis display correctly

#### Test 5: Multiple Pages
1. Create 3 different webpages with different text
2. Verify each has a unique URL
3. Open each URL in browser
4. Verify all pages are accessible and show correct content

#### Test 6: Error Handling
1. **Network Error:** Turn off WiFi, send message, verify error message shown
2. **Empty Input:** Try sending empty message, verify nothing happens
3. **Server Down:** Stop server, send message, verify error message

#### Test 7: Clear Chat
1. Create a webpage
2. Click "Новый чат" button
3. Verify chat clears and welcome message reappears
4. Previous webpage URLs should still be accessible in browser

## Part 4: Performance & Security Verification

### Performance Tests

```bash
# Test server response time
time curl -X POST http://148.253.209.151:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "create_webpage",
      "arguments": {"text": "Performance test"}
    }
  }' > /dev/null

# Should complete in < 2 seconds
```

### Security Verification

```bash
# 1. Verify XSS is escaped
curl http://148.253.209.151/webpages/page_XXXXX.html | grep -o "<script>" | wc -l
# Expected: 0 (no unescaped script tags)

# 2. Verify file permissions
ssh root@148.253.209.151 "ls -la /var/www/html/webpages/ | tail -5"
# Expected: -rw-r--r-- (644 permissions)

# 3. Test file path traversal attack
curl -X POST http://148.253.209.151:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "create_webpage",
      "arguments": {"text": "../../etc/passwd"}
    }
  }' | jq '.result.content[0].text | fromjson'
# Expected: Normal webpage created, no path traversal
```

### Check disk usage

```bash
ssh root@148.253.209.151 "du -sh /var/www/html/webpages/"
# Monitor to ensure it doesn't grow too large
```

## Part 5: Optional - Set Up Cleanup Cron Job

```bash
ssh root@148.253.209.151

# Edit crontab
crontab -e

# Add this line to delete files older than 30 days at 3 AM daily
0 3 * * * find /var/www/html/webpages -name "page_*.html" -mtime +30 -delete

# Save and exit

# Verify cron job
crontab -l | grep webpages
```

## Troubleshooting

### Issue: Server returns error "Failed to create webpage"

**Solution:**
```bash
ssh root@148.253.209.151
ls -la /var/www/html/webpages/
# Check permissions - should be 755 for directory

# Fix permissions if needed
chmod 755 /var/www/html/webpages
```

### Issue: Webpage URL returns 404

**Solution:**
```bash
# Check nginx configuration
sudo nginx -t
sudo systemctl status nginx

# Verify location block exists
grep -A 3 "location /webpages/" /etc/nginx/sites-available/default

# Reload nginx
sudo systemctl reload nginx
```

### Issue: Android app shows "Unresolved reference" errors

**Solution:**
1. File → Invalidate Caches → Invalidate and Restart
2. File → Sync Project with Gradle Files
3. Build → Clean Project
4. Build → Rebuild Project

### Issue: App crashes on send

**Solution:**
1. Check Logcat in Android Studio (View → Tool Windows → Logcat)
2. Filter for errors
3. Common issues:
   - Network permission missing → Check AndroidManifest.xml
   - Server URL incorrect → Check ServerConfig.kt
   - Koin not initialized → Check MainActivity.kt

## Success Criteria Checklist

- [ ] Webpages directory created and accessible
- [ ] nginx serves /webpages/ path
- [ ] MCP server has create_webpage tool
- [ ] MCP tool creates webpage successfully
- [ ] Webpage is accessible via browser
- [ ] XSS protection works (HTML escaped)
- [ ] Android app builds without errors
- [ ] App shows welcome message
- [ ] User can send message
- [ ] App receives webpage URL
- [ ] URL is clickable
- [ ] Browser opens webpage correctly
- [ ] Multiple pages have unique URLs
- [ ] Error handling works (network errors)
- [ ] Clear chat button works

## Next Steps

1. Complete all tests above
2. If everything works, consider:
   - Adding Room database for message persistence
   - Implementing webpage history view
   - Adding QR code generation for sharing
   - Implementing page editing functionality
   - Adding analytics (view counter)
   - Implementing custom themes/templates
