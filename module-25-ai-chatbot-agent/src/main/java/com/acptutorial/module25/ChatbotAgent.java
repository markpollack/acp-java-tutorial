/*
 * Module 25: AI Chatbot Agent - Give Your Agent a Brain
 *
 * Module 12 echoed. This one thinks.
 *
 * The ACP skeleton is IDENTICAL to the echo agent (module 12):
 *   - initializeHandler : unchanged
 *   - newSessionHandler : unchanged
 *   - promptHandler     : the ONLY change - instead of echoing, it calls Claude
 *
 * This is the first module in the tutorial where you can point at the exact
 * line of Java that invokes the AI:
 *
 *     anthropic.messages().createStreaming(...)
 *
 * In the client modules (01-08) the AI lived inside the Gemini subprocess; in
 * the other agent modules (12-22) the agent just echoed. Here the LLM call is
 * right in front of you - and it's a plain method call you can swap for any
 * provider's SDK (see the Spring AI / LangChain4j variants in the README).
 *
 * What it does:
 *   - Streams Claude's response token-by-token back to the client as
 *     agent_message_chunk updates. All chunks in one turn share a single
 *     messageId, so the client renders them as one growing assistant message.
 *   - Keeps a short per-session conversation history so follow-up prompts in
 *     the same session have context.
 *
 * Prerequisites:
 *   - ANTHROPIC_API_KEY environment variable set. Unlike the Gemini client
 *     modules (where the key was checked but never read), THIS key is actually
 *     used - it's what AnthropicOkHttpClient.fromEnv() authenticates with.
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

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;

public class ChatbotAgent {

    /** Swap this for any constant in com.anthropic.models.messages.Model. */
    private static final Model MODEL = Model.CLAUDE_SONNET_4_6;
    private static final long MAX_TOKENS = 1024;
    private static final String SYSTEM_PROMPT =
        "You are a concise, friendly assistant running as an ACP agent inside an IDE.";

    // One Anthropic client for the whole agent. fromEnv() reads ANTHROPIC_API_KEY.
    private static final AnthropicClient ANTHROPIC = AnthropicOkHttpClient.fromEnv();

    // Per-session conversation history so follow-up prompts have context.
    private static final Map<String, List<MessageParam>> HISTORY = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // Fail fast with a clear message if the key is missing - this key is really used.
        if (isBlank(System.getenv("ANTHROPIC_API_KEY"))) {
            System.err.println("""
                ERROR: ANTHROPIC_API_KEY environment variable is not set.

                Get a key at https://console.anthropic.com/ and export it:
                  export ANTHROPIC_API_KEY=sk-ant-...

                In IntelliJ: Run > Edit Configurations > Environment variables.
                """);
            System.exit(1);
        }

        System.err.println("[ChatbotAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            // --- Identical to the echo agent (module 12) ---
            .initializeHandler(req -> InitializeResponse.ok())
            .newSessionHandler(req ->
                new NewSessionResponse(UUID.randomUUID().toString(), null, null))

            // --- The ONLY change from the echo agent: call Claude, don't echo ---
            .promptHandler((req, context) -> {
                String userText = req.text();
                List<MessageParam> turns =
                    HISTORY.computeIfAbsent(context.getSessionId(), k -> new ArrayList<>());

                // Build the request: system prompt + prior turns + this user message.
                MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(MAX_TOKENS)
                    .system(SYSTEM_PROMPT);
                turns.forEach(params::addMessage);
                params.addUserMessage(userText);

                // Stream Claude's answer back to the client, chunk by chunk. The
                // client coalesces consecutive agent_message_chunk updates into one
                // growing assistant message. (ACP 0.13+ adds a sendMessage(text,
                // messageId) overload for explicit grouping; 0.12 streams like this.)
                StringBuilder full = new StringBuilder();
                try (StreamResponse<RawMessageStreamEvent> stream =
                         ANTHROPIC.messages().createStreaming(params.build())) {
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
            })
            .build();

        System.err.println("[ChatbotAgent] Ready, waiting for messages...");
        agent.run();  // blocks until the client disconnects
        System.err.println("[ChatbotAgent] Shutdown.");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
