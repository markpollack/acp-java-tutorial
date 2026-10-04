/*
 * Module 33: Session Config Options - the builder agent
 *
 * Build & run:
 *   ./mvnw package -pl module-33-session-config-options -q
 *   ./mvnw exec:java -pl module-33-session-config-options
 */
package com.acptutorial.module33;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * An agent that exposes per-session settings as <b>session config options</b>, written with
 * the builder API ({@link AcpAgent#sync}).
 *
 * <p>Config options replace {@code session/set_model} from 0.18.0: the agent declares its
 * own selectors, each with an id and an optional semantic category ({@code model},
 * {@code mode}, {@code thought_level}, ...). The flow:
 * <ol>
 *   <li>{@code session/new}: the agent answers {@code new NewSessionResponse(id, modes,
 *   configOptions)}; modes come second.</li>
 *   <li>{@code session/set_config_option}: the client changes one option; the handler
 *   answers with the <em>full</em> list. That response is the client's answer, so the
 *   agent does not also send an update for a change the client made.</li>
 *   <li>{@code config_option_update}: when the agent changes a value <em>itself</em> (here,
 *   a model fallback when the preview model is "rate limited"), it pushes the full list as
 *   a session update.</li>
 * </ol>
 *
 * <p>A builder handler other than the prompt handler receives only its request, so the
 * {@code session/new} handler reaches the client's capabilities through the built agent
 * ({@code self.get().getClientCapabilities()}); compare {@link AnnotatedConfigAgent}, whose
 * handler takes {@code NegotiatedCapabilities} as a parameter.
 *
 * <p>Logs go to stderr: stdout carries the protocol.
 */
public final class ConfigAgent {

    public static void main(String[] args) {
        SessionSettings settings = new SessionSettings();
        AtomicReference<AcpSyncAgent> self = new AtomicReference<>();

        AcpSyncAgent agent = AcpAgent.sync(new StdioAcpAgentTransport())
                .initializeHandler(req -> AcpSchema.InitializeResponse.ok())
                .newSessionHandler(req -> {
                    String sessionId = UUID.randomUUID().toString();
                    boolean booleans = self.get().getClientCapabilities().supportsBooleanConfigOptions();
                    List<AcpSchema.SessionConfigOption> options = settings.open(sessionId, booleans);
                    // (sessionId, modes, configOptions): both, during the modes-to-config-options transition
                    return new AcpSchema.NewSessionResponse(sessionId, settings.modes(sessionId), options);
                })
                .setSessionConfigOptionHandler(req ->
                        // A client-initiated change: the response carries the full list.
                        new AcpSchema.SetSessionConfigOptionResponse(settings.apply(req)))
                .setSessionModeHandler(req -> {
                    // A client that only knows session modes still works: same state.
                    settings.applyMode(req);
                    return new AcpSchema.SetSessionModeResponse();
                })
                .promptHandler((req, ctx) -> {
                    String sessionId = ctx.getSessionId();
                    List<AcpSchema.SessionConfigOption> changed = settings.fallBackIfRateLimited(sessionId);
                    if (changed != null) {
                        // The agent changed a setting on its own: tell the client, with the full list.
                        ctx.sendThought(SessionSettings.PREVIEW_MODEL + " is rate limited; falling back to "
                                + SessionSettings.FALLBACK_MODEL);
                        ctx.sendUpdate(new AcpSchema.ConfigOptionUpdate(changed));
                    }
                    ctx.sendMessage(settings.answer(sessionId, "builder agent"));
                    return AcpSchema.PromptResponse.endTurn();
                })
                .build();
        self.set(agent);
        System.err.println("[ConfigAgent] Ready");
        agent.run();
    }
}
