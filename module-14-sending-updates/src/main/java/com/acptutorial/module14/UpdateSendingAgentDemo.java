/*
 * Module 14: Update Sending Agent Demo
 *
 * This demo launches the UpdateSendingAgent as a subprocess and displays
 * all the different types of session updates an agent can send.
 *
 * Build & run:
 *   ./mvnw package -pl module-14-sending-updates -q
 *   ./mvnw exec:java -pl module-14-sending-updates
 */
package com.acptutorial.module14;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentThoughtChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.AvailableCommandsUpdate;
import com.agentclientprotocol.sdk.spec.AcpSchema.CurrentModeUpdate;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.Plan;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCall;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallUpdateNotification;

public class UpdateSendingAgentDemo {

    private static final String MODULE_NAME = "module-14-sending-updates";
    private static final String JAR_NAME = "update-sending-agent.jar";

    public static void main(String[] args) {
        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg(findAgentJar())
            .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    switch (update) {
                        case AgentThoughtChunk thought -> {
                            String text = ((TextContent) thought.content()).text();
                            System.out.println("[Thought] " + text);
                        }
                        case AgentMessageChunk msg -> {
                            String text = ((TextContent) msg.content()).text();
                            System.out.print(text);
                        }
                        case Plan plan -> {
                            System.out.println("[Plan] " + plan.entries().size() + " steps:");
                            plan.entries().forEach(e ->
                                System.out.println("  - " + e.content() + " [" + e.status() + "]"));
                        }
                        case ToolCall tool ->
                            System.out.println("[ToolCall] " + tool.title() + " (" + tool.kind() + ") - " + tool.status());
                        case ToolCallUpdateNotification toolUpdate ->
                            System.out.println("[ToolUpdate] " + toolUpdate.title() + " - " + toolUpdate.status());
                        case AvailableCommandsUpdate commands -> {
                            System.out.println("[Commands] Available:");
                            commands.availableCommands().forEach(c ->
                                System.out.println("  /" + c.name() + " - " + c.description()));
                        }
                        case CurrentModeUpdate mode ->
                            System.out.println("[Mode] " + mode.currentModeId());
                        default ->
                            System.out.println("[Update] " + update.getClass().getSimpleName());
                    }
                })
                .build()) {

            System.out.println("=== Module 14: Session Update Types Demo ===\n");
            client.initialize();
            System.out.println("Connected to UpdateSendingAgent!\n");

            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            System.out.println("--- Updates from Agent ---");
            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent("Show me all update types"))));

            System.out.println("\n--- End ---");
            System.out.println("Stop reason: " + response.stopReason());

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
