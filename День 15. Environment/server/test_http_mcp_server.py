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
        # Patch DB_PATH to use test directory
        import http_mcp_server
        self.original_db_path = http_mcp_server.DB_PATH
        self.test_db_path = os.path.join(self.test_dir, 'test_jokes.db')
        http_mcp_server.DB_PATH = self.test_db_path

        # Create data directory if needed
        os.makedirs(os.path.dirname(self.test_db_path), exist_ok=True)

        # Initialize test database
        init_database()

        # Create handler instance without calling __init__
        self.handler = object.__new__(MCPServerHandler)

    def tearDown(self):
        """Clean up after each test"""
        import http_mcp_server
        http_mcp_server.DB_PATH = self.original_db_path

        # Remove test database
        if os.path.exists(self.test_db_path):
            os.remove(self.test_db_path)


class TestDatabaseOperations(TestMCPServerHandler):
    """Test database initialization and operations"""

    def test_database_initialization(self):
        """Test that database is created with correct schema"""
        self.assertTrue(os.path.exists(self.test_db_path))

        conn = sqlite3.connect(self.test_db_path)
        cursor = conn.cursor()

        # Check table exists
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='saved_jokes'")
        self.assertIsNotNone(cursor.fetchone())

        # Check table schema
        cursor.execute("PRAGMA table_info(saved_jokes)")
        columns = {row[1]: row[2] for row in cursor.fetchall()}

        self.assertIn('id', columns)
        self.assertIn('joke_api_id', columns)
        self.assertIn('category', columns)
        self.assertIn('type', columns)
        self.assertIn('joke_text', columns)
        self.assertIn('setup', columns)
        self.assertIn('delivery', columns)
        self.assertIn('saved_at', columns)

        conn.close()


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
        self.assertEqual(len(tools), 4)

        tool_names = [tool['name'] for tool in tools]
        self.assertIn('get_joke', tool_names)
        self.assertIn('save_joke', tool_names)
        self.assertIn('get_saved_jokes', tool_names)
        self.assertIn('run_tests', tool_names)

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


class TestGetJokeTool(TestMCPServerHandler):
    """Test get_joke tool functionality"""

    @patch('urllib.request.urlopen')
    def test_get_single_joke_success(self, mock_urlopen):
        """Test getting a single-type joke from JokeAPI"""
        # Mock JokeAPI response
        mock_response = Mock()
        mock_response.read.return_value = json.dumps({
            'error': False,
            'category': 'Programming',
            'type': 'single',
            'joke': 'Why do programmers prefer dark mode? Because light attracts bugs!',
            'id': 123
        }).encode('utf-8')
        mock_response.__enter__ = Mock(return_value=mock_response)
        mock_response.__exit__ = Mock(return_value=False)
        mock_urlopen.return_value = mock_response

        result = self.handler.tool_get_joke({'category': 'Programming'})

        self.assertEqual(result['category'], 'Programming')
        self.assertEqual(result['type'], 'single')
        self.assertEqual(result['id'], 123)
        self.assertIn('joke', result)
        self.assertNotIn('error', result)

    @patch('urllib.request.urlopen')
    def test_get_twopart_joke_success(self, mock_urlopen):
        """Test getting a twopart joke from JokeAPI"""
        mock_response = Mock()
        mock_response.read.return_value = json.dumps({
            'error': False,
            'category': 'Misc',
            'type': 'twopart',
            'setup': 'Why did the chicken cross the road?',
            'delivery': 'To get to the other side!',
            'id': 456
        }).encode('utf-8')
        mock_response.__enter__ = Mock(return_value=mock_response)
        mock_response.__exit__ = Mock(return_value=False)
        mock_urlopen.return_value = mock_response

        result = self.handler.tool_get_joke({'category': 'Misc'})

        self.assertEqual(result['type'], 'twopart')
        self.assertIn('setup', result)
        self.assertIn('delivery', result)
        self.assertNotIn('error', result)

    @patch('urllib.request.urlopen')
    def test_get_joke_with_blacklist(self, mock_urlopen):
        """Test getting joke with blacklist flags"""
        mock_response = Mock()
        mock_response.read.return_value = json.dumps({
            'error': False,
            'category': 'Any',
            'type': 'single',
            'joke': 'A safe joke',
            'id': 789
        }).encode('utf-8')
        mock_response.__enter__ = Mock(return_value=mock_response)
        mock_response.__exit__ = Mock(return_value=False)
        mock_urlopen.return_value = mock_response

        result = self.handler.tool_get_joke({
            'category': 'Any',
            'blacklistFlags': 'nsfw,racist,sexist'
        })

        # Verify URL was called with blacklist parameters
        call_url = mock_urlopen.call_args[0][0]
        self.assertIn('blacklistFlags', call_url)
        self.assertNotIn('error', result)

    @patch('urllib.request.urlopen')
    def test_get_joke_api_error(self, mock_urlopen):
        """Test handling JokeAPI error response"""
        mock_response = Mock()
        mock_response.read.return_value = json.dumps({
            'error': True,
            'message': 'No jokes available for this category'
        }).encode('utf-8')
        mock_response.__enter__ = Mock(return_value=mock_response)
        mock_response.__exit__ = Mock(return_value=False)
        mock_urlopen.return_value = mock_response

        result = self.handler.tool_get_joke({'category': 'InvalidCategory'})

        self.assertTrue(result.get('error'))
        self.assertIn('message', result)

    @patch('urllib.request.urlopen')
    def test_get_joke_network_error(self, mock_urlopen):
        """Test handling network errors"""
        mock_urlopen.side_effect = Exception('Network timeout')

        result = self.handler.tool_get_joke({'category': 'Any'})

        self.assertTrue(result.get('error'))
        self.assertIn('Failed to fetch joke', result['message'])


