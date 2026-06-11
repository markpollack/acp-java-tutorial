/*
 * Module 25: AI Chatbot Agent - Give Your Agent a Brain (and switch to annotations)
 *
 * Module 12 echoed using the fluent builder (AcpAgent.sync().promptHandler(...)).
 * This one thinks - and that's exactly the moment to switch to the @AcpAgent
 * annotation API. Once the prompt handler does real work (call an LLM, stream
 * chunks, keep history), a labelled @Prompt METHOD reads far better than a
 * builder lambda: the ACP plumbing disappears and what's left is one method you
 * can point at and say "this is where the AI happens".
 *
 * Two ways to run an annotated agent:
 *   - Module 23 lets Spring Boot discover the @AcpAgent bean and manage its
 *     lifecycle for you.
 *   - THIS module bootstraps it by hand with AcpAgentSupport - no Spring - so
 *     you can see exactly what the annotations buy you. AcpAgentSupport.create()
 *     scans this class for @Initialize / @NewSession / @Prompt and wires each to
 *     the transport; .run() starts the agent and blocks until the client leaves.
 *
 * The ACP skeleton vs the echo agent (module 12):
 *   - @Initialize / @NewSession : the same boilerplate, now as annotated methods
 *   - @Prompt                   : the ONLY interesting method - it calls Claude
 *
 * What it does:
 *   - Streams Claude's response token-by-token back to the client as
 *     agent_message_chunk updates (context.sendMessage). All chunks in one turn
 *     render as one growing assistant message.
 *   - Keeps a short per-session conversation history so follow-up prompts in the
 *     same session have context (now an instance field, not a static map).
 *
 * Prerequisites:
 *   - ANTHROPIC_API_KEY environment variable set - this key is actually used;
 *     it's what AnthropicOkHttpClient.fromEnv() authenticates with.
 *   - Java 17
 *
 * Build & run:
 *   ./mvnw package -pl module-25-ai-chatbot-agent -q
 *   ./mvnw exec:java -pl module-25-ai-chatbot-agent
 */
package com.acptutorial.module25;

import java.util.ArrayList;
import java.util.List;
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

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;

@AcpAgent(name = "chatbot-agent", version = "1.0")
public class ChatbotAgent {

    /** Swap this for any constant in com.anthropic.models.messages.Model. */
    private static final Model MODEL = Model.CLAUDE_SONNET_4_6;
    private static final long MAX_TOKENS = 1024;
    private static final String SYSTEM_PROMPT =
        "You are a concise, friendly assistant running as an ACP agent inside an IDE.";

    // One Anthropic client for the whole agent. fromEnv() reads ANTHROPIC_API_KEY.
    private final AnthropicClient anthropic = AnthropicOkHttpClient.fromEnv();

    // Per-session conversation history so follow-up prompts have context.
    private final Map<String, List<MessageParam>> history = new ConcurrentHashMap<>();

    // --- Identical to the echo agent (module 12), just as annotated methods ---

    @Initialize
    public InitializeResponse initialize(InitializeRequest request) {
        return InitializeResponse.ok();
    }

    @NewSession
    public NewSessionResponse newSession(NewSessionRequest request) {
        return new NewSessionResponse(UUID.randomUUID().toString(), null, null);
    }

    // --- The ONLY interesting method: call Claude, don't echo ---

    @Prompt
    public PromptResponse prompt(PromptRequest request, SyncPromptContext context) {
        String userText = request.text();
        List<MessageParam> turns = history.computeIfAbsent(context.getSessionId(), k -> new ArrayList<>());

        // Build the request: system prompt + prior turns + this user message.
        MessageCreateParams.Builder params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .system(SYSTEM_PROMPT);
        turns.forEach(params::addMessage);
        params.addUserMessage(userText);

        // Stream Claude's answer back to the client, chunk by chunk. The client
        // coalesces consecutive agent_message_chunk updates into one growing
        // assistant message.
        StringBuilder full = new StringBuilder();
        try (StreamResponse<RawMessageStreamEvent> stream =
                 anthropic.messages().createStreaming(params.build())) {
            stream.stream().forEach(event -> event.contentBlockDelta()
                .flatMap(deltaEvent -> deltaEvent.delta().text()) // Optional<TextDelta>
                .ifPresent(textDelta -> {
                    String piece = textDelta.text();
                    full.append(piece);
                    context.sendMessage(piece);
                }));
        }

        // Remember this turn so the next prompt in the session has context.
        turns.add(MessageParam.builder()
            .role(MessageParam.Role.USER).content(userText).build());
        turns.add(MessageParam.builder()
            .role(MessageParam.Role.ASSISTANT).content(full.toString()).build());

        return PromptResponse.endTurn();
    }

    public static void main(String[] args) {
        // Fail fast with a clear message if the key is missing - this key is really used,
        // and it is read the moment we construct the agent (AnthropicOkHttpClient.fromEnv()).
        if (isBlank(System.getenv("ANTHROPIC_API_KEY"))) {
            System.err.println("""
                ERROR: ANTHROPIC_API_KEY environment variable is not set.

                Get a key at https://console.anthropic.com/ and export it:
                  export ANTHROPIC_API_KEY=sk-ant-...

                In IntelliJ: Run > Edit Configurations > Environment variables.
                """);
            System.exit(1);
        }

        System.err.println("[ChatbotAgent] Starting (annotation-based, no Spring)...");

        // AcpAgentSupport scans this instance for @Initialize/@NewSession/@Prompt
        // and wires them to the stdio transport. run() starts and blocks until the
        // client disconnects.
        AcpAgentSupport.create(new ChatbotAgent())
            .transport(new StdioAcpAgentTransport())
            .build()
            .run();

        System.err.println("[ChatbotAgent] Shutdown.");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
