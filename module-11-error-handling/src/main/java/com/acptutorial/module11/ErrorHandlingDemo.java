/*
 * Module 11: Error Handling Demo
 *
 * Demonstrates handling protocol errors from agents.
 *
 * Key concepts:
 * - Catching AcpClientSession.AcpError on the client
 * - Checking error codes (AcpErrorCodes)
 * - Error recovery strategies
 *
 * Build & run:
 *   ./mvnw package -pl module-11-error-handling -q
 *   ./mvnw exec:java -pl module-11-error-handling
 */
package com.acptutorial.module11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpClientSession;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.LoadSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class ErrorHandlingDemo {

    private static final String MODULE_NAME = "module-11-error-handling";
    private static final String JAR_NAME = "error-prone-agent.jar";

    public static void main(String[] args) {
        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg(findAgentJar())
            .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    if (update instanceof AgentMessageChunk msg) {
                        String text = ((TextContent) msg.content()).text();
                        System.out.print(text);
                    }
                })
                .build()) {

            System.out.println("=== Module 11: Error Handling ===\n");

            // Initialize
            client.initialize();
            System.out.println("Connected to ErrorProneAgent\n");

            String cwd = System.getProperty("user.dir");

            // Create a session
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            String sessionId = session.sessionId();
            System.out.println("Session created: " + sessionId + "\n");

            // Test 1: Normal operation (should succeed)
            System.out.println("--- Test 1: Normal Operation ---");
            try {
                var response = client.prompt(new PromptRequest(sessionId,
                    List.of(new TextContent("hello world"))));
                System.out.println();
                System.out.println("Success! Stop reason: " + response.stopReason());
            } catch (AcpClientSession.AcpError e) {
                System.out.println("Unexpected error: " + e.getMessage());
            }
            System.out.println();

            // Test 2: Trigger INVALID_PARAMS error
            System.out.println("--- Test 2: Invalid Params Error ---");
            try {
                client.prompt(new PromptRequest(sessionId,
                    List.of(new TextContent("this is invalid input"))));
                System.out.println("Expected an error but got success!");
            } catch (AcpClientSession.AcpError e) {
                System.out.println("Caught expected error!");
                System.out.println("  Code: " + e.getCode());
                System.out.println("  Message: " + e.getMessage());
            }
            System.out.println();

            // Test 3: Trigger INTERNAL_ERROR
            System.out.println("--- Test 3: Internal Error ---");
            try {
                client.prompt(new PromptRequest(sessionId,
                    List.of(new TextContent("trigger internal error"))));
                System.out.println("Expected an error but got success!");
            } catch (AcpClientSession.AcpError e) {
                System.out.println("Caught expected error!");
                System.out.println("  Code: " + e.getCode());
                System.out.println("  Message: " + e.getMessage());
            }
            System.out.println();

            // Test 4: Trigger PERMISSION_DENIED error
            System.out.println("--- Test 4: Permission Denied Error ---");
            try {
                client.prompt(new PromptRequest(sessionId,
                    List.of(new TextContent("check permission denied"))));
                System.out.println("Expected an error but got success!");
            } catch (AcpClientSession.AcpError e) {
                System.out.println("Caught expected error!");
                System.out.println("  Code: " + e.getCode());
                System.out.println("  Message: " + e.getMessage());
            }
            System.out.println();

            // Test 5: Trigger SESSION_NOT_FOUND error
            System.out.println("--- Test 5: Session Not Found Error ---");
            try {
                client.loadSession(new LoadSessionRequest("non-existent-session-id", cwd, List.of()));
                System.out.println("Expected an error but got success!");
            } catch (AcpClientSession.AcpError e) {
                System.out.println("Caught expected error!");
                System.out.println("  Code: " + e.getCode());
                System.out.println("  Message: " + e.getMessage());
            }
            System.out.println();

            // Test 6: Error recovery - continue after error
            System.out.println("--- Test 6: Error Recovery ---");
            System.out.println("After handling errors, we can continue using the client...");
            try {
                var response = client.prompt(new PromptRequest(sessionId,
                    List.of(new TextContent("recovery test"))));
                System.out.println();
                System.out.println("Recovery successful! Stop reason: " + response.stopReason());
            } catch (AcpClientSession.AcpError e) {
                System.out.println("Recovery failed: " + e.getMessage());
            }
            System.out.println();

            System.out.println("=== Demo Complete ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
