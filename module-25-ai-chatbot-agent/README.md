# Module 25: AI Chatbot Agent — Give Your Agent a Brain

Module 12's echo agent showed you the *shape* of an ACP agent. It echoed.
This module keeps that exact shape and gives it a **brain**: the `promptHandler`
calls Claude and streams a real answer back.

This is the first module in the tutorial where you can **point at the line of
Java that invokes the AI**:

```java
anthropic.messages().createStreaming(params.build())
```

In the client modules (01–08) the AI lived inside the Gemini subprocess; in the
other agent modules (12–22) the agent just echoed. Here the LLM call is right in
front of you — and it's an ordinary method call you can swap for any provider.

## The one-method diff from the echo agent

`initializeHandler` and `newSessionHandler` are **identical** to module 12.
Only the `promptHandler` changes:

| | Echo agent (module 12) | Chatbot agent (this module) |
|---|---|---|
| `promptHandler` | `context.sendMessage("Echo: " + text)` | call Claude, stream the answer back |

That's the whole point: *an ACP agent is just three handlers; the value is what
you put in the prompt handler.*

## Prerequisites

- **`ANTHROPIC_API_KEY`** set in your environment. Get one at
  <https://console.anthropic.com/>.
  ```bash
  export ANTHROPIC_API_KEY=sk-ant-...
  ```
  Unlike the Gemini client modules — where the key was checked but **never read**
  — this key is *actually used*: it's what `AnthropicOkHttpClient.fromEnv()`
  authenticates with.
- Java 17.

## Build & run

```bash
./mvnw package -pl module-25-ai-chatbot-agent -q
./mvnw exec:java -pl module-25-ai-chatbot-agent
```

The demo launches the agent as a subprocess, asks it to write a haiku about
debugging, then asks it to turn *that* haiku into a limerick — the follow-up only
works because the agent keeps per-session conversation history.

## The simplest version (non-streaming)

If you just want the answer in one shot, the prompt handler is five lines:

```java
Message msg = anthropic.messages().create(
    MessageCreateParams.builder()
        .model(Model.CLAUDE_SONNET_4_6)
        .maxTokens(1024)
        .addUserMessage(req.text())
        .build());
String answer = msg.content().stream()
        .filter(ContentBlock::isText).map(b -> b.asText().text())
        .collect(java.util.stream.Collectors.joining());
context.sendMessage(answer);
return PromptResponse.endTurn();
```

## Streaming (what this module does)

Streaming feels like a real assistant — tokens appear as they're generated.
Open a `createStreaming(...)` call and forward each text delta:

```java
try (StreamResponse<RawMessageStreamEvent> stream =
         anthropic.messages().createStreaming(params.build())) {
    stream.stream().forEach(event -> event.contentBlockDelta()
        .flatMap(d -> d.delta().text())   // Optional<TextDelta>
        .ifPresent(textDelta -> context.sendMessage(textDelta.text())));
}
```

The client coalesces consecutive `agent_message_chunk` updates into one growing
assistant message. (ACP 0.13+ adds `sendMessage(text, messageId)` for explicit
grouping; on 0.12 you stream chunk-by-chunk as above.)

## Conversation memory

A `ConcurrentHashMap<String, List<MessageParam>>` keyed by
`context.getSessionId()` stores each turn and replays it into the next request,
so follow-up prompts have context.

## Use it in your IDE

This agent is plugged into IntelliJ exactly like the echo agent
(see **module 29**) — point the ACP run configuration at `chatbot-agent.jar`
instead of `echo-agent.jar`, and you're chatting with Claude inside the IDE.

## Not locked to one provider

We use the Anthropic SDK directly here because it makes the AI call site
obvious. The same ACP agent works with any model — the prompt handler is the
only thing that changes:

- **Spring AI `ChatClient`** — multi-provider via a property
  (`spring.ai.<provider>.*`); pairs with the Spring Boot `@AcpAgent` starter.
- **LangChain4j** — swap the provider via the `ChatModel` / `StreamingChatModel`
  builder.

The ACP wire protocol never changes; only the line that talks to the model does.