class TestSaveJokeTool(TestMCPServerHandler):
    """Test save_joke tool functionality"""

    def test_save_single_joke(self):
        """Test saving a single-type joke"""
        joke_data = {
            'joke_api_id': 123,
            'category': 'Programming',
            'type': 'single',
            'joke_text': 'Why do programmers prefer dark mode? Light attracts bugs!'
        }

        result = self.handler.tool_save_joke(joke_data)

        self.assertTrue(result['success'])
        self.assertIn('saved_joke_id', result)

        # Verify joke was saved to database
        conn = sqlite3.connect(self.test_db_path)
        cursor = conn.cursor()
        cursor.execute('SELECT * FROM saved_jokes WHERE id = ?', (result['saved_joke_id'],))
        row = cursor.fetchone()
        conn.close()

        self.assertIsNotNone(row)
        self.assertEqual(row[1], 123)  # joke_api_id
        self.assertEqual(row[2], 'Programming')  # category
        self.assertEqual(row[3], 'single')  # type
        self.assertIn('dark mode', row[4])  # joke_text

    def test_save_twopart_joke(self):
        """Test saving a twopart joke"""
        joke_data = {
            'joke_api_id': 456,
            'category': 'Misc',
            'type': 'twopart',
            'setup': 'Why did the chicken cross the road?',
            'delivery': 'To get to the other side!'
        }

        result = self.handler.tool_save_joke(joke_data)

        self.assertTrue(result['success'])

        # Verify both setup and delivery were saved
        conn = sqlite3.connect(self.test_db_path)
        cursor = conn.cursor()
        cursor.execute('SELECT setup, delivery FROM saved_jokes WHERE id = ?',
                      (result['saved_joke_id'],))
        row = cursor.fetchone()
        conn.close()

        self.assertEqual(row[0], 'Why did the chicken cross the road?')
        self.assertEqual(row[1], 'To get to the other side!')

    def test_save_joke_without_api_id(self):
        """Test saving joke without JokeAPI ID (custom joke)"""
        joke_data = {
            'type': 'single',
            'joke_text': 'A custom joke without API ID'
        }

        result = self.handler.tool_save_joke(joke_data)

        self.assertTrue(result['success'])

        # Verify joke was saved with NULL joke_api_id
        conn = sqlite3.connect(self.test_db_path)
        cursor = conn.cursor()
        cursor.execute('SELECT joke_api_id, joke_text FROM saved_jokes WHERE id = ?',
                      (result['saved_joke_id'],))
        row = cursor.fetchone()
        conn.close()

        self.assertIsNone(row[0])  # joke_api_id should be NULL
        self.assertEqual(row[1], 'A custom joke without API ID')

    def test_save_joke_russian_text(self):
        """Test saving joke with Russian text"""
        joke_data = {
            'type': 'single',
            'category': 'Programming',
            'joke_text': 'Почему программисты носят очки? Потому что они не видят C!'
        }

        result = self.handler.tool_save_joke(joke_data)

        self.assertTrue(result['success'])

        # Verify Russian text was saved correctly
        conn = sqlite3.connect(self.test_db_path)
        cursor = conn.cursor()
        cursor.execute('SELECT joke_text FROM saved_jokes WHERE id = ?',
                      (result['saved_joke_id'],))
        row = cursor.fetchone()
        conn.close()

        self.assertIn('программисты', row[0])


