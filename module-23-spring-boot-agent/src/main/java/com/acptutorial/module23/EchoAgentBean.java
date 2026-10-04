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
 * - No initialize handler: the SDK answers initialize for an annotated agent. From SDK
 *   0.80.0 that answer is derived from the class: agentInfo from @AcpAgent(name, version)
 *   and a capability for each optional handler it declares (none here).
 */
package com.acptutorial.module23;

import java.util.UUID;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;

import org.springframework.stereotype.Component;

@Component
@AcpAgent(name = "echo-agent", version = "1.0")
public class EchoAgentBean {

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
