/*
 * Module 10: Cancellation Demo
 *
 * Demonstrates cancelling an in-progress operation.
 *
 * Key APIs:
 * - client.cancel(CancelNotification) - sends cancel notification to agent
 * - CancelNotification(sessionId) - identifies which session to cancel
 *
 * The demo:
 * 1. Starts a long-running prompt (agent counts 1-10, 500ms each)
 * 2. Runs prompt in background thread
 * 3. After 1.5 seconds, sends cancel
 * 4. Agent stops at current step
 *
 * Build & run:
 *   ./mvnw package -pl module-10-cancellation -q
 *   ./mvnw exec:java -pl module-10-cancellation
 */
package com.acptutorial.module10;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.CancelNotification;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class CancellationDemo {

    private static final String MODULE_NAME = "module-10-cancellation";
    private static final String JAR_NAME = "cancellable-agent.jar";

    public static void main(String[] args) {
        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg(findAgentJar())
            .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    if (update instanceof AgentMessageChunk msg) {
                        String text = ((TextContent) msg.content()).text();
                        System.out.print(text);
                        System.out.flush();
                    }
                })
                .build()) {

            System.out.println("=== Module 10: Cancellation Demo ===\n");

            // Initialize
            client.initialize();
            System.out.println("Connected to CancellableAgent\n");

            // Create session
            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            String sessionId = session.sessionId();
            System.out.println("Session: " + sessionId + "\n");

            // ---- Demo 1: Let operation complete ----
            System.out.println("--- Demo 1: Complete Operation ---");
            System.out.println("Starting short operation (will complete)...\n");

            // Use a modified agent message to show fewer steps for this demo
            var response1 = client.prompt(new PromptRequest(
                sessionId,
                List.of(new TextContent("Do a quick task"))));
            System.out.println("\nStop reason: " + response1.stopReason() + "\n");

            // ---- Demo 2: Cancel mid-operation ----
            System.out.println("--- Demo 2: Cancelled Operation ---");
            System.out.println("Starting long operation, will cancel after 1.5 seconds...\n");

            // Run prompt in background
            AtomicReference<PromptResponse> response2Ref = new AtomicReference<>();
            CompletableFuture<Void> promptFuture = CompletableFuture.runAsync(() -> {
                var response = client.prompt(new PromptRequest(
                    sessionId,
                    List.of(new TextContent("Do a long task"))));
                response2Ref.set(response);
            });

            // Wait 1.5 seconds then cancel
            Thread.sleep(1500);
            System.out.println("\n[Client] Sending cancel...");
            client.cancel(new CancelNotification(sessionId));

            // Wait for prompt to finish
            promptFuture.join();
            System.out.println("\nStop reason: " + response2Ref.get().stopReason());

            System.out.println("\n=== Demo Complete ===");
            System.out.println("Cancellation allows graceful interruption of long operations.");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
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
            "Agent JAR not found. Run: ./mvnw package -pl " + MODULE_NAME);
    }
}
