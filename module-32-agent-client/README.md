# Module 32: Agent-Client Agent — From Chatbot to *Agent*

The chatbot (module 25) answers from the model's head: one completion call. This
module gives the agent **hands**. Its `@Prompt` handler hands the user's goal to an
agent *loop* that reads and edits files and runs tools in the open project — for as
many turns as the goal needs — then reports back.

The ACP skeleton is **identical** to the chatbot (`@Initialize` / `@NewSession` /
`@Prompt`, same as module 25). The only change is the body of `@Prompt`:

```java
AgentClient.create(model)
    .goal(request.text())
    .workingDirectory(cwd)
    .run();
```

That's [`agent-client`](https://github.com/spring-ai-community) — the `AgentClient`
abstraction. `agent-claude` drives the local **Claude CLI** as the underlying model.

## Where it fits in the tutorial

```
echo (12) → completion chatbot (25/26/27) → AGENTIC (this, 32) → bud (final boss)
```

The chatbot calls the model once. **bud** is a full domain buddy (knowledge base,
curated review workflow, provenanced report). This module is the honest middle
step: a real agent loop wired into one ACP handler — and nothing more.

> **This is not bud.** No knowledge base, no curated report, no multi-step named
> workflow, no judge. Those are what make bud *bud*. This is just `agent-client`
> in a `@Prompt` method — the smallest example of "an ACP agent that is an *agent*,
> not a chatbot."

## Chatbot vs. agent — the one-method diff

| | Chatbot (module 25) | Agent (this module) |
|---|---|---|
| `@Prompt` | `anthropic.messages()…` — one completion | `AgentClient…goal(text).run()` — a tool-using loop |
| can it read your files? | no (answers from training) | **yes** — it reads and edits files in the cwd |
| backend | Anthropic HTTP API | the local `claude` CLI (Claude Code) |

The line to land: *"The chatbot answers from the model's head. This one has hands —
it reads your files and edits them. Same three ACP handlers; that's the jump from
chatbot to agent."*

## Prerequisites (different from the chatbot!)

- **The Claude CLI (Claude Code) installed and logged in.** `agent-claude` drives
  that CLI — it does **not** call the Anthropic HTTP API directly. Run `claude`
  once to confirm it opens without asking for a key.
- **Billing / no API key:** the CLI uses your Claude **subscription** when
  `ANTHROPIC_API_KEY` is *absent*, and the API when it's present. So — unlike the
  chatbot — run this agent **without** `ANTHROPIC_API_KEY` in its environment. In
  the IDE, launch it via the `acp-no-key.sh` wrapper (see below), not the
  key-injecting one.
- Java 17.

## Build & run

```bash
./mvnw package -pl module-32-agent-client -q
./mvnw exec:java -pl module-32-agent-client
```

The demo launches the agent in a throwaway working directory and gives it an
*agentic* goal — "create a `haiku.txt` file …" — then reads the file back from disk
to prove the agent actually wrote it (a chatbot could only describe one).

## Use it in your IDE

Plugged into IntelliJ like the other agents (see **module 29**), but with the
no-key wrapper so the underlying `claude` CLI uses your subscription:

```json
"Agent-Client": {
  "command": "/path/to/acp-no-key.sh",
  "args": ["java", "-jar", "/abs/path/to/module-32-agent-client/target/agent-client-agent.jar"]
}
```

where `acp-no-key.sh` simply unsets `ANTHROPIC_API_KEY` and execs its arguments.
It reviews / edits whatever project the IDE window has open, and — like bud — the
run is **agentic and slow** (multiple tool-using turns), not instant like the
chatbots.

## Swap the provider

`agent-client` isn't Claude-only: `agent-claude` has siblings (`agent-gemini`,
`agent-codex`, …). Build a different `AgentModel` and the rest of the handler is
unchanged — the agent loop, and the ACP wire protocol, stay the same.
