/*
 * Module 21: Async Client
 *
 * The reactive, non-blocking version of Module 01.
 *
 * This module demonstrates:
 * - AcpClient.async() instead of AcpClient.sync()
 * - Reactive programming with Mono from Project Reactor
 * - Chaining operations with flatMap
 * - Non-blocking I/O patterns
 *
 * Key differences from sync client:
 * - All methods return Mono<T> instead of T
 * - Use flatMap to chain dependent operations
 * - Use subscribe() for fire-and-forget, block() to wait
 * - Session update consumer returns Mono<Void>
 *
 * When to use async:
 * - High-throughput scenarios
 * - Integration with reactive frameworks
 * - When you need non-blocking I/O
 *
 * When to use sync (Module 01):
 * - Simple CLI tools
 * - Scripts and one-off tasks
 * - When blocking is acceptable
 *
 * Prerequisites:
 * - GEMINI_API_KEY environment variable set
 *
 * Build & run:
 *   ./mvnw compile exec:java -pl module-21-async-client
 */
package com.acptutorial.module21;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.agentclientprotocol.sdk.client.AcpAsyncClient;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

import reactor.core.publisher.Mono;

public class AsyncClient {

    public static void main(String[] args) throws InterruptedException {
        checkGeminiApiKey();

        System.out.println("=== Module 21: Async Client ===\n");
        System.out.println("This is the async version of Module 01 (First Contact).\n");

        // 1. Configure agent process (same as sync)
        var params = AgentParameters.builder("gemini")
            .arg("--experimental-acp")
            .build();

        // 2. Create transport (same as sync)
        var transport = new StdioAcpClientTransport(params);

        // 3. Build ASYNC client - note the different builder and return type
        //    Key difference: sessionUpdateConsumer returns Mono<Void>
        AcpAsyncClient client = AcpClient.async(transport)
            .sessionUpdateConsumer(notification -> {
                // Async consumer - must return Mono<Void>
                var update = notification.update();
                if (update instanceof AgentMessageChunk msg) {
                    if (msg.content() instanceof TextContent text) {
                        System.out.print(text.text());
                    }
                }
                return Mono.empty();  // Signal completion
            })
            .build();

        // Latch to wait for async completion
        CountDownLatch done = new CountDownLatch(1);

        // 4. Chain operations reactively using flatMap
        //    This is the key pattern for async programming
        System.out.println("Starting reactive pipeline...\n");

        client.initialize()
            .doOnNext(init -> {
                System.out.println("Connected! Protocol version: " + init.protocolVersion());
            })
            .flatMap(init -> {
                // flatMap chains to the next async operation
                return client.newSession(new NewSessionRequest(".", List.of()));
            })
            .doOnNext(session -> {
                System.out.println("Session: " + session.sessionId());
                System.out.println("\n--- Agent Response ---");
            })
            .flatMap(session -> {
                // Chain to prompt
                return client.prompt(new PromptRequest(
                    session.sessionId(),
                    List.of(new TextContent("What is 2+2? Reply with just the number."))));
            })
            .doOnNext(response -> {
                System.out.println("\n--- End Response ---");
                System.out.println("Stop reason: " + response.stopReason());
            })
            .flatMap(response -> {
                // Chain to graceful close
                return client.closeGracefully();
            })
            .doOnTerminate(() -> {
                done.countDown();
            })
            .subscribe(
                unused -> {},                                    // onNext (Void)
                error -> {                                        // onError
                    System.err.println("Error: " + error.getMessage());
                    error.printStackTrace();
                    done.countDown();
                },
                () -> {                                           // onComplete
                    System.out.println("\nAsync pipeline complete!");
                }
            );

        // Wait for the reactive pipeline to complete
        // In a real reactive app, you wouldn't block - you'd compose further
        if (!done.await(60, TimeUnit.SECONDS)) {
            System.err.println("Timeout waiting for completion");
        }
    }

    /**
     * Alternative pattern: Using block() for simpler code.
     * Less idiomatic but sometimes practical for scripts.
     */
    @SuppressWarnings("unused")
    private static void alternativeWithBlock(AcpAsyncClient client) {
        // You can also use block() to wait synchronously
        // This defeats the purpose of async but can be useful in scripts
        var init = client.initialize().block();
        System.out.println("Connected: " + init.protocolVersion());

        var session = client.newSession(new NewSessionRequest(".", List.of())).block();
        System.out.println("Session: " + session.sessionId());

        var response = client.prompt(new PromptRequest(
            session.sessionId(),
            List.of(new TextContent("Hello")))).block();
        System.out.println("Done: " + response.stopReason());

        client.closeGracefully().block();
    }

    private static void checkGeminiApiKey() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("""
                ERROR: GEMINI_API_KEY environment variable is not set.

                To fix this:
                1. Get your API key from https://aistudio.google.com/apikey
                2. Export it: export GEMINI_API_KEY=your-key
                3. Run this program again.
                """);
            System.exit(1);
        }
    }
}
