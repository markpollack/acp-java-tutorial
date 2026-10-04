/*
 * Module 41: Quarkus - the demo client
 *
 * Build & run:
 *   ./mvnw -Psdk-candidate package -pl module-41-quarkus -q
 *   ./mvnw -Psdk-candidate exec:java -pl module-41-quarkus
 */
package com.acptutorial.module41;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.StreamableHttpAcpClientTransport;
import com.agentclientprotocol.sdk.client.transport.WebSocketAcpClientTransport;
import com.agentclientprotocol.sdk.json.AcpJsonMapper;
import com.agentclientprotocol.sdk.spec.AcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * Starts the packaged Quarkus application ({@code java -jar target/quarkus-app/quarkus-run.jar})
 * as a process on a free port, and talks to {@link StreamingAgent} over the Quarkus HTTP server.
 *
 * <ol>
 *   <li>A Streamable HTTP client at {@code http://localhost:<port>/acp} prints the
 *   {@code initialize} answer it receives: {@code agentInfo} and capabilities derived from the
 *   annotations. It asks for a stream ({@code Multi}), cancels it with {@code session/cancel}
 *   after the third chunk (stop reason {@code cancelled}), and asks the agent what it saw.</li>
 *   <li>A WebSocket client at {@code ws://localhost:<port>/acp}, the same path on the same
 *   server, sends one prompt. The CDI interceptor's call count goes on across the connections:
 *   one bean serves them all.</li>
 * </ol>
 *
 * <p>The clients here are plain SDK clients, so that they run without Quarkus. In a Quarkus
 * application, inject {@code AcpSyncClient} configured by {@code quarkus.acp.client.*}.
 *
 * <p>Why a packaged application and not {@code quarkus dev}: this module also shows the
 * production shape an editor launches. A stdio agent cannot run under {@code quarkus dev} at all
 * (dev mode owns the terminal's standard input): develop one in tests or over HTTP.
 */
public final class QuarkusDemo {

    private static final String MODULE_NAME = "module-41-quarkus";
    private static final String APP = "target/quarkus-app/quarkus-run.jar";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Module 41: Quarkus ===\n");
        Path app = findApp();
        int port = freePort();
        Path log = app.getParent().getParent().resolve("agent.log");
        Process agent = new ProcessBuilder(javaBin(), "-Dquarkus.http.port=" + port, "-jar", app.toString())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        try {
            System.out.println("Quarkus agent application started: " + awaitListening(port, agent));

            System.out.println("\n--- 1. Streamable HTTP on the Quarkus HTTP server ---");
            streamAndCancel(new StreamableHttpAcpClientTransport(URI.create("http://localhost:" + port + "/acp"),
                    AcpJsonMapper.createDefault()));

            System.out.println("\n--- 2. WebSocket, the same path on the same server ---");
            try (AcpSyncClient client = client(new WebSocketAcpClientTransport(
                    URI.create("ws://localhost:" + port + "/acp"), AcpJsonMapper.createDefault()), new AtomicReference<>())) {
                var init = client.initialize();
                System.out.println("   connected: " + init.agentInfo().name() + ", protocol version " + init.protocolVersion());
                String sessionId = newSession(client);
                prompt(client, sessionId, "hi over WebSocket");
            }
        }
        finally {
            agent.destroy();
            agent.waitFor(20, TimeUnit.SECONDS);
            System.out.println("\nQuarkus agent application stopped");
        }
        System.out.println("\n=== Demo Complete ===");
    }

    private static void streamAndCancel(AcpClientTransport transport) throws Exception {
        AtomicReference<Runnable> onThirdChunk = new AtomicReference<>();
        try (AcpSyncClient client = client(transport, onThirdChunk)) {
            printInitialize(client.initialize());
            String sessionId = newSession(client);
            prompt(client, sessionId, "hi over Streamable HTTP");

            System.out.println("   prompt 'count to ten': a Multi, one chunk every 300 ms; cancel after chunk 3");
            onThirdChunk.set(() -> new Thread(() -> {
                System.out.println("   client: session/cancel");
                client.cancel(new AcpSchema.CancelNotification(sessionId, null));
            }).start());
            prompt(client, sessionId, "count to ten");
            onThirdChunk.set(null);

            prompt(client, sessionId, "status");
            client.closeSession(new AcpSchema.CloseSessionRequest(sessionId));
            System.out.println("   session/close: OK");
        }
    }

    /** A client that prints thoughts and messages, and runs the given action on "chunk 3". */
    private static AcpSyncClient client(AcpClientTransport transport, AtomicReference<Runnable> onThirdChunk) {
        return AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    if (notification.update() instanceof AcpSchema.AgentThoughtChunk thought
                            && thought.content() instanceof AcpSchema.TextContent text) {
                        System.out.println("   thought: " + text.text());
                    }
                    else if (notification.update() instanceof AcpSchema.AgentMessageChunk msg
                            && msg.content() instanceof AcpSchema.TextContent text) {
                        System.out.println("   agent: " + text.text());
                        Runnable action = onThirdChunk.get();
                        if (action != null && "chunk 3".equals(text.text())) {
                            action.run();
                        }
                    }
                })
                .build();
    }

    private static String newSession(AcpSyncClient client) {
        return client.newSession(new AcpSchema.NewSessionRequest("/workspace", List.of())).sessionId();
    }

    private static void prompt(AcpSyncClient client, String sessionId, String text) {
        var response = client.prompt(new AcpSchema.PromptRequest(sessionId, List.of(new AcpSchema.TextContent(text))));
        System.out.println("   stop reason: " + response.stopReason());
    }

    /** What the client received for initialize: the JSON, then the fields the annotations produced. */
    private static void printInitialize(AcpSchema.InitializeResponse init) throws IOException {
        System.out.println("   initialize response the client received:");
        System.out.println("   " + AcpJsonMapper.createDefault().writeValueAsString(init));
        var info = init.agentInfo();
        System.out.println("   agentInfo (from @AcpAgent): " + info.name() + " " + info.version() + ", title '" + info.title() + "'");
        var caps = init.agentCapabilities();
        var session = caps.sessionCapabilities();
        System.out.println("   sessionCapabilities.close (from @CloseSession): " + (session != null && session.close() != null));
        System.out.println("   promptCapabilities.image (from @Prompt(image = true)): " + caps.promptCapabilities().image());
    }

    /** Waits until the application accepts connections on its port. */
    private static boolean awaitListening(int port, Process agent) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline && agent.isAlive()) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", port), 500);
                return true;
            }
            catch (IOException notYet) {
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException("The Quarkus application did not start; see target/agent.log");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static Path findApp() {
        Path fromModule = Path.of(APP);
        if (Files.exists(fromModule)) {
            return fromModule.toAbsolutePath();
        }
        Path fromRoot = Path.of(MODULE_NAME, APP);
        if (Files.exists(fromRoot)) {
            return fromRoot.toAbsolutePath();
        }
        throw new RuntimeException("Quarkus application not found. Run: ./mvnw -Psdk-candidate package -pl " + MODULE_NAME + " -q");
    }
}
