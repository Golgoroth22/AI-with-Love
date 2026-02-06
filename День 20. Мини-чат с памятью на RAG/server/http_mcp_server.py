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

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data', 'jokes.db')
EMBEDDINGS_DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data', 'embeddings.db')
# Use remote Ollama server for embeddings (remote MCP server has Ollama running)
OLLAMA_API_URL = "http://localhost:11434"

# Remote MCP server configuration
# When running on local machine: Uses SSH tunnel (localhost:8081 -> remote:8080)
# When running on remote machine: Connects directly to localhost:8080
# This config works for REMOTE deployment (connects to local remote MCP server)
REMOTE_MCP_SERVER = {
    'host': 'localhost',
    'port': 8080,
    'url': 'http://localhost:8080'
}

# Semantic search configuration
SEMANTIC_SEARCH_CONFIG = {
    'default_threshold': 0.6,  # Default similarity threshold (60%) - Matches app default
    'min_threshold': 0.3,      # Minimum allowed threshold (30%)
    'max_threshold': 0.95      # Maximum allowed threshold (95%)
}

def init_database():
    """Initialize SQLite database"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS saved_jokes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            joke_api_id INTEGER,
            category TEXT,
            type TEXT,
            joke_text TEXT,
            setup TEXT,
            delivery TEXT,
            saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    conn.commit()
    conn.close()
    print(f"📦 Database initialized: {DB_PATH}")

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
                'name': 'get_joke',
                'description': 'Get a random joke from JokeAPI',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'category': {
                            'type': 'string',
                            'description': 'Joke category: Any, Programming, Misc, Dark, Pun, Spooky, Christmas',
                            'default': 'Any'
                        },
                        'blacklistFlags': {
                            'type': 'string',
                            'description': 'Comma-separated flags to blacklist: nsfw,religious,political,racist,sexist,explicit',
                            'default': ''
                        }
                    }
                }
            },
            {
                'name': 'save_joke',
                'description': 'Save a joke to the local database. Use this when user asks to save, remember, or add joke to favorites. Can save jokes in any language (Russian or English).',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'joke_api_id': {
                            'type': 'integer',
                            'description': 'Original joke ID from JokeAPI'
                        },
                        'category': {
                            'type': 'string',
                            'description': 'Joke category from JokeAPI'
                        },
                        'type': {
                            'type': 'string',
                            'description': 'Joke type: single or twopart'
                        },
                        'joke_text': {
                            'type': 'string',
                            'description': 'Full joke text for single type jokes (can be in any language)'
                        },
                        'setup': {
                            'type': 'string',
                            'description': 'Setup part for twopart jokes (can be in any language)'
                        },
                        'delivery': {
                            'type': 'string',
                            'description': 'Delivery/punchline for twopart jokes (can be in any language)'
                        }
                    },
                    'required': ['type']
                }
            },
            {
                'name': 'get_saved_jokes',
                'description': 'Get all saved jokes from the local database. Use this when user asks to show saved jokes, my jokes, or favorites.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'limit': {
                            'type': 'integer',
                            'description': 'Maximum number of jokes to return (default: 50)',
                            'default': 50
                        }
                    }
                }
            },
            {
                'name': 'run_tests',
                'description': 'Run MCP server tests in an isolated Docker container. Use this when user asks to run tests, test the server, or check if everything works. Returns summary of test results.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {}
                }
            },
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
            if tool_name == 'get_joke':
                result = self.tool_get_joke(arguments)
            elif tool_name == 'save_joke':
                result = self.tool_save_joke(arguments)
            elif tool_name == 'get_saved_jokes':
                result = self.tool_get_saved_jokes(arguments)
            elif tool_name == 'run_tests':
                result = self.tool_run_tests(arguments)
            elif tool_name == 'create_embedding':
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
    
    def tool_get_joke(self, args):
        """Get joke from JokeAPI"""
        category = args.get('category', 'Any')
        blacklist_flags = args.get('blacklistFlags', '')
        
        url = f'https://v2.jokeapi.dev/joke/{category}'
        
        params = {}
        if blacklist_flags:
            params['blacklistFlags'] = blacklist_flags
        
        if params:
            url += '?' + urllib.parse.urlencode(params)
        
        self.log(f"🌐 Requesting JokeAPI: {url}")
        
        try:
            with urllib.request.urlopen(url, timeout=10) as response:
                data = json.loads(response.read().decode('utf-8'))
            
            if data.get('error'):
                self.log(f"❌ JokeAPI error: {data.get('message')}")
                return {
                    'error': True,
                    'message': data.get('message', 'Unknown error')
                }
            
            joke_result = {
                'category': data.get('category'),
                'type': data.get('type'),
                'id': data.get('id')
            }
            
            if data.get('type') == 'single':
                joke_result['joke'] = data.get('joke')
                self.log(f"😄 Got single joke (ID: {data.get('id')})")
            else:
                joke_result['setup'] = data.get('setup')
                joke_result['delivery'] = data.get('delivery')
                self.log(f"😄 Got twopart joke (ID: {data.get('id')})")
            
            return joke_result
            
        except Exception as e:
            self.log(f"❌ JokeAPI request failed: {str(e)}")
            return {
                'error': True,
                'message': f'Failed to fetch joke: {str(e)}'
            }
    
    def tool_save_joke(self, args):
        """Save joke to SQLite database"""
        joke_api_id = args.get('joke_api_id')
        category = args.get('category', '')
        joke_type = args.get('type', 'single')
        joke_text = args.get('joke_text', '')
        setup = args.get('setup', '')
        delivery = args.get('delivery', '')
        
        self.log(f"💾 Saving joke: type={joke_type}, category={category}")
        
        try:
            conn = sqlite3.connect(DB_PATH)
            cursor = conn.cursor()
            
            cursor.execute('''
                INSERT INTO saved_jokes (joke_api_id, category, type, joke_text, setup, delivery)
                VALUES (?, ?, ?, ?, ?, ?)
            ''', (joke_api_id, category, joke_type, joke_text, setup, delivery))
            
            joke_id = cursor.lastrowid
            conn.commit()
            conn.close()
            
            self.log(f"✅ Joke saved with ID: {joke_id}")
            
            return {
                'success': True,
                'message': 'Joke saved successfully',
                'saved_joke_id': joke_id
            }
            
        except Exception as e:
            self.log(f"❌ Failed to save joke: {str(e)}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def tool_get_saved_jokes(self, args):
        """Get all saved jokes from database"""
        limit = args.get('limit', 50)
        
        self.log(f"📖 Getting saved jokes (limit: {limit})")
        
        try:
            conn = sqlite3.connect(DB_PATH)
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            
            cursor.execute('''
                SELECT id, joke_api_id, category, type, joke_text, setup, delivery, saved_at
                FROM saved_jokes
                ORDER BY saved_at DESC
                LIMIT ?
            ''', (limit,))
            
            rows = cursor.fetchall()
            conn.close()
            
            jokes = []
            for row in rows:
                joke = {
                    'id': row['id'],
                    'joke_api_id': row['joke_api_id'],
                    'category': row['category'],
                    'type': row['type'],
                    'saved_at': row['saved_at']
                }
                
                if row['type'] == 'single':
                    joke['joke'] = row['joke_text']
                else:
                    joke['setup'] = row['setup']
                    joke['delivery'] = row['delivery']
                
                jokes.append(joke)
            
            self.log(f"📖 Found {len(jokes)} saved jokes")
            
            return {
                'success': True,
                'count': len(jokes),
                'jokes': jokes
            }
            
        except Exception as e:
            self.log(f"❌ Failed to get saved jokes: {str(e)}")
            return {
                'success': False,
                'error': str(e),
                'jokes': []
            }
    
    def tool_run_tests(self, args):
        """Run tests in Docker container"""
        import time
        from datetime import datetime

        start_time = time.time()
        start_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]

        self.log(f"🐳 Running tests in Docker container...")

        # Build detailed server logs
        server_logs = []
        server_logs.append(f"[{start_timestamp}] Test execution initiated")
        server_logs.append(f"[{start_timestamp}] Preparing Docker environment...")

        try:
            # Check if Docker is available
            docker_check_start = time.time()
            docker_check_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]
            server_logs.append(f"[{docker_check_timestamp}] Checking Docker availability...")

            # Run Docker container with tests
            docker_start_time = time.time()
            docker_start_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]
            server_logs.append(f"[{docker_start_timestamp}] Launching Docker container 'mcp-server-tests'...")
            server_logs.append(f"[{docker_start_timestamp}] Command: docker run --rm mcp-server-tests")

            result = subprocess.run(
                [
                    'docker', 'run', '--rm',
                    'mcp-server-tests'
                ],
                capture_output=True,
                text=True,
                timeout=120
            )

            docker_end_time = time.time()
            docker_end_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]
            docker_execution_time = docker_end_time - docker_start_time

            server_logs.append(f"[{docker_end_timestamp}] Docker container finished execution")
            server_logs.append(f"[{docker_end_timestamp}] Container execution time: {docker_execution_time:.2f}s")
            server_logs.append(f"[{docker_end_timestamp}] Return code: {result.returncode}")

            execution_time = time.time() - start_time
            output = result.stdout + result.stderr

            server_logs.append(f"[{docker_end_timestamp}] Parsing test results...")

            self.log(f"🐳 Docker execution completed with return code: {result.returncode}")

            # Parse test results from output
            # Look for patterns like "Ran X tests" and "FAILED (failures=Y, errors=Z)" or "OK"
            tests_run = 0
            failures = 0
            errors = 0
            success = False

            # Extract "Ran X tests"
            ran_match = re.search(r'Ran (\d+) test', output)
            if ran_match:
                tests_run = int(ran_match.group(1))

            # Check if all tests passed
            if 'OK' in output and result.returncode == 0:
                success = True
                passed = tests_run
                server_logs.append(f"[{docker_end_timestamp}] ✅ All tests passed!")
            else:
                # Extract failures and errors
                fail_match = re.search(r'failures=(\d+)', output)
                error_match = re.search(r'errors=(\d+)', output)

                if fail_match:
                    failures = int(fail_match.group(1))
                if error_match:
                    errors = int(error_match.group(1))

                passed = tests_run - failures - errors

                if failures > 0:
                    server_logs.append(f"[{docker_end_timestamp}] ❌ Found {failures} test failure(s)")
                if errors > 0:
                    server_logs.append(f"[{docker_end_timestamp}] ❌ Found {errors} test error(s)")

            end_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]
            server_logs.append(f"[{end_timestamp}] Test results: {passed} passed, {failures} failed, {errors} errors")
            server_logs.append(f"[{end_timestamp}] Total execution time: {execution_time:.2f}s")
            server_logs.append(f"[{end_timestamp}] Test execution completed successfully")

            self.log(f"✅ Test results: {passed} passed, {failures} failed, {errors} errors (took {execution_time:.2f}s)")

            return {
                'success': success,
                'tests_run': tests_run,
                'passed': passed,
                'failed': failures,
                'errors': errors,
                'execution_time': f"{execution_time:.2f}s",
                'summary': f"{passed} passed, {failures} failed, {errors} errors out of {tests_run} tests",
                'server_logs': '\n'.join(server_logs),
                'output': output[-1000:] if len(output) > 1000 else output  # Last 1000 chars
            }

        except subprocess.TimeoutExpired:
            timeout_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]
            server_logs.append(f"[{timeout_timestamp}] ⏰ ERROR: Test execution timed out after 120 seconds")
            server_logs.append(f"[{timeout_timestamp}] Docker container was forcefully terminated")
            self.log(f"⏰ Test execution timed out after 120 seconds")
            return {
                'success': False,
                'error': 'Test execution timed out after 120 seconds',
                'tests_run': 0,
                'passed': 0,
                'failed': 0,
                'errors': 0,
                'server_logs': '\n'.join(server_logs)
            }
        except FileNotFoundError:
            error_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]
            server_logs.append(f"[{error_timestamp}] ❌ ERROR: Docker command not found")
            server_logs.append(f"[{error_timestamp}] Please ensure Docker is installed and running")
            server_logs.append(f"[{error_timestamp}] Check: 'docker --version' and 'docker ps'")
            self.log(f"❌ Docker not found or not running")
            return {
                'success': False,
                'error': 'Docker is not installed or not running',
                'tests_run': 0,
                'passed': 0,
                'failed': 0,
                'errors': 0,
                'server_logs': '\n'.join(server_logs)
            }
        except Exception as e:
            error_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]
            server_logs.append(f"[{error_timestamp}] ❌ FATAL ERROR: {str(e)}")
            server_logs.append(f"[{error_timestamp}] Exception type: {type(e).__name__}")
            self.log(f"❌ Failed to run tests: {str(e)}")
            return {
                'success': False,
                'error': str(e),
                'tests_run': 0,
                'passed': 0,
                'failed': 0,
                'errors': 0,
                'server_logs': '\n'.join(server_logs)
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
        """Save document with embedding to database (proxied to remote server)"""
        content = args.get('content', '')

        if not content:
            return {
                'success': False,
                'error': 'Content is required'
            }

        self.log(f"💾 Proxying save_document to remote server: {content[:50]}...")

        try:
            # Prepare JSON-RPC request for remote MCP server
            request_data = {
                'jsonrpc': '2.0',
                'id': 1,
                'method': 'tools/call',
                'params': {
                    'name': 'save_document',
                    'arguments': args
                }
            }

            # Call remote MCP server
            self.log(f"📡 Calling remote MCP server at {REMOTE_MCP_SERVER['url']}")

            req = urllib.request.Request(
                REMOTE_MCP_SERVER['url'],
                data=json.dumps(request_data).encode('utf-8'),
                headers={
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            )

            # Increased timeout to 120s for operations that involve embedding generation
            with urllib.request.urlopen(req, timeout=120) as response:
                remote_response = json.loads(response.read().decode('utf-8'))

            self.log(f"✅ Received response from remote server")

            # Parse the response
            if 'error' in remote_response:
                error_msg = remote_response['error'].get('message', 'Unknown error')
                self.log(f"❌ Remote server error: {error_msg}")
                return {
                    'success': False,
                    'error': f'Remote server error: {error_msg}'
                }

            # Extract the result from MCP response format
            result_content = remote_response.get('result', {}).get('content', [])

            if not result_content:
                self.log(f"⚠️ No content in remote response")
                return {
                    'success': False,
                    'error': 'No response from remote server'
                }

            # Parse the text content (which should be JSON)
            result_text = result_content[0].get('text', '{}')
            save_result = json.loads(result_text)

            return save_result

        except urllib.error.URLError as e:
            self.log(f"❌ Network error connecting to remote server: {str(e)}")
            return {
                'success': False,
                'error': f'Cannot connect to remote server: {str(e)}'
            }
        except Exception as e:
            self.log(f"❌ Failed to save document: {str(e)}")
            return {
                'success': False,
                'error': f'Failed to save document: {str(e)}'
            }

    def tool_search_similar(self, args):
        """Search for similar documents using cosine similarity (proxied to remote server)"""
        query = args.get('query', '')
        limit = args.get('limit', 5)

        if not query:
            return {
                'success': False,
                'error': 'Query is required'
            }

        self.log(f"🔍 Proxying search_similar to remote server: {query[:50]}...")

        try:
            # Prepare JSON-RPC request for remote MCP server
            request_data = {
                'jsonrpc': '2.0',
                'id': 1,
                'method': 'tools/call',
                'params': {
                    'name': 'search_similar',
                    'arguments': args
                }
            }

            # Call remote MCP server
            self.log(f"📡 Calling remote MCP server at {REMOTE_MCP_SERVER['url']}")

            req = urllib.request.Request(
                REMOTE_MCP_SERVER['url'],
                data=json.dumps(request_data).encode('utf-8'),
                headers={
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            )

            # Increased timeout to 120s for operations that involve embedding generation
            with urllib.request.urlopen(req, timeout=120) as response:
                remote_response = json.loads(response.read().decode('utf-8'))

            self.log(f"✅ Received response from remote server")

            # Parse the response
            if 'error' in remote_response:
                error_msg = remote_response['error'].get('message', 'Unknown error')
                self.log(f"❌ Remote server error: {error_msg}")
                return {
                    'success': False,
                    'error': f'Remote server error: {error_msg}',
                    'documents': []
                }

            # Extract the result from MCP response format
            result_content = remote_response.get('result', {}).get('content', [])

            if not result_content:
                self.log(f"⚠️ No content in remote response")
                return {
                    'success': True,
                    'count': 0,
                    'documents': []
                }

            # Parse the text content (which should be JSON)
            result_text = result_content[0].get('text', '{}')
            search_result = json.loads(result_text)

            return search_result

        except urllib.error.URLError as e:
            self.log(f"❌ Network error connecting to remote server: {str(e)}")
            return {
                'success': False,
                'error': f'Cannot connect to remote server: {str(e)}',
                'documents': []
            }
        except Exception as e:
            self.log(f"❌ Failed to search documents: {str(e)}")
            return {
                'success': False,
                'error': f'Failed to search documents: {str(e)}',
                'documents': []
            }

    def tool_semantic_search(self, args):
        """Search for relevant chunks from remote MCP server with threshold filtering"""
        query = args.get('query', '')
        limit = args.get('limit', 3)
        threshold = args.get('threshold', SEMANTIC_SEARCH_CONFIG['default_threshold'])
        compare_mode = args.get('compare_mode', False)

        if not query:
            return {
                'success': False,
                'error': 'Query is required'
            }

        # Validate and clamp threshold
        threshold = max(SEMANTIC_SEARCH_CONFIG['min_threshold'],
                       min(SEMANTIC_SEARCH_CONFIG['max_threshold'], threshold))

        self.log(f"🌐 Semantic search on remote server: {query[:50]}... (threshold={threshold:.2f}, compare_mode={compare_mode})")

        try:
            # STAGE 1: Get raw results from remote server
            # Request limit * 2 documents to have buffer for filtering
            request_limit = limit * 2

            request_data = {
                'jsonrpc': '2.0',
                'id': 1,
                'method': 'tools/call',
                'params': {
                    'name': 'search_similar',
                    'arguments': {
                        'query': query,
                        'limit': request_limit
                    }
                }
            }

            # Call remote MCP server
            self.log(f"📡 Calling remote MCP server at {REMOTE_MCP_SERVER['url']} (requesting {request_limit} docs)")

            req = urllib.request.Request(
                REMOTE_MCP_SERVER['url'],
                data=json.dumps(request_data).encode('utf-8'),
                headers={
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            )

            # Increased timeout to 120s for search operations that involve embedding generation
            with urllib.request.urlopen(req, timeout=120) as response:
                remote_response = json.loads(response.read().decode('utf-8'))

            self.log(f"✅ Received response from remote server")

            # Parse the response
            if 'error' in remote_response:
                error_msg = remote_response['error'].get('message', 'Unknown error')
                self.log(f"❌ Remote server error: {error_msg}")
                return {
                    'success': False,
                    'error': f'Remote server error: {error_msg}',
                    'documents': []
                }

            # Extract the result from MCP response format
            result_content = remote_response.get('result', {}).get('content', [])

            if not result_content:
                self.log(f"⚠️ No content in remote response")
                return {
                    'success': True,
                    'count': 0,
                    'documents': [],
                    'threshold': threshold,
                    'filtered': True,
                    'message': 'No documents found on remote server'
                }

            # Parse the text content (which should be JSON)
            result_text = result_content[0].get('text', '{}')
            search_result = json.loads(result_text)

            documents = search_result.get('documents', [])

            self.log(f"🔍 Received {len(documents)} documents from remote server")

            # STAGE 2: Apply threshold filtering and add citations
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

            # STAGE 3: Return based on mode
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
                    'source': 'remote_mcp_server',
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
                    'source': 'remote_mcp_server',
                    'sources_summary': sources_summary
                }

        except urllib.error.URLError as e:
            self.log(f"❌ Network error connecting to remote server: {str(e)}")
            return {
                'success': False,
                'error': f'Cannot connect to remote server: {str(e)}',
                'documents': []
            }
        except Exception as e:
            self.log(f"❌ Failed to search remote server: {str(e)}")
            return {
                'success': False,
                'error': f'Failed to search remote server: {str(e)}',
                'documents': []
            }

    def tool_process_pdf(self, args):
        """Process PDF: extract text, chunk, and save with embeddings (proxied to remote server)"""
        pdf_base64 = args.get('pdf_base64', '')
        filename = args.get('filename', 'document.pdf')

        if not pdf_base64:
            return {
                'success': False,
                'error': 'PDF base64 content is required'
            }

        self.log(f"📄 Proxying process_pdf to remote server: {filename}")

        try:
            # Prepare JSON-RPC request for remote MCP server
            request_data = {
                'jsonrpc': '2.0',
                'id': 1,
                'method': 'tools/call',
                'params': {
                    'name': 'process_pdf',
                    'arguments': args
                }
            }

            # Call remote MCP server with longer timeout (PDF processing can take time)
            self.log(f"📡 Calling remote MCP server at {REMOTE_MCP_SERVER['url']}")
            self.log(f"⏳ This may take a while for large PDFs...")

            req = urllib.request.Request(
                REMOTE_MCP_SERVER['url'],
                data=json.dumps(request_data).encode('utf-8'),
                headers={
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            )

            # Use 120 second timeout for PDF processing (can be slow for large files)
            with urllib.request.urlopen(req, timeout=120) as response:
                remote_response = json.loads(response.read().decode('utf-8'))

            self.log(f"✅ Received response from remote server")

            # Parse the response
            if 'error' in remote_response:
                error_msg = remote_response['error'].get('message', 'Unknown error')
                self.log(f"❌ Remote server error: {error_msg}")
                return {
                    'success': False,
                    'error': f'Remote server error: {error_msg}'
                }

            # Extract the result from MCP response format
            result_content = remote_response.get('result', {}).get('content', [])

            if not result_content:
                self.log(f"⚠️ No content in remote response")
                return {
                    'success': False,
                    'error': 'No response from remote server'
                }

            # Parse the text content (which should be JSON)
            result_text = result_content[0].get('text', '{}')
            pdf_result = json.loads(result_text)

            chunks_saved = pdf_result.get('chunks_saved', 0)
            self.log(f"🎉 PDF processed on remote server: {chunks_saved} chunks saved")

            return pdf_result

        except urllib.error.URLError as e:
            self.log(f"❌ Network error connecting to remote server: {str(e)}")
            return {
                'success': False,
                'error': f'Cannot connect to remote server: {str(e)}'
            }
        except Exception as e:
            self.log(f"❌ Failed to process PDF: {str(e)}")
            return {
                'success': False,
                'error': f'Failed to process PDF: {str(e)}'
            }

        except ImportError:
            self.log(f"❌ pdfplumber not installed")
            return {
                'success': False,
                'error': 'PDF processing library not installed. Please install pdfplumber.'
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
        """Process extracted text: chunk and save with embeddings (proxied to remote server)"""
        text = args.get('text', '')
        filename = args.get('filename', 'document.txt')

        if not text:
            return {
                'success': False,
                'error': 'Text content is required'
            }

        self.log(f"📝 Proxying process_text_chunks to remote server: {filename}")
        self.log(f"📊 Text length: {len(text)} characters")

        try:
            # Prepare JSON-RPC request for remote MCP server
            request_data = {
                'jsonrpc': '2.0',
                'id': 1,
                'method': 'tools/call',
                'params': {
                    'name': 'process_text_chunks',
                    'arguments': args
                }
            }

            # Call remote MCP server with longer timeout (text processing can take time)
            self.log(f"📡 Calling remote MCP server at {REMOTE_MCP_SERVER['url']}")

            req = urllib.request.Request(
                REMOTE_MCP_SERVER['url'],
                data=json.dumps(request_data).encode('utf-8'),
                headers={
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            )

            # Use 300 second timeout for text chunking and embedding generation
            with urllib.request.urlopen(req, timeout=300) as response:
                remote_response = json.loads(response.read().decode('utf-8'))

            self.log(f"✅ Received response from remote server")

            # Parse the response
            if 'error' in remote_response:
                error_msg = remote_response['error'].get('message', 'Unknown error')
                self.log(f"❌ Remote server error: {error_msg}")
                return {
                    'success': False,
                    'error': f'Remote server error: {error_msg}'
                }

            # Extract the result from MCP response format
            result_content = remote_response.get('result', {}).get('content', [])

            if not result_content:
                self.log(f"⚠️ No content in remote response")
                return {
                    'success': False,
                    'error': 'No response from remote server'
                }

            # Parse the text content (which should be JSON)
            result_text = result_content[0].get('text', '{}')
            text_result = json.loads(result_text)

            chunks_saved = text_result.get('chunks_saved', 0)
            self.log(f"🎉 Text processed on remote server: {chunks_saved} chunks saved")

            return text_result

        except urllib.error.URLError as e:
            self.log(f"❌ Network error connecting to remote server: {str(e)}")
            return {
                'success': False,
                'error': f'Cannot connect to remote server: {str(e)}'
            }
        except Exception as e:
            self.log(f"❌ Failed to process text: {str(e)}")
            return {
                'success': False,
                'error': f'Failed to process text: {str(e)}'
            }

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

def run_server(host='0.0.0.0', port=8080):
    """Start MCP HTTP server"""
    init_database()
    
    server_address = (host, port)
    httpd = HTTPServer(server_address, MCPServerHandler)
    
    print('=' * 70)
    print('🚀 MCP HTTP Server with JokeAPI, Ollama & SQLite'.center(70))
    print('=' * 70)
    print(f'Server: http://{host}:{port}')
    print(f'From Android emulator: http://10.0.2.2:{port}')
    print(f'From real device: http://<your-computer-ip>:{port}')
    print()
    print('Available Tools (10):')
    print('  🎭 get_joke              - Get random jokes from JokeAPI')
    print('  💾 save_joke             - Save a joke to local database')
    print('  📖 get_saved_jokes       - Get all saved jokes from database')
    print('  🧪 run_tests             - Run server tests in Docker container')
    print('  🔮 create_embedding      - Generate embeddings using Ollama')
    print('  📝 save_document         - Save document with embeddings')
    print('  🔍 search_similar        - Search similar documents by embeddings')
    print('  🌐 semantic_search       - Search relevant chunks from remote MCP server')
    print('  📄 process_pdf           - Extract text from PDF, chunk, and index')
    print('  📝 process_text_chunks   - Process extracted text into chunks')
    print()
    print('Databases:')
    print(f'  📦 Jokes: {DB_PATH}')
    print(f'  📦 Embeddings: {EMBEDDINGS_DB_PATH}')
    print()
    print('JokeAPI Integration:')
    print('  • Base URL: https://v2.jokeapi.dev')
    print('  • Categories: Any, Programming, Misc, Dark, Pun, Spooky, Christmas')
    print('  • Safe mode support via blacklist flags')
    print()
    print('Ollama Integration:')
    print(f'  • API URL: {OLLAMA_API_URL}')
    print('  • Model: nomic-embed-text')
    print('  • Embedding dimensions: 768')
    print()
    print('Docker Testing:')
    print('  • Image: mcp-server-tests')
    print('  • Timeout: 120 seconds')
    print('  • Auto-cleanup: enabled')
    print()
    print('Press Ctrl+C to stop')
    print('=' * 70)
    print()
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print('\n\n🛑 Server stopped')

if __name__ == '__main__':
    run_server()
