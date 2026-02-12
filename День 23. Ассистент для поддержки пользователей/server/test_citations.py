#!/usr/bin/env python3
"""Test citation functionality"""

import requests
import json

SERVER_URL = "http://localhost:8080"

def test_semantic_search_citations():
    """Test that semantic_search returns citations"""

    # Test query
    response = requests.post(
        SERVER_URL,
        json={
            "jsonrpc": "2.0",
            "id": 1,
            "method": "tools/call",
            "params": {
                "name": "semantic_search",
                "arguments": {
                    "query": "What is MCP protocol?",
                    "limit": 3,
                    "threshold": 0.7
                }
            }
        }
    )

    result = response.json()
    print(json.dumps(result, indent=2, ensure_ascii=False))

    # Verify citations present
    if 'result' in result:
        content = result['result']['content'][0]['text']
        data = json.loads(content)

        assert 'documents' in data, "Missing documents"

        for doc in data['documents']:
            assert 'citation' in doc, f"Document {doc['id']} missing citation field"
            assert 'citation_info' in doc, f"Document {doc['id']} missing citation_info"
            print(f"✅ Doc {doc['id']}: {doc['citation']}")

        assert 'sources_summary' in data, "Missing sources_summary"
        print(f"✅ Sources: {data['sources_summary']}")

        return True

    return False

if __name__ == "__main__":
    success = test_semantic_search_citations()
    print("✅ All tests passed!" if success else "❌ Tests failed")
