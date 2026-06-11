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

## Known issue (Spring Boot 4.1)

Under Spring Boot 4.1 with ACP SDK 0.14.0, the autoconfigured **client** fails at session
creation with `IllegalStateException: Sinks.many().unicast() sinks only allow a single Subscriber`
(`com.agentclientprotocol.sdk.spec.AcpClientSession`). This is a bug in the ACP Java SDK's client
session (a unicast Reactor sink subscribed more than once), surfaced by Boot 4.1's newer Reactor —
**not** in this module or the Spring Boot autoconfiguration. The **agent** side (Module 23) is
unaffected and works on Boot 4.1.

Tracked in the ACP Java SDK project: see `plans/inbox/2026-06-11-acpclientsession-unicast-boot41.md`
and `plans/ROADMAP.md` in the `acp-java` repo. Until it is fixed, run this module on Spring Boot 4.0.x.

## Documentation

See the [tutorial page](https://springaicommunity.mintlify.app/acp-java-sdk/tutorial/24-spring-boot-client) for the full walkthrough.
