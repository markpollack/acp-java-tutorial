# Module 24: Spring Boot Client

Use the autoconfigured ACP client in a Spring Boot application.

**Requires Java 21+** (Spring Boot 4.x).

## What You'll Learn

- Injecting `AcpSyncClient` from autoconfiguration
- Configuring transport via `application.properties`
- Property-driven transport selection (stdio vs WebSocket)

## Build & Run

Requires Module 23's agent JAR:

```bash
# Build agent and client
./mvnw package -pl module-23-spring-boot-agent,module-24-spring-boot-client -q

# Run the client (from repo root)
./mvnw spring-boot:run -pl module-24-spring-boot-client
```

## Documentation

See the [tutorial page](https://springaicommunity.mintlify.app/acp-java-sdk/tutorial/24-spring-boot-client) for the full walkthrough.
