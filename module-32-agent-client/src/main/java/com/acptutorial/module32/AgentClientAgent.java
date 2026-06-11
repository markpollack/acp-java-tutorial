/*
 * Module 32: Agent-Client Agent - from chatbot to AGENT
 *
 * The chatbot (module 25) answers from the model's head: one completion call.
 * This agent has HANDS - its @Prompt handler hands the user's goal to an agent
 * LOOP that reads and edits files and runs tools in the open project, for as many
 * turns as it takes, then reports back.
 *
 * The ACP skeleton is identical to the chatbot (same @Initialize / @NewSession /
 * @Prompt as module 25). The ONLY change is the body of @Prompt:
 *
 *     AgentClient.create(model).goal(text).workingDirectory(cwd).run();
 *
 * That's `agent-client` (the agentworks AgentClient abstraction). `agent-claude`
 * drives the local Claude CLI as the underlying model.
 *
 * This is the bridge between the completion chatbot (25/26/27) and a full domain
 * buddy like bud. It is NOT bud: there is no knowledge base, no curated report,
 * no multi-step named workflow - just an agent loop wired into one ACP handler.
 *
 * Prerequisites (DIFFERENT from the chatbot):
 *   - The Claude CLI (Claude Code) installed and logged in. agent-claude drives
 *     that CLI; it does NOT call the Anthropic HTTP API directly.
 *   - Billing: the CLI uses your Claude subscription when ANTHROPIC_API_KEY is
 *     ABSENT (and the API when it's present). In the IDE, launch this agent via
 *     the acp-no-key.sh wrapper - NOT acp-with-key.sh.
 *   - Java 17.
 *
 * Build & run:
 *   ./mvnw package -pl module-32-agent-client -q
 *   ./mvnw exec:java -pl module-32-agent-client
 */
package com.acptutorial.module32;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.support.AcpAgentSupport;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.Initialize;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;

import io.github.markpollack.agents.claude.ClaudeAgentModel;
import io.github.markpollack.agents.claude.ClaudeAgentOptions;
import io.github.markpollack.agents.client.AgentClient;
import io.github.markpollack.agents.client.AgentClientResponse;

@AcpAgent(name = "agent-client-agent", version = "1.0")
public class AgentClientAgent {

    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final int MAX_TURNS = 40;

    // The project the IDE has open, per session - that's the agent's working directory.
    private final Map<String, String> sessionCwds = new ConcurrentHashMap<>();

    // --- Identical to the chatbot (module 25): the ACP plumbing ---

    @Initialize
    public InitializeResponse initialize(InitializeRequest request) {
        return InitializeResponse.ok();
    }

    @NewSession
    public NewSessionResponse newSession(NewSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();
        // Remember the cwd so the agent acts in the project the client opened.
        sessionCwds.put(sessionId, request.cwd());
        return new NewSessionResponse(sessionId, null, null);
    }

    // --- The ONLY change from the chatbot: run an agent loop, don't just complete ---

    @Prompt
    public PromptResponse prompt(PromptRequest request, SyncPromptContext context) {
        String goal = request.text();
        String cwd = sessionCwds.getOrDefault(context.getSessionId(), System.getProperty("user.dir"));
        Path workingDir = Path.of(cwd);

        context.sendMessage("Running an agent in " + cwd
            + " - it can read and edit files and may take a moment...\n\n");

        ClaudeAgentOptions options = ClaudeAgentOptions.builder()
            .model(MODEL)
            .maxTurns(MAX_TURNS)
            .yolo(true) // non-interactive: don't pause for permission prompts
            .build();

        // ClaudeAgentModel wraps the local Claude CLI as a reusable model.
        try (ClaudeAgentModel model = ClaudeAgentModel.builder()
                .workingDirectory(workingDir)
                .defaultOptions(options)
                .build()) {

            if (!model.isAvailable()) {
                context.sendMessage("The Claude CLI isn't available. Install it and run `claude` "
                    + "once to log in, then try again.");
                return PromptResponse.endTurn();
            }

            // THE line. AgentClient drives the model through as many tool-using turns
            // as the goal needs, in the working directory. Blocking - returns when done.
            AgentClientResponse response = AgentClient.create(model)
                .goal(goal)
                .workingDirectory(workingDir)
                .run();

            context.sendMessage(response.getResult());
        }
        catch (Exception e) {
            context.sendMessage("Agent run failed: " + e.getMessage());
        }
        return PromptResponse.endTurn();
    }

    public static void main(String[] args) {
        System.err.println("[AgentClientAgent] Starting (annotation-based, no Spring)...");
        // AcpAgentSupport scans this instance for @Initialize/@NewSession/@Prompt and
        // wires them to the stdio transport. run() blocks until the client disconnects.
        AcpAgentSupport.create(new AgentClientAgent())
            .transport(new StdioAcpAgentTransport())
            .build()
            .run();
        System.err.println("[AgentClientAgent] Shutdown.");
    }
}
