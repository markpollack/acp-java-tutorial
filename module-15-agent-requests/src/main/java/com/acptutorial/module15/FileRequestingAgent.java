/*
 * Module 15: Agent Requests
 *
 * Demonstrates how agents request files and permissions from clients.
 *
 * Key APIs exercised:
 * - agent.run() - starts agent and blocks until client disconnects
 * - SyncPromptContext.readTextFile() - blocking file read from client
 * - SyncPromptContext.writeTextFile() - blocking file write to client
 * - SyncPromptContext.requestPermission() - blocking permission request
 * - SyncPromptContext.sendUpdate() - blocking update send
 * - ReadTextFileRequest/Response - file read types
 * - WriteTextFileRequest/Response - file write types
 * - RequestPermissionRequest/Response - permission types
 * - PermissionOption, PermissionOptionKind - permission choices
 *
 * In ACP, agents can access the client's filesystem through these APIs.
 * The client must have the appropriate handlers registered (see Module 07).
 *
 * NOTE: This module uses the new SyncPromptContext API (added in SDK 0.9.1)
 * which provides clean access to all agent capabilities without the need
 * for AtomicReference workarounds.
 *
 * Build & run:
 *   ./mvnw package -pl module-15-agent-requests -q
 *   ./mvnw exec:java -pl module-15-agent-requests
 */
package com.acptutorial.module15;

import java.util.List;
import java.util.UUID;

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

        // With PromptContext, no AtomicReference needed - the context provides
        // all agent capabilities directly to the handler!
        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req ->
                new InitializeResponse(1, new AgentCapabilities(), List.of()))

            .newSessionHandler(req ->
                new NewSessionResponse(UUID.randomUUID().toString(), null, null))

            // The context parameter provides access to all agent capabilities:
            // - sendUpdate() for session updates
            // - readTextFile(), writeTextFile() for file operations
            // - requestPermission() for permission requests
            // - createTerminal() etc for terminal operations
            // - getClientCapabilities() to check what client supports
            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();

                // 1. Read a file from the client
                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Reading pom.xml from your system...\n")));

                var fileResponse = context.readTextFile(
                    new ReadTextFileRequest(sessionId, "pom.xml", null, 10));

                context.sendUpdate(sessionId,
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

                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Requesting permission to create summary.txt...\n")));

                var permissionResponse = context.requestPermission(
                    new RequestPermissionRequest(sessionId, toolCall, options));

                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Permission response: " + permissionResponse.outcome() + "\n")));

                // 3. Write a file (in real code, check permission first!)
                context.writeTextFile(
                    new WriteTextFileRequest(sessionId, "summary.txt",
                        "This file was created by the FileRequestingAgent.\n" +
                        "It demonstrates the writeTextFile API in ACP.\n"));

                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Successfully wrote summary.txt!\n")));

                // 4. Complete the turn
                return new PromptResponse(StopReason.END_TURN);
            })
            .build();

        // Start agent and block until client disconnects
        System.err.println("[FileRequestingAgent] Ready, waiting for messages...");
        agent.run();  // Combines start() + await()
        System.err.println("[FileRequestingAgent] Shutdown.");
    }
}
