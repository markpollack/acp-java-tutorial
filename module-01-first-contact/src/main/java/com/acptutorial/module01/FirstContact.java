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
 * - Gemini CLI installed with --experimental-acp support
 * - GEMINI_API_KEY environment variable set (get key from https://aistudio.google.com/apikey)
 * - ACP Java SDK on classpath
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
        // Check for required API key before starting
        checkGeminiApiKey();

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

    /**
     * Verify GEMINI_API_KEY is set before attempting to connect.
     */
    private static void checkGeminiApiKey() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("""
                ERROR: GEMINI_API_KEY environment variable is not set.

                To fix this:

                1. Get your API key (or create one at https://aistudio.google.com/apikey)
                   - Terminal: echo $GEMINI_API_KEY

                2. Add it to IntelliJ (for all Java apps):
                   - Run > Edit Configurations
                   - Click "Edit configuration templates..." (bottom-left)
                   - Select "Application"
                   - Environment variables > Add: GEMINI_API_KEY=your-key
                   - Delete existing run configs to pick up the new template

                3. Run this program again.
                """);
            System.exit(1);
        }
    }
}