class TestGetSavedJokesTool(TestMCPServerHandler):
    """Test get_saved_jokes tool functionality"""

    def setUp(self):
        """Set up test database with sample jokes"""
        super().setUp()

        # Insert sample jokes
        conn = sqlite3.connect(self.test_db_path)
        cursor = conn.cursor()

        jokes = [
            (101, 'Programming', 'single', 'Joke 1', '', '', '2024-01-01 10:00:00'),
            (102, 'Misc', 'twopart', '', 'Setup 2', 'Delivery 2', '2024-01-02 10:00:00'),
            (103, 'Programming', 'single', 'Joke 3', '', '', '2024-01-03 10:00:00'),
        ]

        cursor.executemany('''
            INSERT INTO saved_jokes (joke_api_id, category, type, joke_text, setup, delivery, saved_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        ''', jokes)

        conn.commit()
        conn.close()

    def test_get_all_saved_jokes(self):
        """Test retrieving all saved jokes"""
        result = self.handler.tool_get_saved_jokes({})

        self.assertTrue(result['success'])
        self.assertEqual(result['count'], 3)
        self.assertEqual(len(result['jokes']), 3)

        # Verify jokes are ordered by saved_at DESC (newest first)
        self.assertEqual(result['jokes'][0]['joke_api_id'], 103)
        self.assertEqual(result['jokes'][1]['joke_api_id'], 102)
        self.assertEqual(result['jokes'][2]['joke_api_id'], 101)

    def test_get_saved_jokes_with_limit(self):
        """Test retrieving saved jokes with limit"""
        result = self.handler.tool_get_saved_jokes({'limit': 2})

        self.assertTrue(result['success'])
        self.assertEqual(result['count'], 2)
        self.assertEqual(len(result['jokes']), 2)

    def test_get_saved_jokes_empty_database(self):
        """Test retrieving from empty database"""
        # Clear database
        conn = sqlite3.connect(self.test_db_path)
        cursor = conn.cursor()
        cursor.execute('DELETE FROM saved_jokes')
        conn.commit()
        conn.close()

        result = self.handler.tool_get_saved_jokes({})

        self.assertTrue(result['success'])
        self.assertEqual(result['count'], 0)
        self.assertEqual(len(result['jokes']), 0)

    def test_get_saved_jokes_single_type(self):
        """Test that single-type jokes return 'joke' field"""
        result = self.handler.tool_get_saved_jokes({})

        single_jokes = [j for j in result['jokes'] if j['type'] == 'single']

        for joke in single_jokes:
            self.assertIn('joke', joke)
            self.assertNotIn('setup', joke)
            self.assertNotIn('delivery', joke)

    def test_get_saved_jokes_twopart_type(self):
        """Test that twopart jokes return 'setup' and 'delivery' fields"""
        result = self.handler.tool_get_saved_jokes({})

        twopart_jokes = [j for j in result['jokes'] if j['type'] == 'twopart']

        for joke in twopart_jokes:
            self.assertIn('setup', joke)
            self.assertIn('delivery', joke)
            self.assertNotIn('joke', joke)


class TestToolsCallIntegration(TestMCPServerHandler):
    """Integration tests for tools/call endpoint"""

    @patch('urllib.request.urlopen')
    def test_tools_call_get_joke(self, mock_urlopen):
        """Test tools/call with get_joke"""
        # Mock JokeAPI response
        mock_response = Mock()
        mock_response.read.return_value = json.dumps({
            'error': False,
            'category': 'Programming',
            'type': 'single',
            'joke': 'Test joke',
            'id': 999
        }).encode('utf-8')
        mock_response.__enter__ = Mock(return_value=mock_response)
        mock_response.__exit__ = Mock(return_value=False)
        mock_urlopen.return_value = mock_response

        request = {
            'jsonrpc': '2.0',
            'id': 10,
            'method': 'tools/call',
            'params': {
                'name': 'get_joke',
                'arguments': {'category': 'Programming'}
            }
        }

        response = self.handler.handle_mcp_request(request)

        self.assertEqual(response['jsonrpc'], '2.0')
        self.assertEqual(response['id'], 10)
        self.assertIn('result', response)
        self.assertIn('content', response['result'])

        # Parse the JSON content
        content_text = response['result']['content'][0]['text']
        joke_data = json.loads(content_text)

        self.assertEqual(joke_data['category'], 'Programming')
        self.assertEqual(joke_data['type'], 'single')

    def test_tools_call_save_joke(self):
        """Test tools/call with save_joke"""
        request = {
            'jsonrpc': '2.0',
            'id': 11,
            'method': 'tools/call',
            'params': {
                'name': 'save_joke',
                'arguments': {
                    'type': 'single',
                    'joke_text': 'Integration test joke'
                }
            }
        }

        response = self.handler.handle_mcp_request(request)

        self.assertIn('result', response)
        content_text = response['result']['content'][0]['text']
        result_data = json.loads(content_text)

        self.assertTrue(result_data['success'])
        self.assertIn('saved_joke_id', result_data)

    def test_tools_call_get_saved_jokes(self):
        """Test tools/call with get_saved_jokes"""
        # First save a joke
        self.handler.tool_save_joke({
            'type': 'single',
            'joke_text': 'Test saved joke'
        })

        request = {
            'jsonrpc': '2.0',
            'id': 12,
            'method': 'tools/call',
            'params': {
                'name': 'get_saved_jokes',
                'arguments': {}
            }
        }

        response = self.handler.handle_mcp_request(request)

        self.assertIn('result', response)
        content_text = response['result']['content'][0]['text']
        result_data = json.loads(content_text)

        self.assertTrue(result_data['success'])
        self.assertGreater(result_data['count'], 0)

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


