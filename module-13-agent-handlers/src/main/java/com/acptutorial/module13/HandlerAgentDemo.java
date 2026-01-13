/*
 * Module 13: Agent Handlers - Demo Client
 *
 * This demo launches the HandlerAgent and exercises all its handlers.
 *
 * Build & run:
 *   ./mvnw package -pl module-13-agent-handlers -q
 *   ./mvnw exec:java -pl module-13-agent-handlers
 */
package com.acptutorial.module13;

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

public class HandlerAgentDemo {

    private static final String MODULE_NAME = "module-13-agent-handlers";
    private static final String JAR_NAME = "handler-agent.jar";

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
                    }
                })
                .build()) {

            System.out.println("=== Module 13: Agent Handlers Demo ===\n");

            // 1. Initialize - triggers initializeHandler
            System.out.println("1. Calling initialize()...");
            var initResponse = client.initialize();
            System.out.println("   Protocol version: " + initResponse.protocolVersion());
            System.out.println();

            // 2. New Session - triggers newSessionHandler
            System.out.println("2. Calling newSession()...");
            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("   Session ID: " + session.sessionId());
            System.out.println();

            // 3. Prompt - triggers promptHandler
            System.out.println("3. Calling prompt()...");
            System.out.println("--- Agent Response ---");
            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent("Hello, handler agent!"))));
            System.out.println("\n--- End ---");
            System.out.println("   Stop reason: " + response.stopReason());
            System.out.println();

            // 4. Create another session to show session tracking
            System.out.println("4. Creating second session...");
            var session2 = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("   Session 2 ID: " + session2.sessionId());
            System.out.println();

            // 5. Final prompt
            System.out.println("5. Final prompt to show session count...");
            System.out.println("--- Agent Response ---");
            var response2 = client.prompt(new PromptRequest(
                session2.sessionId(),
                List.of(new TextContent("How many sessions exist?"))));
            System.out.println("\n--- End ---");
            System.out.println("   Stop reason: " + response2.stopReason());

            System.out.println("\nAll handlers exercised successfully!");

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
