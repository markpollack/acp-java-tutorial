/*
 * Module 19: MCP Servers Demo
 *
 * Demonstrates passing MCP server configurations to agents.
 *
 * Key APIs:
 * - NewSessionRequest with mcpServers list
 * - McpServerStdio - STDIO-based MCP server
 * - McpServerHttp - HTTP-based MCP server
 * - McpServerSse - SSE-based MCP server
 *
 * Build & run:
 *   ./mvnw package -pl module-19-mcp-servers -q
 *   ./mvnw exec:java -pl module-19-mcp-servers
 */
package com.acptutorial.module19;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServer;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServerHttp;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServerSse;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServerStdio;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class McpDemo {

    private static final String MODULE_NAME = "module-19-mcp-servers";
    private static final String JAR_NAME = "mcp-agent.jar";

    public static void main(String[] args) {
        System.out.println("=== Module 19: MCP Servers ===\n");

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

            // Initialize and check agent MCP capabilities
            client.initialize();
            System.out.println("Connected to McpAgent\n");

            NegotiatedCapabilities agentCaps = client.getAgentCapabilities();
            System.out.println("Agent MCP capabilities:");
            System.out.println("  - HTTP: " + agentCaps.supportsMcpHttp());
            System.out.println("  - SSE: " + agentCaps.supportsMcpSse());
            System.out.println();

            String cwd = System.getProperty("user.dir");

            // Part 1: Session with no MCP servers
            System.out.println("--- Part 1: Session without MCP servers ---");
            var session1 = client.newSession(new NewSessionRequest(cwd, List.of()));
            String sessionId1 = session1.sessionId();
            System.out.println("Session created: " + sessionId1 + "\n");

            client.prompt(new PromptRequest(sessionId1,
                List.of(new TextContent("list"))));
            System.out.println("\n");

            // Part 2: Session with STDIO MCP server
            System.out.println("--- Part 2: Session with STDIO MCP server ---");
            List<McpServer> stdioServers = List.of(
                new McpServerStdio(
                    "filesystem",
                    "npx",
                    List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"),
                    List.of()
                )
            );
            var session2 = client.newSession(new NewSessionRequest(cwd, stdioServers));
            String sessionId2 = session2.sessionId();
            System.out.println("Session created: " + sessionId2 + "\n");

            client.prompt(new PromptRequest(sessionId2,
                List.of(new TextContent("list"))));
            System.out.println("\n");

            // Part 3: Session with multiple MCP servers (HTTP and SSE)
            System.out.println("--- Part 3: Session with multiple MCP servers ---");
            List<McpServer> multipleServers = List.of(
                new McpServerStdio(
                    "git",
                    "npx",
                    List.of("-y", "@modelcontextprotocol/server-git"),
                    List.of()
                ),
                new McpServerHttp(
                    "weather-api",
                    "https://api.weather.example.com/mcp",
                    List.of()
                ),
                new McpServerSse(
                    "live-data",
                    "https://stream.example.com/mcp/events",
                    List.of()
                )
            );
            var session3 = client.newSession(new NewSessionRequest(cwd, multipleServers));
            String sessionId3 = session3.sessionId();
            System.out.println("Session created: " + sessionId3 + "\n");

            client.prompt(new PromptRequest(sessionId3,
                List.of(new TextContent("list"))));
            System.out.println("\n");

            System.out.println("=== Demo Complete ===");
            System.out.println("\nNote: The agent receives MCP server configs but doesn't connect to them.");
            System.out.println("In a real agent, you would use an MCP client library to establish connections.");

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