class TestEndToEndScenarios(TestMCPServerHandler):
    """End-to-end integration tests simulating real usage"""

    @patch('urllib.request.urlopen')
    def test_complete_joke_workflow(self, mock_urlopen):
        """Test complete workflow: get joke -> save joke -> retrieve saved jokes"""
        # Step 1: Get a joke from JokeAPI
        mock_response = Mock()
        mock_response.read.return_value = json.dumps({
            'error': False,
            'category': 'Programming',
            'type': 'single',
            'joke': 'Why do Java developers wear glasses? Because they can\'t C#!',
            'id': 888
        }).encode('utf-8')
        mock_response.__enter__ = Mock(return_value=mock_response)
        mock_response.__exit__ = Mock(return_value=False)
        mock_urlopen.return_value = mock_response

        joke_result = self.handler.tool_get_joke({'category': 'Programming'})
        self.assertNotIn('error', joke_result)

        # Step 2: Save the joke
        save_result = self.handler.tool_save_joke({
            'joke_api_id': joke_result['id'],
            'category': joke_result['category'],
            'type': joke_result['type'],
            'joke_text': joke_result['joke']
        })
        self.assertTrue(save_result['success'])

        # Step 3: Retrieve saved jokes
        saved_jokes_result = self.handler.tool_get_saved_jokes({})
        self.assertTrue(saved_jokes_result['success'])
        self.assertGreater(saved_jokes_result['count'], 0)

        # Verify our joke is in the saved jokes
        saved_joke_ids = [j['joke_api_id'] for j in saved_jokes_result['jokes']]
        self.assertIn(888, saved_joke_ids)


class TestErrorHandling(TestMCPServerHandler):
    """Test error handling and edge cases"""

    def test_save_joke_missing_required_field(self):
        """Test saving joke without required type field"""
        result = self.handler.tool_save_joke({
            'joke_text': 'Missing type field'
        })

        # Should handle gracefully with default type='single'
        self.assertTrue(result['success'])

    @patch('sqlite3.connect')
    def test_database_connection_error(self, mock_connect):
        """Test handling database connection errors"""
        mock_connect.side_effect = Exception('Database connection failed')

        result = self.handler.tool_save_joke({
            'type': 'single',
            'joke_text': 'Test joke'
        })

        self.assertFalse(result['success'])
        self.assertIn('error', result)

    def test_get_saved_jokes_large_limit(self):
        """Test get_saved_jokes with very large limit"""
        result = self.handler.tool_get_saved_jokes({'limit': 1000000})

        self.assertTrue(result['success'])
        # Should not crash, just return available jokes


def run_tests():
    """Run all tests with detailed output"""
    # Create test suite
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()

    # Add all test classes
    suite.addTests(loader.loadTestsFromTestCase(TestDatabaseOperations))
    suite.addTests(loader.loadTestsFromTestCase(TestJSONRPCProtocol))
    suite.addTests(loader.loadTestsFromTestCase(TestGetJokeTool))
    suite.addTests(loader.loadTestsFromTestCase(TestSaveJokeTool))
    suite.addTests(loader.loadTestsFromTestCase(TestGetSavedJokesTool))
    suite.addTests(loader.loadTestsFromTestCase(TestToolsCallIntegration))
    suite.addTests(loader.loadTestsFromTestCase(TestEndToEndScenarios))
    suite.addTests(loader.loadTestsFromTestCase(TestErrorHandling))

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
