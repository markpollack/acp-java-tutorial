/*
 * Module 15: File Requesting Agent Demo
 *
 * This demo launches the FileRequestingAgent as a subprocess. The agent
 * requests files and permissions from this client, demonstrating the
 * inverted client-server model in ACP.
 *
 * Build & run:
 *   ./mvnw package -pl module-15-agent-requests -q
 *   ./mvnw exec:java -pl module-15-agent-requests
 */
package com.acptutorial.module15;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.ClientCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.FileSystemCapability;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PermissionSelected;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReadTextFileRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReadTextFileResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.RequestPermissionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.RequestPermissionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.WriteTextFileRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.WriteTextFileResponse;

public class FileRequestingAgentDemo {

    private static final String MODULE_NAME = "module-15-agent-requests";
    private static final String JAR_NAME = "file-requesting-agent.jar";

    public static void main(String[] args) {
        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg(findAgentJar())
            .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport)
                // Handle agent's file read requests
                // Error handling: throw exceptions - SDK converts to JSON-RPC errors
                .readTextFileHandler((ReadTextFileRequest req) -> {
                    System.out.println("[READ] " + req.path());
                    Path path = Path.of(req.path());
                    if (!Files.exists(path)) {
                        throw new RuntimeException("File not found: " + req.path());
                    }
                    try {
                        String content = Files.readString(path);
                        // Apply line limit if specified
                        if (req.limit() != null && req.limit() > 0) {
                            content = content.lines().limit(req.limit())
                                .collect(java.util.stream.Collectors.joining("\n"));
                        }
                        return new ReadTextFileResponse(content);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
                    }
                })
                // Handle agent's file write requests
                // Error handling: throw exceptions - SDK converts to JSON-RPC errors
                .writeTextFileHandler((WriteTextFileRequest req) -> {
                    System.out.println("[WRITE] " + req.path());
                    try {
                        Files.writeString(Path.of(req.path()), req.content());
                        return new WriteTextFileResponse();
                    } catch (IOException e) {
                        System.err.println("[WRITE] Error: " + e.getMessage());
                        throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
                    }
                })
                // Handle agent's permission requests (auto-approve for demo)
                .requestPermissionHandler((RequestPermissionRequest req) -> {
                    System.out.println("[PERMISSION] " + req.toolCall().title() + " - auto-approved");
                    return new RequestPermissionResponse(
                        new PermissionSelected(req.options().get(0).optionId()));
                })
                // Handle session updates
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    if (update instanceof AgentMessageChunk msg) {
                        String text = ((TextContent) msg.content()).text();
                        System.out.print(text);
                    }
                })
                .build()) {

            System.out.println("=== Module 15: Agent File Requests Demo ===\n");

            // Initialize with file system capabilities
            var caps = new ClientCapabilities(
                new FileSystemCapability(true, true), false);
            client.initialize(new InitializeRequest(1, caps));
            System.out.println("Connected with file capabilities!\n");

            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            System.out.println("--- Agent Output ---");
            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent("Demonstrate file operations"))));

            System.out.println("--- End ---");
            System.out.println("Stop reason: " + response.stopReason());

            // Show created file if exists
            Path summaryPath = Path.of("summary.txt");
            if (Files.exists(summaryPath)) {
                System.out.println("\nCreated summary.txt:");
                System.out.println(Files.readString(summaryPath));
                Files.delete(summaryPath);
            }

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
