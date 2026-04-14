# Module 23: Spring Boot Agent

Build an ACP agent as a Spring Boot application using `@AcpAgent` annotations and autoconfiguration.

**Requires Java 21+** (Spring Boot 4.x).

## What You'll Learn

- Using `@AcpAgent`, `@Initialize`, `@NewSession`, `@Prompt` annotations
- Spring Boot autoconfiguration for transport and lifecycle management
- Redirecting logging to stderr for stdio agents

## Key Comparison with Module 12

| Module 12 (Builder) | Module 23 (Spring Boot) |
|---------------------|------------------------|
| Manual `StdioAcpAgentTransport` | Autoconfigured |
| Builder lambda handlers | Annotated methods |
| Explicit `agent.run()` | `SmartLifecycle` |

## Build & Run

```bash
# Package the agent
./mvnw package -pl module-23-spring-boot-agent -q

# Run the demo
./mvnw exec:java -pl module-23-spring-boot-agent
```

## Documentation

See the [tutorial page](https://springaicommunity.mintlify.app/acp-java-sdk/tutorial/23-spring-boot-agent) for the full walkthrough.
