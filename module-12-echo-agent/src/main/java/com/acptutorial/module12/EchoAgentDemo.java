/*
 * Module 12: Echo Agent Demo
 *
 * This demo launches the EchoAgent as a subprocess and communicates with it.
 * It demonstrates the complete client-agent round trip using YOUR OWN agent.
 *
 * The pattern:
 * 1. Package agent as executable JAR (via maven-shade-plugin)
 * 2. Launch with simple "java -jar"
 * 3. Communicate via StdioAcpClientTransport
 *
 * Build & run:
 *   ./mvnw package -pl module-12-echo-agent -q
 *   ./mvnw exec:java -pl module-12-echo-agent
 */
package com.acptutorial.module12;

import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class EchoAgentDemo {

    public static void main(String[] args) {
        // Launch the EchoAgent as a subprocess using the packaged JAR
        // This is clean: just "java -jar" with no Maven at runtime
        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg("module-12-echo-agent/target/echo-agent.jar")
            .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    if (update instanceof AgentMessageChunk msg) {
                        String text = ((TextContent) msg.content()).text();
                        System.out.println("[Agent] " + text);
                    }
                })
                .build()) {

            System.out.println("=== Module 12: Echo Agent Demo ===\n");

            // Initialize
            System.out.println("Sending initialize...");
            client.initialize();
            System.out.println("Connected to EchoAgent!\n");

            // Create session
            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            // Send a prompt and see the echo
            String message = "Hello from the client!";
            System.out.println("Sending: " + message);

            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent(message))));

            System.out.println("\nStop reason: " + response.stopReason());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
