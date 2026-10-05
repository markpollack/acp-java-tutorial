/*
 * Module 02: Protocol Basics
 *
 * Deep dive into the ACP initialize handshake and version negotiation.
 *
 * Key APIs exercised:
 * - AcpClient.sync(...) handlers - the capabilities the client advertises follow from them
 * - initialize() - sends the protocol version and those capabilities
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;

public class ProtocolBasics {

    public static void main(String[] args) {

        var params = AgentParameters.builder("grok")
            .arg("agent")
            .arg("stdio")
            .build();

        var transport = new StdioAcpClientTransport(params);

        // The client advertises the capabilities its handlers serve: registering the two file
        // handlers below advertises fs.readTextFile and fs.writeTextFile in the initialize
        // request, and no terminal, since no terminal handlers are registered (module 07 covers
        // the file handlers; module 17 sets capabilities explicitly with clientCapabilities(..)).
        try (AcpSyncClient client = AcpClient.sync(transport)
                .readTextFileHandler(req -> {
                    try {
                        return new AcpSchema.ReadTextFileResponse(Files.readString(Path.of(req.path())));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .writeTextFileHandler(req -> {
                    try {
                        Files.writeString(Path.of(req.path()), req.content());
                        return new AcpSchema.WriteTextFileResponse();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .build()) {

            System.out.println("=== Module 02: Protocol Basics ===\n");

            // The initialize handshake has two key components:
            // 1. Protocol version - ensures client and agent speak the same language
            // 2. Capabilities - what features each side supports

            System.out.println("Sending initialize request:");
            System.out.println("  Protocol version: " + AcpSchema.LATEST_PROTOCOL_VERSION);
            System.out.println("  Client capabilities:");
            System.out.println("    - FileSystem: read=true, write=true");
            System.out.println("    - Terminal: false");
            System.out.println();

            // Send the initialize request: the SDK's protocol version plus the
            // capabilities the handlers imply
            var response = client.initialize();

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

}
