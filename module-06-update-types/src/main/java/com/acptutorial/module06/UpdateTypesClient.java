/*
 * Module 06: Update Types
 *
 * Comprehensive coverage of all SessionUpdate types in ACP.
 *
 * Key APIs exercised:
 * - AgentMessageChunk - incremental response text
 * - AgentThoughtChunk - agent's thinking process
 * - ToolCall - tool execution notification
 * - ToolCallUpdateNotification - tool progress
 * - Plan - agent's planned actions
 * - AvailableCommandsUpdate - slash commands
 * - CurrentModeUpdate - mode changes
 *
 * Understanding update types helps build rich UIs that show agent activity.
 *
 * Build & run:
 *   ./mvnw exec:java -pl module-06-update-types
 */
package com.acptutorial.module06;

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

public class UpdateTypesClient {

    /**
     * Tracks counts of each update type received during a prompt.
     * Using a class instead of static fields allows for proper encapsulation
     * and makes the code reusable in concurrent scenarios.
     */
    private static class UpdateStats {
        int messageChunks = 0;
        int thoughtChunks = 0;
        int toolCalls = 0;
        int toolUpdates = 0;
        int plans = 0;
        int commandUpdates = 0;
        int modeUpdates = 0;
        int otherUpdates = 0;

        void handleUpdate(Object update) {
            if (update instanceof AgentMessageChunk msg) {
                messageChunks++;
                String text = ((TextContent) msg.content()).text();
                // Print message chunks inline (they build up the response)
                System.out.print(text);
            } else if (update instanceof AgentThoughtChunk thought) {
                thoughtChunks++;
                String text = ((TextContent) thought.content()).text();
                System.out.println("[THOUGHT] " + text.trim());
            } else if (update instanceof ToolCall tool) {
                toolCalls++;
                System.out.println("[TOOL] " + tool.title() + " (" + tool.kind() + ") - " + tool.status());
            } else if (update instanceof ToolCallUpdateNotification toolUpdate) {
                toolUpdates++;
                System.out.println("[TOOL_UPDATE] " + toolUpdate.toolCallId() + " -> " + toolUpdate.status());
            } else if (update instanceof Plan plan) {
                plans++;
                System.out.println("[PLAN] " + plan.entries().size() + " steps");
            } else if (update instanceof AvailableCommandsUpdate commands) {
                commandUpdates++;
                System.out.println("[COMMANDS] " + commands.availableCommands().size() + " available");
            } else if (update instanceof CurrentModeUpdate mode) {
                modeUpdates++;
                System.out.println("[MODE] " + mode.currentModeId());
            } else {
                otherUpdates++;
                System.out.println("[OTHER] " + update.getClass().getSimpleName());
            }
        }

        void printSummary() {
            System.out.println("Update types received:");
            System.out.println("  AgentMessageChunk:         " + messageChunks);
            System.out.println("  AgentThoughtChunk:         " + thoughtChunks);
            System.out.println("  ToolCall:                  " + toolCalls);
            System.out.println("  ToolCallUpdateNotification:" + toolUpdates);
            System.out.println("  Plan:                      " + plans);
            System.out.println("  AvailableCommandsUpdate:   " + commandUpdates);
            System.out.println("  CurrentModeUpdate:         " + modeUpdates);
            System.out.println("  Other:                     " + otherUpdates);
        }
    }

    public static void main(String[] args) {
        checkGeminiApiKey();

        var params = AgentParameters.builder("gemini")
            .arg("--experimental-acp")
            .build();

        var transport = new StdioAcpClientTransport(params);

        // Local state for tracking updates - no static fields needed
        var stats = new UpdateStats();

        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    stats.handleUpdate(update);
                })
                .build()) {

            System.out.println("=== Module 06: Update Types ===\n");
            System.out.println("This module logs all session update types received from the agent.\n");

            client.initialize();
            var session = client.newSession(new NewSessionRequest(".", List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            // Send a prompt that triggers various update types
            System.out.println("Sending prompt to trigger updates...\n");
            System.out.println("--- Updates ---");

            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent(
                    "Please explain what you're thinking step by step, " +
                    "then give me a brief answer to: What is the speed of light?"))));

            System.out.println("\n--- Summary ---");
            System.out.println("Stop reason: " + response.stopReason());
            System.out.println();
            stats.printSummary();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void checkGeminiApiKey() {
        if (System.getenv("GEMINI_API_KEY") == null) {
            System.err.println("ERROR: GEMINI_API_KEY not set. See module-01 for setup instructions.");
            System.exit(1);
        }
    }
}
