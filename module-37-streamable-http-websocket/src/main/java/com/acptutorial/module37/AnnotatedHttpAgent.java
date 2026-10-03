/*
 * Module 37: Streamable HTTP and WebSocket - an annotated agent behind the listener
 */
package com.acptutorial.module37;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.support.AcpAgentSupport;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * An annotated agent for a listener transport.
 *
 * <p>{@code AcpAgentSupport.create(bean).buildFactory()} returns an {@code AcpAgentFactory}:
 * every connection gets its own agent runtime, but they all dispatch to <b>this one
 * bean</b>. So the bean must be thread-safe: here the only state is an
 * {@link AtomicInteger} counting prompts across all connections. Per-session state would go
 * in a concurrent map keyed by session id.
 */
@AcpAgent(name = "annotated-http-agent", version = "1.0.0")
public class AnnotatedHttpAgent {

    private final AtomicInteger prompts = new AtomicInteger();

    @NewSession
    AcpSchema.NewSessionResponse newSession(AcpSchema.NewSessionRequest req) {
        return new AcpSchema.NewSessionResponse(UUID.randomUUID().toString(), null, null);
    }

    @Prompt
    void prompt(AcpSchema.PromptRequest req, SyncPromptContext ctx) {
        // A void @Prompt method answers end_turn.
        ctx.sendMessage("annotated agent, prompt #" + prompts.incrementAndGet() + " across all connections: "
                + req.text());
    }

    static AcpAgentSupport.Builder support() {
        return AcpAgentSupport.create(new AnnotatedHttpAgent());
    }
}
