#!/usr/bin/env python3
"""
MCP Server with JokeAPI Integration and SQLite Database
Supports JSON-RPC 2.0 protocol via HTTP POST
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import urllib.request
import urllib.parse
import sqlite3
import os
import subprocess
import re
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading

EMBEDDINGS_DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data', 'embeddings.db')
# Use local Ollama instance for embeddings
OLLAMA_API_URL = "http://localhost:11434"

# Semantic search configuration
SEMANTIC_SEARCH_CONFIG = {
    'default_threshold': 0.6,  # Default similarity threshold (60%) - Matches app default
    'min_threshold': 0.3,      # Minimum allowed threshold (30%)
    'max_threshold': 0.95      # Maximum allowed threshold (95%)
}

# GitHub API configuration
GITHUB_API_BASE_URL = "https://api.github.com"
GITHUB_TOKEN = None  # Will be set from environment or config
GITHUB_DEFAULT_OWNER = "Golgoroth22"  # Default repository owner

# Local Git configuration
GIT_DEFAULT_REPO_PATH = "/Users/falin/AndroidStudioProjects/AI-with-Love"

def set_github_token(token):
    """Set GitHub Personal Access Token"""
    global GITHUB_TOKEN
    GITHUB_TOKEN = token

def github_api_request(endpoint, method='GET', data=None):
    """Make authenticated request to GitHub API"""
    if not GITHUB_TOKEN:
        raise ValueError("GitHub token not configured")

    url = f"{GITHUB_API_BASE_URL}{endpoint}"
    headers = {
        'Authorization': f'Bearer {GITHUB_TOKEN}',
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28'
    }

    req = urllib.request.Request(url, headers=headers, method=method)
    if data:
        req.data = json.dumps(data).encode('utf-8')
        req.add_header('Content-Type', 'application/json')

    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.loads(response.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        error_body = e.read().decode('utf-8')
        raise Exception(f"GitHub API error {e.code}: {error_body}")

def init_database():
    """Initialize SQLite database"""
    # Initialize embeddings database
    conn = sqlite3.connect(EMBEDDINGS_DB_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS documents (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            content TEXT NOT NULL,
            embedding BLOB NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')

    # Add citation columns for source tracking (backward compatible)
    try:
        cursor.execute("ALTER TABLE documents ADD COLUMN source_file TEXT DEFAULT 'unknown'")
        cursor.execute("ALTER TABLE documents ADD COLUMN source_type TEXT DEFAULT 'manual'")
        cursor.execute("ALTER TABLE documents ADD COLUMN chunk_index INTEGER DEFAULT 0")
        cursor.execute("ALTER TABLE documents ADD COLUMN page_number INTEGER")
        cursor.execute("ALTER TABLE documents ADD COLUMN total_chunks INTEGER DEFAULT 1")
        cursor.execute("ALTER TABLE documents ADD COLUMN metadata TEXT DEFAULT '{}'")
        print("✅ Added citation columns to documents table")
    except sqlite3.OperationalError:
        # Columns already exist
        pass

    # Create index for better query performance
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_documents_source ON documents(source_file, chunk_index)")

    conn.commit()
    conn.close()
    print(f"📦 Embeddings database initialized: {EMBEDDINGS_DB_PATH}")

# Database helper utilities
def _serialize_embedding(embedding):
    """Convert embedding list to binary blob"""
    import struct
    return struct.pack(f'{len(embedding)}f', *embedding)

def _deserialize_embedding(blob):
    """Convert binary blob to embedding list"""
    import struct
    num_floats = len(blob) // 4
    return list(struct.unpack(f'{num_floats}f', blob))

def _cosine_similarity(vec1, vec2):
    """Calculate cosine similarity between two vectors"""
    import math
    dot_product = sum(a * b for a, b in zip(vec1, vec2))
    magnitude1 = math.sqrt(sum(a * a for a in vec1))
    magnitude2 = math.sqrt(sum(b * b for b in vec2))

    if magnitude1 == 0 or magnitude2 == 0:
        return 0.0

    return dot_product / (magnitude1 * magnitude2)

class EmbeddingsDatabase:
    """Helper class for embeddings database operations"""

    @staticmethod
    def save_document_with_embedding(content, embedding, source_file='manual_entry',
                                     source_type='manual', chunk_index=0,
                                     page_number=None, total_chunks=1, metadata='{}'):
        """Save document with embedding to database"""
        conn = sqlite3.connect(EMBEDDINGS_DB_PATH)
        cursor = conn.cursor()

        # Convert embedding to binary format
        embedding_blob = _serialize_embedding(embedding)

        cursor.execute('''
            INSERT INTO documents
            (content, embedding, source_file, source_type, chunk_index,
             page_number, total_chunks, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ''', (content, embedding_blob, source_file, source_type,
              chunk_index, page_number, total_chunks, metadata))

        doc_id = cursor.lastrowid
        conn.commit()
        conn.close()

        return doc_id

    @staticmethod
    def search_similar_documents(query_embedding, limit=5):
        """Search for similar documents using cosine similarity"""
        conn = sqlite3.connect(EMBEDDINGS_DB_PATH)
        cursor = conn.cursor()

        # Fetch all documents
        cursor.execute('''
            SELECT id, content, embedding, source_file, source_type,
                   chunk_index, page_number, total_chunks, metadata
            FROM documents
        ''')

        results = []
        query_emb_array = query_embedding

        for row in cursor.fetchall():
            doc_id, content, emb_blob, src_file, src_type, chunk_idx, page_num, total, meta = row

            # Deserialize embedding
            doc_embedding = _deserialize_embedding(emb_blob)

            # Calculate cosine similarity
            similarity = _cosine_similarity(query_emb_array, doc_embedding)

            results.append({
                'id': doc_id,
                'content': content,
                'similarity': similarity,
                'source_file': src_file,
                'source_type': src_type,
                'chunk_index': chunk_idx,
                'page_number': page_num,
                'total_chunks': total,
                'metadata': meta
            })

        conn.close()

        # Sort by similarity descending
        results.sort(key=lambda x: x['similarity'], reverse=True)

        return results[:limit]

    @staticmethod
    def count_documents():
        """Get total document count"""
        conn = sqlite3.connect(EMBEDDINGS_DB_PATH)
        cursor = conn.cursor()
        cursor.execute('SELECT COUNT(*) FROM documents')
        count = cursor.fetchone()[0]
        conn.close()
        return count

class MCPServerHandler(BaseHTTPRequestHandler):
    
    def log(self, message):
        """Custom logging"""
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        print(f'[{timestamp}] {message}')
    
    def do_POST(self):
        """Handle POST requests"""
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        
        try:
            request = json.loads(post_data.decode('utf-8'))
            self.log(f"📨 Received request: {request.get('method')}")
            
            response = self.handle_mcp_request(request)
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            self.wfile.write(json.dumps(response).encode('utf-8'))
            self.log(f"✅ Response sent successfully")
            
        except Exception as e:
            self.log(f"❌ Error: {str(e)}")
            self.send_error(500, str(e))
    
    def do_OPTIONS(self):
        """Handle CORS preflight"""
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def handle_mcp_request(self, request):
        """Process MCP JSON-RPC request"""
        method = request.get('method')
        request_id = request.get('id')
        
        if method == 'initialize':
            return self.handle_initialize(request_id)
        elif method == 'tools/list':
            return self.handle_tools_list(request_id)
        elif method == 'tools/call':
            return self.handle_tools_call(request_id, request.get('params', {}))
        else:
            return {
                'jsonrpc': '2.0',
                'id': request_id,
                'error': {
                    'code': -32601,
                    'message': f'Method not found: {method}'
                }
            }
    
    def handle_initialize(self, request_id):
        """Handle initialize request"""
        return {
            'jsonrpc': '2.0',
            'id': request_id,
            'result': {
                'protocolVersion': '2024-11-05',
                'capabilities': {
                    'tools': {}
                },
                'serverInfo': {
                    'name': 'Python HTTP MCP Server',
                    'version': '2.0.0'
                }
            }
        }
    
    def handle_tools_list(self, request_id):
        """Return list of available tools"""
        tools = [
            {
                'name': 'create_embedding',
                'description': 'Generate embeddings for text using Ollama nomic-embed-text model',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'text': {
                            'type': 'string',
                            'description': 'Text to generate embeddings for'
                        }
                    },
                    'required': ['text']
                }
            },
            {
                'name': 'save_document',
                'description': 'Save a document with its embedding and source citation info to the database. Automatically generates embedding if not provided.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'content': {
                            'type': 'string',
                            'description': 'Document content to save'
                        },
                        'source_file': {
                            'type': 'string',
                            'description': 'Source filename (e.g., "api_guide.pdf")',
                            'default': 'manual_entry'
                        },
                        'source_type': {
                            'type': 'string',
                            'description': 'File type: pdf, txt, or manual',
                            'default': 'manual'
                        },
                        'chunk_index': {
                            'type': 'integer',
                            'description': 'Chunk position in document (0-based)',
                            'default': 0
                        },
                        'page_number': {
                            'type': 'integer',
                            'description': 'Page number in PDF (optional)'
                        },
                        'total_chunks': {
                            'type': 'integer',
                            'description': 'Total number of chunks from this source',
                            'default': 1
                        },
                        'metadata': {
                            'type': 'string',
                            'description': 'JSON metadata (author, title, date, etc.)',
                            'default': '{}'
                        }
                    },
                    'required': ['content']
                }
            },
            {
                'name': 'search_similar',
                'description': 'Search for similar documents using cosine similarity',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'query': {
                            'type': 'string',
                            'description': 'Query text to search for similar documents'
                        },
                        'limit': {
                            'type': 'integer',
                            'description': 'Maximum number of results to return (default: 5)',
                            'default': 5
                        }
                    },
                    'required': ['query']
                }
            },
            {
                'name': 'semantic_search',
                'description': 'Search for relevant document chunks from remote MCP server using semantic similarity. Use this to find context from indexed documents to answer questions.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'query': {
                            'type': 'string',
                            'description': 'Question or query text to search for relevant document chunks'
                        },
                        'limit': {
                            'type': 'integer',
                            'description': 'Maximum number of relevant chunks to return (default: 3)',
                            'default': 3
                        },
                        'threshold': {
                            'type': 'number',
                            'description': 'Minimum similarity score (0.0-1.0). Only return documents with similarity >= threshold (default: 0.7)',
                            'default': 0.7
                        },
                        'compare_mode': {
                            'type': 'boolean',
                            'description': 'If true, return both unfiltered and filtered results for comparison (default: false)',
                            'default': False
                        }
                    },
                    'required': ['query']
                }
            },
            {
                'name': 'process_pdf',
                'description': 'Process a PDF file: extract text, chunk it, and save chunks with embeddings',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'pdf_base64': {
                            'type': 'string',
                            'description': 'Base64-encoded PDF file content'
                        },
                        'filename': {
                            'type': 'string',
                            'description': 'Original filename of the PDF'
                        },
                        'chunk_size': {
                            'type': 'integer',
                            'description': 'Characters per chunk (default: 1000)',
                            'default': 1000
                        },
                        'chunk_overlap': {
                            'type': 'integer',
                            'description': 'Overlap between chunks (default: 200)',
                            'default': 200
                        }
                    },
                    'required': ['pdf_base64', 'filename']
                }
            },
            {
                'name': 'process_text_chunks',
                'description': 'Process extracted text: chunk it and save chunks with embeddings (for client-side PDF extraction)',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'text': {
                            'type': 'string',
                            'description': 'Extracted text content to process'
                        },
                        'filename': {
                            'type': 'string',
                            'description': 'Original filename for metadata'
                        },
                        'chunk_size': {
                            'type': 'integer',
                            'description': 'Characters per chunk (default: 1000)',
                            'default': 1000
                        },
                        'chunk_overlap': {
                            'type': 'integer',
                            'description': 'Overlap between chunks (default: 200)',
                            'default': 200
                        }
                    },
                    'required': ['text', 'filename']
                }
            },
            {
                'name': 'get_repo',
                'description': 'Get detailed information about a GitHub repository. Owner defaults to Golgoroth22 if not specified.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'owner': {
                            'type': 'string',
                            'description': 'Repository owner (default: Golgoroth22)',
                            'default': 'Golgoroth22'
                        },
                        'repo': {
                            'type': 'string',
                            'description': 'Repository name'
                        }
                    },
                    'required': ['repo']
                }
            },
            {
                'name': 'search_code',
                'description': 'Search for code across GitHub repositories',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'query': {
                            'type': 'string',
                            'description': 'Search query using GitHub search syntax'
                        },
                        'max_results': {
                            'type': 'integer',
                            'description': 'Maximum number of results to return (default: 5)',
                            'default': 5
                        }
                    },
                    'required': ['query']
                }
            },
            {
                'name': 'create_issue',
                'description': 'Create a new issue in a GitHub repository. Owner defaults to Golgoroth22 if not specified.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'owner': {
                            'type': 'string',
                            'description': 'Repository owner (default: Golgoroth22)',
                            'default': 'Golgoroth22'
                        },
                        'repo': {
                            'type': 'string',
                            'description': 'Repository name'
                        },
                        'title': {
                            'type': 'string',
                            'description': 'Issue title'
                        },
                        'body': {
                            'type': 'string',
                            'description': 'Issue description (markdown supported)'
                        }
                    },
                    'required': ['repo', 'title', 'body']
                }
            },
            {
                'name': 'list_issues',
                'description': 'List issues from a GitHub repository. Owner defaults to Golgoroth22 if not specified.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'owner': {
                            'type': 'string',
                            'description': 'Repository owner (default: Golgoroth22)',
                            'default': 'Golgoroth22'
                        },
                        'repo': {
                            'type': 'string',
                            'description': 'Repository name'
                        },
                        'state': {
                            'type': 'string',
                            'description': 'Filter by state: open, closed, or all (default: open)',
                            'default': 'open'
                        }
                    },
                    'required': ['repo']
                }
            },
            {
                'name': 'list_commits',
                'description': 'List commit history from a GitHub repository. Owner defaults to Golgoroth22 if not specified.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'owner': {
                            'type': 'string',
                            'description': 'Repository owner (default: Golgoroth22)',
                            'default': 'Golgoroth22'
                        },
                        'repo': {
                            'type': 'string',
                            'description': 'Repository name'
                        },
                        'max_results': {
                            'type': 'integer',
                            'description': 'Maximum number of commits to return (default: 10)',
                            'default': 10
                        }
                    },
                    'required': ['repo']
                }
            },
            {
                'name': 'get_repo_content',
                'description': 'Get file contents or directory listing from a GitHub repository. Owner defaults to Golgoroth22 if not specified.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'owner': {
                            'type': 'string',
                            'description': 'Repository owner (default: Golgoroth22)',
                            'default': 'Golgoroth22'
                        },
                        'repo': {
                            'type': 'string',
                            'description': 'Repository name'
                        },
                        'path': {
                            'type': 'string',
                            'description': 'File or directory path in the repository'
                        }
                    },
                    'required': ['repo', 'path']
                }
            },
            {
                'name': 'git_status',
                'description': 'Get current git repository status: modified files, staged files, untracked files, current branch, ahead/behind remote',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'repo_path': {
                            'type': 'string',
                            'description': f'Path to git repository (default: {GIT_DEFAULT_REPO_PATH})',
                            'default': GIT_DEFAULT_REPO_PATH
                        }
                    },
                    'required': []
                }
            },
            {
                'name': 'git_branch',
                'description': 'List all local and remote branches, showing which is currently active',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'repo_path': {
                            'type': 'string',
                            'description': f'Path to git repository (default: {GIT_DEFAULT_REPO_PATH})',
                            'default': GIT_DEFAULT_REPO_PATH
                        },
                        'include_remote': {
                            'type': 'boolean',
                            'description': 'Include remote branches (default: true)',
                            'default': True
                        }
                    },
                    'required': []
                }
            },
            {
                'name': 'git_diff',
                'description': 'Get diff for files (unstaged or staged changes)',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'repo_path': {
                            'type': 'string',
                            'description': f'Path to git repository (default: {GIT_DEFAULT_REPO_PATH})',
                            'default': GIT_DEFAULT_REPO_PATH
                        },
                        'filepath': {
                            'type': 'string',
                            'description': 'Optional: specific file path to diff (omit for all changes)'
                        },
                        'staged': {
                            'type': 'boolean',
                            'description': 'If true, show staged changes; if false, show unstaged (default: false)',
                            'default': False
                        },
                        'max_lines': {
                            'type': 'integer',
                            'description': 'Maximum lines of diff output (default: 500)',
                            'default': 500
                        }
                    },
                    'required': []
                }
            },
            {
                'name': 'git_pr_status',
                'description': 'Check pull request status for current branch by combining local git info with GitHub API. Returns current branch, related PRs, and whether PRs are open.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'repo_path': {
                            'type': 'string',
                            'description': f'Path to git repository (default: {GIT_DEFAULT_REPO_PATH})',
                            'default': GIT_DEFAULT_REPO_PATH
                        }
                    },
                    'required': []
                }
            }
        ]

        return {
            'jsonrpc': '2.0',
            'id': request_id,
            'result': {
                'tools': tools
            }
        }
    
    def handle_tools_call(self, request_id, params):
        """Execute tool call"""
        tool_name = params.get('name')
        arguments = params.get('arguments', {})

        # Truncate large arguments for logging
        log_args = arguments.copy()
        if 'pdf_base64' in log_args and len(log_args['pdf_base64']) > 100:
            log_args['pdf_base64'] = f"{log_args['pdf_base64'][:100]}... ({len(log_args['pdf_base64'])} chars)"

        self.log(f"🔧 Calling tool: {tool_name} with args: {log_args}")

        try:
            if tool_name == 'create_embedding':
                result = self.tool_create_embedding(arguments)
            elif tool_name == 'save_document':
                result = self.tool_save_document(arguments)
            elif tool_name == 'search_similar':
                result = self.tool_search_similar(arguments)
            elif tool_name == 'semantic_search':
                result = self.tool_semantic_search(arguments)
            elif tool_name == 'process_pdf':
                result = self.tool_process_pdf(arguments)
            elif tool_name == 'process_text_chunks':
                result = self.tool_process_text_chunks(arguments)
            elif tool_name == 'get_repo':
                result = self.tool_get_repo(arguments)
            elif tool_name == 'search_code':
                result = self.tool_search_code(arguments)
            elif tool_name == 'create_issue':
                result = self.tool_create_issue(arguments)
            elif tool_name == 'list_issues':
                result = self.tool_list_issues(arguments)
            elif tool_name == 'list_commits':
                result = self.tool_list_commits(arguments)
            elif tool_name == 'get_repo_content':
                result = self.tool_get_repo_content(arguments)
            elif tool_name == 'git_status':
                result = self.tool_git_status(arguments)
            elif tool_name == 'git_branch':
                result = self.tool_git_branch(arguments)
            elif tool_name == 'git_diff':
                result = self.tool_git_diff(arguments)
            elif tool_name == 'git_pr_status':
                result = self.tool_git_pr_status(arguments)
            else:
                raise ValueError(f'Unknown tool: {tool_name}')
            
            self.log(f"✨ Tool result: {json.dumps(result, ensure_ascii=False)[:100]}...")
            
            return {
                'jsonrpc': '2.0',
                'id': request_id,
                'result': {
                    'content': [
                        {
                            'type': 'text',
                            'text': json.dumps(result, indent=2, ensure_ascii=False)
                        }
                    ]
                }
            }
        except Exception as e:
            self.log(f"❌ Tool error: {str(e)}")
            return {
                'jsonrpc': '2.0',
                'id': request_id,
                'error': {
                    'code': -32000,
                    'message': str(e)
                }
            }

    def tool_create_embedding(self, args):
        """Generate embeddings using Ollama"""
        text = args.get('text', '')

        if not text:
            return {
                'success': False,
                'error': 'Text is required'
            }

        self.log(f"🔮 Generating embedding for text: {text[:50]}...")

        try:
            # Call Ollama API to generate embeddings
            url = f"{OLLAMA_API_URL}/api/embeddings"
            data = {
                'model': 'nomic-embed-text',
                'prompt': text
            }

            req = urllib.request.Request(
                url,
                data=json.dumps(data).encode('utf-8'),
                headers={'Content-Type': 'application/json'}
            )

            with urllib.request.urlopen(req, timeout=60) as response:
                result = json.loads(response.read().decode('utf-8'))

            embedding = result.get('embedding', [])

            if not embedding:
                self.log(f"❌ No embedding returned from Ollama")
                return {
                    'success': False,
                    'error': 'No embedding generated'
                }

            self.log(f"✨ Embedding generated: {len(embedding)} dimensions")

            return {
                'success': True,
                'embedding': embedding,
                'dimensions': len(embedding)
            }

        except Exception as e:
            self.log(f"❌ Failed to generate embedding: {str(e)}")
            return {
                'success': False,
                'error': f'Failed to generate embedding: {str(e)}'
            }

    def tool_save_document(self, args):
        """Save document with embedding to local database"""
        content = args.get('content', '').strip()
        source_file = args.get('source_file', 'manual_entry')
        source_type = args.get('source_type', 'manual')
        chunk_index = args.get('chunk_index', 0)
        page_number = args.get('page_number')
        total_chunks = args.get('total_chunks', 1)
        metadata = args.get('metadata', '{}')

        if not content:
            return {
                'success': False,
                'error': 'Content is required',
                'document_id': None
            }

        self.log(f"💾 Saving document locally: {content[:50]}...")

        try:
            # 1. Generate embedding using local Ollama
            embedding_result = self.tool_create_embedding({'text': content})

            if not embedding_result.get('success'):
                return {
                    'success': False,
                    'error': f"Failed to generate embedding: {embedding_result.get('error')}",
                    'document_id': None
                }

            embedding = embedding_result['embedding']

            # 2. Save to local database
            doc_id = EmbeddingsDatabase.save_document_with_embedding(
                content=content,
                embedding=embedding,
                source_file=source_file,
                source_type=source_type,
                chunk_index=chunk_index,
                page_number=page_number,
                total_chunks=total_chunks,
                metadata=metadata
            )

            self.log(f"✅ Document saved with ID: {doc_id}")

            return {
                'success': True,
                'document_id': doc_id,
                'message': 'Document saved successfully with embedding',
                'embedding_dimensions': len(embedding)
            }

        except Exception as e:
            self.log(f"❌ Failed to save document: {str(e)}")
            import traceback
            self.log(f"❌ Traceback: {traceback.format_exc()}")
            return {
                'success': False,
                'error': f'Failed to save document: {str(e)}',
                'document_id': None
            }

    def tool_search_similar(self, args):
        """Search for similar documents using cosine similarity in local database"""
        query = args.get('query', '').strip()
        limit = args.get('limit', 5)

        if not query:
            return {
                'success': False,
                'error': 'Query is required',
                'documents': []
            }

        self.log(f"🔍 Searching locally for: {query[:50]}... (limit={limit})")

        try:
            # 1. Generate query embedding
            embedding_result = self.tool_create_embedding({'text': query})

            if not embedding_result.get('success'):
                return {
                    'success': False,
                    'error': f"Failed to generate query embedding: {embedding_result.get('error')}",
                    'documents': []
                }

            query_embedding = embedding_result['embedding']

            # 2. Search database
            results = EmbeddingsDatabase.search_similar_documents(
                query_embedding=query_embedding,
                limit=limit
            )

            self.log(f"✅ Found {len(results)} similar documents")

            return {
                'success': True,
                'count': len(results),
                'documents': results
            }

        except Exception as e:
            self.log(f"❌ Search failed: {str(e)}")
            import traceback
            self.log(f"❌ Traceback: {traceback.format_exc()}")
            return {
                'success': False,
                'error': f'Failed to search documents: {str(e)}',
                'documents': []
            }

    def tool_semantic_search(self, args):
        """Search for relevant chunks from local database with threshold filtering"""
        query = args.get('query', '').strip()
        limit = args.get('limit', 3)
        threshold = args.get('threshold', SEMANTIC_SEARCH_CONFIG['default_threshold'])
        compare_mode = args.get('compare_mode', False)

        if not query:
            return {
                'success': False,
                'error': 'Query is required',
                'documents': []
            }

        # Validate and clamp threshold
        threshold = max(SEMANTIC_SEARCH_CONFIG['min_threshold'],
                       min(SEMANTIC_SEARCH_CONFIG['max_threshold'], threshold))

        self.log(f"🌐 Semantic search locally: {query[:50]}... (threshold={threshold:.2f}, compare_mode={compare_mode})")

        try:
            # Get raw search results (request more to have buffer for filtering)
            raw_results = self.tool_search_similar({
                'query': query,
                'limit': limit * 2
            })

            if not raw_results.get('success'):
                return raw_results

            documents = raw_results.get('documents', [])

            self.log(f"🔍 Retrieved {len(documents)} documents from local database")

            # Apply threshold filtering and add citations
            filtered_documents = []
            for doc in documents:
                if doc.get('similarity', 0) >= threshold:
                    # Add formatted citation
                    doc['citation'] = self.format_citation(doc)

                    # Add citation object for structured access
                    doc['citation_info'] = {
                        'source_file': doc.get('source_file', 'unknown'),
                        'source_type': doc.get('source_type', 'manual'),
                        'chunk_index': doc.get('chunk_index', 0),
                        'page_number': doc.get('page_number'),
                        'total_chunks': doc.get('total_chunks', 1),
                        'formatted': doc['citation']
                    }

                    filtered_documents.append(doc)

                    if len(filtered_documents) >= limit:
                        break

            self.log(f"✅ After threshold filtering: {len(filtered_documents)} documents pass (threshold={threshold:.2f})")

            # Generate sources summary
            sources = {}
            for doc in filtered_documents:
                src = doc.get('source_file', 'unknown')
                sources[src] = sources.get(src, 0) + 1

            sources_summary = []
            for src, count in sources.items():
                if count == 1:
                    word = 'фрагмент'
                elif count < 5:
                    word = 'фрагмента'
                else:
                    word = 'фрагментов'
                sources_summary.append(f"{src} ({count} {word})")

            # Return based on mode
            if compare_mode:
                # Return both unfiltered and filtered for comparison
                unfiltered_docs = documents[:limit]
                return {
                    'success': True,
                    'threshold': threshold,
                    'unfiltered': {
                        'count': len(unfiltered_docs),
                        'documents': unfiltered_docs
                    },
                    'filteredResults': {
                        'count': len(filtered_documents),
                        'documents': filtered_documents
                    },
                    'source': 'local_database',
                    'sources_summary': sources_summary
                }
            else:
                # Return filtered results only
                return {
                    'success': True,
                    'count': len(filtered_documents),
                    'documents': filtered_documents,
                    'threshold': threshold,
                    'isFiltered': True,
                    'source': 'local_database',
                    'sources_summary': sources_summary
                }

        except Exception as e:
            self.log(f"❌ Semantic search failed: {str(e)}")
            import traceback
            self.log(f"❌ Traceback: {traceback.format_exc()}")
            return {
                'success': False,
                'error': f'Failed to search: {str(e)}',
                'documents': []
            }

    def tool_process_pdf(self, args):
        """Process PDF: extract text, chunk, and save with embeddings locally"""
        pdf_base64 = args.get('pdf_base64', '')
        filename = args.get('filename', 'document.pdf')
        chunk_size = args.get('chunk_size', 1000)
        chunk_overlap = args.get('chunk_overlap', 200)

        if not pdf_base64:
            return {
                'success': False,
                'error': 'PDF base64 content is required'
            }

        self.log(f"📄 Processing PDF locally: {filename}")

        try:
            import base64
            import io

            # Decode base64
            pdf_bytes = base64.b64decode(pdf_base64)

            # Extract text using pdfplumber (if available)
            try:
                import pdfplumber

                with io.BytesIO(pdf_bytes) as pdf_file:
                    with pdfplumber.open(pdf_file) as pdf:
                        text_parts = []
                        for page in pdf.pages:
                            text_parts.append(page.extract_text() or '')

                        extracted_text = '\n\n'.join(text_parts)

                self.log(f"✅ Extracted {len(extracted_text)} characters from PDF")

                # Process extracted text
                return self.tool_process_text_chunks({
                    'text': extracted_text,
                    'filename': filename,
                    'chunk_size': chunk_size,
                    'chunk_overlap': chunk_overlap
                })

            except ImportError:
                self.log(f"❌ pdfplumber not installed")
                return {
                    'success': False,
                    'error': 'pdfplumber not installed. Use process_text_chunks with client-side extraction instead.'
                }

        except Exception as e:
            self.log(f"❌ Failed to process PDF: {str(e)}")
            import traceback
            self.log(f"❌ Traceback: {traceback.format_exc()}")
            return {
                'success': False,
                'error': f'Failed to process PDF: {str(e)}'
            }

    def tool_process_text_chunks(self, args):
        """Process extracted text: chunk and save with embeddings locally (with parallel processing)"""
        text = args.get('text', '').strip()
        filename = args.get('filename', 'document.txt')
        chunk_size = args.get('chunk_size', 1000)
        chunk_overlap = args.get('chunk_overlap', 200)
        max_workers = args.get('max_workers', 4)  # Number of parallel threads

        if not text:
            return {
                'success': False,
                'error': 'Text content is required',
                'chunks_saved': 0
            }

        # Determine source type from filename
        source_type = 'txt'
        if filename.endswith('.pdf'):
            source_type = 'pdf'
        elif filename.endswith('.md'):
            source_type = 'markdown'

        self.log(f"📝 Processing text chunks locally: {filename} (type={source_type})")
        self.log(f"📊 Text length: {len(text)} characters")
        self.log(f"⚡ Using {max_workers} parallel workers")

        try:
            import time
            start_time = time.time()

            # 1. Chunk the text
            chunks = self._chunk_text(text, chunk_size, chunk_overlap)
            total_chunks = len(chunks)

            self.log(f"✂️ Created {total_chunks} chunks")

            # 2. Process chunks in parallel
            saved_count = 0
            failed_count = 0
            lock = threading.Lock()  # For thread-safe counter updates

            def process_chunk(chunk_data):
                """Process a single chunk (generate embedding and save)"""
                i, chunk_content = chunk_data
                try:
                    # Generate embedding
                    emb_result = self.tool_create_embedding({'text': chunk_content})

                    if not emb_result.get('success'):
                        self.log(f"⚠️ Failed to embed chunk {i+1}/{total_chunks}: {emb_result.get('error')}")
                        return False, i

                    embedding = emb_result['embedding']

                    # Save to database
                    doc_id = EmbeddingsDatabase.save_document_with_embedding(
                        content=chunk_content,
                        embedding=embedding,
                        source_file=filename,
                        source_type=source_type,
                        chunk_index=i,
                        page_number=None,
                        total_chunks=total_chunks,
                        metadata='{}'
                    )

                    return True, i

                except Exception as e:
                    self.log(f"❌ Error processing chunk {i+1}: {str(e)}")
                    return False, i

            # Use ThreadPoolExecutor for parallel processing
            with ThreadPoolExecutor(max_workers=max_workers) as executor:
                # Submit all tasks
                futures = {
                    executor.submit(process_chunk, (i, chunk)): i
                    for i, chunk in enumerate(chunks)
                }

                # Process completed tasks
                for future in as_completed(futures):
                    success, chunk_index = future.result()

                    with lock:
                        if success:
                            saved_count += 1
                        else:
                            failed_count += 1

                        # Log progress every 10 chunks or on last chunk
                        if (saved_count + failed_count) % 10 == 0 or (saved_count + failed_count) == total_chunks:
                            self.log(f"💾 Progress: {saved_count}/{total_chunks} chunks saved...")

            processing_time = time.time() - start_time

            self.log(f"🎉 Processing complete: {saved_count} chunks saved, {failed_count} failed in {processing_time:.2f}s")
            self.log(f"⚡ Average speed: {processing_time/total_chunks:.2f}s per chunk")

            return {
                'success': True,
                'chunks_saved': saved_count,
                'chunks_failed': failed_count,
                'total_characters': len(text),
                'filename': filename,
                'chunk_size': chunk_size,
                'chunk_overlap': chunk_overlap,
                'processing_time_seconds': round(processing_time, 2),
                'average_time_per_chunk': round(processing_time / total_chunks, 2)
            }

        except Exception as e:
            self.log(f"❌ Failed to process text: {str(e)}")
            import traceback
            self.log(f"❌ Traceback: {traceback.format_exc()}")
            return {
                'success': False,
                'error': f'Failed to process text: {str(e)}',
                'chunks_saved': 0
            }

    def tool_get_repo(self, args):
        """Get repository information from GitHub"""
        owner = args.get('owner', GITHUB_DEFAULT_OWNER)
        repo = args.get('repo')

        if not repo:
            return {'success': False, 'error': 'repo is required'}

        try:
            data = github_api_request(f"/repos/{owner}/{repo}")
            return {
                'success': True,
                'name': data['full_name'],
                'description': data.get('description', ''),
                'stars': data.get('stargazers_count', 0),
                'forks': data.get('forks_count', 0),
                'language': data.get('language', ''),
                'topics': data.get('topics', []),
                'url': data['html_url'],
                'created_at': data.get('created_at', ''),
                'updated_at': data.get('updated_at', '')
            }
        except Exception as e:
            self.log(f"❌ GitHub API error: {str(e)}")
            return {'success': False, 'error': str(e)}

    def tool_search_code(self, args):
        """Search code on GitHub"""
        query = args.get('query')
        max_results = args.get('max_results', 5)

        if not query:
            return {'success': False, 'error': 'query is required'}

        try:
            # URL encode the query
            encoded_query = urllib.parse.quote(query)
            data = github_api_request(f"/search/code?q={encoded_query}&per_page={max_results}")

            results = []
            for item in data.get('items', [])[:max_results]:
                results.append({
                    'name': item['name'],
                    'path': item['path'],
                    'repository': item['repository']['full_name'],
                    'url': item['html_url'],
                    'score': item.get('score', 0)
                })

            return {
                'success': True,
                'total_count': data.get('total_count', 0),
                'results': results
            }
        except Exception as e:
            self.log(f"❌ GitHub API error: {str(e)}")
            return {'success': False, 'error': str(e)}

    def tool_create_issue(self, args):
        """Create a new GitHub issue"""
        owner = args.get('owner', GITHUB_DEFAULT_OWNER)
        repo = args.get('repo')
        title = args.get('title')
        body = args.get('body')

        if not all([repo, title, body]):
            return {'success': False, 'error': 'repo, title, and body are required'}

        try:
            data = github_api_request(
                f"/repos/{owner}/{repo}/issues",
                method='POST',
                data={'title': title, 'body': body}
            )
            return {
                'success': True,
                'issue_number': data['number'],
                'url': data['html_url'],
                'state': data['state']
            }
        except Exception as e:
            self.log(f"❌ GitHub API error: {str(e)}")
            return {'success': False, 'error': str(e)}

    def tool_list_issues(self, args):
        """List issues from a GitHub repository"""
        owner = args.get('owner', GITHUB_DEFAULT_OWNER)
        repo = args.get('repo')
        state = args.get('state', 'open')

        if not repo:
            return {'success': False, 'error': 'repo is required'}

        try:
            data = github_api_request(f"/repos/{owner}/{repo}/issues?state={state}&per_page=10")

            issues = []
            for item in data:
                # Skip pull requests (they appear in issues endpoint)
                if 'pull_request' in item:
                    continue

                issues.append({
                    'number': item['number'],
                    'title': item['title'],
                    'state': item['state'],
                    'url': item['html_url'],
                    'created_at': item['created_at'],
                    'user': item['user']['login']
                })

            return {
                'success': True,
                'count': len(issues),
                'issues': issues
            }
        except Exception as e:
            self.log(f"❌ GitHub API error: {str(e)}")
            return {'success': False, 'error': str(e)}

    def tool_list_commits(self, args):
        """List commits from a GitHub repository"""
        owner = args.get('owner', GITHUB_DEFAULT_OWNER)
        repo = args.get('repo')
        max_results = args.get('max_results', 10)

        if not repo:
            return {'success': False, 'error': 'repo is required'}

        try:
            data = github_api_request(f"/repos/{owner}/{repo}/commits?per_page={max_results}")

            commits = []
            for item in data[:max_results]:
                commits.append({
                    'sha': item['sha'][:7],
                    'message': item['commit']['message'].split('\n')[0],
                    'author': item['commit']['author']['name'],
                    'date': item['commit']['author']['date'],
                    'url': item['html_url']
                })

            return {
                'success': True,
                'count': len(commits),
                'commits': commits
            }
        except Exception as e:
            self.log(f"❌ GitHub API error: {str(e)}")
            return {'success': False, 'error': str(e)}

    def tool_get_repo_content(self, args):
        """Get file content or directory listing from GitHub"""
        owner = args.get('owner', GITHUB_DEFAULT_OWNER)
        repo = args.get('repo')
        path = args.get('path')

        if not all([repo, path]):
            return {'success': False, 'error': 'repo and path are required'}

        try:
            data = github_api_request(f"/repos/{owner}/{repo}/contents/{path}")

            # Check if it's a file or directory
            if isinstance(data, list):
                # Directory listing
                items = []
                for item in data:
                    items.append({
                        'name': item['name'],
                        'path': item['path'],
                        'type': item['type'],
                        'size': item.get('size', 0)
                    })
                return {
                    'success': True,
                    'type': 'directory',
                    'items': items
                }
            else:
                # File content
                import base64
                content = base64.b64decode(data['content']).decode('utf-8')
                return {
                    'success': True,
                    'type': 'file',
                    'name': data['name'],
                    'size': data['size'],
                    'content': content,
                    'url': data['html_url']
                }
        except Exception as e:
            self.log(f"❌ GitHub API error: {str(e)}")
            return {'success': False, 'error': str(e)}

    def tool_git_status(self, args):
        """Get git status: modified, staged, untracked files"""
        repo_path = args.get('repo_path', GIT_DEFAULT_REPO_PATH)

        if not os.path.exists(repo_path):
            return {'success': False, 'error': f'Repository not found: {repo_path}'}

        try:
            # Get status
            result = subprocess.run(
                ['git', 'status', '--porcelain', '--branch'],
                cwd=repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )

            if result.returncode != 0:
                return {'success': False, 'error': result.stderr}

            # Parse output
            lines = result.stdout.strip().split('\n')
            branch_line = lines[0] if lines else ''
            file_lines = lines[1:] if len(lines) > 1 else []

            # Parse branch info
            current_branch = 'unknown'
            ahead_behind = {'ahead': 0, 'behind': 0}
            if branch_line.startswith('## '):
                branch_info = branch_line[3:]
                if '...' in branch_info:
                    local_remote = branch_info.split('...')
                    current_branch = local_remote[0]
                    # Parse ahead/behind from "[ahead 2, behind 1]" format
                    if '[' in branch_info:
                        ahead_behind_str = branch_info.split('[')[1].split(']')[0]
                        if 'ahead' in ahead_behind_str:
                            ahead_behind['ahead'] = int(ahead_behind_str.split('ahead')[1].split(',')[0].strip())
                        if 'behind' in ahead_behind_str:
                            ahead_behind['behind'] = int(ahead_behind_str.split('behind')[1].strip())
                else:
                    current_branch = branch_info

            # Parse file statuses
            modified = []
            staged = []
            untracked = []

            for line in file_lines:
                if not line.strip():
                    continue
                status = line[:2]
                filepath = line[3:].strip()

                if status == '??':
                    untracked.append(filepath)
                elif status[0] != ' ':
                    staged.append({'file': filepath, 'status': status})
                elif status[1] != ' ':
                    modified.append({'file': filepath, 'status': status})

            return {
                'success': True,
                'repo_path': repo_path,
                'current_branch': current_branch,
                'ahead': ahead_behind['ahead'],
                'behind': ahead_behind['behind'],
                'modified': modified,
                'staged': staged,
                'untracked': untracked,
                'clean': len(modified) + len(staged) + len(untracked) == 0
            }

        except subprocess.TimeoutExpired:
            return {'success': False, 'error': 'Git command timeout'}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def tool_git_branch(self, args):
        """List all local and remote branches"""
        repo_path = args.get('repo_path', GIT_DEFAULT_REPO_PATH)
        include_remote = args.get('include_remote', True)

        if not os.path.exists(repo_path):
            return {'success': False, 'error': f'Repository not found: {repo_path}'}

        try:
            # Get current branch
            current_result = subprocess.run(
                ['git', 'branch', '--show-current'],
                cwd=repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )
            current_branch = current_result.stdout.strip()

            # Get all branches
            cmd = ['git', 'branch', '-a'] if include_remote else ['git', 'branch']
            result = subprocess.run(
                cmd,
                cwd=repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )

            if result.returncode != 0:
                return {'success': False, 'error': result.stderr}

            # Parse branches
            local_branches = []
            remote_branches = []

            for line in result.stdout.strip().split('\n'):
                line = line.strip()
                if not line:
                    continue

                # Remove current branch marker
                is_current = line.startswith('* ')
                branch_name = line[2:] if is_current else line

                if branch_name.startswith('remotes/'):
                    remote_branches.append(branch_name[8:])  # Remove 'remotes/' prefix
                else:
                    local_branches.append({
                        'name': branch_name,
                        'current': is_current
                    })

            return {
                'success': True,
                'repo_path': repo_path,
                'current_branch': current_branch,
                'local_branches': local_branches,
                'remote_branches': remote_branches if include_remote else []
            }

        except subprocess.TimeoutExpired:
            return {'success': False, 'error': 'Git command timeout'}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def tool_git_diff(self, args):
        """Get diff for specified files or all changes"""
        repo_path = args.get('repo_path', GIT_DEFAULT_REPO_PATH)
        filepath = args.get('filepath')  # Optional: specific file
        staged = args.get('staged', False)  # If True, show staged changes
        max_lines = args.get('max_lines', 500)  # Limit output

        if not os.path.exists(repo_path):
            return {'success': False, 'error': f'Repository not found: {repo_path}'}

        try:
            cmd = ['git', 'diff']
            if staged:
                cmd.append('--cached')
            if filepath:
                cmd.append('--')
                cmd.append(filepath)

            result = subprocess.run(
                cmd,
                cwd=repo_path,
                capture_output=True,
                text=True,
                timeout=30
            )

            if result.returncode != 0:
                return {'success': False, 'error': result.stderr}

            diff_output = result.stdout

            # Truncate if too long
            lines = diff_output.split('\n')
            truncated = False
            if len(lines) > max_lines:
                lines = lines[:max_lines]
                truncated = True

            return {
                'success': True,
                'repo_path': repo_path,
                'filepath': filepath or 'all',
                'staged': staged,
                'diff': '\n'.join(lines),
                'truncated': truncated,
                'total_lines': len(diff_output.split('\n'))
            }

        except subprocess.TimeoutExpired:
            return {'success': False, 'error': 'Git command timeout'}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def tool_git_pr_status(self, args):
        """
        Check PR status by combining local git info with GitHub API
        Returns: current branch, related PRs, and sync status
        """
        repo_path = args.get('repo_path', GIT_DEFAULT_REPO_PATH)

        if not os.path.exists(repo_path):
            return {'success': False, 'error': f'Repository not found: {repo_path}'}

        try:
            # 1. Get current branch
            branch_result = subprocess.run(
                ['git', 'branch', '--show-current'],
                cwd=repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )
            current_branch = branch_result.stdout.strip()

            # 2. Get remote URL to extract owner/repo
            remote_result = subprocess.run(
                ['git', 'config', '--get', 'remote.origin.url'],
                cwd=repo_path,
                capture_output=True,
                text=True,
                timeout=10
            )
            remote_url = remote_result.stdout.strip()

            # Parse GitHub owner/repo from URL
            # Handles: git@github.com:owner/repo.git or https://github.com/owner/repo.git
            import re
            match = re.search(r'github\.com[:/](.+?)/(.+?)(\.git)?$', remote_url)
            if not match:
                return {
                    'success': False,
                    'error': 'Could not parse GitHub repository from remote URL',
                    'remote_url': remote_url
                }

            owner = match.group(1)
            repo = match.group(2)

            # 3. Query GitHub API for PRs
            if not GITHUB_TOKEN:
                return {
                    'success': True,
                    'current_branch': current_branch,
                    'repo_name': f"{owner}/{repo}",
                    'prs': [],
                    'note': 'GitHub token not configured, cannot fetch PRs'
                }

            # Get PRs for this branch
            try:
                # List all PRs
                all_prs = github_api_request(f"/repos/{owner}/{repo}/pulls?state=all&per_page=100")

                # Filter PRs for current branch
                branch_prs = []
                for pr in all_prs:
                    if pr['head']['ref'] == current_branch:
                        branch_prs.append({
                            'number': pr['number'],
                            'title': pr['title'],
                            'state': pr['state'],
                            'url': pr['html_url'],
                            'created_at': pr['created_at'],
                            'updated_at': pr['updated_at'],
                            'user': pr['user']['login'],
                            'base': pr['base']['ref']  # Target branch
                        })

                return {
                    'success': True,
                    'current_branch': current_branch,
                    'repo_name': f"{owner}/{repo}",
                    'owner': owner,
                    'repo': repo,
                    'prs': branch_prs,
                    'has_open_pr': any(pr['state'] == 'open' for pr in branch_prs)
                }

            except Exception as gh_error:
                return {
                    'success': True,
                    'current_branch': current_branch,
                    'repo_name': f"{owner}/{repo}",
                    'prs': [],
                    'error': f'Failed to fetch PRs from GitHub: {str(gh_error)}'
                }

        except subprocess.TimeoutExpired:
            return {'success': False, 'error': 'Git command timeout'}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def format_citation(self, doc, language='ru'):
        """
        Format citation in standard format

        Args:
            doc: Document dict with citation fields
            language: 'ru' or 'en'

        Returns:
            Formatted citation string
        """
        source_file = doc.get('source_file', 'unknown')

        if source_file == 'unknown':
            return '[unknown source]'

        parts = [source_file]

        # Add page number if available
        if doc.get('page_number'):
            page_word = 'стр.' if language == 'ru' else 'p.'
            parts.append(f"{page_word} {doc['page_number']}")

        # Add chunk info
        chunk_idx = doc.get('chunk_index', 0)
        total = doc.get('total_chunks', 1)
        if total > 1:
            fragment_word = 'фрагмент' if language == 'ru' else 'chunk'
            parts.append(f"{fragment_word} {chunk_idx + 1}/{total}")

        return f"[{', '.join(parts)}]"

    def _chunk_text(self, text, chunk_size, overlap):
        """Split text into overlapping chunks"""
        chunks = []
        start = 0
        text_length = len(text)

        while start < text_length:
            # Get chunk
            end = start + chunk_size
            chunk = text[start:end]

            # Try to break at sentence boundary
            if end < text_length:
                # Look for sentence ending within last 200 chars
                last_period = chunk.rfind('. ')
                last_newline = chunk.rfind('\n\n')
                last_break = max(last_period, last_newline)

                if last_break > chunk_size - 200:  # Only break if near end
                    chunk = chunk[:last_break + 1]
                    end = start + last_break + 1

            chunks.append(chunk.strip())

            # Move to next chunk with overlap
            start = end - overlap

            # Avoid infinite loop
            if start >= text_length or len(chunk.strip()) == 0:
                break

        return [c for c in chunks if c]  # Remove empty chunks

    def log_message(self, format, *args):
        """Custom log format"""
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        print(f'[{timestamp}] {format % args}')

def run_server(host='0.0.0.0', port=8080, github_token=None):
    """Start MCP HTTP server"""
    init_database()

    # Set GitHub token if provided
    if github_token:
        set_github_token(github_token)
        print(f'✅ GitHub token configured')

    server_address = (host, port)
    httpd = HTTPServer(server_address, MCPServerHandler)

    print('=' * 70)
    print('🚀 MCP HTTP Server - Local Mode with Ollama & GitHub'.center(70))
    print('=' * 70)
    print(f'Server: http://{host}:{port}')
    print(f'From Android emulator: http://10.0.2.2:{port}')
    print(f'From real device: http://<your-computer-ip>:{port}')
    print()
    print('Available Tools (12):')
    print('  🔮 create_embedding      - Generate embeddings using local Ollama')
    print('  📝 save_document         - Save document with embeddings to local DB')
    print('  🔍 search_similar        - Search similar documents in local DB')
    print('  🌐 semantic_search       - Search relevant chunks from local DB')
    print('  📄 process_pdf           - Extract text from PDF, chunk, and index locally')
    print('  📝 process_text_chunks   - Process extracted text into chunks locally')
    print('  📦 get_repo              - Get GitHub repository information')
    print('  🔎 search_code           - Search code on GitHub')
    print('  🐛 create_issue          - Create GitHub issue')
    print('  📋 list_issues           - List GitHub issues')
    print('  📝 list_commits          - List repository commits')
    print('  📄 get_repo_content      - Get file content from GitHub')
    print()
    print('Databases:')
    print(f'  📦 Embeddings: {EMBEDDINGS_DB_PATH}')
    print()
    print('Ollama Integration:')
    print(f'  • API URL: {OLLAMA_API_URL}')
    print('  • Model: nomic-embed-text')
    print('  • Embedding dimensions: 768')
    print('  • Status: Local Mac instance')
    print()
    print('Supported File Types:')
    print('  • PDF (.pdf) - Client-side extraction via PDFBox')
    print('  • Text (.txt) - Plain text files')
    print('  • Markdown (.md) - Markdown files')
    print()
    print('Press Ctrl+C to stop')
    print('=' * 70)
    print()
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print('\n\n🛑 Server stopped')

if __name__ == '__main__':
    import sys
    # Get port from command line (default: 8080)
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    # Get GitHub token from environment or use default from SecureData
    github_token = os.environ.get('GITHUB_TOKEN', '')
    run_server(port=port, github_token=github_token)
