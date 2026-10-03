/*
 * Module 37: Streamable HTTP and WebSocket - the demo
 *
 * Build & run:
 *   ./mvnw compile -pl module-37-streamable-http-websocket -q
 *   ./mvnw exec:java -pl module-37-streamable-http-websocket
 */
package com.acptutorial.module37;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.agentclientprotocol.sdk.agent.transport.StreamableHttpAcpAgentTransport;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.StreamableHttpAcpClientTransport;
import com.agentclientprotocol.sdk.client.transport.WebSocketAcpClientTransport;
import com.agentclientprotocol.sdk.json.AcpJsonMapper;
import com.agentclientprotocol.sdk.spec.AcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * Runs a remote agent and talks to it over both network transports at once.
 *
 * <ol>
 *   <li>Starts {@link HttpAgent} on port 0 and reads the bound port with
 *   {@code getPort()}.</li>
 *   <li>Shows that plain {@code http://} speaks HTTP/2 (h2c): the same bodiless GET the
 *   Streamable HTTP client sends first, so that later requests reuse an upgraded HTTP/2
 *   connection. A server that does not upgrade makes the client pin HTTP/1.1; either
 *   works.</li>
 *   <li>Connects two clients <b>concurrently</b>: one over
 *   {@link StreamableHttpAcpClientTransport} ({@code http://host:port/acp}), one over
 *   {@link WebSocketAcpClientTransport} ({@code ws://host:port/acp}). Each connection gets
 *   its own agent from the factory, so they report different connection numbers. The HTTP
 *   client opens a second ACP session on the same connection: same agent, its own session
 *   stream.</li>
 *   <li>Serves an annotated agent through {@code AcpAgentSupport.Builder#buildFactory()} on a
 *   second listener: one bean behind every connection.</li>
 * </ol>
 *
 * <p>Client side, only the transport changes: {@code AcpClient.sync(transport)} and the
 * rest of the client code are the same as over stdio. Close clients, then the listener
 * ({@code closeGracefully()} waits up to {@code shutdownTimeout}, 5 s by default, for its
 * connections).
 */
public final class HttpTransportsDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Module 37: Streamable HTTP and WebSocket ===\n");

        StreamableHttpAcpAgentTransport server = HttpAgent.start(0);
        int port = server.getPort();
        System.out.println("1. agent listening on port 0 -> bound port from getPort(): " + (port > 0));

        System.out.println("2. plain http:// negotiates: " + probeHttpVersion(URI.create("http://localhost:" + port + "/acp")));

        System.out.println("3. two clients at once, one per transport");
        Map<String, List<String>> transcripts = new TreeMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<String>> http = pool.submit(() -> converse("streamable-http",
                    new StreamableHttpAcpClientTransport(URI.create("http://localhost:" + port + "/acp"),
                            AcpJsonMapper.createDefault()), 2));
            Future<List<String>> ws = pool.submit(() -> converse("websocket",
                    new WebSocketAcpClientTransport(URI.create("ws://localhost:" + port + "/acp"),
                            AcpJsonMapper.createDefault()), 1));
            transcripts.put("streamable-http", http.get());
            transcripts.put("websocket", ws.get());
        }
        finally {
            pool.shutdown();
        }
        transcripts.forEach((name, lines) -> lines.forEach(line -> System.out.println("   [" + name + "] " + line)));
        String httpAgent = connectionOf(transcripts.get("streamable-http").get(0));
        String wsAgent = connectionOf(transcripts.get("websocket").get(0));
        System.out.println("   one agent per connection (different connection numbers): " + !httpAgent.equals(wsAgent));
        System.out.println("   both HTTP sessions served by the same agent: "
                + httpAgent.equals(connectionOf(transcripts.get("streamable-http").get(1))));

        server.closeGracefully().block(Duration.ofSeconds(10));
        System.out.println("   listener closed");

        System.out.println("4. an annotated agent behind a listener (buildFactory)");
        var annotated = new StreamableHttpAcpAgentTransport(0, AcpJsonMapper.createDefault(),
                AnnotatedHttpAgent.support().buildFactory());
        annotated.start().block();
        int annotatedPort = annotated.getPort();
        for (String name : List.of("first", "second")) {
            List<String> lines = converse(name, new StreamableHttpAcpClientTransport(
                    URI.create("http://localhost:" + annotatedPort + "/acp"), AcpJsonMapper.createDefault()), 1);
            lines.forEach(line -> System.out.println("   [" + name + " connection] " + line));
        }
        annotated.closeGracefully().block(Duration.ofSeconds(10));
        System.out.println("   listener closed");

        System.out.println("\n=== Demo Complete ===");
    }

    /** One connection: initialize, open {@code sessions} sessions, one prompt each. Returns the agent's answers. */
    private static List<String> converse(String name, AcpClientTransport transport, int sessions) {
        List<String> answers = new CopyOnWriteArrayList<>();
        Map<String, String> bySession = new ConcurrentHashMap<>();
        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(n -> {
                    if (n.update() instanceof AcpSchema.AgentMessageChunk msg
                            && msg.content() instanceof AcpSchema.TextContent text) {
                        bySession.put(n.sessionId(), text.text());
                    }
                })
                .build()) {
            client.initialize();
            for (int i = 1; i <= sessions; i++) {
                String sid = client.newSession(new AcpSchema.NewSessionRequest("/", List.of())).sessionId();
                var response = client.prompt(new AcpSchema.PromptRequest(sid,
                        List.of(new AcpSchema.TextContent("hello from " + name + " session " + i))));
                // Updates are handled before prompt() returns, so the answer is already here.
                answers.add(bySession.get(sid) + " (" + response.stopReason() + ")");
            }
        }
        return answers;
    }

    private static String connectionOf(String answer) {
        int hash = answer.indexOf('#');
        return hash < 0 ? answer : answer.substring(hash, answer.indexOf(' ', hash));
    }

    /** The bodiless GET the Streamable HTTP client sends to upgrade a cleartext connection to h2c. */
    private static HttpClient.Version probeHttpVersion(URI endpoint) throws Exception {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        HttpResponse<Void> response = http.send(HttpRequest.newBuilder(endpoint).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        return response.version();
    }
}
