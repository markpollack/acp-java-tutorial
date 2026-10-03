/*
 * Module 34: Extension Methods - the builder agent
 *
 * Build & run:
 *   ./mvnw package -pl module-34-extension-methods -q
 *   ./mvnw exec:java -pl module-34-extension-methods
 */
package com.acptutorial.module34;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.acptutorial.module34.Extensions.LogEvent;
import com.acptutorial.module34.Extensions.Selection;
import com.acptutorial.module34.Extensions.SelectionQuery;
import com.acptutorial.module34.Extensions.WordCountParams;
import com.acptutorial.module34.Extensions.WordCountResult;
import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.json.TypeRef;
import com.agentclientprotocol.sdk.spec.AcpError;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * An agent that serves custom extension methods and calls the client's, written with the
 * builder API.
 *
 * <p>Extension methods are how an agent and a client that know each other add features ACP
 * does not define (an editor's current selection, a custom log channel, ...) without
 * forking the protocol. They are ordinary JSON-RPC requests and notifications whose names
 * start with {@code _}.
 *
 * <p><b>Serving</b> (client to agent), on the builder:
 * <ul>
 *   <li>{@code extRequestHandler(name, new TypeRef<P>() {}, params -> result)}: typed; the
 *   params are read as {@code P}, the result is any value the JSON mapper can write.</li>
 *   <li>{@code extRequestHandler(name, params -> result)}: raw; the params arrive as the
 *   JSON value ({@code Map}, {@code List}, {@code String}, {@code Number},
 *   {@code Boolean}).</li>
 *   <li>{@code extNotificationHandler(name, ...)}: the same two forms, no answer.</li>
 * </ul>
 * A handler must not return {@code null} (the request is then answered {@code -32603});
 * return an empty map when there is nothing to say. A request nobody serves is answered
 * {@code -32601}; a notification nobody handles is ignored, as the protocol asks.
 *
 * <p><b>Calling</b> (agent to client), on the agent facade: {@code sendExtRequest(name,
 * params, TypeRef)} (typed), {@code sendExtRequest(name, params)} (raw) and
 * {@code sendExtNotification(name, params)}. A builder handler reaches the built agent
 * through an {@code AtomicReference}; compare {@link AnnotatedExtensionAgent}, whose
 * handlers take the connection's {@link AcpSyncAgent} as a parameter.
 */
public final class ExtensionAgent {

    public static void main(String[] args) {
        AtomicReference<AcpSyncAgent> self = new AtomicReference<>();

        AcpSyncAgent agent = AcpAgent.sync(new StdioAcpAgentTransport())
                .initializeHandler(req -> AcpSchema.InitializeResponse.ok())
                .newSessionHandler(req -> new AcpSchema.NewSessionResponse(UUID.randomUUID().toString(), null, null))

                // Typed request: params read as WordCountParams, result written as JSON.
                .extRequestHandler(Extensions.WORD_COUNT, new TypeRef<WordCountParams>() {
                }, params -> new WordCountResult(params.text().split("\\s+").length, params.text().length()))

                // Raw request: params arrive as the plain JSON value.
                .extRequestHandler(Extensions.ECHO, params -> Extensions.ordered(
                        "echoed", params,
                        "agentSawJavaType", params.getClass().getSimpleName()))

                // Typed notification: no answer, so the agent acknowledges with a notification of its own.
                .extNotificationHandler(Extensions.LOG, new TypeRef<LogEvent>() {
                }, event -> self.get().sendExtNotification(Extensions.ACK,
                        Extensions.ordered("received", event.level() + ": " + event.message(), "by", "builder agent")))

                .promptHandler((req, ctx) -> {
                    AcpSyncAgent me = self.get();
                    // Agent -> client, typed request.
                    Selection selection = me.sendExtRequest(Extensions.SELECTION,
                            new SelectionQuery("src/Main.java"), new TypeRef<Selection>() {
                            });
                    // Agent -> client, raw notification.
                    me.sendExtNotification(Extensions.STATUS, Extensions.ordered("state", "reviewing", "file", selection.file()));
                    // Agent -> client, a method the client does not serve.
                    try {
                        me.sendExtRequest(Extensions.NOT_SERVED, Map.of());
                    }
                    catch (AcpError e) {
                        ctx.sendThought("the client does not serve " + Extensions.NOT_SERVED
                                + " (code " + e.getCode() + ")");
                    }
                    ctx.sendMessage("Reviewed " + selection.file() + " lines " + selection.startLine() + "-"
                            + selection.endLine() + ": '" + selection.text() + "' looks fine (builder agent).");
                    return AcpSchema.PromptResponse.endTurn();
                })
                .build();
        self.set(agent);
        System.err.println("[ExtensionAgent] Ready");
        agent.run();
    }
}
