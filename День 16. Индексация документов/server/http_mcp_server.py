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
OLLAMA_API_URL = "http://localhost:11434"

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
                'description': 'Save a document with its embedding to the database. Automatically generates embedding if not provided.',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'content': {
                            'type': 'string',
                            'description': 'Document content to save'
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
        
        self.log(f"🔧 Calling tool: {tool_name} with args: {arguments}")
        
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

            with urllib.request.urlopen(req, timeout=30) as response:
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
        """Save document with embedding to database"""
        content = args.get('content', '')

        if not content:
            return {
                'success': False,
                'error': 'Content is required'
            }

        self.log(f"💾 Saving document: {content[:50]}...")

        try:
            # Generate embedding
            embedding_result = self.tool_create_embedding({'text': content})

            if not embedding_result.get('success'):
                return embedding_result

            embedding = embedding_result['embedding']

            # Convert embedding to binary format for storage
            import struct
            embedding_blob = struct.pack(f'{len(embedding)}f', *embedding)

            # Save to database
            conn = sqlite3.connect(EMBEDDINGS_DB_PATH)
            cursor = conn.cursor()

            cursor.execute('''
                INSERT INTO documents (content, embedding)
                VALUES (?, ?)
            ''', (content, embedding_blob))

            doc_id = cursor.lastrowid
            conn.commit()
            conn.close()

            self.log(f"✅ Document saved with ID: {doc_id}")

            return {
                'success': True,
                'message': 'Document saved successfully',
                'document_id': doc_id,
                'embedding_dimensions': len(embedding)
            }

        except Exception as e:
            self.log(f"❌ Failed to save document: {str(e)}")
            return {
                'success': False,
                'error': f'Failed to save document: {str(e)}'
            }

    def tool_search_similar(self, args):
        """Search for similar documents using cosine similarity"""
        query = args.get('query', '')
        limit = args.get('limit', 5)

        if not query:
            return {
                'success': False,
                'error': 'Query is required'
            }

        self.log(f"🔍 Searching for similar documents: {query[:50]}...")

        try:
            # Generate embedding for query
            embedding_result = self.tool_create_embedding({'text': query})

            if not embedding_result.get('success'):
                return embedding_result

            query_embedding = embedding_result['embedding']

            # Get all documents from database
            conn = sqlite3.connect(EMBEDDINGS_DB_PATH)
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()

            cursor.execute('''
                SELECT id, content, embedding, created_at
                FROM documents
                ORDER BY created_at DESC
            ''')

            rows = cursor.fetchall()
            conn.close()

            if not rows:
                self.log(f"📭 No documents found in database")
                return {
                    'success': True,
                    'count': 0,
                    'documents': []
                }

            # Calculate cosine similarity for each document
            import struct
            import math

            results = []
            for row in rows:
                # Unpack embedding from binary
                doc_embedding = list(struct.unpack(f'{len(query_embedding)}f', row['embedding']))

                # Calculate cosine similarity
                dot_product = sum(a * b for a, b in zip(query_embedding, doc_embedding))
                magnitude_a = math.sqrt(sum(a * a for a in query_embedding))
                magnitude_b = math.sqrt(sum(b * b for b in doc_embedding))

                similarity = dot_product / (magnitude_a * magnitude_b) if magnitude_a and magnitude_b else 0

                results.append({
                    'id': row['id'],
                    'content': row['content'],
                    'similarity': similarity,
                    'created_at': row['created_at']
                })

            # Sort by similarity and limit results
            results.sort(key=lambda x: x['similarity'], reverse=True)
            results = results[:limit]

            self.log(f"🔍 Found {len(results)} similar documents")

            return {
                'success': True,
                'count': len(results),
                'documents': results
            }

        except Exception as e:
            self.log(f"❌ Failed to search documents: {str(e)}")
            return {
                'success': False,
                'error': f'Failed to search documents: {str(e)}',
                'documents': []
            }

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
    print('Available Tools (7):')
    print('  🎭 get_joke          - Get random jokes from JokeAPI')
    print('  💾 save_joke         - Save a joke to local database')
    print('  📖 get_saved_jokes   - Get all saved jokes from database')
    print('  🧪 run_tests         - Run server tests in Docker container')
    print('  🔮 create_embedding  - Generate embeddings using Ollama')
    print('  📝 save_document     - Save document with embeddings')
    print('  🔍 search_similar    - Search similar documents by embeddings')
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
