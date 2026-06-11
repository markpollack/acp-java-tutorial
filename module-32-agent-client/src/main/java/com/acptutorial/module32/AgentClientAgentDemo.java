/*
 * Module 32: Agent-Client Agent Demo
 *
 * Launches the AgentClientAgent as a subprocess and gives it an AGENTIC goal in a
 * throwaway working directory: create a file. A chatbot would only describe one;
 * this agent actually writes it - we then read it back from disk to prove it.
 *
 * No ANTHROPIC_API_KEY needed: the agent drives the local Claude CLI (subscription).
 * The Claude CLI must be installed and logged in.
 *
 * Build & run:
 *   ./mvnw package -pl module-32-agent-client -q
 *   ./mvnw exec:java -pl module-32-agent-client
 */
package com.acptutorial.module32;

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

public class AgentClientAgentDemo {

    private static final String MODULE_NAME = "module-32-agent-client";
    private static final String JAR_NAME = "agent-client-agent.jar";

    public static void main(String[] args) throws Exception {
        // A throwaway working directory for the agent to act in (it will create files here).
        Path workdir = Files.createTempDirectory("agent-client-demo");

        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg(findAgentJar())
            .build();
        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    if (notification.update() instanceof AgentMessageChunk msg) {
                        System.out.print(((TextContent) msg.content()).text());
                        System.out.flush();
                    }
                })
                .build()) {

            System.out.println("=== Module 32: Agent-Client Agent Demo ===\n");
            client.initialize();
            System.out.println("Connected to AgentClientAgent (a real agent, not a chatbot)!");
            System.out.println("Working directory: " + workdir + "\n");

            var session = client.newSession(new NewSessionRequest(workdir.toString(), List.of()));

            // An agentic goal: the agent must WRITE a file, not just answer from memory.
            ask(client, session.sessionId(),
                "Create a file called haiku.txt containing a haiku about AI agents, "
                + "then tell me the path and the haiku you wrote.");

            // Prove it actually touched the filesystem.
            Path created = workdir.resolve("haiku.txt");
            System.out.println("\n\nhaiku.txt created? " + Files.exists(created));
            if (Files.exists(created)) {
                System.out.println("--- contents ---\n" + Files.readString(created));
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void ask(AcpSyncClient client, String sessionId, String goal) {
        System.out.println("\nYou: " + goal);
        System.out.print("Agent: ");
        var response = client.prompt(new PromptRequest(sessionId, List.of(new TextContent(goal))));
        System.out.println("\n(stop reason: " + response.stopReason() + ")");
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
        throw new RuntimeException("Agent JAR not found. Run: ./mvnw package -pl " + MODULE_NAME);
    }
}
