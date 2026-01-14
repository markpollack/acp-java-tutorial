/*
 * Module 19: MCP Agent
 *
 * An agent that receives and reports MCP server configurations.
 *
 * Key concepts:
 * - AgentCapabilities.mcpCapabilities() - advertise MCP support
 * - NewSessionRequest.mcpServers() - receive MCP server configs
 * - McpServerStdio, McpServerHttp, McpServerSse - MCP server types
 *
 * Build & run:
 *   ./mvnw package -pl module-19-mcp-servers -q
 *   ./mvnw exec:java -pl module-19-mcp-servers
 */
package com.acptutorial.module19;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServer;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServerHttp;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServerSse;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServerStdio;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class McpAgent {

    // Store MCP servers per session
    private static final Map<String, List<McpServer>> sessionMcpServers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.err.println("[McpAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[McpAgent] Initialize");

                // Advertise MCP capabilities (we support HTTP and SSE)
                var mcpCaps = new McpCapabilities(true, true);
                var agentCaps = new AgentCapabilities(
                    true,  // loadSession
                    mcpCaps,
                    new PromptCapabilities()
                );

                return new InitializeResponse(1, agentCaps, List.of());
            })

            .newSessionHandler(req -> {
                String sessionId = UUID.randomUUID().toString();
                System.err.println("[McpAgent] New session: " + sessionId);

                // Store MCP servers for this session
                List<McpServer> mcpServers = req.mcpServers();
                if (mcpServers != null && !mcpServers.isEmpty()) {
                    sessionMcpServers.put(sessionId, new ArrayList<>(mcpServers));
                    System.err.println("[McpAgent] Received " + mcpServers.size() + " MCP server(s)");
                    for (McpServer server : mcpServers) {
                        System.err.println("[McpAgent]   - " + describeMcpServer(server));
                    }
                } else {
                    System.err.println("[McpAgent] No MCP servers provided");
                }

                return new NewSessionResponse(sessionId, null, null);
            })

            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();
                String text = req.prompt().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse("");

                System.err.println("[McpAgent] Prompt: " + text);

                StringBuilder response = new StringBuilder();

                if (text.contains("list")) {
                    // List MCP servers for this session
                    List<McpServer> servers = sessionMcpServers.get(sessionId);
                    if (servers == null || servers.isEmpty()) {
                        response.append("No MCP servers configured for this session.");
                    } else {
                        response.append("MCP servers for this session:\n\n");
                        for (int i = 0; i < servers.size(); i++) {
                            McpServer server = servers.get(i);
                            response.append((i + 1)).append(". ").append(describeMcpServer(server)).append("\n");
                        }
                    }
                } else {
                    response.append("Commands:\n");
                    response.append("  'list' - Show MCP servers configured for this session\n\n");
                    response.append("Note: MCP server configs are received via newSession/loadSession.\n");
                    response.append("The agent can then use an MCP client library to connect to them.");
                }

                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent(response.toString())));

                return new PromptResponse(StopReason.END_TURN);
            })

            .loadSessionHandler(req -> {
                String sessionId = req.sessionId();
                System.err.println("[McpAgent] Load session: " + sessionId);

                // Store MCP servers for this session (may update existing)
                List<McpServer> mcpServers = req.mcpServers();
                if (mcpServers != null && !mcpServers.isEmpty()) {
                    sessionMcpServers.put(sessionId, new ArrayList<>(mcpServers));
                    System.err.println("[McpAgent] Updated " + mcpServers.size() + " MCP server(s)");
                }

                return new com.agentclientprotocol.sdk.spec.AcpSchema.LoadSessionResponse(null, null);
            })
            .build();

        System.err.println("[McpAgent] Ready, waiting for messages...");
        agent.run();
        System.err.println("[McpAgent] Shutdown.");
    }

    private static String describeMcpServer(McpServer server) {
        if (server instanceof McpServerStdio stdio) {
            return String.format("STDIO[name=%s, command=%s, args=%s]",
                stdio.name(), stdio.command(), stdio.args());
        } else if (server instanceof McpServerHttp http) {
            return String.format("HTTP[name=%s, url=%s]",
                http.name(), http.url());
        } else if (server instanceof McpServerSse sse) {
            return String.format("SSE[name=%s, url=%s]",
                sse.name(), sse.url());
        } else {
            return server.toString();
        }
    }
}
