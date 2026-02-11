/*
 * Module 12: Echo Agent - The Reveal
 *
 * This is the "aha moment" - you finally see what's on the other side of ACP.
 * A minimal, protocol-compliant agent in ~25 lines of code.
 *
 * This agent:
 * 1. Accepts initialization
 * 2. Creates sessions
 * 3. Echoes back any prompt it receives
 *
 * Key APIs exercised:
 * - AcpAgent.sync() - synchronous agent builder
 * - agent.run() - starts agent and blocks until client disconnects
 * - InitializeResponse.ok() - factory method for successful init
 * - PromptResponse.endTurn() - factory method for turn completion
 * - context.sendMessage() - convenience method for sending messages
 *
 * Build & run:
 *   ./mvnw package -pl module-12-echo-agent -q
 *   ./mvnw exec:java -pl module-12-echo-agent
 */
package com.acptutorial.module12;

import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;


public class EchoAgent {

    public static void main(String[] args) {
        System.err.println("[EchoAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        // Build sync agent - handlers use plain return values (no Mono!)
        AcpSyncAgent agent = AcpAgent.sync(transport)
            // Handle initialization - return our capabilities
            .initializeHandler(req -> InitializeResponse.ok())

            // Handle session creation - generate a unique session ID
            .newSessionHandler(req ->
                new NewSessionResponse(UUID.randomUUID().toString(), null, null))

            // Handle prompts - echo back the input
            .promptHandler((req, context) -> {
                String text = req.text();

                // Send the echo using convenience method
                context.sendMessage("Echo: " + text);

                // Return using factory method
                return PromptResponse.endTurn();
            })
            .build();

        // Start agent and block until client disconnects
        System.err.println("[EchoAgent] Ready, waiting for messages...");
        agent.run();  // Combines start() + await()
        System.err.println("[EchoAgent] Shutdown.");
    }
}
