#!/usr/bin/env python3
"""
Comprehensive Test Suite for MCP HTTP Server
Tests all tools, JSON-RPC protocol handling, and database operations
"""

import unittest
import json
import sqlite3
import os
import tempfile
import shutil
from unittest.mock import patch, MagicMock, Mock
from http_mcp_server import MCPServerHandler, init_database


class TestMCPServerHandler(unittest.TestCase):
    """Test suite for MCP Server Handler"""

    @classmethod
    def setUpClass(cls):
        """Set up test database directory"""
        cls.test_dir = tempfile.mkdtemp()
        cls.original_db_path = None

    @classmethod
    def tearDownClass(cls):
        """Clean up test directory"""
        shutil.rmtree(cls.test_dir)

    def setUp(self):
        """Set up test database for each test"""
        # Patch EMBEDDINGS_DB_PATH to use test directory
        import http_mcp_server
        self.original_embeddings_db_path = http_mcp_server.EMBEDDINGS_DB_PATH
        self.test_db_path = os.path.join(self.test_dir, 'test_embeddings.db')
        http_mcp_server.EMBEDDINGS_DB_PATH = self.test_db_path

        # Create data directory if needed
        os.makedirs(os.path.dirname(self.test_db_path), exist_ok=True)

        # Initialize test database
        init_database()

        # Create handler instance without calling __init__
        self.handler = object.__new__(MCPServerHandler)

    def tearDown(self):
        """Clean up after each test"""
        import http_mcp_server
        http_mcp_server.EMBEDDINGS_DB_PATH = self.original_embeddings_db_path

        # Remove test database
        if os.path.exists(self.test_db_path):
            os.remove(self.test_db_path)


class TestDatabaseOperations(TestMCPServerHandler):
    """Test database initialization and operations"""

    def test_embeddings_database_exists(self):
        """Test that embeddings database exists"""
        # Just verify we can connect - actual schema testing would require embeddings tools
        self.assertTrue(os.path.exists(self.test_db_path))


class TestJSONRPCProtocol(TestMCPServerHandler):
    """Test JSON-RPC 2.0 protocol handling"""

    def test_initialize_request(self):
        """Test initialize method"""
        request = {
            'jsonrpc': '2.0',
            'id': 1,
            'method': 'initialize'
        }

        response = self.handler.handle_mcp_request(request)

        self.assertEqual(response['jsonrpc'], '2.0')
        self.assertEqual(response['id'], 1)
        self.assertIn('result', response)
        self.assertEqual(response['result']['protocolVersion'], '2024-11-05')
        self.assertIn('serverInfo', response['result'])
        self.assertEqual(response['result']['serverInfo']['name'], 'Python HTTP MCP Server')

    def test_tools_list_request(self):
        """Test tools/list method"""
        request = {
            'jsonrpc': '2.0',
            'id': 2,
            'method': 'tools/list'
        }

        response = self.handler.handle_mcp_request(request)

        self.assertEqual(response['jsonrpc'], '2.0')
        self.assertEqual(response['id'], 2)
        self.assertIn('result', response)
        self.assertIn('tools', response['result'])

        tools = response['result']['tools']
        self.assertEqual(len(tools), 6)

        tool_names = [tool['name'] for tool in tools]
        self.assertIn('create_embedding', tool_names)
        self.assertIn('save_document', tool_names)
        self.assertIn('search_similar', tool_names)
        self.assertIn('semantic_search', tool_names)
        self.assertIn('process_pdf', tool_names)
        self.assertIn('process_text_chunks', tool_names)

    def test_unknown_method(self):
        """Test unknown method returns error"""
        request = {
            'jsonrpc': '2.0',
            'id': 3,
            'method': 'unknown_method'
        }

        response = self.handler.handle_mcp_request(request)

        self.assertEqual(response['jsonrpc'], '2.0')
        self.assertEqual(response['id'], 3)
        self.assertIn('error', response)
        self.assertEqual(response['error']['code'], -32601)
        self.assertIn('Method not found', response['error']['message'])


    def test_tools_call_unknown_tool(self):
        """Test tools/call with unknown tool name"""
        request = {
            'jsonrpc': '2.0',
            'id': 13,
            'method': 'tools/call',
            'params': {
                'name': 'unknown_tool',
                'arguments': {}
            }
        }

        response = self.handler.handle_mcp_request(request)

        self.assertIn('error', response)
        self.assertEqual(response['error']['code'], -32000)


