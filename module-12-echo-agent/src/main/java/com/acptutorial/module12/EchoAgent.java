/*
 * Module 12: Echo Agent - The Reveal
 *
 * This is the "aha moment" - you finally see what's on the other side of ACP.
 * A minimal, protocol-compliant agent in ~30 lines of code.
 *
 * This agent:
 * 1. Accepts initialization
 * 2. Creates sessions
 * 3. Echoes back any prompt it receives
 *
 * Key APIs exercised:
 * - AcpAgent.sync() - synchronous agent builder
 * - agent.run() - starts agent and blocks until client disconnects
 * - SyncInitializeHandler - plain return value (no Mono)
 * - SyncNewSessionHandler - plain return value (no Mono)
 * - SyncPromptHandler - plain return value with SyncSessionUpdateSender
 * - SyncSessionUpdateSender.sendUpdate() - blocking void method
 *
 * Build & run:
 *   ./mvnw package -pl module-12-echo-agent -q
 *   ./mvnw exec:java -pl module-12-echo-agent
 */
package com.acptutorial.module12;

import java.util.List;
import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class EchoAgent {

    public static void main(String[] args) {
        System.err.println("[EchoAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        // Build sync agent - handlers use plain return values (no Mono!)
        AcpSyncAgent agent = AcpAgent.sync(transport)
            // Handle initialization - return our capabilities
            .initializeHandler(req ->
                new InitializeResponse(1, new AgentCapabilities(), List.of()))

            // Handle session creation - generate a unique session ID
            .newSessionHandler(req ->
                new NewSessionResponse(UUID.randomUUID().toString(), null, null))

            // Handle prompts - echo back the input
            .promptHandler((req, updater) -> {
                // Extract text from prompt
                String text = req.prompt().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse("(no text)");

                // Send the echo as an agent message update (blocking, void return)
                updater.sendUpdate(req.sessionId(),
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Echo: " + text)));

                // Return response directly (no Mono.just!)
                return new PromptResponse(StopReason.END_TURN);
            })
            .build();

        // Start agent and block until client disconnects
        System.err.println("[EchoAgent] Ready, waiting for messages...");
        agent.run();  // Combines start() + await()
        System.err.println("[EchoAgent] Shutdown.");
    }
}
