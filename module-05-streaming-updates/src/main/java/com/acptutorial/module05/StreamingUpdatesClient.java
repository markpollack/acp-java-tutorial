/*
 * Module 05: Streaming Updates
 *
 * Demonstrates receiving real-time session updates from agents during prompt processing.
 *
 * Key APIs exercised:
 * - SyncSpec.sessionUpdateConsumer() - register update handler
 * - SessionNotification - wrapper containing sessionId and update
 * - SessionUpdate interface and its implementations:
 *   - AgentMessageChunk - incremental response text
 *   - AgentThoughtChunk - agent's thinking process
 *   - ToolCall - tool execution start
 *   - ToolCallUpdateNotification - tool progress
 *   - Plan - agent's planned actions
 *   - AvailableCommandsUpdate - available slash commands
 *   - CurrentModeUpdate - mode changes
 *
 * Prerequisites:
 * - Gemini CLI installed with --experimental-acp support
 * - GEMINI_API_KEY environment variable set (get key from https://aistudio.google.com/apikey)
 */
package com.acptutorial.module05;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.agentclientprotocol.sdk.spec.AcpSchema.SessionNotification;
import com.agentclientprotocol.sdk.spec.AcpSchema.SessionUpdate;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCall;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallUpdateNotification;
import com.agentclientprotocol.sdk.spec.AcpSchema.UserMessageChunk;

public class StreamingUpdatesClient {

    public static void main(String[] args) {
        // Check for required API key before starting
        checkGeminiApiKey();

        var params = AgentParameters.builder("gemini")
            .arg("--experimental-acp")
            .build();

        var transport = new StdioAcpClientTransport(params);

        // Counter to track updates received
        AtomicInteger updateCount = new AtomicInteger(0);

        // Build client with session update consumer
        try (AcpSyncClient client = AcpClient.sync(transport)
                // Register handler for streaming updates
                // Note: Uses plain Consumer (no Mono) - this is a sync consumer!
                .sessionUpdateConsumer(notification -> handleSessionUpdate(notification, updateCount))
                .build()) {

            // Initialize with defaults (protocol version 1, default capabilities)
            client.initialize();
            System.out.println("Connected to agent\n");

            // Create session - use absolute path to avoid IDE vs Maven working directory differences
            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            // Send prompt - updates will stream during processing
            System.out.println("=== Sending prompt, watching for updates... ===\n");
            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent("List 3 interesting facts about Java programming."))));

            System.out.println("\n=== Prompt complete ===");
            System.out.println("Stop reason: " + response.stopReason());
            System.out.println("Total updates received: " + updateCount.get());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle different types of session updates.
     * Demonstrates pattern matching on the SessionUpdate sealed interface.
     */
    private static void handleSessionUpdate(SessionNotification notification, AtomicInteger counter) {
        int count = counter.incrementAndGet();
        SessionUpdate update = notification.update();

        // Pattern match on update type with readable formatting
        switch (update) {
            case AgentMessageChunk msg -> {
                String text = ((TextContent) msg.content()).text();
                System.out.print(text);  // No prefix/newline - streams naturally
            }
            case AgentThoughtChunk thought -> {
                String text = ((TextContent) thought.content()).text();
                System.out.println("[" + count + "][Thinking] " + text.trim());
            }
            case ToolCall tc ->
                System.out.println("[" + count + "][ToolCall] " + tc.title() + " | " + tc.kind() + " | " + tc.status());

            case ToolCallUpdateNotification tcUpdate ->
                System.out.println("[" + count + "][ToolUpdate] " + tcUpdate.toolCallId() + " -> " + tcUpdate.status());

            case Plan plan -> {
                System.out.println("[" + count + "][Plan] " + plan.entries().size() + " entries:");
                plan.entries().forEach(entry ->
                    System.out.println("         - " + entry.content() + " [" + entry.status() + "]"));
            }
            case AvailableCommandsUpdate commands ->
                System.out.println("[" + count + "][Commands] " + commands.availableCommands().size() + " available");

            case CurrentModeUpdate mode ->
                System.out.println("[" + count + "][Mode] " + mode.currentModeId());

            case UserMessageChunk userMsg -> {
                String text = ((TextContent) userMsg.content()).text();
                System.out.println("[" + count + "][UserEcho] " + text);
            }
            default ->
                System.out.println("[" + count + "][" + update.getClass().getSimpleName() + "]");
        }
    }

    /**
     * Verify GEMINI_API_KEY is set before attempting to connect.
     * Provides clear instructions if the environment variable is missing.
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