class TestLocalDatabaseOperations(TestMCPServerHandler):
    """Test local database operations without remote proxy"""

    def test_save_document_locally(self):
        """Test saving document with local embedding generation"""
        # Mock Ollama response
        with patch('urllib.request.urlopen') as mock_urlopen:
            mock_response = MagicMock()
            mock_response.read.return_value = json.dumps({
                'embedding': [0.1] * 768
            }).encode('utf-8')
            mock_response.__enter__ = Mock(return_value=mock_response)
            mock_response.__exit__ = Mock(return_value=False)
            mock_urlopen.return_value = mock_response

            result = self.handler.tool_save_document({
                'content': 'Test document content'
            })

            self.assertTrue(result['success'])
            self.assertIsNotNone(result['document_id'])
            self.assertEqual(result['embedding_dimensions'], 768)

    def test_search_similar_locally(self):
        """Test searching similar documents in local database"""
        # First, save a document
        with patch('urllib.request.urlopen') as mock_urlopen:
            mock_response = MagicMock()
            mock_response.read.return_value = json.dumps({
                'embedding': [0.1] * 768
            }).encode('utf-8')
            mock_response.__enter__ = Mock(return_value=mock_response)
            mock_response.__exit__ = Mock(return_value=False)
            mock_urlopen.return_value = mock_response

            # Save document
            self.handler.tool_save_document({
                'content': 'Test document about REST API'
            })

            # Search for similar
            result = self.handler.tool_search_similar({
                'query': 'API documentation',
                'limit': 5
            })

            self.assertTrue(result['success'])
            self.assertGreater(result['count'], 0)
            self.assertIn('documents', result)

    def test_process_text_chunks_locally(self):
        """Test chunking and indexing text locally"""
        with patch('urllib.request.urlopen') as mock_urlopen:
            mock_response = MagicMock()
            mock_response.read.return_value = json.dumps({
                'embedding': [0.1] * 768
            }).encode('utf-8')
            mock_response.__enter__ = Mock(return_value=mock_response)
            mock_response.__exit__ = Mock(return_value=False)
            mock_urlopen.return_value = mock_response

            long_text = "Test sentence. " * 100  # ~1500 characters

            result = self.handler.tool_process_text_chunks({
                'text': long_text,
                'filename': 'test.txt',
                'chunk_size': 500,
                'chunk_overlap': 100
            })

            self.assertTrue(result['success'])
            self.assertGreater(result['chunks_saved'], 1)
            self.assertEqual(result['filename'], 'test.txt')

    def test_markdown_file_detection(self):
        """Test that .md files are detected as markdown source type"""
        with patch('urllib.request.urlopen') as mock_urlopen:
            mock_response = MagicMock()
            mock_response.read.return_value = json.dumps({
                'embedding': [0.1] * 768
            }).encode('utf-8')
            mock_response.__enter__ = Mock(return_value=mock_response)
            mock_response.__exit__ = Mock(return_value=False)
            mock_urlopen.return_value = mock_response

            result = self.handler.tool_process_text_chunks({
                'text': '# Markdown Title\n\nSome content',
                'filename': 'README.md'
            })

            self.assertTrue(result['success'])
            self.assertGreater(result['chunks_saved'], 0)

    def test_semantic_search_with_threshold(self):
        """Test semantic search with threshold filtering"""
        with patch('urllib.request.urlopen') as mock_urlopen:
            mock_response = MagicMock()
            mock_response.read.return_value = json.dumps({
                'embedding': [0.1] * 768
            }).encode('utf-8')
            mock_response.__enter__ = Mock(return_value=mock_response)
            mock_response.__exit__ = Mock(return_value=False)
            mock_urlopen.return_value = mock_response

            # Save a document first
            self.handler.tool_save_document({
                'content': 'Machine learning documentation'
            })

            # Search with threshold
            result = self.handler.tool_semantic_search({
                'query': 'ML docs',
                'limit': 3,
                'threshold': 0.6
            })

            self.assertTrue(result['success'])
            self.assertIn('documents', result)
            self.assertEqual(result['threshold'], 0.6)
            self.assertEqual(result['source'], 'local_database')


def run_tests():
    """Run all tests with detailed output"""
    # Create test suite
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()

    # Add all test classes
    suite.addTests(loader.loadTestsFromTestCase(TestDatabaseOperations))
    suite.addTests(loader.loadTestsFromTestCase(TestJSONRPCProtocol))
    suite.addTests(loader.loadTestsFromTestCase(TestLocalDatabaseOperations))

    # Run tests with verbose output
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)

    # Print summary
    print('\n' + '=' * 70)
    print('TEST SUMMARY')
    print('=' * 70)
    print(f'Tests run: {result.testsRun}')
    print(f'Successes: {result.testsRun - len(result.failures) - len(result.errors)}')
    print(f'Failures: {len(result.failures)}')
    print(f'Errors: {len(result.errors)}')
    print('=' * 70)

    return result.wasSuccessful()


if __name__ == '__main__':
    success = run_tests()
    exit(0 if success else 1)
