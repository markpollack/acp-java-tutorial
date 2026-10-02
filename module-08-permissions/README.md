# Module 08: Permissions

Handle permission requests from agents on the client side.

## Documentation

Full tutorial: https://lab.pollack.ai/docs/acp-java-sdk/tutorial/08-permissions

## Running

Requires the Grok CLI on your `PATH`, signed in once with `grok login`.
The module launches it as `grok agent stdio`; no API key is needed.

```bash
./mvnw exec:java -pl module-08-permissions
```

The prompt asks Grok to create `hello.txt`. In Grok's default (ask) mode it sends
`session/request_permission` before the write, and the client prints the options and
reads your choice. Grok also reads Claude Code's settings: if `~/.claude/settings.json`
sets `"defaultMode"` to `"auto"` or `"acceptEdits"`, Grok writes the file without asking
and no permission request appears.
