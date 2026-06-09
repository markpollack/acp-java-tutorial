/*
 * Module 27: LangChain4j Chatbot Agent (portability flavor)
 *
 * The third way to build the SAME chatbot agent:
 *   - Module 25: raw Anthropic Java SDK.
 *   - Module 26: Spring AI ChatClient.
 *   - This module: LangChain4j's ChatModel.
 *
 * The ACP skeleton is identical to the echo agent (module 12). The ONLY change is
 * the prompt handler: instead of echoing, it asks the model via LangChain4j.
 *
 * Why LangChain4j is a "portability" layer: ChatModel is an interface with many
 * implementations. We use AnthropicChatModel here; swap langchain4j-anthropic for
 * langchain4j-open-ai / -google-ai-gemini / -ollama and change the builder - the
 * ACP agent does not change.
 *
 * Prerequisites:
 *   - ANTHROPIC_API_KEY environment variable set (this key is actually used).
 *   - Java 17
 *
 * Build & run:
 *   ./mvnw package -pl module-27-langchain4j-chatbot -q
 *   ./mvnw exec:java -pl module-27-langchain4j-chatbot
 */
package com.acptutorial.module27;

import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;

public class LangChain4jChatbotAgent {

    /** Swap this (and the langchain4j-* dependency) to change provider. */
    private static final String MODEL_NAME = "claude-sonnet-4-6";

    public static void main(String[] args) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: ANTHROPIC_API_KEY is not set. "
                + "Get a key at https://console.anthropic.com/ and export it.");
            System.exit(1);
        }

        // Build the model once. ChatModel is LangChain4j's provider-agnostic interface.
        ChatModel model = AnthropicChatModel.builder()
            .apiKey(apiKey)
            .modelName(MODEL_NAME)
            .maxTokens(1024)
            .build();

        System.err.println("[LangChain4jChatbotAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            // --- Identical to the echo agent (module 12) ---
            .initializeHandler(req -> InitializeResponse.ok())
            .newSessionHandler(req ->
                new NewSessionResponse(UUID.randomUUID().toString(), null, null))

            // --- The ONLY change from echo: ask the model via LangChain4j ---
            .promptHandler((req, context) -> {
                String answer = model.chat(req.text());
                context.sendMessage(answer);
                return PromptResponse.endTurn();
            })
            .build();

        System.err.println("[LangChain4jChatbotAgent] Ready, waiting for messages...");
        agent.run();
        System.err.println("[LangChain4jChatbotAgent] Shutdown.");
    }
}
