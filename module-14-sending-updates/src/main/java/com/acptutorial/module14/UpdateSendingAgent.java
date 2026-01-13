/*
 * Module 14: Sending Updates
 *
 * Demonstrates how agents send streaming updates to clients during prompt processing.
 *
 * Key APIs exercised:
 * - agent.run() - starts agent and blocks until client disconnects
 * - SyncSessionUpdateSender.sendUpdate() - blocking void method
 * - All SessionUpdate types:
 *   - AgentThoughtChunk - share thinking process
 *   - AgentMessageChunk - send response incrementally
 *   - ToolCall - report tool execution
 *   - ToolCallUpdateNotification - report tool progress
 *   - Plan - share planned actions
 *   - AvailableCommandsUpdate - advertise commands
 *   - CurrentModeUpdate - report mode changes
 *
 * Build & run:
 *   ./mvnw package -pl module-14-sending-updates -q
 *   ./mvnw exec:java -pl module-14-sending-updates
 */
package com.acptutorial.module14;

import java.util.List;
import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentThoughtChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.AvailableCommand;
import com.agentclientprotocol.sdk.spec.AcpSchema.AvailableCommandInput;
import com.agentclientprotocol.sdk.spec.AcpSchema.AvailableCommandsUpdate;
import com.agentclientprotocol.sdk.spec.AcpSchema.CurrentModeUpdate;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.Plan;
import com.agentclientprotocol.sdk.spec.AcpSchema.PlanEntry;
import com.agentclientprotocol.sdk.spec.AcpSchema.PlanEntryPriority;
import com.agentclientprotocol.sdk.spec.AcpSchema.PlanEntryStatus;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCall;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallStatus;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallUpdateNotification;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolKind;

public class UpdateSendingAgent {

    public static void main(String[] args) {
        System.err.println("[UpdateSendingAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req ->
                new InitializeResponse(1, new AgentCapabilities(), List.of()))

            .newSessionHandler(req ->
                new NewSessionResponse(UUID.randomUUID().toString(), null, null))

            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();

                // 1. Send thought update - show thinking process
                context.sendUpdate(sessionId,
                    new AgentThoughtChunk("agent_thought_chunk",
                        new TextContent("Let me analyze this request...")));

                // 2. Send plan update - show what we're going to do
                context.sendUpdate(sessionId,
                    new Plan("plan", List.of(
                        new PlanEntry("Analyze the prompt", PlanEntryPriority.HIGH, PlanEntryStatus.IN_PROGRESS),
                        new PlanEntry("Generate response", PlanEntryPriority.HIGH, PlanEntryStatus.PENDING),
                        new PlanEntry("Format output", PlanEntryPriority.MEDIUM, PlanEntryStatus.PENDING)
                    )));

                // 3. Send tool call - show tool execution starting
                context.sendUpdate(sessionId,
                    new ToolCall("tool_call",
                        "tool-1",
                        "Analyzing prompt",
                        ToolKind.THINK,
                        ToolCallStatus.IN_PROGRESS,
                        List.of(),
                        null, null, null, null));

                // 4. Send tool call update - show progress
                context.sendUpdate(sessionId,
                    new ToolCallUpdateNotification("tool_call_update",
                        "tool-1",
                        "Analyzing prompt",
                        ToolKind.THINK,
                        ToolCallStatus.COMPLETED,
                        List.of(),
                        null, null, null, null));

                // 5. Send available commands update
                context.sendUpdate(sessionId,
                    new AvailableCommandsUpdate("available_commands_update", List.of(
                        new AvailableCommand("help", "Show help",
                            new AvailableCommandInput("topic")),
                        new AvailableCommand("clear", "Clear context", null)
                    )));

                // 6. Send mode update
                context.sendUpdate(sessionId,
                    new CurrentModeUpdate("current_mode_update", "default"));

                // 7. Send message chunks - the actual response
                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Here is my response ")));
                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("streamed in ")));
                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("multiple chunks.")));

                // 8. Complete the turn
                return new PromptResponse(StopReason.END_TURN);
            })
            .build();

        // Start agent and block until client disconnects
        System.err.println("[UpdateSendingAgent] Ready, waiting for messages...");
        agent.run();  // Combines start() + await()
        System.err.println("[UpdateSendingAgent] Shutdown.");
    }
}
