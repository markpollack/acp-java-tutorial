# Module 38: Spring Boot over HTTP

Serve a Spring Boot `@AcpAgent` over Streamable HTTP with the SDK's Spring Boot starter,
`com.agentclientprotocol:acp-spring-boot-starter` (`spring.acp.agent.transport.type=http`:
the SDK's Jetty listener in a non-web application, port 0 supported), and talk to it from a
Spring Boot client over Streamable HTTP (`spring.acp.client.transport.http.uri`) and WebSocket
(`spring.acp.client.transport.websocket.uri`). The client's `AcpClientCustomizer` registers a
session-update handler and a file handler, with the matching
`spring.acp.client.capabilities.read-text-file` property. No API key required.

**Requires Java 21+** (Spring Boot 4.x).

## Documentation

Full tutorial: https://lab.pollack.ai/docs/acp-java-sdk/tutorial/38-spring-boot-http

## Running

```bash
./mvnw -Psdk-candidate compile -pl module-38-spring-boot-http -q
./mvnw -Psdk-candidate exec:java -pl module-38-spring-boot-http

# Or each side on its own: the agent on port 8080, then the client against it
./mvnw -Psdk-candidate exec:java -pl module-38-spring-boot-http \
    -Dexec.mainClass=com.acptutorial.module38.agent.HttpAgentApplication
./mvnw -Psdk-candidate exec:java -pl module-38-spring-boot-http \
    -Dexec.mainClass=com.acptutorial.module38.client.HttpClientApplication \
    -Dexec.args=--spring.acp.client.transport.http.uri=http://localhost:8080/acp
```
