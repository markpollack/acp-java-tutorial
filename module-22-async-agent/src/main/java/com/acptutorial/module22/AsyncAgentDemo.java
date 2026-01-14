/*
 * Module 22: Async Agent Demo
 *
 * This demo launches the AsyncAgent as a subprocess and communicates with it.
 * The client uses sync API for simplicity - the focus is on the async agent.
 *
 * Build & run:
 *   ./mvnw package -pl module-22-async-agent -q
 *   ./mvnw exec:java -pl module-22-async-agent
 */
package com.acptutorial.module22;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class AsyncAgentDemo {

    private static final String MODULE_NAME = "module-22-async-agent";
    private static final String JAR_NAME = "async-agent.jar";

    public static void main(String[] args) {
        // Launch the AsyncAgent as a subprocess
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
                        System.out.println("[Agent] " + text);
                    }
                })
                .build()) {

            System.out.println("=== Module 22: Async Agent Demo ===\n");
            System.out.println("This agent uses AcpAgent.async() with reactive Mono patterns.\n");

            // Initialize
            System.out.println("Sending initialize...");
            client.initialize();
            System.out.println("Connected to AsyncAgent!\n");

            // Create session
            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            // Send prompts
            String[] messages = {
                "Hello from async world!",
                "Reactive programming is fun!"
            };

            for (String message : messages) {
                System.out.println("Sending: " + message);
                var response = client.prompt(new PromptRequest(
                    session.sessionId(),
                    List.of(new TextContent(message))));
                System.out.println("Stop reason: " + response.stopReason() + "\n");
            }

            System.out.println("Demo complete!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Find the agent JAR whether running from repo root or module directory.
     */
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
