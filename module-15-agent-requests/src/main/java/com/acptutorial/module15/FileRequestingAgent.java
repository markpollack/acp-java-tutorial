/*
 * Module 15: Agent Requests
 *
 * Demonstrates how agents request files and permissions from clients.
 *
 * Key APIs exercised:
 * - agent.run() - starts agent and blocks until client disconnects
 * - AcpSyncAgent.readTextFile() - blocking file read from client
 * - AcpSyncAgent.writeTextFile() - blocking file write to client
 * - AcpSyncAgent.requestPermission() - blocking permission request
 * - SyncSessionUpdateSender.sendUpdate() - blocking update send
 * - ReadTextFileRequest/Response - file read types
 * - WriteTextFileRequest/Response - file write types
 * - RequestPermissionRequest/Response - permission types
 * - PermissionOption, PermissionOptionKind - permission choices
 *
 * In ACP, agents can access the client's filesystem through these APIs.
 * The client must have the appropriate handlers registered (see Module 07).
 *
 * Build & run:
 *   ./mvnw package -pl module-15-agent-requests -q
 *   ./mvnw exec:java -pl module-15-agent-requests
 */
package com.acptutorial.module15;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PermissionOption;
import com.agentclientprotocol.sdk.spec.AcpSchema.PermissionOptionKind;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReadTextFileRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.RequestPermissionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallStatus;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallUpdate;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolKind;
import com.agentclientprotocol.sdk.spec.AcpSchema.WriteTextFileRequest;

public class FileRequestingAgent {

    public static void main(String[] args) {
        System.err.println("[FileRequestingAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        // Store agent reference for use in prompt handler
        AtomicReference<AcpSyncAgent> agentRef = new AtomicReference<>();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req ->
                new InitializeResponse(1, new AgentCapabilities(), List.of()))

            .newSessionHandler(req ->
                new NewSessionResponse(UUID.randomUUID().toString(), null, null))

            .promptHandler((req, updater) -> {
                String sessionId = req.sessionId();
                AcpSyncAgent agentInstance = agentRef.get();

                // 1. Read a file from the client
                updater.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Reading pom.xml from your system...\n")));

                var fileResponse = agentInstance.readTextFile(
                    new ReadTextFileRequest(sessionId, "pom.xml", null, 10));

                updater.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("File content (first 10 lines):\n" + fileResponse.content() + "\n\n")));

                // 2. Request permission before modifying anything
                ToolCallUpdate toolCall = new ToolCallUpdate(
                    "tool-write-1",
                    "Create summary.txt",
                    ToolKind.EDIT,
                    ToolCallStatus.PENDING,
                    null, null, null, null
                );

                List<PermissionOption> options = List.of(
                    new PermissionOption("allow", "Allow this once", PermissionOptionKind.ALLOW_ONCE),
                    new PermissionOption("allow_always", "Always allow", PermissionOptionKind.ALLOW_ALWAYS),
                    new PermissionOption("deny", "Deny", PermissionOptionKind.REJECT_ONCE)
                );

                updater.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Requesting permission to create summary.txt...\n")));

                var permissionResponse = agentInstance.requestPermission(
                    new RequestPermissionRequest(sessionId, toolCall, options));

                updater.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Permission response: " + permissionResponse.outcome() + "\n")));

                // 3. Write a file (in real code, check permission first!)
                agentInstance.writeTextFile(
                    new WriteTextFileRequest(sessionId, "summary.txt",
                        "This file was created by the FileRequestingAgent.\n" +
                        "It demonstrates the writeTextFile API in ACP.\n"));

                updater.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Successfully wrote summary.txt!\n")));

                // 4. Complete the turn
                return new PromptResponse(StopReason.END_TURN);
            })
            .build();

        // Store the agent reference before starting
        agentRef.set(agent);

        // Start agent and block until client disconnects
        System.err.println("[FileRequestingAgent] Ready, waiting for messages...");
        agent.run();  // Combines start() + await()
        System.err.println("[FileRequestingAgent] Shutdown.");
    }
}
