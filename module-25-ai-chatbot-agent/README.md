# Module 25: AI Chatbot Agent — Give Your Agent a Brain (and switch to annotations)

Module 12's echo agent showed you the *shape* of an ACP agent with the fluent
builder (`AcpAgent.sync().promptHandler(...)`). This module gives it a **brain** —
and that's exactly the moment to switch to the **`@AcpAgent` annotation API**.
Once the prompt handler does real work (call an LLM, stream chunks, keep
history), a labelled `@Prompt` *method* reads far better than a builder lambda:
the ACP plumbing disappears and what's left is one method you can point at.

This is the first module where you can **point at the line of Java that invokes
the AI** — now inside a clean `@Prompt` method:

```java
anthropic.messages().createStreaming(params.build())
```

## Two ways to run an annotated agent

The annotations (`@Initialize` / `@NewSession` / `@Prompt`) are identical whether
or not you use Spring:

- **Module 23** lets **Spring Boot** discover the `@AcpAgent` bean and manage its
  lifecycle for you.
- **This module** bootstraps it **by hand, no Spring** — so you can see exactly
  what the annotations buy you. `AcpAgentSupport` scans the instance and wires
  each annotated method to the transport:

  ```java
  AcpAgentSupport.create(new ChatbotAgent())   // scans @Initialize/@NewSession/@Prompt
      .transport(new StdioAcpAgentTransport())
      .build()
      .run();                                  // start, then block until the client leaves
  ```

## The one-method diff from the echo agent

`@Initialize` and `@NewSession` are **identical boilerplate** to module 12's
handlers. Only `@Prompt` does anything interesting:

| | Echo agent (module 12, fluent) | Chatbot agent (this module, annotated) |
|---|---|---|
| prompt | `context.sendMessage("Echo: " + text)` | call Claude, stream the answer back |

That's the whole point: *an ACP agent is just three handlers — fluent lambdas or
annotated methods — and the value is what you put in the prompt handler.*

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
