/*
 * Module 16: In-Memory Testing
 *
 * Demonstrates unit testing ACP agents without real processes or I/O.
 *
 * Key concepts:
 * - InMemoryTransportPair - bidirectional in-memory transport
 * - Testing client and agent together
 * - Fast, deterministic tests
 * - No external dependencies
 *
 * The SDK provides InMemoryTransportPair in test scope. This module
 * demonstrates the pattern and provides a working example.
 *
 * Build & run:
 *   ./mvnw test -pl module-16-in-memory-testing
 *
 * Or run the demo:
 *   ./mvnw exec:java -pl module-16-in-memory-testing
 */
package com.acptutorial.module16;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpAsyncAgent;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

import reactor.core.publisher.Mono;

public class InMemoryTestingDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Module 16: In-Memory Testing Demo ===\n");

        System.out.println("This module demonstrates testing ACP without real processes.\n");
        System.out.println("Key benefits:");
        System.out.println("  - No subprocess launching (fast)");
        System.out.println("  - No I/O (deterministic)");
        System.out.println("  - Test client and agent together");
        System.out.println("  - Perfect for unit tests\n");

        // Create in-memory transport pair
        System.out.println("Creating InMemoryTransportPair...");
        var transportPair = SimpleInMemoryTransportPair.create();
        System.out.println("  Client transport: " + transportPair.clientTransport().getClass().getSimpleName());
        System.out.println("  Agent transport: " + transportPair.agentTransport().getClass().getSimpleName());
        System.out.println();

        // Create agent
        AtomicReference<String> receivedPrompt = new AtomicReference<>();
        CountDownLatch agentReady = new CountDownLatch(1);

        AcpAsyncAgent agent = AcpAgent.async(transportPair.agentTransport())
            .initializeHandler(req ->
                Mono.just(new InitializeResponse(1, new AgentCapabilities(), List.of())))
            .newSessionHandler(req ->
                Mono.just(new NewSessionResponse(UUID.randomUUID().toString(), null, null)))
            .promptHandler((req, context) -> {
                // Capture the prompt for verification
                String text = req.prompt().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse("");
                receivedPrompt.set(text);

                // Send response
                return context.sendUpdate(req.sessionId(),
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Echo: " + text)))
                    .then(Mono.just(new PromptResponse(StopReason.END_TURN)));
            })
            .build();

        // Start agent in background
        System.out.println("Starting agent...");
        agent.start().subscribe(
            v -> {},
            e -> System.err.println("Agent error: " + e),
            () -> agentReady.countDown()
        );

        // Give agent time to start
        Thread.sleep(100);

        // Create client
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        try (AcpSyncClient client = AcpClient.sync(transportPair.clientTransport())
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    if (update instanceof AgentMessageChunk msg) {
                        receivedMessage.set(((TextContent) msg.content()).text());
                    }
                })
                .build()) {

            System.out.println("Client connecting...\n");

            // Run test scenario
            client.initialize();
            System.out.println("1. Initialize: OK");

            var session = client.newSession(new NewSessionRequest(".", List.of()));
            System.out.println("2. New session: " + session.sessionId());

            String testPrompt = "Hello from in-memory test!";
            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent(testPrompt))));
            System.out.println("3. Prompt sent: \"" + testPrompt + "\"");
            System.out.println("4. Stop reason: " + response.stopReason());

            // Verify results
            System.out.println("\n--- Verification ---");
            System.out.println("Agent received: \"" + receivedPrompt.get() + "\"");
            System.out.println("Client received: \"" + receivedMessage.get() + "\"");

            boolean success = testPrompt.equals(receivedPrompt.get()) &&
                             ("Echo: " + testPrompt).equals(receivedMessage.get());
            System.out.println("Test passed: " + success);
        }

        // Cleanup
        transportPair.closeGracefully().block();
        System.out.println("\nTransports closed. Demo complete!");
    }
}
