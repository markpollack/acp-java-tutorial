# Module 40: Micronaut

Make an annotated ACP agent a Micronaut bean with `com.agentclientprotocol:acp-micronaut`:
`@Singleton` plus `@AcpAgent`, found from its compile-time bean definition, configured under
`acp.*`. The same application runs as a stdio agent (launched by a Micronaut client from
`acp.client.transport.stdio.*`) and over Streamable HTTP and WebSocket on the SDK's listener
port (`acp.agent.transport.type=http`). The client prints the `initialize` answer it receives,
whose `agentInfo` and capabilities are derived from the annotations; a Micronaut `@Around`
interceptor runs around the prompt handler, which returns a Reactive Streams `Publisher`.
No API key required.

## Documentation

Full tutorial: https://lab.pollack.ai/docs/acp-java-sdk/tutorial/40-micronaut

## Running

```bash
./mvnw package -pl module-40-micronaut -q
./mvnw exec:java -pl module-40-micronaut

# The agent application on its own, over stdio or on port 8080:
java -jar module-40-micronaut/target/micronaut-agent.jar
java -jar module-40-micronaut/target/micronaut-agent.jar --acp.agent.transport.type=http
```
