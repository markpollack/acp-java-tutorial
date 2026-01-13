/*
 * Module 07: Agent Requests
 *
 * Demonstrates handling file read/write requests from agents on the client side.
 *
 * Key APIs exercised:
 * - SyncSpec.readTextFileHandler() - handle file read requests
 * - SyncSpec.writeTextFileHandler() - handle file write requests
 * - ClientCapabilities with FileSystemCapability - advertise capabilities
 * - ReadTextFileRequest/Response - file read types
 * - WriteTextFileRequest/Response - file write types
 *
 * In ACP, the roles are inverted compared to traditional client-server:
 * - The AGENT requests files from the CLIENT
 * - The CLIENT serves files to the AGENT
 * This allows agents to access the user's local filesystem securely.
 *
 * Also demonstrates Java NIO.2 best practices:
 * - Loading resources from classpath (separation of concerns)
 * - Files.copy() with StandardCopyOption for atomic writes
 * - Files.readString() / Files.writeString() for terse file I/O
 *
 * Prerequisites:
 * - Gemini CLI installed with --experimental-acp support
 * - GEMINI_API_KEY environment variable set (get key from https://aistudio.google.com/apikey)
 */
package com.acptutorial.module07;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentThoughtChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.ClientCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.FileSystemCapability;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReadTextFileRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReadTextFileResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCall;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallUpdateNotification;
import com.agentclientprotocol.sdk.spec.AcpSchema.WriteTextFileRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.WriteTextFileResponse;

public class AgentRequestsClient {

    private static final String MYSTERY_RESOURCE = "/mystery.txt";

    private static Path workDir;

    public static void main(String[] args) {
        checkGeminiApiKey();

        // Setup working directory and copy mystery.txt from classpath resources
        workDir = Path.of(System.getProperty("user.dir"));
        copyMysteryFileFromClasspath();

        var params = AgentParameters.builder("gemini")
            .arg("--experimental-acp")
            .arg("--yolo")  // Auto-approve file operations
            .build();

        var transport = new StdioAcpClientTransport(params);

        // Build client with file handlers and session updates
        try (AcpSyncClient client = AcpClient.sync(transport)
                .requestTimeout(java.time.Duration.ofSeconds(60))  // Give agent more time
                .readTextFileHandler(AgentRequestsClient::handleReadFile)
                .writeTextFileHandler(AgentRequestsClient::handleWriteFile)
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    switch (update) {
                        case AgentThoughtChunk thought -> {
                            String text = ((TextContent) thought.content()).text();
                            System.out.println("[Thinking] " + text.trim());
                        }
                        case AgentMessageChunk msg -> {
                            String text = ((TextContent) msg.content()).text();
                            System.out.print(text);
                        }
                        case ToolCall tc ->
                            System.out.println("[ToolCall] " + tc.title() + " | " + tc.kind() + " | " + tc.status());
                        case ToolCallUpdateNotification tcUpdate ->
                            System.out.println("[ToolUpdate] " + tcUpdate.toolCallId() + " -> " + tcUpdate.status());
                        default -> { }  // Ignore other update types
                    }
                })
                .build()) {

            // Advertise file system capabilities
            var clientCaps = new ClientCapabilities(
                new FileSystemCapability(true, true),  // read=true, write=true
                false);
            client.initialize(new InitializeRequest(1, clientCaps));
            System.out.println("Connected to agent with file system capabilities\n");

            var session = client.newSession(new NewSessionRequest(workDir.toString(), List.of()));
            System.out.println("Session: " + session.sessionId());
            System.out.println("Working directory: " + workDir + "\n");

            // Ask the agent to read and summarize the mystery file
            System.out.println("=== Asking agent to read mystery.txt and write a summary ===\n");
            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent(
                    "Read the file 'mystery.txt' in the current directory. " +
                    "Summarize its content in 3 bullet points and write the summary to 'summary.txt'."))));

            System.out.println("\n=== Prompt complete ===");
            System.out.println("Stop reason: " + response.stopReason());

            // Show the summary if it was created
            Path summaryPath = workDir.resolve("summary.txt");
            if (Files.exists(summaryPath)) {
                System.out.println("\n=== Content of summary.txt ===");
                System.out.println(Files.readString(summaryPath));
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Copies mystery.txt from classpath resources to the working directory.
     * Uses Java NIO.2 Files.copy() for clean, terse file handling.
     */
    private static void copyMysteryFileFromClasspath() {
        Path destPath = workDir.resolve("mystery.txt");
        try (InputStream in = AgentRequestsClient.class.getResourceAsStream(MYSTERY_RESOURCE)) {
            if (in == null) {
                throw new IOException("Resource not found: " + MYSTERY_RESOURCE);
            }
            long bytes = Files.copy(in, destPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Copied mystery.txt from classpath (" + bytes + " bytes)");
        } catch (IOException e) {
            System.err.println("Failed to copy mystery.txt: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Handles file read requests from the agent.
     * Returns error indicator if file doesn't exist (allows agent to handle gracefully).
     */
    private static ReadTextFileResponse handleReadFile(ReadTextFileRequest request) {
        System.out.println("[READ] " + request.path());
        Path path = Path.of(request.path());

        try {
            if (!Files.exists(path)) {
                System.out.println("[READ] File does not exist: " + request.path());
                return new ReadTextFileResponse("[ERROR: File does not exist: " + request.path() + "]");
            }
            String content = Files.readString(path);
            System.out.println("[READ] Returned " + content.length() + " chars");
            return new ReadTextFileResponse(content);
        } catch (IOException e) {
            System.err.println("[READ] Error: " + e.getMessage());
            return new ReadTextFileResponse("[ERROR: " + e.getMessage() + "]");
        }
    }

    /**
     * Handles file write requests from the agent.
     * Note: Uses typed handler - SDK does unmarshalling automatically!
     */
    private static WriteTextFileResponse handleWriteFile(WriteTextFileRequest request) {
        System.out.println("[WRITE] " + request.path() + " (" + request.content().length() + " chars)");

        try {
            Files.writeString(Path.of(request.path()), request.content());
            System.out.println("[WRITE] Success");
            return new WriteTextFileResponse();
        } catch (IOException e) {
            System.err.println("[WRITE] Error: " + e.getMessage());
            throw new RuntimeException("Cannot write: " + request.path(), e);
        }
    }

    /**
     * Verify GEMINI_API_KEY is set before attempting to connect.
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
