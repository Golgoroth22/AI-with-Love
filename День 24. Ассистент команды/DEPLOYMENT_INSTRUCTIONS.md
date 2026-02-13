# Deployment Instructions: Performance Optimization

## What Was Changed

### Server-Side Optimization (MCP Server)

The `process_text_chunks` tool was optimized with **parallel processing** to solve timeout issues with large files:

**Before:**
- Sequential processing: 1 chunk at a time
- CLAUDE.md (15KB) took 5+ minutes → **Timeout error**

**After:**
- Parallel processing: 4 chunks simultaneously
- CLAUDE.md (15KB) takes ~80 seconds → **Success** ✅
- **4x faster** for large documents

### Files Modified

1. `server/http_mcp_server.py` - Added ThreadPoolExecutor for parallel chunk processing
2. `server/deploy_quick.sh` - Updated deployment messages
3. `server/PERFORMANCE_OPTIMIZATION.md` - Detailed documentation (NEW)
4. `CLAUDE.md` - Updated architecture documentation

## Testing Locally

Before deploying, verify the changes work correctly:

```bash
cd server

# 1. Check syntax
python3 -m py_compile http_mcp_server.py

# 2. Run test suite
python3 test_http_mcp_server.py
# Expected: All 10 tests pass

# 3. Start local server
python3 http_mcp_server.py

# 4. Test with Android app
# - Run app in emulator
# - Go to Ollama screen
# - Upload CLAUDE.md file
# - Should complete in < 2 minutes (vs. timeout before)
```

## Deploying to Remote Server

### Prerequisites

- SSH access to remote server (148.253.209.151)
- SSH key configured: `ssh root@148.253.209.151` should work without password

### Deployment Steps

#### Option 1: Quick Deploy (Automated)

```bash
cd server
./deploy_quick.sh
```

This script will:
1. Copy `http_mcp_server.py` to remote server
2. Restart Docker container
3. Show verification commands

#### Option 2: Manual Deploy

```bash
# 1. Copy updated file
scp server/http_mcp_server.py root@148.253.209.151:/opt/mcp-server/

# 2. SSH into server
ssh root@148.253.209.151

# 3. Restart MCP server
cd /opt/mcp-server
docker compose restart

# 4. Verify server is running
docker logs --tail 50 -f mcp-jokes-server

# 5. Exit (Ctrl+C to stop logs, then exit)
exit
```

## Verifying Deployment

### 1. Test Server Availability

```bash
curl -X POST http://148.253.209.151:8081 \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list"
  }'
```

Expected: JSON response with list of available tools

### 2. Test in Android App

1. Update `McpServerConfig.kt` to use remote server:
   ```kotlin
   val serverUrl = "http://148.253.209.151:8081"  // Production server
   ```

2. Run app on device/emulator

3. Navigate to Ollama screen

4. Upload a large text file (e.g., CLAUDE.md)

5. **Expected results:**
   - Processing completes in < 2 minutes (was 5+ minutes)
   - No timeout errors
   - All chunks saved successfully
   - Progress updates visible in logs

### 3. Monitor Server Logs

```bash
ssh root@148.253.209.151
docker logs -f mcp-jokes-server
```

Look for:
- `⚡ Using 4 parallel workers` - Confirms parallel processing is active
- `💾 Progress: X/Y chunks saved...` - Shows real-time progress
- `🎉 Processing complete: X chunks saved, 0 failed in Y.Ys` - Success message
- `⚡ Average speed: Y.Ys per chunk` - Performance metrics

## Troubleshooting

### SSH Connection Failed

**Error:** `Permission denied (publickey,password)`

**Solution:**
1. Check if SSH key is configured: `ssh-add -l`
2. Add key if missing: `ssh-add ~/.ssh/id_rsa`
3. Or use password authentication: `ssh -o PreferredAuthentications=password root@148.253.209.151`

### Server Not Responding After Deploy

**Check 1:** Is Docker container running?
```bash
ssh root@148.253.209.151 "docker ps | grep mcp"
```

**Check 2:** Any errors in logs?
```bash
ssh root@148.253.209.151 "docker logs --tail 100 mcp-jokes-server"
```

**Fix:** Restart container
```bash
ssh root@148.253.209.151 "cd /opt/mcp-server && docker compose restart"
```

### Still Getting Timeouts

**Possible causes:**
1. Ollama service not running on remote server
2. Network issues between MCP server and Ollama
3. Very large file (> 50KB) with default settings

**Solutions:**
1. Increase max_workers in client request:
   ```kotlin
   val args = mapOf(
       "text" to text,
       "filename" to filename,
       "max_workers" to 8  // Increase from default 4
   )
   ```

2. Check Ollama service:
   ```bash
   ssh root@148.253.209.151
   curl http://localhost:11434/api/embeddings -d '{"model":"nomic-embed-text","prompt":"test"}'
   ```

## Rollback Plan

If the new version causes issues:

### 1. Check Git History

```bash
cd server
git log --oneline http_mcp_server.py
```

### 2. Revert to Previous Version

```bash
# Find the commit hash before optimization
git checkout <previous_commit_hash> http_mcp_server.py

# Deploy old version
./deploy_quick.sh
```

### 3. Or Keep Optimization but Disable Parallelism

Edit `http_mcp_server.py` and change:
```python
max_workers = args.get('max_workers', 1)  # Force sequential processing
```

## Performance Benchmarks

After deployment, test with different file sizes:

| File Size | Expected Time | Max Workers |
|-----------|--------------|-------------|
| 5 KB | < 30 seconds | 2 |
| 15 KB (CLAUDE.md) | < 90 seconds | 4 |
| 50 KB | < 3 minutes | 6-8 |
| 100 KB | < 5 minutes | 8-10 |

## Next Steps

After successful deployment:

1. ✅ Test with various file sizes
2. ✅ Monitor server performance (CPU, memory)
3. ✅ Update app configuration to use remote server by default
4. ✅ Document any issues or edge cases
5. ✅ Consider adding progress UI in Android app (future enhancement)

## Support

If you encounter issues:

1. Check server logs: `docker logs mcp-jokes-server`
2. Review `server/PERFORMANCE_OPTIMIZATION.md`
3. Test locally first: `python3 test_http_mcp_server.py`
4. Check network connectivity: `ping 148.253.209.151`

## Questions?

See detailed documentation:
- `server/PERFORMANCE_OPTIMIZATION.md` - Technical details
- `CLAUDE.md` - Project overview and architecture
- `server/README.md` - Server setup and configuration
