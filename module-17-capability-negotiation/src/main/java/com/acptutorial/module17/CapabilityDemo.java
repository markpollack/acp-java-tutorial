/*
 * Module 17: Capability Negotiation Demo
 *
 * Demonstrates capability negotiation between client and agent.
 *
 * Key APIs:
 * - ClientCapabilities - advertise client capabilities
 * - client.getAgentCapabilities() - check what agent supports
 * - NegotiatedCapabilities.supportsXxx() - check specific capabilities
 *
 * Build & run:
 *   ./mvnw package -pl module-17-capability-negotiation -q
 *   ./mvnw exec:java -pl module-17-capability-negotiation
 */
package com.acptutorial.module17;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReadTextFileResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.WriteTextFileResponse;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.ClientCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.FileSystemCapability;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class CapabilityDemo {

    private static final String MODULE_NAME = "module-17-capability-negotiation";
    private static final String JAR_NAME = "capability-agent.jar";

    public static void main(String[] args) {
        System.out.println("=== Module 17: Capability Negotiation ===\n");

        // Part 1: Full file capabilities client. A client advertises only what it serves:
        // build() fails for an advertised capability without its handler. Terminal needs all
        // five terminal handlers, so it is left to module 18, which serves them.
        System.out.println("--- Part 1: Full Capabilities Client ---");
        runWithCapabilities(
            new ClientCapabilities(
                new FileSystemCapability(true, true),  // readTextFile=true, writeTextFile=true
                false  // terminal: no terminal handlers here (module 18)
            ),
            "Full file capabilities client"
        );

        System.out.println();

        // Part 2: Limited capabilities client (read-only)
        System.out.println("--- Part 2: Limited Capabilities Client (Read-Only) ---");
        runWithCapabilities(
            new ClientCapabilities(
                new FileSystemCapability(true, false),  // readTextFile=true, writeTextFile=false
                false  // terminal=false
            ),
            "Read-only client"
        );

        System.out.println();

        // Part 3: Minimal capabilities client
        System.out.println("--- Part 3: Minimal Capabilities Client ---");
        runWithCapabilities(
            new ClientCapabilities(
                new FileSystemCapability(false, false),  // no file access
                false  // no terminal
            ),
            "Minimal client"
        );

        System.out.println("\n=== Demo Complete ===");
    }

    private static void runWithCapabilities(ClientCapabilities clientCaps, String description) {
        System.out.println("Starting " + description + "...");
        System.out.println("  Advertised capabilities:");
        System.out.println("    - fs.readTextFile: " + (clientCaps.fs() != null ? clientCaps.fs().readTextFile() : false));
        System.out.println("    - fs.writeTextFile: " + (clientCaps.fs() != null ? clientCaps.fs().writeTextFile() : false));
        System.out.println("    - terminal: " + clientCaps.terminal());

        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg(findAgentJar())
            .build();

        var transport = new StdioAcpClientTransport(params);

        AcpClient.SyncSpec spec = AcpClient.sync(transport)
                .clientCapabilities(clientCaps)  // what initialize() advertises
                .sessionUpdateHandler(notification -> {
                    var update = notification.update();
                    if (update instanceof AgentMessageChunk msg) {
                        String text = ((TextContent) msg.content()).text();
                        System.out.print("    Agent: " + text);
                    }
                });
        // Register a handler for each capability we advertise, and only those: build() fails for
        // an advertised capability without its handler, and warns about a handler for one that
        // is not advertised (the agent will not call it).
        if (clientCaps.fs() != null && Boolean.TRUE.equals(clientCaps.fs().readTextFile())) {
            spec.readTextFileHandler(req -> {
                try {
                    String content = Files.readString(Path.of(req.path()));
                    return new ReadTextFileResponse(content);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read file: " + req.path(), e);
                }
            });
        }
        if (clientCaps.fs() != null && Boolean.TRUE.equals(clientCaps.fs().writeTextFile())) {
            spec.writeTextFileHandler(req -> {
                try {
                    Files.writeString(Path.of(req.path()), req.content());
                    return new WriteTextFileResponse();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to write file: " + req.path(), e);
                }
            });
        }

        try (AcpSyncClient client = spec.build()) {

            // Initialize: sends the capabilities set on the builder
            client.initialize();

            NegotiatedCapabilities agentCaps = client.getAgentCapabilities();
            System.out.println("\n  Agent capabilities:");
            System.out.println("    - loadSession: " + agentCaps.supportsLoadSession());
            System.out.println("    - imageContent: " + agentCaps.supportsImageContent());
            System.out.println("    - audioContent: " + agentCaps.supportsAudioContent());
            System.out.println("    - embeddedContext: " + agentCaps.supportsEmbeddedContext());
            System.out.println("    - mcpHttp: " + agentCaps.supportsMcpHttp());
            System.out.println("    - mcpSse: " + agentCaps.supportsMcpSse());

            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            String sessionId = session.sessionId();

            // Test 1: Ask agent to check capabilities it sees
            System.out.println("\n  Test: Agent checking our capabilities...");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("check capabilities"))));
            System.out.println();

            // Test 2: Ask agent to try file read
            System.out.println("  Test: Agent trying file read...");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("read a file"))));
            System.out.println();

            // Test 3: Ask agent to try file write
            System.out.println("  Test: Agent trying file write...");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("write a file"))));
            System.out.println();

            // Test 4: Ask agent to try terminal
            System.out.println("  Test: Agent trying terminal...");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("use terminal"))));
            System.out.println();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
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
