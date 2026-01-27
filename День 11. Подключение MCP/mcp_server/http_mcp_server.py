#!/usr/bin/env python3
"""
Simple HTTP MCP Server for Android testing
Supports JSON-RPC 2.0 protocol via HTTP POST
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import os
import platform
import psutil
from datetime import datetime

class MCPServerHandler(BaseHTTPRequestHandler):
    
    def do_POST(self):
        """Handle POST requests"""
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        
        try:
            request = json.loads(post_data.decode('utf-8'))
            response = self.handle_mcp_request(request)
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            self.wfile.write(json.dumps(response).encode('utf-8'))
            
        except Exception as e:
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
                'name': 'get_system_info',
                'description': 'Get system information (OS, CPU, memory)',
                'inputSchema': {
                    'type': 'object',
                    'properties': {}
                }
            },
            {
                'name': 'get_current_time',
                'description': 'Get current date and time',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'format': {
                            'type': 'string',
                            'description': 'Time format (default: ISO)',
                            'default': 'iso'
                        }
                    }
                }
            },
            {
                'name': 'list_directory',
                'description': 'List files in a directory',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'path': {
                            'type': 'string',
                            'description': 'Directory path'
                        }
                    },
                    'required': ['path']
                }
            },
            {
                'name': 'calculate',
                'description': 'Evaluate a mathematical expression',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'expression': {
                            'type': 'string',
                            'description': 'Mathematical expression to evaluate'
                        }
                    },
                    'required': ['expression']
                }
            },
            {
                'name': 'get_weather_mock',
                'description': 'Get mock weather data for testing',
                'inputSchema': {
                    'type': 'object',
                    'properties': {
                        'city': {
                            'type': 'string',
                            'description': 'City name'
                        }
                    },
                    'required': ['city']
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
        
        try:
            if tool_name == 'get_system_info':
                result = self.tool_get_system_info()
            elif tool_name == 'get_current_time':
                result = self.tool_get_current_time(arguments)
            elif tool_name == 'list_directory':
                result = self.tool_list_directory(arguments)
            elif tool_name == 'calculate':
                result = self.tool_calculate(arguments)
            elif tool_name == 'get_weather_mock':
                result = self.tool_get_weather_mock(arguments)
            else:
                raise ValueError(f'Unknown tool: {tool_name}')
            
            return {
                'jsonrpc': '2.0',
                'id': request_id,
                'result': {
                    'content': [
                        {
                            'type': 'text',
                            'text': json.dumps(result, indent=2)
                        }
                    ]
                }
            }
        except Exception as e:
            return {
                'jsonrpc': '2.0',
                'id': request_id,
                'error': {
                    'code': -32000,
                    'message': str(e)
                }
            }
    
    def tool_get_system_info(self):
        """Get system information"""
        return {
            'os': platform.system(),
            'os_version': platform.version(),
            'architecture': platform.machine(),
            'cpu_count': psutil.cpu_count(),
            'cpu_percent': psutil.cpu_percent(interval=1),
            'memory_total_gb': round(psutil.virtual_memory().total / (1024**3), 2),
            'memory_available_gb': round(psutil.virtual_memory().available / (1024**3), 2),
            'memory_percent': psutil.virtual_memory().percent
        }
    
    def tool_get_current_time(self, args):
        """Get current time"""
        time_format = args.get('format', 'iso')
        now = datetime.now()
        
        if time_format == 'iso':
            return {'time': now.isoformat()}
        elif time_format == 'unix':
            return {'time': int(now.timestamp())}
        else:
            return {'time': now.strftime('%Y-%m-%d %H:%M:%S')}
    
    def tool_list_directory(self, args):
        """List directory contents"""
        path = args.get('path', '.')
        try:
            files = os.listdir(path)
            return {
                'path': path,
                'files': files[:20],
                'total': len(files)
            }
        except Exception as e:
            return {'error': str(e)}
    
    def tool_calculate(self, args):
        """Calculate expression"""
        expression = args.get('expression', '')
        try:
            result = eval(expression, {"__builtins__": {}}, {})
            return {
                'expression': expression,
                'result': result
            }
        except Exception as e:
            return {'error': str(e)}
    
    def tool_get_weather_mock(self, args):
        """Mock weather data"""
        city = args.get('city', 'Unknown')
        return {
            'city': city,
            'temperature': 22,
            'condition': 'Sunny',
            'humidity': 65,
            'wind_speed': 12,
            'note': 'This is mock data for testing'
        }
    
    def log_message(self, format, *args):
        """Custom log format"""
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        print(f'[{timestamp}] {format % args}')

def run_server(host='0.0.0.0', port=8080):
    """Start MCP HTTP server"""
    server_address = (host, port)
    httpd = HTTPServer(server_address, MCPServerHandler)
    
    print('=' * 60)
    print('🚀 MCP HTTP Server Started')
    print('=' * 60)
    print(f'Server: http://{host}:{port}')
    print(f'From Android emulator use: http://10.0.2.2:{port}')
    print(f'From real device use: http://<your-computer-ip>:{port}')
    print()
    print('Available endpoints:')
    print('  POST / - MCP JSON-RPC requests')
    print()
    print('Press Ctrl+C to stop')
    print('=' * 60)
    print()
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print('\n\n🛑 Server stopped')

if __name__ == '__main__':
    run_server()
