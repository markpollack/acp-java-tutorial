/*
 * Module 33: Session Config Options - the annotated agent
 *
 * Started by the demo with:  java -cp target/config-agent.jar com.acptutorial.module33.AnnotatedConfigAgent
 */
package com.acptutorial.module33;

import java.util.List;
import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.support.AcpAgentSupport;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.CloseSession;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.annotation.SessionId;
import com.agentclientprotocol.sdk.annotation.SetSessionConfigOption;
import com.agentclientprotocol.sdk.annotation.SetSessionMode;
import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * The same config-options agent as {@link ConfigAgent}, written with annotations and run by
 * {@link AcpAgentSupport}.
 *
 * <p>What the annotation model adds: any handler method may declare
 * {@link NegotiatedCapabilities} and {@link AcpSyncAgent} parameters, and receives the ones
 * of the connection the request arrived on. So {@code @NewSession} reads
 * {@code supportsBooleanConfigOptions()} straight from its parameter, and {@code @Prompt}
 * pushes the agent-initiated {@code config_option_update} through the connection's agent
 * (it could equally use the {@link SyncPromptContext}).
 *
 * <p>There is no {@code @Initialize} method: the default answer
 * ({@code InitializeResponse.ok()}) is used. There is no session-state annotation either:
 * per-session state lives in {@link SessionSettings}' concurrent map and is dropped in
 * {@code @CloseSession}.
 */
@AcpAgent(name = "config-agent-annotated", version = "1.0.0")
public class AnnotatedConfigAgent {

    private final SessionSettings settings = new SessionSettings();

    @NewSession
    AcpSchema.NewSessionResponse newSession(AcpSchema.NewSessionRequest req, NegotiatedCapabilities clientCaps) {
        String sessionId = UUID.randomUUID().toString();
        List<AcpSchema.SessionConfigOption> options =
                settings.open(sessionId, clientCaps.supportsBooleanConfigOptions());
        return new AcpSchema.NewSessionResponse(sessionId, settings.modes(sessionId), options);
    }

    @SetSessionConfigOption
    AcpSchema.SetSessionConfigOptionResponse setConfigOption(AcpSchema.SetSessionConfigOptionRequest req) {
        return new AcpSchema.SetSessionConfigOptionResponse(settings.apply(req));
    }

    @SetSessionMode
    AcpSchema.SetSessionModeResponse setMode(AcpSchema.SetSessionModeRequest req) {
        settings.applyMode(req);
        return new AcpSchema.SetSessionModeResponse();
    }

    @Prompt
    AcpSchema.PromptResponse prompt(AcpSchema.PromptRequest req, @SessionId String sessionId,
            SyncPromptContext ctx, AcpSyncAgent agent) {
        List<AcpSchema.SessionConfigOption> changed = settings.fallBackIfRateLimited(sessionId);
        if (changed != null) {
            ctx.sendThought(SessionSettings.PREVIEW_MODEL + " is rate limited; falling back to "
                    + SessionSettings.FALLBACK_MODEL);
            // Agent-initiated change: push the full list, here through the connection's agent.
            agent.sendSessionUpdate(sessionId, new AcpSchema.ConfigOptionUpdate(changed));
        }
        ctx.sendMessage(settings.answer(sessionId, "annotated agent"));
        return AcpSchema.PromptResponse.endTurn();
    }

    @CloseSession
    AcpSchema.CloseSessionResponse close(AcpSchema.CloseSessionRequest req) {
        settings.close(req.sessionId());
        return new AcpSchema.CloseSessionResponse();
    }

    public static void main(String[] args) {
        System.err.println("[AnnotatedConfigAgent] Ready");
        AcpAgentSupport.create(new AnnotatedConfigAgent())
                .transport(new StdioAcpAgentTransport())
                .build()
                .run();
    }
}
