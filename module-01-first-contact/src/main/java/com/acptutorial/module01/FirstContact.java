/*
 * Module 01: First Contact
 *
 * Your first ACP client - connect to Gemini CLI and get a response.
 *
 * This module demonstrates the minimal code needed to:
 * 1. Create a transport to communicate with an ACP agent
 * 2. Build a client
 * 3. Initialize the connection
 * 4. Create a session
 * 5. Send a prompt and receive a response
 *
 * Prerequisites:
 * - An ACP-capable agent CLI on your PATH. We launch `gemini --experimental-acp`
 *   here, but ACP is model-agnostic: point this at any agentic CLI (e.g.
 *   claude-code-acp, codex-acp) by changing the command below.
 * - ACP Java SDK on classpath
 *
 * Note on API keys: this tutorial code never reads an API key. The agent CLI
 * you launch handles its own model and authentication (Gemini CLI, for example,
 * can use an OAuth login or its own GEMINI_API_KEY). There is nothing to set
 * here for the Java client itself.
 */
package com.acptutorial.module01;

import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class FirstContact {

    public static void main(String[] args) {
        // 1. Configure agent process - tells the transport how to launch the agent
        var params = AgentParameters.builder("gemini")
            .arg("--experimental-acp")
            .build();

        // 2. Create transport (launches subprocess when client connects)
        var transport = new StdioAcpClientTransport(params);

        // 3. Build sync client with an update consumer that prints the agent's response
        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    if (notification.update() instanceof AgentMessageChunk msg) {
                        System.out.print(((TextContent) msg.content()).text());
                    }
                })
                .build()) {

            // 4. Initialize - handshake with the agent
            var initResponse = client.initialize();
            System.out.println("Connected to agent!");
            System.out.println("Agent capabilities: " + initResponse.agentCapabilities());

            // 5. Create a session - workspace context for conversation
            var session = client.newSession(
                new NewSessionRequest(".", List.of()));
            System.out.println("Session created: " + session.sessionId());

            // 6. Send a prompt - the magic moment!
            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent("What is 2+2? Reply with just the number."))));

            // 7. Done! Print the result
            System.out.println("\nSuccess! Stop reason: " + response.stopReason());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
