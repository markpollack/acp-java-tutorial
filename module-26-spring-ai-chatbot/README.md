# Module 26: Spring AI Chatbot Agent (portability flavor)

The [module-25 chatbot](../module-25-ai-chatbot-agent/), rebuilt as a **Spring Boot
`@AcpAgent`** that talks to the model through **Spring AI's `ChatClient`**.

It exists to answer one question: *"Am I locked to one model provider?"* No. The
ACP agent never changes — the model is whatever Spring AI autoconfigures from the
`spring-ai-starter-model-*` dependency on the classpath.

## Three flavors, one agent

| Module | How it calls the model |
|--------|------------------------|
| [25](../module-25-ai-chatbot-agent/) | Raw Anthropic Java SDK (clearest call site) |
| **26 (this)** | **Spring AI `ChatClient` — multi-provider** |
| [27](../module-27-langchain4j-chatbot/) | LangChain4j `ChatModel` |

The ACP handlers (`@NewSession` / `@Prompt`) are identical to the
[module-23 echo bean](../module-23-spring-boot-agent/). The only difference from
echo is the body of `@Prompt`:

```java
String answer = chatClient.prompt().user(request.text()).call().content();
context.sendMessage(answer);
```

## Switch providers without touching code

Spring AI's `ChatClient` sits over a `ChatModel` that's autoconfigured from the
starter on the classpath. To use OpenAI, Gemini, or a local Ollama model instead
of Anthropic, swap the dependency and the `spring.ai.<provider>.*` properties —
`ChatbotAgentBean` does not change:

```xml
<!-- this module -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
<!-- swap for: spring-ai-starter-model-openai / -ollama / ... -->
```

## Built on the ACP Spring Boot starter

This module depends on the ACP Spring Boot starter (the same autoconfiguration as module
23). From SDK **0.80.0** the starter is part of the SDK,
`com.agentclientprotocol:acp-spring-boot-starter` at the SDK's version; through SDK **0.18.0**
it was `org.springaicommunity:acp-spring-boot-starter` **0.12.0**. Autoconfiguration discovers the
`@AcpAgent` bean and manages the agent lifecycle.

## Prerequisites
- Java 21 (Spring Boot 4)
- `ANTHROPIC_API_KEY` exported (read via `spring.ai.anthropic.api-key`)

## Build & run
```bash
./mvnw package -pl module-26-spring-ai-chatbot -q
./mvnw exec:java -pl module-26-spring-ai-chatbot
```

## Streaming
`ChatClient` also streams — `.stream().content()` returns a `Flux<String>`:

```java
chatClient.prompt().user(request.text()).stream().content()
    .toStream().forEach(context::sendMessage);
```
