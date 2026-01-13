/*
 * Module 08: Permissions
 *
 * Demonstrates handling permission requests from agents on the client side.
 *
 * Key APIs exercised:
 * - SyncSpec.requestPermissionHandler() - register permission handler
 * - RequestPermissionRequest - contains toolCall and options
 * - RequestPermissionResponse - contains outcome
 * - PermissionOption - individual choice (optionId, name, kind)
 * - PermissionOptionKind - ALLOW_ONCE, ALLOW_ALWAYS, REJECT_ONCE, REJECT_ALWAYS
 * - RequestPermissionOutcome - PermissionSelected or PermissionCancelled
 * - PermissionSelected - user chose an option
 * - PermissionCancelled - user cancelled the prompt
 *
 * In ACP, agents must request permission for sensitive operations.
 * The client presents choices to the user and returns the decision.
 * This completes the bidirectional request flow (with Module 07 for files).
 *
 * Note: This module also requires file handlers since the agent needs to
 * actually write files after permission is granted.
 *
 * Prerequisites:
 * - Gemini CLI installed with --experimental-acp support
 * - GEMINI_API_KEY environment variable set (get key from https://aistudio.google.com/apikey)
 */
package com.acptutorial.module08;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentThoughtChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallUpdateNotification;
import com.agentclientprotocol.sdk.spec.AcpSchema.ClientCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.FileSystemCapability;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PermissionCancelled;
import com.agentclientprotocol.sdk.spec.AcpSchema.PermissionOption;
import com.agentclientprotocol.sdk.spec.AcpSchema.PermissionSelected;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReadTextFileRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReadTextFileResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.RequestPermissionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.RequestPermissionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCall;
import com.agentclientprotocol.sdk.spec.AcpSchema.WriteTextFileRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.WriteTextFileResponse;

public class PermissionsClient {

    private static Path workDir;

    public static void main(String[] args) {
        // Check for required API key before starting
        checkGeminiApiKey();

        workDir = Path.of(System.getProperty("user.dir"));

        var params = AgentParameters.builder("gemini")
            .arg("--experimental-acp")
            // Note: NOT using --yolo so agent will ask for permission
            .build();

        var transport = new StdioAcpClientTransport(params);
        var scanner = new Scanner(System.in);

        try (AcpSyncClient client = AcpClient.sync(transport)
                .requestTimeout(Duration.ofMinutes(2))  // User needs time to respond
                // File handlers - required for agent to actually read/write files
                .readTextFileHandler(PermissionsClient::handleReadFile)
                .writeTextFileHandler(PermissionsClient::handleWriteFile)
                // Session updates - log agent's progress with readable formatting
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    switch (update) {
                        case AgentThoughtChunk thought -> {
                            String text = ((TextContent) thought.content()).text();
                            System.out.println("[Thinking] " + text.trim());
                        }
                        case AgentMessageChunk msg -> {
                            String text = ((TextContent) msg.content()).text();
                            System.out.print(text);  // No newline - agent streams chunks
                        }
                        case ToolCall tc ->
                            System.out.println("[ToolCall] " + tc.title() + " | " + tc.kind() + " | " + tc.status());
                        case ToolCallUpdateNotification tcUpdate ->
                            System.out.println("[ToolUpdate] " + tcUpdate.toolCallId() + " -> " + tcUpdate.status());
                        default ->
                            System.out.println("[" + update.getClass().getSimpleName() + "]");
                    }
                })
                // Permission handler - the focus of this module
                .requestPermissionHandler((RequestPermissionRequest request) -> {
                    // Display the permission request to user
                    System.out.println("\n" + "=".repeat(60));
                    System.out.println("PERMISSION REQUEST from agent");
                    System.out.println("=".repeat(60));

                    // Show what tool is requesting permission
                    if (request.toolCall() != null) {
                        System.out.println("Tool: " + request.toolCall().title());
                        System.out.println("Kind: " + request.toolCall().kind());
                    }

                    // Show available options
                    System.out.println("\nOptions:");
                    List<PermissionOption> options = request.options();
                    for (int i = 0; i < options.size(); i++) {
                        PermissionOption opt = options.get(i);
                        System.out.printf("  [%d] %s (%s) - %s%n",
                            i + 1, opt.name(), opt.optionId(), opt.kind());
                    }
                    System.out.println("  [0] Cancel");

                    // Get user choice
                    System.out.print("\nYour choice: ");
                    String input = scanner.nextLine().trim();

                    try {
                        int choice = Integer.parseInt(input);
                        if (choice == 0) {
                            System.out.println("Permission cancelled by user");
                            return new RequestPermissionResponse(new PermissionCancelled());
                        } else if (choice >= 1 && choice <= options.size()) {
                            String selectedId = options.get(choice - 1).optionId();
                            System.out.println("Selected: " + selectedId);
                            return new RequestPermissionResponse(new PermissionSelected(selectedId));
                        }
                    } catch (NumberFormatException e) {
                        // Invalid input
                    }

                    // Default: cancel on invalid input
                    System.out.println("Invalid input, cancelling");
                    return new RequestPermissionResponse(new PermissionCancelled());
                })
                .build()) {

            // Initialize with file system capabilities
            var clientCaps = new ClientCapabilities(
                new FileSystemCapability(true, true),  // read=true, write=true
                false);
            client.initialize(new InitializeRequest(1, clientCaps));
            System.out.println("Connected to agent with file system capabilities\n");

            // Create session
            var session = client.newSession(new NewSessionRequest(workDir.toString(), List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            // Send a prompt that will trigger permission requests
            String promptText = "Create a new file called 'hello.txt' with the content 'Hello, World!'";
            System.out.println("=== Sending prompt (will trigger permission request) ===");
            System.out.println("Prompt: " + promptText + "\n");

            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent(promptText))));

            System.out.println("\n=== Prompt complete ===");
            System.out.println("Stop reason: " + response.stopReason());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    /**
     * Handles file read requests from the agent.
     *
     * Error handling: Throws exceptions for errors, which the SDK converts to
     * JSON-RPC error responses (code -32603). This follows the standard pattern
     * used by other ACP SDKs (Kotlin, Python).
     */
    private static ReadTextFileResponse handleReadFile(ReadTextFileRequest request) {
        System.out.println("[READ] " + request.path());
        Path path = Path.of(request.path());

        if (!Files.exists(path)) {
            System.out.println("[READ] File does not exist: " + request.path());
            throw new RuntimeException("File not found: " + request.path());
        }
        try {
            String content = Files.readString(path);
            return new ReadTextFileResponse(content);
        } catch (IOException e) {
            System.err.println("[READ] Error: " + e.getMessage());
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }

    /**
     * Handles file write requests from the agent.
     *
     * Error handling: Throws exceptions for errors, which the SDK converts to
     * JSON-RPC error responses (code -32603). This is consistent with handleReadFile.
     */
    private static WriteTextFileResponse handleWriteFile(WriteTextFileRequest request) {
        System.out.println("[WRITE] " + request.path() + " (" + request.content().length() + " chars)");
        try {
            Files.writeString(Path.of(request.path()), request.content());
            System.out.println("[WRITE] Success!");
            return new WriteTextFileResponse();
        } catch (IOException e) {
            System.err.println("[WRITE] Error: " + e.getMessage());
            throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
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
