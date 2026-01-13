/*
 * Module 02: Protocol Basics
 *
 * Deep dive into the ACP initialize handshake and version negotiation.
 *
 * Key APIs exercised:
 * - InitializeRequest - protocol version, client capabilities
 * - InitializeResponse - agent capabilities, supported features
 * - Version negotiation semantics
 *
 * The initialize handshake is the first message exchange in ACP.
 * It establishes protocol version compatibility and exchanges capabilities.
 *
 * Build & run:
 *   ./mvnw exec:java -pl module-02-protocol-basics
 */
package com.acptutorial.module02;

import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.ClientCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.FileSystemCapability;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeRequest;

public class ProtocolBasics {

    public static void main(String[] args) {
        checkGeminiApiKey();

        var params = AgentParameters.builder("gemini")
            .arg("--experimental-acp")
            .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport).build()) {

            System.out.println("=== Module 02: Protocol Basics ===\n");

            // The initialize handshake has two key components:
            // 1. Protocol version - ensures client and agent speak the same language
            // 2. Capabilities - what features each side supports

            // Create explicit InitializeRequest to show all fields
            var clientCapabilities = new ClientCapabilities(
                new FileSystemCapability(true, true),  // We can read/write files
                false  // No terminal support
            );

            var initRequest = new InitializeRequest(
                1,  // Protocol version 1
                clientCapabilities
            );

            System.out.println("Sending initialize request:");
            System.out.println("  Protocol version: 1");
            System.out.println("  Client capabilities:");
            System.out.println("    - FileSystem: read=true, write=true");
            System.out.println("    - Terminal: false");
            System.out.println();

            // Send the initialize request
            var response = client.initialize(initRequest);

            System.out.println("Received initialize response:");
            System.out.println("  Protocol version: " + response.protocolVersion());
            System.out.println("  Agent capabilities: " + response.agentCapabilities());
            System.out.println();

            // Version negotiation rules:
            // - Client sends its supported version
            // - Agent responds with the version it will use (may be lower)
            // - If versions are incompatible, connection fails

            System.out.println("Version negotiation:");
            if (response.protocolVersion() == 1) {
                System.out.println("  Both client and agent are using protocol version 1");
            } else {
                System.out.println("  Agent negotiated to version: " + response.protocolVersion());
            }

            System.out.println("\nInitialize handshake complete!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void checkGeminiApiKey() {
        if (System.getenv("GEMINI_API_KEY") == null) {
            System.err.println("ERROR: GEMINI_API_KEY not set. See module-01 for setup instructions.");
            System.exit(1);
        }
    }
}
