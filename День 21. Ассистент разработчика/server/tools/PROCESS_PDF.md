# process_pdf Tool (Day 21: Local Processing)

## Overview

Processes PDF files by extracting text, chunking, and generating embeddings using **local Ollama**. **Note**: The Android app uses **client-side PDF extraction** (PDFBox), so this server-side tool is rarely used but available for server-side PDF processing.

## Architecture (Day 21)

```
MCP Server (process_pdf)
    ↓ [Decode base64 PDF]
    ↓ [Extract text with pdfplumber]
Local MCP Server (process_text_chunks)
    ↓ [Chunk text: 1000 chars, 200 overlap]
    ↓ [Generate embeddings via Ollama]
Local SQLite (/server/data/embeddings.db)
    ↓ [Save chunks + embeddings]
Return: chunks_saved count
```

## Parameters

- `pdf_base64` (required): Base64-encoded PDF file content
- `filename` (optional, default="document.pdf"): Original PDF filename
- `chunk_size` (optional, default=1000): Chunk size in characters
- `chunk_overlap` (optional, default=200): Overlap between chunks

## Response Format

```json
{
  "success": true,
  "chunks_saved": 15,
  "total_characters": 12500,
  "filename": "guide.pdf",
  "chunk_size": 1000,
  "chunk_overlap": 200,
  "processing_time_seconds": 8.25
}
```

## Processing Flow

1. **Decode Base64** to PDF bytes
2. **Extract Text** using pdfplumber library (optional dependency)
3. **Delegate to process_text_chunks** for chunking and embedding
4. **Return** processing results

## Requirements

**Optional**: pdfplumber library
```bash
pip install pdfplumber
```

**Fallback**: If pdfplumber not installed, returns error recommending client-side extraction.

## Example Usage

### Via curl (with base64-encoded PDF)
```bash
# First encode PDF to base64
PDF_BASE64=$(base64 -i document.pdf)

# Send to server
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "process_pdf",
      "arguments": {
        "pdf_base64": "'"$PDF_BASE64"'",
        "filename": "document.pdf"
      }
    }
  }'
```

## Client-Side vs Server-Side

### Client-Side (Android App - Recommended) ✅
**Advantages**:
- Faster (no base64 encoding/transfer)
- Less server load
- Better error handling
- Works with large PDFs

**Implementation**:
```kotlin
// OllamaViewModel.kt
fun uploadPdf(uri: Uri, fileName: String, context: Context) {
    // 1. Extract text with PDFBox (client-side)
    val extractedText = PDFTextStripper.getText(document)
    
    // 2. Send text to server
    mcpClient.callTool("process_text_chunks", mapOf(
        "text" to extractedText,
        "filename" to fileName
    ))
}
```

### Server-Side (MCP Tool - Optional)
**Use Cases**:
- Batch processing on server
- API integration without client PDF library
- Automated document ingestion

**Limitation**: Requires pdfplumber installation

## Day 21 Changes

- **Local Processing**: Removed remote proxy
- **Delegates to process_text_chunks**: Simplified implementation
- **Optional pdfplumber**: Graceful fallback if not installed
- **Recommendation**: Use client-side extraction (Android PDFBox)

## Recommendation

**For Android App**: Continue using client-side PDF extraction (current implementation) ✅
- Faster, more reliable, better UX
- No need for this server-side tool

**For Server APIs**: Install pdfplumber for server-side PDF processing
