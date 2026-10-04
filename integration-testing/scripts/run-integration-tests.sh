#!/bin/bash
#
# Run all integration tests for ACP Java Tutorial
#
# Usage:
#   ./scripts/run-integration-tests.sh              # Run all tests
#   ./scripts/run-integration-tests.sh --local      # Run only local agent tests (no API key needed)
#   ./scripts/run-integration-tests.sh --grok       # Run only Grok client tests (requires the grok CLI)
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
RUN_GROK=true

if [ "$1" == "--local" ]; then
    RUN_GROK=false
    echo -e "${YELLOW}Running only local agent tests (no API key required)${NC}"
elif [ "$1" == "--grok" ]; then
    RUN_LOCAL=false
    echo -e "${YELLOW}Running only Grok client tests (requires the grok CLI)${NC}"
fi

echo "════════════════════════════════════════════════════════════"
echo "   ACP Java Tutorial - Integration Test Suite"
echo "════════════════════════════════════════════════════════════"
echo ""

# Check for the grok CLI if running Grok client tests
if [ "$RUN_GROK" == "true" ] && ! command -v grok >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  grok CLI not on PATH - skipping Grok client tests${NC}"
    echo "   Install it (curl -fsSL https://x.ai/cli/install.sh | bash) and run 'grok login' to run modules 01-08 and 21"
    echo ""
    RUN_GROK=false
fi

# Local agent modules (no API key required)
LOCAL_MODULES=(
    "module-12-echo-agent"
    "module-13-agent-handlers"
    "module-14-sending-updates"
    "module-15-agent-requests"
    "module-16-in-memory-testing"
    "module-20-session-management"
    "module-31-elicitation"
    "module-23-spring-boot-agent"
    "module-24-spring-boot-client"
)

# SDK 0.80.0 feature modules: local agents, no API key, but they build only against the
# candidate SDK, so they run only when ACP_SDK_CANDIDATE is set (it activates the sdk-candidate profile)
if [ -n "${ACP_SDK_CANDIDATE:-}" ]; then
    LOCAL_MODULES+=(
        "module-33-session-config-options"
        "module-34-extension-methods"
        "module-35-cancellation-timeouts"
        "module-36-terminal-auth-logout"
        "module-37-streamable-http-websocket"
        "module-38-spring-boot-http"
        "module-39-forward-compatibility"
        "module-40-micronaut"
    )
fi

# Grok client modules (launch `grok agent stdio`; require the grok CLI, signed in)
GROK_MODULES=(
    "module-01-first-contact"
    "module-02-protocol-basics"
    "module-03-sessions"
    "module-04-prompts"
    "module-05-streaming-updates"
    "module-06-update-types"
    "module-07-agent-requests"
    "module-08-permissions"
    "module-21-async-client"
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

# Run Grok client tests
if [ "$RUN_GROK" == "true" ]; then
    echo ""
    echo "🌐 Grok Client Tests (requires the grok CLI)"
    echo "----------------------------------------"
    for module in "${GROK_MODULES[@]}"; do
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
