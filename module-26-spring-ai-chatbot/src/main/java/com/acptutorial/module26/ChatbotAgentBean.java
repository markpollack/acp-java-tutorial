/*
 * Module 26: Spring AI Chatbot Agent - The @AcpAgent Bean
 *
 * Compare three ways to build the SAME chatbot agent:
 *   - Module 25: raw Anthropic SDK, in the prompt handler.
 *   - This module: Spring AI's ChatClient (multi-provider) in a Spring bean.
 *   - Module 27: LangChain4j's ChatModel.
 *
 * The ACP parts (@Initialize / @NewSession / @Prompt) are identical to module 23's
 * echo bean. The ONLY difference from echo is the body of @Prompt: instead of
 * echoing, it asks the model via ChatClient.
 *
 * Why this is the "portability" flavor: ChatClient sits over a ChatModel that
 * Spring AI autoconfigures from the spring-ai-starter-model-* dependency on the
 * classpath. Swap that starter (or set spring.ai.* properties) to talk to
 * OpenAI, Gemini, Ollama, etc. - this code does not change.
 */
package com.acptutorial.module26;

import java.util.UUID;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
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

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

@Component
@AcpAgent(name = "spring-ai-chatbot", version = "1.0")
public class ChatbotAgentBean {

    private final ChatClient chatClient;

    // Spring AI autoconfigures a ChatClient.Builder from the model starter on the
    // classpath (here: spring-ai-starter-model-anthropic).
    public ChatbotAgentBean(ChatClient.Builder builder) {
        // Conversation memory, the Spring AI way. Module 25 kept its own
        // List<MessageParam> per session by hand; here a sliding window of the last
        // 20 messages lives in a ChatMemory (in-memory by default), and an advisor
        // injects it into every call. That's the minimum for a multi-turn chatbot.
        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(20).build();
        this.chatClient = builder
            .defaultSystem("You are a concise, friendly assistant running as an ACP agent inside an IDE.")
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
    }

    @Initialize
    public InitializeResponse initialize(InitializeRequest request) {
        return InitializeResponse.ok();
    }

    @NewSession
    public NewSessionResponse newSession(NewSessionRequest request) {
        return new NewSessionResponse(UUID.randomUUID().toString(), null, null);
    }

    @Prompt
    public PromptResponse prompt(PromptRequest request, SyncPromptContext context) {
        // Ask the model. The CONVERSATION_ID param scopes the memory window to this
        // ACP session, so each IDE chat keeps its own history and follow-ups have context.
        String answer = chatClient.prompt()
            .user(request.text())
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, context.getSessionId()))
            .call()
            .content();
        context.sendMessage(answer);
        return PromptResponse.endTurn();
        // Streaming variant (Spring AI returns a Flux<String>):
        //   chatClient.prompt().user(request.text())
        //       .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, context.getSessionId()))
        //       .stream().content().toStream().forEach(context::sendMessage);
    }

}
