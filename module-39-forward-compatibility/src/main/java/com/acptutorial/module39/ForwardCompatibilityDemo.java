/*
 * Module 39: Forward Compatibility and _meta
 *
 * Build & run:
 *   ./mvnw compile -pl module-39-forward-compatibility -q
 *   ./mvnw exec:java -pl module-39-forward-compatibility
 */
package com.acptutorial.module39;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.json.AcpJsonMapper;
import com.agentclientprotocol.sdk.json.TypeRef;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.test.InMemoryTransportPair;

/**
 * What happens when the other side is newer than you, and how to attach your own data to
 * protocol messages.
 *
 * <p><b>Unknown variants are kept, not rejected.</b> ACP grows: new session update kinds,
 * new content blocks, new stop reasons. A Java SDK peer never fails a message because of a
 * variant it does not know. An unknown union variant reads as an {@code Unknown*} record
 * ({@code UnknownSessionUpdate}, {@code UnknownContentBlock},
 * {@code UnknownToolCallContent}, {@code UnknownSessionConfigOption},
 * {@code UnknownPermissionOutcome}, {@code UnknownElicitationPropertySchema},
 * {@code UnknownMultiSelectItems}) that keeps the discriminator and every field, and writes
 * them back unchanged. ({@code McpServer} instead defaults to stdio, {@code AuthMethod} to
 * the agent method.)
 *
 * <p><b>No dispatch is exhaustive.</b> The union interfaces are not sealed, and the SDK
 * targets Java 17, so write {@code instanceof} chains that end with an {@code Unknown*}
 * branch and a final {@code else}.
 *
 * <p><b>Open values.</b> {@code StopReason}, {@code ToolCallStatus},
 * {@code PermissionOptionKind}, {@code PlanEntryStatus}, {@code PlanEntryPriority},
 * {@code Role} and {@code ElicitationAction} are records over the wire string, with
 * constants for the known values, {@code of(..)}, {@code known()}, {@code isKnown()} and
 * {@code value()}. Compare them with {@code equals} (or switch on {@code value()}), never
 * {@code ==}. ({@code ToolKind} stays an enum; an unknown kind reads as {@code OTHER}.)
 *
 * <p><b>{@code _meta}.</b> Every schema record the ACP schema gives a {@code _meta} carries it
 * as its last component, {@code meta}: a free-form map for your own data (trace ids,
 * vendor hints) that peers pass through. Use namespaced keys.
 *
 * <p>The demo runs a "newer" agent in memory ({@code acp-test}'s
 * {@link InMemoryTransportPair}) that sends a session update kind, a content block type and
 * a stop reason this SDK does not define, then reads a raw JSON notification from a newer
 * peer and writes it back.
 */
public final class ForwardCompatibilityDemo {

    private static final String TRACE_KEY = "acptutorial.dev/traceId";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Module 39: Forward Compatibility and _meta ===\n");
        var pair = InMemoryTransportPair.create();
        AcpSyncAgent agent = newerAgent(pair);
        agent.start();

        try (AcpSyncClient client = AcpClient.sync(pair.clientTransport())
                .sessionUpdateHandler(ForwardCompatibilityDemo::handle)
                .build()) {
            client.initialize();
            String sid = client.newSession(new AcpSchema.NewSessionRequest("/", List.of())).sessionId();

            System.out.println("--- a prompt to a newer agent, with _meta ---");
            var request = new AcpSchema.PromptRequest(sid, List.of(new AcpSchema.TextContent("hello")),
                    Map.of(TRACE_KEY, "trace-42"));
            AcpSchema.PromptResponse response = client.prompt(request);

            AcpSchema.StopReason stop = response.stopReason();
            System.out.println("stop reason: " + stop.value() + ", known: " + stop.isKnown()
                    + ", equals END_TURN: " + AcpSchema.StopReason.END_TURN.equals(stop));
            System.out.println("known stop reasons: " + AcpSchema.StopReason.known());
            System.out.println("response _meta: " + response.meta());
        }
        pair.closeGracefully().block();

        System.out.println("\n--- raw JSON from a newer peer, read and written back ---");
        String json = "{\"sessionId\":\"s1\",\"update\":{\"sessionUpdate\":\"usage_forecast\","
                + "\"tokensLeft\":1200,\"resetsAt\":\"2026-10-04T00:00:00Z\"}}";
        AcpJsonMapper mapper = AcpJsonMapper.createDefault();
        AcpSchema.SessionNotification notification = mapper.readValue(json, new TypeRef<AcpSchema.SessionNotification>() {
        });
        System.out.println("read as: " + notification.update().getClass().getSimpleName());
        String written = mapper.writeValueAsString(notification);
        System.out.println("written back: " + written);
        System.out.println("unchanged: " + json.equals(written));

        System.out.println("\n=== Demo Complete ===");
    }

    /** A client's update dispatch: known types, then the Unknown* record, then a final default. */
    private static void handle(AcpSchema.SessionNotification notification) {
        AcpSchema.SessionUpdate update = notification.update();
        if (update instanceof AcpSchema.AgentMessageChunk chunk) {
            if (chunk.content() instanceof AcpSchema.TextContent text) {
                System.out.println("message: " + text.text() + (chunk.meta() != null ? "   _meta " + chunk.meta() : ""));
            }
            else if (chunk.content() instanceof AcpSchema.UnknownContentBlock block) {
                System.out.println("message with an unknown content block: type '" + block.type() + "', fields "
                        + block.fields());
            }
            else {
                System.out.println("message with other content: " + chunk.content().getClass().getSimpleName());
            }
        }
        else if (update instanceof AcpSchema.UnknownSessionUpdate unknown) {
            System.out.println("unknown session update kept: '" + unknown.sessionUpdate() + "', fields "
                    + unknown.fields());
        }
        else {
            System.out.println("other update: " + update.getClass().getSimpleName());
        }
    }

    /**
     * An agent pretending to be a newer ACP version: it sends variants this SDK does not
     * define. An agent built with this SDK can do that only through the {@code Unknown*}
     * records, which is also how a proxy would pass a newer peer's messages on.
     */
    private static AcpSyncAgent newerAgent(InMemoryTransportPair pair) {
        return AcpAgent.sync(pair.agentTransport())
                .initializeHandler(req -> AcpSchema.InitializeResponse.ok())
                .newSessionHandler(req -> new AcpSchema.NewSessionResponse(UUID.randomUUID().toString(), null, null))
                .promptHandler((req, ctx) -> {
                    Object trace = req.meta() != null ? req.meta().get(TRACE_KEY) : null;
                    // _meta on a session update: the record's last component.
                    ctx.sendSessionUpdate(new AcpSchema.AgentMessageChunk("agent_message_chunk",
                            new AcpSchema.TextContent("the agent saw prompt _meta " + TRACE_KEY + "=" + trace), null,
                            Map.of(TRACE_KEY, trace)));
                    ctx.sendSessionUpdate(new AcpSchema.UnknownSessionUpdate("usage_forecast",
                            Map.of("tokensLeft", 1200)));
                    ctx.sendSessionUpdate(new AcpSchema.AgentMessageChunk(
                            new AcpSchema.UnknownContentBlock("hologram", Map.of("frames", 3))));
                    return new AcpSchema.PromptResponse(AcpSchema.StopReason.of("paused_for_review"),
                            Map.of(TRACE_KEY, trace));
                })
                .build();
    }
}
