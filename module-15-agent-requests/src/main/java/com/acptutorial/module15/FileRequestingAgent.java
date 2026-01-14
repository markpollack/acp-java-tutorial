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
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PermissionOption;
import com.agentclientprotocol.sdk.spec.AcpSchema.PermissionOptionKind;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.RequestPermissionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallStatus;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallUpdate;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolKind;

public class FileRequestingAgent {

    public static void main(String[] args) {
        System.err.println("[FileRequestingAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        // With PromptContext, no AtomicReference needed - the context provides
        // all agent capabilities directly to the handler!
        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> InitializeResponse.ok())

            .newSessionHandler(req ->
                new NewSessionResponse(UUID.randomUUID().toString(), null, null))

            // The context parameter provides access to all agent capabilities:
            // - sendMessage(), sendThought() for simple updates
            // - readFile(), writeFile() for file operations (convenience)
            // - askPermission(), askChoice() for permissions (convenience)
            // - execute() for terminal operations (convenience)
            // - getClientCapabilities() to check what client supports
            .promptHandler((req, context) -> {
                String sessionId = context.getSessionId();

                // 1. Read a file from the client (convenience method)
                context.sendMessage("Reading pom.xml from your system...\n");

                String content = context.readFile("pom.xml", 0, 10);

                context.sendMessage("File content (first 10 lines):\n" + content + "\n\n");

                // 2. Request permission before modifying anything (full API for complex permissions)
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

                context.sendMessage("Requesting permission to create summary.txt...\n");

                var permissionResponse = context.requestPermission(
                    new RequestPermissionRequest(sessionId, toolCall, options));

                context.sendMessage("Permission response: " + permissionResponse.outcome() + "\n");

                // 3. Write a file (convenience method)
                context.writeFile("summary.txt",
                    "This file was created by the FileRequestingAgent.\n" +
                    "It demonstrates the writeFile API in ACP.\n");

                context.sendMessage("Successfully wrote summary.txt!\n");

                // 4. Complete the turn
                return PromptResponse.endTurn();
            })
            .build();

        // Start agent and block until client disconnects
        System.err.println("[FileRequestingAgent] Ready, waiting for messages...");
        agent.run();  // Combines start() + await()
        System.err.println("[FileRequestingAgent] Shutdown.");
    }
}
