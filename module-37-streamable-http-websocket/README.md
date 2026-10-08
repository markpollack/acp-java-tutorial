# Module 37: Streamable HTTP and WebSocket

Serve an agent over the network with `StreamableHttpAcpAgentTransport` (port 0 and
`getPort()`, one agent per connection from an `AcpAgentFactory` or
`AcpAgentSupport.Builder#buildFactory()`), and connect to it with
`StreamableHttpAcpClientTransport` and `WebSocketAcpClientTransport` at `/acp`, two
connections at once, over plain `http://` with h2c. No API key required.

## Documentation

Full tutorial: https://lab.pollack.ai/docs/acp-java-sdk/tutorial/37-streamable-http-websocket

## Running

```bash
./mvnw compile -pl module-37-streamable-http-websocket -q
./mvnw exec:java -pl module-37-streamable-http-websocket

# The listener on its own, on port 8080:
./mvnw exec:java -pl module-37-streamable-http-websocket \
    -Dexec.mainClass=com.acptutorial.module37.HttpAgent -Dexec.args=8080
```
