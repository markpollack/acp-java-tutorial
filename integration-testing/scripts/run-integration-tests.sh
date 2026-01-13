#!/bin/bash
#
# Run all integration tests for ACP Java Tutorial
#
# Usage:
#   ./scripts/run-integration-tests.sh              # Run all tests
#   ./scripts/run-integration-tests.sh --local      # Run only local agent tests (no API key needed)
#   ./scripts/run-integration-tests.sh --gemini     # Run only Gemini tests (requires GEMINI_API_KEY)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Track results
PASSED=0
FAILED=0
SKIPPED=0

# Parse arguments
RUN_LOCAL=true
RUN_GEMINI=true

if [ "$1" == "--local" ]; then
    RUN_GEMINI=false
    echo -e "${YELLOW}Running only local agent tests (no API key required)${NC}"
elif [ "$1" == "--gemini" ]; then
    RUN_LOCAL=false
    echo -e "${YELLOW}Running only Gemini tests (requires GEMINI_API_KEY)${NC}"
fi

echo "════════════════════════════════════════════════════════════"
echo "   ACP Java Tutorial - Integration Test Suite"
echo "════════════════════════════════════════════════════════════"
echo ""

# Check for GEMINI_API_KEY if running Gemini tests
if [ "$RUN_GEMINI" == "true" ] && [ -z "$GEMINI_API_KEY" ]; then
    echo -e "${YELLOW}⚠️  GEMINI_API_KEY not set - skipping Gemini tests${NC}"
    echo "   Set GEMINI_API_KEY to run modules 01-08"
    echo ""
    RUN_GEMINI=false
fi

# Local agent modules (no API key required)
LOCAL_MODULES=(
    "module-12-echo-agent"
    "module-13-agent-handlers"
    "module-14-sending-updates"
    "module-15-agent-requests"
    "module-16-in-memory-testing"
)

# Gemini modules (require GEMINI_API_KEY)
GEMINI_MODULES=(
    "module-01-first-contact"
    "module-02-protocol-basics"
    "module-03-sessions"
    "module-04-prompts"
    "module-05-streaming-updates"
    "module-06-update-types"
    "module-07-agent-requests"
    "module-08-permissions"
)

run_test() {
    local module=$1
    echo ""
    echo "────────────────────────────────────────────────────────────"
    echo "Running: $module"
    echo "────────────────────────────────────────────────────────────"

    if jbang RunIntegrationTest.java "$module"; then
        echo -e "${GREEN}✅ PASSED: $module${NC}"
        ((PASSED++))
    else
        echo -e "${RED}❌ FAILED: $module${NC}"
        ((FAILED++))
    fi
}

# Run local agent tests
if [ "$RUN_LOCAL" == "true" ]; then
    echo ""
    echo "🔧 Local Agent Tests (no API key required)"
    echo "----------------------------------------"
    for module in "${LOCAL_MODULES[@]}"; do
        if [ -f "configs/${module}.json" ]; then
            run_test "$module"
        else
            echo -e "${YELLOW}⚠️  Skipping $module (no config)${NC}"
            ((SKIPPED++))
        fi
    done
fi

# Run Gemini tests
if [ "$RUN_GEMINI" == "true" ]; then
    echo ""
    echo "🌐 Gemini Tests (requires GEMINI_API_KEY)"
    echo "----------------------------------------"
    for module in "${GEMINI_MODULES[@]}"; do
        if [ -f "configs/${module}.json" ]; then
            run_test "$module"
        else
            echo -e "${YELLOW}⚠️  Skipping $module (no config)${NC}"
            ((SKIPPED++))
        fi
    done
fi

# Summary
echo ""
echo "════════════════════════════════════════════════════════════"
echo "   Test Summary"
echo "════════════════════════════════════════════════════════════"
echo -e "   ${GREEN}Passed:${NC}  $PASSED"
echo -e "   ${RED}Failed:${NC}  $FAILED"
echo -e "   ${YELLOW}Skipped:${NC} $SKIPPED"
echo ""

if [ $FAILED -gt 0 ]; then
    echo -e "${RED}❌ Some tests failed${NC}"
    exit 1
else
    echo -e "${GREEN}✅ All tests passed!${NC}"
    exit 0
fi
