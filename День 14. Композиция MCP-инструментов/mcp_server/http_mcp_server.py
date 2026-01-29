#!/usr/bin/env python3
"""
MCP Server with JokeAPI Integration
Supports JSON-RPC 2.0 protocol via HTTP POST
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import urllib.request
import urllib.parse
from datetime import datetime

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
                    'version': '1.0.0'
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
            else:
                raise ValueError(f'Unknown tool: {tool_name}')
            
            self.log(f"✨ Tool result: {json.dumps(result)[:100]}...")
            
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
    
    def log_message(self, format, *args):
        """Custom log format"""
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        print(f'[{timestamp}] {format % args}')

def run_server(host='0.0.0.0', port=8080):
    """Start MCP HTTP server"""
    server_address = (host, port)
    httpd = HTTPServer(server_address, MCPServerHandler)
    
    print('=' * 70)
    print('🚀 MCP HTTP Server with JokeAPI Integration'.center(70))
    print('=' * 70)
    print(f'Server: http://{host}:{port}')
    print(f'From Android emulator: http://10.0.2.2:{port}')
    print(f'From real device: http://<your-computer-ip>:{port}')
    print()
    print('Available Tools:')
    print('  🎭 get_joke - Get random jokes from JokeAPI')
    print()
    print('JokeAPI Integration:')
    print('  • Base URL: https://v2.jokeapi.dev')
    print('  • Categories: Any, Programming, Misc, Dark, Pun, Spooky, Christmas')
    print('  • Safe mode support via blacklist flags')
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
