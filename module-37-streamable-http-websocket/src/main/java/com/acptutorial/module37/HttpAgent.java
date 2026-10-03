/*
 * Module 37: Streamable HTTP and WebSocket - the remote agent
 *
 * Run the listener on its own (Ctrl+C to stop):
 *   ./mvnw compile exec:java -pl module-37-streamable-http-websocket \
 *       -Dexec.mainClass=com.acptutorial.module37.HttpAgent -Dexec.args=8080
 */
package com.acptutorial.module37;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpAgentFactory;
import com.agentclientprotocol.sdk.agent.transport.StreamableHttpAcpAgentTransport;
import com.agentclientprotocol.sdk.json.AcpJsonMapper;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * An agent served over the network instead of stdio.
 *
 * <p>Over stdio one process is one connection is one agent. A network listener accepts
 * many connections, so it takes an {@link AcpAgentFactory} instead of an agent, and builds a
 * <b>fresh agent for every connection</b>, bound to that connection's own transport:
 *
 * <pre>{@code
 * AcpAgentFactory factory = AcpAgentFactory.sync(transport -> AcpAgent.sync(transport)...build());
 * var server = new StreamableHttpAcpAgentTransport(0, AcpJsonMapper.createDefault(), factory);
 * server.start().block();
 * int port = server.getPort();
 * }</pre>
 *
 * <p>{@link StreamableHttpAcpAgentTransport} (module {@code acp-streamable-http-jetty})
 * runs an embedded Jetty listener on {@code /acp} that speaks:
 * <ul>
 *   <li><b>Streamable HTTP</b>: requests are POSTs; the agent's messages come back on SSE
 *   streams, one for the connection and one per ACP session ({@code Acp-Connection-Id} and
 *   {@code Acp-Session-Id} headers route them). HTTP/1.1, and HTTP/2 over plain
 *   {@code http://} (h2c).</li>
 *   <li><b>WebSocket</b>: an upgrade on the same path, one socket per connection.</li>
 * </ul>
 * Port {@code 0} picks a free port; {@link StreamableHttpAcpAgentTransport#getPort()} returns
 * it after {@code start()}. The listener is not itself an {@code AcpAgentTransport}: each
 * connection gets its own, inside the SDK.
 *
 * <p>Limits of the built-in listener in 0.80.0: plain connector only (terminate TLS in front
 * of it, or mount {@code StreamableHttpAcpServlet} in your own container, which serves
 * HTTP/SSE but not the WebSocket upgrade). Module 38 shows the servlet-free Spring Boot
 * setup.
 *
 * <p>The connection counter shows the per-connection agents: each one remembers its number.
 */
public final class HttpAgent {

    private static final AtomicInteger CONNECTIONS = new AtomicInteger();

    /** One sync agent per connection. The lambda runs once for every new connection. */
    static AcpAgentFactory factory() {
        return AcpAgentFactory.sync(transport -> {
            int connection = CONNECTIONS.incrementAndGet();
            return AcpAgent.sync(transport)
                    .initializeHandler(req -> AcpSchema.InitializeResponse.ok())
                    .newSessionHandler(req -> new AcpSchema.NewSessionResponse(UUID.randomUUID().toString(), null, null))
                    .promptHandler((req, ctx) -> {
                        ctx.sendMessage("agent for connection #" + connection + " echoes: " + req.text());
                        return AcpSchema.PromptResponse.endTurn();
                    })
                    .build();
        });
    }

    /** Starts the listener; port 0 picks a free one. */
    static StreamableHttpAcpAgentTransport start(int port) {
        var server = new StreamableHttpAcpAgentTransport(port, AcpJsonMapper.createDefault(), factory());
        server.start().block();
        return server;
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        var server = start(port);
        System.out.println("ACP agent listening on http://localhost:" + server.getPort() + "/acp"
                + " (Streamable HTTP, h2c, WebSocket)");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.closeGracefully().block()));
        server.awaitTermination().block();
    }
}
