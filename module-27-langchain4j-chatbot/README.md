# Module 27: LangChain4j Chatbot Agent (portability flavor)

The [module-25 chatbot](../module-25-ai-chatbot-agent/), built with **LangChain4j** —
the other top Java AI framework. Same ACP agent, different way to reach the model.

## Three flavors, one agent

| Module | How it calls the model |
|--------|------------------------|
| [25](../module-25-ai-chatbot-agent/) | Raw Anthropic Java SDK (clearest call site) |
| [26](../module-26-spring-ai-chatbot/) | Spring AI `ChatClient` |
| **27 (this)** | **LangChain4j `ChatModel`** |

The ACP skeleton is identical to the [echo agent](../module-12-echo-agent/). The
only change from echo is the prompt handler:

```java
String answer = model.chat(req.text());   // model is a LangChain4j ChatModel
context.sendMessage(answer);
```

## Switch providers without touching the agent

`ChatModel` is LangChain4j's provider-agnostic interface. We build an
`AnthropicChatModel`; swap the dependency + builder to use OpenAI, Gemini, or a
local Ollama model — the ACP handler doesn't change:

```xml
<!-- this module -->
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-anthropic</artifactId>
</dependency>
<!-- swap for: langchain4j-open-ai / langchain4j-google-ai-gemini / langchain4j-ollama -->
```

## Prerequisites
- Java 17
- `ANTHROPIC_API_KEY` exported (used by `AnthropicChatModel`)

## Build & run
```bash
./mvnw package -pl module-27-langchain4j-chatbot -q
./mvnw exec:java -pl module-27-langchain4j-chatbot
```

> Note: `langchain4j-anthropic` is fetched from Maven Central on first build.

## Conversation memory
This module keeps it to a single turn for clarity. LangChain4j adds memory via
`MessageWindowChatMemory` (or an `AiServices` interface with `@MemoryId`) — see the
LangChain4j docs.
