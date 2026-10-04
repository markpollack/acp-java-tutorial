/*
 * Module 40: Micronaut - the @AcpAgent bean
 */
package com.acptutorial.module40;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.CloseSession;
import com.agentclientprotocol.sdk.annotation.ListSessions;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.spec.AcpSchema;

import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * An annotated ACP agent that is a Micronaut bean.
 *
 * <p><b>Two annotations make it an agent bean.</b> {@code @Singleton} makes it a bean, so its
 * dependencies ({@link Greeter}) are injected; {@code @AcpAgent} makes it the agent. Micronaut
 * finds it from the bean definition its annotation processor wrote at compile time, with no
 * classpath scan, and exactly one such bean is allowed (startup names them all otherwise).
 *
 * <p><b>No {@code @Initialize} method.</b> The SDK derives the {@code initialize} answer from
 * the class:
 * <ul>
 *   <li>{@code agentInfo} from {@code @AcpAgent(name, version, title)};</li>
 *   <li>{@code sessionCapabilities.list} from {@code @ListSessions} and
 *   {@code sessionCapabilities.close} from {@code @CloseSession};</li>
 *   <li>{@code promptCapabilities.embeddedContext} from {@code @Prompt(embeddedContext = true)}.</li>
 * </ul>
 * The demo prints the answer the client receives.
 *
 * <p><b>Return types.</b> Besides a value, a {@code Mono} or a {@code CompletionStage}, a
 * handler may return any single-value Reactive Streams {@link Publisher}, the type Micronaut's
 * reactive APIs use. The methods here declare {@code Publisher} (the value happens to be a
 * Reactor {@code Mono}; any implementation does). Its first element is the answer. A {@code @Prompt} method's {@code String} is sent to the client as an
 * agent message chunk, and the turn ends with {@code end_turn}.
 *
 * <p><b>Advice.</b> {@link Audited @Audited} applies a Micronaut {@code @Around} interceptor to
 * the prompt handler (see {@link AuditInterceptor}).
 *
 * <p>One instance serves every session and, over HTTP, every connection, so its state is in a
 * concurrent map keyed by session id.
 */
@Singleton
@AcpAgent(name = "micronaut-greeting-agent", version = "1.0.0", title = "Micronaut Greeting Agent")
public class GreetingAgent {

    private final Greeter greeter;

    /** Session id to working directory. */
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public GreetingAgent(Greeter greeter) {
        this.greeter = greeter;
    }

    @NewSession
    public Publisher<AcpSchema.NewSessionResponse> newSession(AcpSchema.NewSessionRequest req) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, req.cwd());
        return Mono.just(new AcpSchema.NewSessionResponse(sessionId, null, null));
    }

    @Prompt(embeddedContext = true)
    @Audited
    public Publisher<String> prompt(AcpSchema.PromptRequest req, SyncPromptContext ctx) {
        return Mono.just(greeter.greet(req.text()));
    }

    @ListSessions
    public AcpSchema.ListSessionsResponse listSessions(AcpSchema.ListSessionsRequest req) {
        List<AcpSchema.SessionInfo> infos = sessions.entrySet().stream()
                .map(e -> new AcpSchema.SessionInfo(e.getKey(), e.getValue()))
                .toList();
        return new AcpSchema.ListSessionsResponse(infos);
    }

    @CloseSession
    public AcpSchema.CloseSessionResponse closeSession(AcpSchema.CloseSessionRequest req) {
        sessions.remove(req.sessionId());
        return new AcpSchema.CloseSessionResponse();
    }
}
