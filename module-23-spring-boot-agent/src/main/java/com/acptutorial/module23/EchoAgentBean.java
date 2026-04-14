/*
 * Module 23: Spring Boot Agent - The @AcpAgent Bean
 *
 * This is the same echo agent from Module 12, but using annotations instead
 * of the builder API. Spring Boot autoconfiguration discovers this bean,
 * wires it through AcpAgentSupport, and manages its lifecycle automatically.
 *
 * Compare with Module 12's EchoAgent.java:
 * - No manual transport creation
 * - No builder chain (AcpAgent.sync().initializeHandler()...)
 * - No agent.run() call
 * - Spring manages the lifecycle (start/stop)
 */
package com.acptutorial.module23;

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

import org.springframework.stereotype.Component;

@Component
@AcpAgent(name = "echo-agent", version = "1.0")
public class EchoAgentBean {

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
        context.sendMessage("Echo: " + request.text());
        return PromptResponse.endTurn();
    }

}
