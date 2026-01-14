/*
 * Module 22: Async Agent
 *
 * The reactive, non-blocking version of Module 12 (Echo Agent).
 *
 * This module demonstrates:
 * - AcpAgent.async() instead of AcpAgent.sync()
 * - Handlers returning Mono<T> instead of T
 * - Reactive sendUpdate() returning Mono<Void>
 * - Chaining async operations with flatMap/then
 *
 * Key differences from sync agent (Module 12):
 * - initializeHandler returns Mono<InitializeResponse>
 * - newSessionHandler returns Mono<NewSessionResponse>
 * - promptHandler returns Mono<PromptResponse>
 * - context.sendUpdate() returns Mono<Void> (must subscribe!)
 * - Use agent.start().block() + awaitTermination().block()
 *
 * When to use async agent:
 * - Non-blocking I/O operations
 * - Parallel processing
 * - Integration with reactive frameworks
 *
 * Build & run:
 *   ./mvnw package -pl module-22-async-agent -q
 *   ./mvnw exec:java -pl module-22-async-agent
 */
package com.acptutorial.module22;

import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpAsyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

import reactor.core.publisher.Mono;

public class AsyncAgent {

    public static void main(String[] args) {
        System.err.println("[AsyncAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        // Build ASYNC agent - handlers return Mono<T>
        AcpAsyncAgent agent = AcpAgent.async(transport)
            // Handler returns Mono<InitializeResponse>
            .initializeHandler(req -> {
                System.err.println("[AsyncAgent] Initialize received");
                return Mono.just(InitializeResponse.ok());
            })

            // Handler returns Mono<NewSessionResponse>
            .newSessionHandler(req -> {
                System.err.println("[AsyncAgent] New session: " + req.cwd());
                String sessionId = UUID.randomUUID().toString();
                return Mono.just(new NewSessionResponse(sessionId, null, null));
            })

            // Handler returns Mono<PromptResponse>
            // context.sendMessage() returns Mono<Void> - must chain with then()
            .promptHandler((req, context) -> {
                System.err.println("[AsyncAgent] Prompt received");

                // Extract text from prompt
                String text = req.prompt().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse("(no text)");

                // Using convenience method - returns Mono<Void>
                // Must chain with then() to ensure it executes before returning response
                return context.sendMessage("Async Echo: " + text)
                    .then(Mono.just(PromptResponse.endTurn()));
            })
            .build();

        // Start agent and wait for termination
        System.err.println("[AsyncAgent] Ready, waiting for messages...");
        agent.start().block();           // Start accepting messages
        agent.awaitTermination().block(); // Block until transport closes
        System.err.println("[AsyncAgent] Shutdown.");
    }
}
