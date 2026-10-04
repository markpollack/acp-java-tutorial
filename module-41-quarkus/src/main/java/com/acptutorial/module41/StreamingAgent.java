/*
 * Module 41: Quarkus - the @AcpAgent bean
 */
package com.acptutorial.module41;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.CloseSession;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.spec.AcpSchema;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * An annotated ACP agent on the Quarkus extension.
 *
 * <p><b>{@code @AcpAgent} alone makes it a bean</b>: a CDI singleton, found at build time (a
 * second {@code @AcpAgent} class fails the build), into which other beans ({@link Greeting})
 * are injected. No {@code @Singleton} or {@code @ApplicationScoped} is needed.
 *
 * <p><b>No {@code @Initialize} method.</b> The SDK derives the {@code initialize} answer from
 * the class: {@code agentInfo} from {@code @AcpAgent(name, version, title)},
 * {@code sessionCapabilities.close} from {@code @CloseSession} and
 * {@code promptCapabilities.image} from {@code @Prompt(image = true)}.
 *
 * <p><b>Mutiny return values.</b>
 * <ul>
 *   <li>{@code @NewSession} returns a {@link Uni} of its response; the handler's thread waits
 *   for it.</li>
 *   <li>{@code @Prompt} returns a {@link Multi}: each {@code String} item is sent as an agent
 *   message chunk as it is emitted, and the turn ends {@code end_turn} when the stream
 *   completes. When the prompt is cancelled ({@code session/cancel}), the SDK cancels the
 *   stream's subscription and ends the turn {@code cancelled}: the {@code Multi} sees the
 *   cancellation ({@code onCancellation()}) with no cancel handler of the agent's.</li>
 * </ul>
 *
 * <p><b>An interceptor-bound bean.</b> {@link Audited @Audited} binds a CDI interceptor
 * ({@link AuditInterceptor}) to the prompt handler.
 *
 * <p>Over HTTP each connection runs its own agent runtime over this one bean, so its state is
 * in concurrent maps keyed by session id.
 */
@AcpAgent(name = "quarkus-streaming-agent", version = "1.0.0", title = "Quarkus Streaming Agent")
public class StreamingAgent {

    private static final int CHUNKS = 10;

    private final Greeting greeting;

    /** Session id to a note about its last stream, set when the stream was cancelled. */
    private final Map<String, String> lastStream = new ConcurrentHashMap<>();

    StreamingAgent(Greeting greeting) {
        this.greeting = greeting;
    }

    @NewSession
    public Uni<AcpSchema.NewSessionResponse> newSession(AcpSchema.NewSessionRequest req) {
        return Uni.createFrom().item(() -> new AcpSchema.NewSessionResponse(UUID.randomUUID().toString(), null, null));
    }

    @Prompt(image = true)
    @Audited
    public Multi<String> prompt(AcpSchema.PromptRequest req, SyncPromptContext ctx) {
        String sessionId = ctx.getSessionId();
        String text = req.text();
        if (text.startsWith("count")) {
            AtomicInteger sent = new AtomicInteger();
            return Multi.createFrom().ticks().every(Duration.ofMillis(300))
                    .select().first(CHUNKS)
                    .map(tick -> "chunk " + sent.incrementAndGet())
                    .onCancellation().invoke(() -> lastStream.put(sessionId,
                            "your last stream was cancelled after " + sent.get() + " of " + CHUNKS + " chunks"));
        }
        if (text.startsWith("status")) {
            return Multi.createFrom().item(lastStream.getOrDefault(sessionId, "no stream was cancelled"));
        }
        return Multi.createFrom().item(greeting.greet(text));
    }

    @CloseSession
    public AcpSchema.CloseSessionResponse closeSession(AcpSchema.CloseSessionRequest req) {
        lastStream.remove(req.sessionId());
        return new AcpSchema.CloseSessionResponse();
    }
}
