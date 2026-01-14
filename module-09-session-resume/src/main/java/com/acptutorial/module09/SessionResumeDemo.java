/*
 * Module 09: Session Resume Demo
 *
 * Demonstrates loading and resuming existing sessions.
 *
 * Key APIs:
 * - client.loadSession(LoadSessionRequest) - resumes an existing session
 * - LoadSessionRequest(sessionId, cwd, mcpServers) - session to load
 *
 * The demo:
 * 1. Creates a new session, sends messages, notes the session ID
 * 2. Loads the same session ID to resume
 * 3. Shows that session state persists across load
 *
 * Build & run:
 *   ./mvnw package -pl module-09-session-resume -q
 *   ./mvnw exec:java -pl module-09-session-resume
 */
package com.acptutorial.module09;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.LoadSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class SessionResumeDemo {

    private static final String MODULE_NAME = "module-09-session-resume";
    private static final String JAR_NAME = "stateful-agent.jar";

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

            System.out.println("=== Module 09: Session Resume Demo ===\n");

            // Initialize
            client.initialize();
            System.out.println("Connected to StatefulAgent\n");

            String cwd = System.getProperty("user.dir");

            // ---- Part 1: Create new session and send messages ----
            System.out.println("--- Part 1: New Session ---");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            String sessionId = session.sessionId();
            System.out.println("Created session: " + sessionId + "\n");

            // Send first message
            System.out.println("Sending message 1...");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("Hello, this is my first message!"))));
            System.out.println();

            // Send second message
            System.out.println("Sending message 2...");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("This is my second message."))));
            System.out.println();

            // ---- Part 2: Load (resume) the same session ----
            System.out.println("--- Part 2: Resume Session ---");
            System.out.println("Loading session: " + sessionId + "\n");

            client.loadSession(new LoadSessionRequest(sessionId, cwd, List.of()));
            System.out.println("Session loaded successfully!\n");

            // Send third message - should show history from before
            System.out.println("Sending message 3 (after resume)...");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("Third message after resuming!"))));
            System.out.println();

            // ---- Part 3: Try loading unknown session ----
            System.out.println("--- Part 3: Load Unknown Session ---");
            String fakeSessionId = "unknown-session-12345";
            System.out.println("Loading unknown session: " + fakeSessionId + "\n");

            client.loadSession(new LoadSessionRequest(fakeSessionId, cwd, List.of()));
            System.out.println("Agent handled unknown session (created fresh state)\n");

            // Send to the new session
            System.out.println("Sending to new session...");
            client.prompt(new PromptRequest(fakeSessionId,
                List.of(new TextContent("First message to fresh session"))));
            System.out.println();

            System.out.println("=== Demo Complete ===");
            System.out.println("loadSession allows resuming existing conversations.");

        } catch (Exception e) {
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
