/*
 * Module 04: Prompts
 *
 * Deep dive into prompt requests and response handling.
 *
 * Key APIs exercised:
 * - PromptRequest - session ID, content list
 * - PromptResponse - stop reason
 * - StopReason - END_TURN, MAX_TOKENS, REFUSAL, CANCELLED, etc. (an open value type, not an enum)
 * - Content types - TextContent for text prompts
 *
 * The prompt/response cycle is the core of ACP communication.
 * Understanding stop reasons helps handle different agent behaviors.
 *
 * Build & run:
 *   ./mvnw exec:java -pl module-04-prompts
 */
package com.acptutorial.module04;

import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class PromptsClient {

    public static void main(String[] args) {

        var params = AgentParameters.builder("grok")
            .arg("agent")
            .arg("stdio")
            .build();

        var transport = new StdioAcpClientTransport(params);

        // Print the agent's streamed answer, so each prompt's response is visible
        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    if (notification.update() instanceof AgentMessageChunk msg
                            && msg.content() instanceof TextContent text) {
                        System.out.print(text.text());
                    }
                })
                .build()) {

            System.out.println("=== Module 04: Prompts ===\n");

            client.initialize();
            var session = client.newSession(new NewSessionRequest(System.getProperty("user.dir"), List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            // Prompt 1: Simple question
            System.out.println("--- Prompt 1: Simple question ---");
            var prompt1 = new PromptRequest(
                session.sessionId(),
                List.of(new TextContent("What is 2+2? Reply with just the number."))
            );
            System.out.println("Sending: " + ((TextContent) prompt1.prompt().get(0)).text());

            var response1 = client.prompt(prompt1);
            System.out.println();
            System.out.println("Stop reason: " + response1.stopReason());
            explainStopReason(response1.stopReason());
            System.out.println();

            // Prompt 2: Multi-part content
            System.out.println("--- Prompt 2: Multi-part content ---");
            var prompt2 = new PromptRequest(
                session.sessionId(),
                List.of(
                    new TextContent("I have two questions:"),
                    new TextContent("1. What is the capital of France?"),
                    new TextContent("2. What is the capital of Japan?"),
                    new TextContent("Answer briefly.")
                )
            );
            System.out.println("Sending multi-part prompt (4 TextContent items)");

            var response2 = client.prompt(prompt2);
            System.out.println();
            System.out.println("Stop reason: " + response2.stopReason());
            System.out.println();

            // Prompt 3: Follow-up (conversation continuity)
            System.out.println("--- Prompt 3: Follow-up ---");
            var prompt3 = new PromptRequest(
                session.sessionId(),
                List.of(new TextContent("What was my first question in this session?"))
            );
            System.out.println("Sending follow-up question");

            var response3 = client.prompt(prompt3);
            System.out.println();
            System.out.println("Stop reason: " + response3.stopReason());
            System.out.println();

            System.out.println("=== Stop Reason Summary ===");
            System.out.println("END_TURN          - Agent finished responding normally");
            System.out.println("MAX_TOKENS        - Response hit token limit");
            System.out.println("MAX_TURN_REQUESTS - Too many turn requests");
            System.out.println("REFUSAL           - Agent refused the request");
            System.out.println("CANCELLED         - Request was cancelled");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void explainStopReason(StopReason reason) {
        // StopReason is an open value type, not an enum: a newer agent may send a stop
        // reason this SDK does not know, and it is kept rather than failing the response.
        // So compare with equals (never ==, never switch) and always have a fallback.
        String explanation;
        if (StopReason.END_TURN.equals(reason)) {
            explanation = "Agent completed its response naturally";
        } else if (StopReason.MAX_TOKENS.equals(reason)) {
            explanation = "Response was truncated due to length";
        } else if (StopReason.MAX_TURN_REQUESTS.equals(reason)) {
            explanation = "Too many turn requests";
        } else if (StopReason.REFUSAL.equals(reason)) {
            explanation = "Agent refused the request";
        } else if (StopReason.CANCELLED.equals(reason)) {
            explanation = "Request was cancelled";
        } else {
            explanation = "A stop reason this SDK does not know: " + reason;
        }
        System.out.println("  -> " + explanation);
    }

}
