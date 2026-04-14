/*
 * Module 23: Spring Boot Agent Demo
 *
 * Launches the Spring Boot agent as a subprocess and communicates with it.
 * Same pattern as Module 12's EchoAgentDemo, but the agent is a Spring Boot app.
 *
 * Build & run:
 *   ./mvnw package -pl module-23-spring-boot-agent -q
 *   ./mvnw exec:java -pl module-23-spring-boot-agent
 */
package com.acptutorial.module23;

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

public class SpringBootAgentDemo {

    private static final String MODULE_NAME = "module-23-spring-boot-agent";

    private static final String JAR_NAME = "module-23-spring-boot-agent-1.0.0-SNAPSHOT.jar";

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
                        System.out.println("[Agent] " + text);
                    }
                })
                .build()) {

            System.out.println("=== Module 23: Spring Boot Agent Demo ===\n");

            System.out.println("Sending initialize...");
            client.initialize();
            System.out.println("Connected to Spring Boot EchoAgent!\n");

            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            String message = "Hello from the client!";
            System.out.println("Sending: " + message);

            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent(message))));

            System.out.println("\nStop reason: " + response.stopReason());

        }
        catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
