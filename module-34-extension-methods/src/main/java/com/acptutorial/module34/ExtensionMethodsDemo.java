/*
 * Module 34: Extension Methods - the demo client
 *
 * Build & run:
 *   ./mvnw package -pl module-34-extension-methods -q
 *   ./mvnw exec:java -pl module-34-extension-methods
 */
package com.acptutorial.module34;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.acptutorial.module34.Extensions.LogEvent;
import com.acptutorial.module34.Extensions.Selection;
import com.acptutorial.module34.Extensions.SelectionQuery;
import com.acptutorial.module34.Extensions.WordCountParams;
import com.acptutorial.module34.Extensions.WordCountResult;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.json.TypeRef;
import com.agentclientprotocol.sdk.spec.AcpError;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * A client that calls an agent's extension methods and serves extension methods of its own.
 *
 * <p>The client side mirrors the agent side:
 * <ul>
 *   <li><b>Serving</b> (agent to client): {@code extRequestHandler(name, TypeRef, fn)} and
 *   {@code extNotificationHandler(name, fn)} on the client builder, typed or raw.</li>
 *   <li><b>Calling</b> (client to agent): {@code sendExtRequest(name, params, TypeRef)}
 *   reads the result as a type; {@code sendExtRequest(name, params)} returns the raw JSON
 *   value; {@code sendExtNotification(name, params)} sends and returns.</li>
 *   <li>A request nobody serves fails with {@code AcpError} code {@code -32601}; a name
 *   without the leading {@code _} is refused locally with
 *   {@code IllegalArgumentException}, before anything is sent.</li>
 * </ul>
 *
 * <p>It runs the same steps against the builder agent ({@link ExtensionAgent}) and the
 * annotated agent ({@link AnnotatedExtensionAgent}).
 *
 * <p>A notification has no answer, so the agent acknowledges {@code _acptutorial/log} with
 * an {@code _acptutorial/ack} notification of its own; the client waits for that with a
 * latch. The agent-to-client calls made <em>during</em> a prompt need no waiting: the
 * client handles them before it sees the prompt's response.
 */
public final class ExtensionMethodsDemo {

    private static final String MODULE_NAME = "module-34-extension-methods";
    private static final String JAR_NAME = "extension-agent.jar";

    private static final AtomicReference<CountDownLatch> ACKS = new AtomicReference<>();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Module 34: Extension Methods ===\n");
        String jar = findAgentJar();
        run("builder", AgentParameters.builder("java").arg("-jar").arg(jar).build());
        run("annotated", AgentParameters.builder("java").arg("-cp").arg(jar)
                .arg(AnnotatedExtensionAgent.class.getName()).build());
        System.out.println("=== Demo Complete ===");
    }

    private static void run(String variant, AgentParameters params) throws InterruptedException {
        System.out.println("--- " + variant + " agent ---");
        try (AcpSyncClient client = AcpClient.sync(new StdioAcpClientTransport(params))
                .sessionUpdateConsumer(ExtensionMethodsDemo::printUpdate)
                // Agent -> client, typed request: params read as SelectionQuery.
                .extRequestHandler(Extensions.SELECTION, new TypeRef<SelectionQuery>() {
                }, query -> {
                    System.out.println("  [client] agent asked " + Extensions.SELECTION + " for " + query.file());
                    return new Selection(query.file(), 12, 14, "int total = a + b;");
                })
                // Agent -> client, raw notifications: params arrive as a Map.
                .extNotificationHandler(Extensions.STATUS, status ->
                        System.out.println("  [client] " + Extensions.STATUS + " notification: " + status))
                .extNotificationHandler(Extensions.ACK, ack -> {
                    System.out.println("  [client] " + Extensions.ACK + " notification: " + ack);
                    ACKS.get().countDown();
                })
                .build()) {

            client.initialize();
            var session = client.newSession(new AcpSchema.NewSessionRequest(System.getProperty("user.dir"), List.of()));

            // 1. Typed request.
            WordCountResult count = client.sendExtRequest(Extensions.WORD_COUNT,
                    new WordCountParams("extension methods are just JSON-RPC"), new TypeRef<WordCountResult>() {
                    });
            System.out.println("1. typed request " + Extensions.WORD_COUNT + " -> " + count);

            // 2. Raw request: Map in, raw JSON value (here a Map) out.
            Object echoed = client.sendExtRequest(Extensions.ECHO, Map.of("numbers", List.of(1, 2, 3)));
            System.out.println("2. raw request " + Extensions.ECHO + " -> " + echoed);

            // 3. Notification: no answer; the agent acknowledges with a notification of its own.
            ACKS.set(new CountDownLatch(1));
            client.sendExtNotification(Extensions.LOG, new LogEvent("info", "client started"));
            System.out.println("3. sent notification " + Extensions.LOG);
            if (!ACKS.get().await(5, TimeUnit.SECONDS)) {
                System.out.println("  (no acknowledgement arrived)");
            }

            // 4. A method nobody serves.
            try {
                client.sendExtRequest(Extensions.NOT_SERVED, Map.of());
                System.out.println("4. unexpectedly answered");
            }
            catch (AcpError e) {
                System.out.println("4. " + Extensions.NOT_SERVED + " -> AcpError code " + e.getCode());
            }

            // 5. A name without the leading underscore never leaves the client.
            try {
                client.sendExtRequest("acptutorial/word_count", Map.of());
                System.out.println("5. unexpectedly sent");
            }
            catch (IllegalArgumentException e) {
                System.out.println("5. 'acptutorial/word_count' refused locally: IllegalArgumentException");
            }

            // 6. A prompt during which the agent calls the client's extension methods.
            System.out.println("6. prompt: the agent calls the client back");
            var response = client.prompt(new AcpSchema.PromptRequest(session.sessionId(),
                    List.of(new AcpSchema.TextContent("Review my selection"))));
            System.out.println("  stop reason: " + response.stopReason());
        }
        System.out.println();
    }

    private static void printUpdate(AcpSchema.SessionNotification notification) {
        if (notification.update() instanceof AcpSchema.AgentMessageChunk msg
                && msg.content() instanceof AcpSchema.TextContent text) {
            System.out.println("  agent: " + text.text());
        }
        else if (notification.update() instanceof AcpSchema.AgentThoughtChunk thought
                && thought.content() instanceof AcpSchema.TextContent text) {
            System.out.println("  thought: " + text.text());
        }
    }

    private static String findAgentJar() {
        Path fromModule = Path.of("target/" + JAR_NAME);
        if (Files.exists(fromModule)) {
            return fromModule.toString();
        }
        Path fromRoot = Path.of(MODULE_NAME + "/target/" + JAR_NAME);
        if (Files.exists(fromRoot)) {
            return fromRoot.toString();
        }
        throw new RuntimeException(
            "Agent JAR not found. Run: ./mvnw package -pl " + MODULE_NAME + " -q");
    }
}
