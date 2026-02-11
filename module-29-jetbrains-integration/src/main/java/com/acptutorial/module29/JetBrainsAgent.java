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
 *     "agent_servers": {
 *       "Java Tutorial Agent": {
 *         "command": "java",
 *         "args": ["-jar", "/absolute/path/to/jetbrains-agent.jar"]
 *       }
 *     }
 *   }
 */
package com.acptutorial.module29;

import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;


public class JetBrainsAgent {

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
                return new NewSessionResponse(UUID.randomUUID().toString(), null, null);
            })

            .promptHandler((req, context) -> {
                String promptText = req.text();

                System.err.println("[JetBrainsAgent] Processing: " + promptText);

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
