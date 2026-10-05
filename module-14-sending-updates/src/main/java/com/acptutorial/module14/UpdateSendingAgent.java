/*
 * Module 14: Sending Updates
 *
 * Demonstrates how agents send streaming updates to clients during prompt processing.
 *
 * Key APIs exercised:
 * - agent.run() - starts agent and blocks until client disconnects
 * - SyncPromptContext.sendSessionUpdate(update) - blocking void method; the context knows its session
 * - All SessionUpdate types:
 *   - AgentThoughtChunk - share thinking process
 *   - AgentMessageChunk - send response incrementally
 *   - ToolCall - report tool execution
 *   - ToolCallUpdateNotification - report tool progress
 *   - Plan - share planned actions
 *   - AvailableCommandsUpdate - advertise commands
 *   - CurrentModeUpdate - report mode changes
 *   - UsageUpdate - report context window and cost usage (unstable)
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
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCall;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallStatus;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallUpdateNotification;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolKind;
import com.agentclientprotocol.sdk.spec.AcpSchema.UsageUpdate;

public class UpdateSendingAgent {

    public static void main(String[] args) {
        System.err.println("[UpdateSendingAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> InitializeResponse.ok())

            .newSessionHandler(req ->
                new NewSessionResponse(UUID.randomUUID().toString(), null, null))

            .promptHandler((req, context) -> {
                // 1. Send thought update - show thinking process (convenience method)
                context.sendThought("Let me analyze this request...");

                // 2. Send plan update - show what we're going to do (full API for complex types)
                context.sendSessionUpdate(new Plan(List.of(
                        new PlanEntry("Analyze the prompt", PlanEntryPriority.HIGH, PlanEntryStatus.IN_PROGRESS),
                        new PlanEntry("Generate response", PlanEntryPriority.HIGH, PlanEntryStatus.PENDING),
                        new PlanEntry("Format output", PlanEntryPriority.MEDIUM, PlanEntryStatus.PENDING)
                    )));

                // 3. Send tool call - show tool execution starting
                context.sendSessionUpdate(new ToolCall("tool_call",
                        "tool-1",
                        "Analyzing prompt",     // title: what the user sees
                        "analyze_prompt",       // name: the tool's own identifier (may be null)
                        ToolKind.THINK,
                        ToolCallStatus.IN_PROGRESS,
                        List.of(),
                        null, null, null, null));

                // 4. Send tool call update - show progress
                context.sendSessionUpdate(new ToolCallUpdateNotification("tool_call_update",
                        "tool-1",
                        "Analyzing prompt",
                        "analyze_prompt",
                        ToolKind.THINK,
                        ToolCallStatus.COMPLETED,
                        List.of(),
                        null, null, null, null));

                // 5. Send available commands update
                context.sendSessionUpdate(new AvailableCommandsUpdate(List.of(
                        new AvailableCommand("help", "Show help",
                            new AvailableCommandInput("topic")),
                        new AvailableCommand("clear", "Clear context", null)
                    )));

                // 6. Send mode update
                context.sendSessionUpdate(new CurrentModeUpdate("default"));

                // 7. Send usage update - report token usage (unstable)
                context.sendSessionUpdate(new UsageUpdate(53000L, 200000L));

                // 8. Send message chunks - the actual response (convenience method)
                context.sendMessage("Here is my response ");
                context.sendMessage("streamed in ");
                context.sendMessage("multiple chunks.");

                // 9. Complete the turn
                return PromptResponse.endTurn();
            })
            .build();

        // Start agent and block until client disconnects
        System.err.println("[UpdateSendingAgent] Ready, waiting for messages...");
        agent.run();  // Combines start() + await()
        System.err.println("[UpdateSendingAgent] Shutdown.");
    }
}
