/*
 * Module 29: JetBrains Integration
 *
 * This module demonstrates how to connect a Java ACP agent to JetBrains IDEs
 * (IntelliJ IDEA, PyCharm, WebStorm, etc.).
 *
 * JetBrains joined the ACP initiative in October 2025, working with Zed to
 * create a unified protocol instead of competing standards.
 *
 * Key concept: The SAME agent code works whether launched from Zed, JetBrains,
 * VS Code, or any ACP-compatible client. Only the configuration differs.
 *
 * Build:
 *   ./mvnw package -pl module-29-jetbrains-integration -q
 *
 * Configure JetBrains - create/edit ~/.jetbrains/acp.json:
 *   {
 *     "default_mcp_settings": {},
 *     "agent_servers": {
 *       "Java Tutorial Agent": {
 *         "command": "java",
 *         "args": ["-jar", "/absolute/path/to/jetbrains-agent.jar", "acp"]
 *       }
 *     }
 *   }
 */
package com.acptutorial.module29;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCall;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallLocation;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallStatus;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallUpdateNotification;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolKind;


public class JetBrainsAgent {

    /** cwd per session - the project the IDE has open. Needed to build absolute paths. */
    private static final Map<String, String> SESSION_CWDS = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.err.println("[JetBrainsAgent] Starting Java ACP agent...");

        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[JetBrainsAgent] Received initialize request");
                return InitializeResponse.ok();
            })

            .newSessionHandler(req -> {
                System.err.println("[JetBrainsAgent] Creating session for cwd: " + req.cwd());
                String sessionId = UUID.randomUUID().toString();
                SESSION_CWDS.put(sessionId, req.cwd());
                return new NewSessionResponse(sessionId, null, null);
            })

            .promptHandler((req, context) -> {
                String promptText = req.text();

                System.err.println("[JetBrainsAgent] Processing: " + promptText);

                // Follow-along demo: emit tool call locations so the editor
                // jumps to each file we "visit"
                if (promptText.toLowerCase().contains("tour")) {
                    return tourProject(context);
                }

                // Send thinking update using convenience method
                context.sendThought("Analyzing your request...");

                // Generate and send response using convenience method
                String response = generateResponse(promptText);
                context.sendMessage(response);

                return PromptResponse.endTurn();
            })
            .build();

        System.err.println("[JetBrainsAgent] Ready - waiting for IDE to connect...");
        agent.run();
        System.err.println("[JetBrainsAgent] Shutdown.");
    }

    /**
     * Follow-along demo ("Following the Agent" in the ACP spec).
     *
     * There is no agent-to-client "openFile" request in ACP. Instead, tool call
     * updates carry a "locations" field - a list of {path, line} entries - and
     * clients that support follow-along navigate to them as they arrive.
     *
     * In Zed: click the crosshair icon at the bottom left of the Agent Panel
     * (or hold cmd/ctrl when submitting), then say "tour" - the editor jumps
     * to each file this method reports.
     *
     * JetBrains IDEs render the locations on the tool call cards in the chat,
     * but have no auto-follow toggle as of June 2026.
     */
    private static PromptResponse tourProject(SyncPromptContext context) {
        String sessionId = context.getSessionId();
        String cwd = SESSION_CWDS.getOrDefault(sessionId, ".");

        context.sendMessage("Taking a quick tour of your project - " +
            "with follow-along enabled, your editor jumps to each stop.\n");

        // Visit well-known files that exist in most projects
        List<Path> stops = List.of(
                Path.of(cwd, "pom.xml"),
                Path.of(cwd, "build.gradle.kts"),
                Path.of(cwd, "build.gradle"),
                Path.of(cwd, "package.json"),
                Path.of(cwd, "README.md"),
                Path.of(cwd, ".gitignore"))
            .stream()
            .filter(Files::isRegularFile)
            .toList();

        if (stops.isEmpty()) {
            context.sendMessage("No well-known files (pom.xml, README.md, ...) found in " + cwd);
            return PromptResponse.endTurn();
        }

        int stopNumber = 0;
        for (Path stop : stops) {
            String toolCallId = "tour-" + (++stopNumber);
            String absolutePath = stop.toAbsolutePath().toString();

            // Tool call start: location at line 1 - the editor opens the file
            context.sendUpdate(sessionId, new ToolCall(
                "tool_call", toolCallId,
                "Visiting " + stop.getFileName(),
                ToolKind.READ, ToolCallStatus.IN_PROGRESS,
                List.of(),
                List.of(new ToolCallLocation(absolutePath, 1)),
                null, null, null));

            pause(1500);

            // Tool call update: location at the middle of the file - the
            // editor scrolls there, demonstrating line-level following
            context.sendUpdate(sessionId, new ToolCallUpdateNotification(
                "tool_call_update", toolCallId,
                "Visited " + stop.getFileName(),
                ToolKind.READ, ToolCallStatus.COMPLETED,
                List.of(),
                List.of(new ToolCallLocation(absolutePath, middleLine(stop))),
                null, null, null));
        }

        context.sendMessage("Tour complete - visited " + stops.size() + " file(s).");
        return PromptResponse.endTurn();
    }

    private static Integer middleLine(Path file) {
        try {
            return Math.max(1, Files.readAllLines(file).size() / 2);
        }
        catch (Exception e) {
            return 1;
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String generateResponse(String prompt) {
        String lowerPrompt = prompt.toLowerCase();

        if (lowerPrompt.contains("hello") || lowerPrompt.contains("hi")) {
            return "Hello! I'm a Java ACP agent running in your JetBrains IDE. " +
                   "I was built with the ACP Java SDK.";
        }

        if (lowerPrompt.contains("help")) {
            return """
                I'm a demonstration agent from the ACP Java Tutorial.

                I work with:
                - IntelliJ IDEA
                - PyCharm
                - WebStorm
                - All other JetBrains IDEs with AI Assistant

                The same agent code also works in Zed and VS Code!

                Try saying "tour" - I'll report file locations as I walk your
                project, and editors with follow-along (Zed's crosshair icon)
                will jump to each file.
                """;
        }

        if (lowerPrompt.contains("jetbrains")) {
            return """
                JetBrains joined the ACP initiative in October 2025.

                They were about to publish their own protocol, but teamed up
                with Zed instead to create a single unified standard.

                Key quote from JetBrains: "No vendor lock-in" - you choose
                your agent, you choose your IDE.
                """;
        }

        return "You asked: \"" + prompt + "\"\n\n" +
               "I'm a demo agent. Try 'help' or ask about 'JetBrains'.";
    }
}
