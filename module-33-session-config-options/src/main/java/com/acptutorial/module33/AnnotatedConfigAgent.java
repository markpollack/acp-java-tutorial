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
import com.agentclientprotocol.sdk.annotation.ConfigId;
import com.agentclientprotocol.sdk.annotation.ConfigValue;
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
 * <p>{@code @SetSessionConfigOption} takes the option as typed parameters instead of the
 * request: {@link ConfigId @ConfigId} {@code String} is the option id and
 * {@link ConfigValue @ConfigValue} the new value, typed by the parameter ({@code String} for a
 * select, {@code boolean} for a boolean, {@code Object} for either, as here). A value of the
 * other kind than the parameter's is answered {@code -32602} without calling the method. The
 * SDK does not check that the session offered the id or the value: that is the method's job.
 *
 * <p>The SDK requires the {@code @NewSession} method: an agent with
 * {@code @SetSessionMode} or {@code @SetSessionConfigOption} and no {@code @NewSession} fails
 * to build, since the default {@code session/new} answer offers no modes or options.
 *
 * <p>There is no {@code @Initialize} method: the SDK derives the {@code initialize} answer
 * from the class ({@code agentInfo} from {@code @AcpAgent}, {@code sessionCapabilities.close}
 * from {@code @CloseSession}). There is no session-state annotation either: per-session state
 * lives in {@link SessionSettings}' concurrent map and is dropped in {@code @CloseSession}.
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
    AcpSchema.SetSessionConfigOptionResponse setConfigOption(@SessionId String sessionId, @ConfigId String id,
            @ConfigValue Object value) {
        return new AcpSchema.SetSessionConfigOptionResponse(settings.apply(sessionId, id, value));
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
