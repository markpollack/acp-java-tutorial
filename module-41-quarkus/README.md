# Module 41: Quarkus

Serve an annotated ACP agent with the Quarkus extension, `com.agentclientprotocol:acp-quarkus`:
`@AcpAgent` alone makes the class a CDI bean, found at build time, configured under
`quarkus.acp.*`. With `quarkus.acp.agent.transport.type=http` it is served over Streamable HTTP
and WebSocket at `/acp` on the Quarkus HTTP server itself (`quarkus.http.port`). Handlers return
Mutiny types: a `Uni` from `@NewSession`, and a `Multi` from `@Prompt` that streams message
chunks and is cancelled by `session/cancel` (stop reason `cancelled`). A CDI interceptor binding
runs around the prompt handler, and the client prints the derived `initialize` answer. JVM mode
only. No API key required.

A stdio agent (the default transport) cannot run under `quarkus dev`, which owns the terminal's
standard input: develop it in tests or over HTTP, as here.

## Documentation

Full tutorial: https://lab.pollack.ai/docs/acp-java-sdk/tutorial/41-quarkus

## Running

```bash
./mvnw -Psdk-candidate package -pl module-41-quarkus -q
./mvnw -Psdk-candidate exec:java -pl module-41-quarkus

# The Quarkus application on its own, on port 8080:
java -jar module-41-quarkus/target/quarkus-app/quarkus-run.jar
```
