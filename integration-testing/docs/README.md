# ACP Java Tutorial - Integration Testing

Automated integration testing using JBang + AI validation.

## Prerequisites

1. **JBang** - Install from https://www.jbang.dev/
   ```bash
   curl -Ls https://sh.jbang.dev | bash -s - app setup
   ```

2. **Claude Code SDK** - Must be installed in local Maven repo
   ```bash
   cd ~/path/to/claude-code-sdk-java
   ./mvnw install -DskipTests
   ```

3. **Claude CLI** - Required for AI validation
   ```bash
   npm install -g @anthropic-ai/claude-code
   ```

4. **GEMINI_API_KEY** (optional) - Required for modules 01-08
   ```bash
   export GEMINI_API_KEY=your-key-here
   ```

## Quick Start

```bash
cd integration-testing

# Run a single test
jbang RunIntegrationTest.java module-12-echo-agent

# Run all local tests (no API key needed)
./scripts/run-integration-tests.sh --local

# Run all tests (requires GEMINI_API_KEY)
./scripts/run-integration-tests.sh

# List available modules
jbang RunIntegrationTest.java --list
```

## Module Categories

### Local Agent Modules (No API Key Required)

| Module | Description |
|--------|-------------|
| module-12-echo-agent | Minimal echo agent |
| module-13-agent-handlers | All handler types |
| module-14-sending-updates | Agent sends all update types |
| module-15-agent-requests | Agent requests files/permissions |
| module-16-in-memory-testing | In-memory transport testing |

### Gemini Modules (Require GEMINI_API_KEY)

| Module | Description |
|--------|-------------|
| module-01-first-contact | Basic connection and prompt |
| module-02-protocol-basics | Initialize handshake |
| module-03-sessions | Session lifecycle |
| module-04-prompts | Prompt/response and stop reasons |
| module-05-streaming-updates | Real-time updates |
| module-06-update-types | All SessionUpdate types |
| module-07-agent-requests | Client file handlers |
| module-08-permissions | Permission handling |

## How It Works

1. **Load Config** - Read JSON config from `configs/<module>.json`
2. **Verify Environment** - Check required environment variables
3. **Build Module** - Run `mvn compile` or `mvn package`
4. **Run Module** - Execute via `mvn exec:java`
5. **AI Validation** - Use Claude to validate output against expected behavior

## Configuration Schema

```json
{
  "moduleId": "module-XX-name",
  "displayName": "Module XX: Title",
  "timeoutSec": 120,
  "requiredEnv": ["GEMINI_API_KEY"],
  "requiresPackage": false,
  "expectedBehavior": "Description of what the module should demonstrate..."
}
```

- `moduleId` - Maven module name
- `displayName` - Human-readable name
- `timeoutSec` - Max execution time
- `requiredEnv` - Required environment variables
- `requiresPackage` - If true, runs `mvn package` instead of `mvn compile`
- `expectedBehavior` - Description for AI validation

## AI Validation

Uses Claude (via claude-code-sdk) to semantically validate test output:

- No brittle regex patterns
- Understands expected behavior descriptions
- Returns structured result with confidence score
- Cost: ~$0.001 per validation using Claude Haiku

## Logs

Test output is saved to `logs/<module>-<timestamp>.log`

## Troubleshooting

### "Config not found"
Make sure you're in the `integration-testing` directory.

### "GEMINI_API_KEY not set"
Export the environment variable or use `--local` flag to skip Gemini tests.

### "Build failed"
Run `./mvnw compile -pl module-XX-name` from repo root to see build errors.

### "AI validation failed"
Check that Claude CLI is installed and working: `claude --version`
