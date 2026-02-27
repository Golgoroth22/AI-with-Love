#!/bin/bash
# Automated test script for MCP server create_webpage tool

SERVER_URL="http://148.253.209.151:8080"
WEBPAGE_BASE="http://148.253.209.151/webpages"

echo "🧪 MCP Server Test Suite"
echo "========================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Function to test MCP tool call
test_create_webpage() {
    local test_name=$1
    local text=$2
    local title=$3

    echo -n "Testing: $test_name... "

    if [ -z "$title" ]; then
        response=$(curl -s -X POST "$SERVER_URL" \
            -H "Content-Type: application/json" \
            -d "{
                \"jsonrpc\": \"2.0\",
                \"id\": 1,
                \"method\": \"tools/call\",
                \"params\": {
                    \"name\": \"create_webpage\",
                    \"arguments\": {
                        \"text\": \"$text\"
                    }
                }
            }")
    else
        response=$(curl -s -X POST "$SERVER_URL" \
            -H "Content-Type: application/json" \
            -d "{
                \"jsonrpc\": \"2.0\",
                \"id\": 1,
                \"method\": \"tools/call\",
                \"params\": {
                    \"name\": \"create_webpage\",
                    \"arguments\": {
                        \"text\": \"$text\",
                        \"title\": \"$title\"
                    }
                }
            }")
    fi

    # Check if response contains success
    if echo "$response" | grep -q '"success": true'; then
        # Extract URL
        url=$(echo "$response" | jq -r '.result.content[0].text | fromjson | .url')

        # Test if URL is accessible
        http_code=$(curl -s -o /dev/null -w "%{http_code}" "$url")

        if [ "$http_code" = "200" ]; then
            echo -e "${GREEN}✓ PASSED${NC} (URL: $url)"
            TESTS_PASSED=$((TESTS_PASSED + 1))
            return 0
        else
            echo -e "${RED}✗ FAILED${NC} (URL not accessible: $http_code)"
            TESTS_FAILED=$((TESTS_FAILED + 1))
            return 1
        fi
    else
        echo -e "${RED}✗ FAILED${NC} (Tool call failed)"
        echo "Response: $response"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Test 1: Check if server is running
echo "1️⃣  Checking if MCP server is accessible..."
response=$(curl -s -X POST "$SERVER_URL" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}')

if echo "$response" | grep -q "create_webpage"; then
    echo -e "${GREEN}✓ Server is running and create_webpage tool is available${NC}"
    echo ""
else
    echo -e "${RED}✗ Server is not accessible or create_webpage tool not found${NC}"
    echo "Response: $response"
    echo ""
    echo "Please deploy the updated server first:"
    echo "  cd '/Users/falin/AndroidStudioProjects/AI-with-Love/День 24. Ассистент команды/server'"
    echo "  ./deploy_quick.sh"
    exit 1
fi

# Test 2: Basic webpage creation
echo "2️⃣  Running webpage creation tests..."
echo ""

test_create_webpage "Simple text" "Hello World! This is a test webpage."

test_create_webpage "With title" "This is the content of my webpage." "My Test Page"

test_create_webpage "Long text" "$(printf 'A%.0s' {1..1000})"

test_create_webpage "Unicode & Emoji" "🚀 Привет, мир! 🎉 Hello World! 💻"

test_create_webpage "Special chars" "Testing special characters: < > & \" '"

echo ""
echo "3️⃣  Testing XSS prevention..."
echo ""

# Create webpage with script tag
response=$(curl -s -X POST "$SERVER_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": "create_webpage",
            "arguments": {
                "text": "<script>alert(\"XSS\")</script>This should be safe!"
            }
        }
    }')

url=$(echo "$response" | jq -r '.result.content[0].text | fromjson | .url')

# Download webpage and check if script tags are escaped
page_content=$(curl -s "$url")

if echo "$page_content" | grep -q "&lt;script&gt;"; then
    echo -e "${GREEN}✓ XSS Prevention: Script tags properly escaped${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}✗ XSS Prevention: Script tags NOT escaped!${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

echo ""
echo "4️⃣  Testing error handling..."
echo ""

# Test with empty text
response=$(curl -s -X POST "$SERVER_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": "create_webpage",
            "arguments": {
                "text": ""
            }
        }
    }')

if echo "$response" | grep -q '"success": false'; then
    echo -e "${GREEN}✓ Empty text rejected${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${YELLOW}⚠ Empty text not rejected (may be acceptable)${NC}"
fi

# Test with very long text (>10000 chars)
long_text=$(printf 'A%.0s' {1..10001})
response=$(curl -s -X POST "$SERVER_URL" \
    -H "Content-Type: application/json" \
    -d "{
        \"jsonrpc\": \"2.0\",
        \"id\": 1,
        \"method\": \"tools/call\",
        \"params\": {
            \"name\": \"create_webpage\",
            \"arguments\": {
                \"text\": \"$long_text\"
            }
        }
    }")

if echo "$response" | grep -q '"success": false'; then
    echo -e "${GREEN}✓ Too long text rejected (>10000 chars)${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${YELLOW}⚠ Long text accepted (may be acceptable)${NC}"
fi

echo ""
echo "5️⃣  Testing server infrastructure..."
echo ""

# Check if webpages directory is accessible
http_code=$(curl -s -o /dev/null -w "%{http_code}" "$WEBPAGE_BASE/")

if [ "$http_code" = "200" ] || [ "$http_code" = "403" ] || [ "$http_code" = "404" ]; then
    echo -e "${GREEN}✓ Webpages directory is served by nginx${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}✗ Webpages directory not accessible (HTTP $http_code)${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

echo ""
echo "================================================"
echo "📊 Test Results"
echo "================================================"
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 All tests passed! Server is ready for use.${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Build and run the Android app in Android Studio"
    echo "  2. Send a message in the app"
    echo "  3. Click the webpage URL to verify it opens in browser"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Please check the errors above.${NC}"
    exit 1
fi
