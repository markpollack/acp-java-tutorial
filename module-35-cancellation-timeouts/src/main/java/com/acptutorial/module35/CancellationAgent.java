/*
 * Module 35: Cancellation and Timeouts - the agent
 *
 * Build & run:
 *   ./mvnw package -pl module-35-cancellation-timeouts -q
 *   ./mvnw exec:java -pl module-35-cancellation-timeouts
 */
package com.acptutorial.module35;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * A deterministic agent for cancellation demos: what it does depends on the prompt text.
 *
 * <ul>
 *   <li>{@code #slow}: sends {@code tick 1..10}, one every 200 ms, and checks between ticks
 *   whether the session was cancelled. This is a <b>cooperative</b> handler: on
 *   {@code session/cancel} it sends a last message, takes a moment to wrap up, and answers
 *   {@code StopReason.CANCELLED}, as the protocol asks.</li>
 *   <li>{@code #stubborn}: ticks and ignores {@code session/cancel}. The SDK answers for it
 *   once the cancel grace period has passed.</li>
 *   <li>{@code #hang}: never answers. The SDK ends it at {@code maxPromptDuration}.</li>
 *   <li>anything else: answers {@code pong} at once.</li>
 * </ul>
 *
 * <p><b>{@code session/cancel} is a notification.</b> The {@code cancelHandler} runs (here it
 * records the session id), but the turn is <em>not</em> over: it stays active until the
 * cancelled prompt answers. A new prompt on that session in between is rejected with
 * {@code -32600}. The handler may still send final updates before it answers.
 *
 * <p><b>The two prompt timeouts are Java SDK policy, not protocol.</b> ACP defines neither.
 * They exist because the spec requires every cancelled prompt to be answered
 * {@code cancelled}, and a session allows one turn at a time, so a handler that never
 * answers would keep its session busy for good:
 * <ul>
 *   <li>{@code cancelGracePeriod} (default 60 s): how long a handler has after
 *   {@code session/cancel}. Then the SDK cancels the handler (a sync handler's thread is
 *   interrupted) and answers {@code cancelled} itself.</li>
 *   <li>{@code maxPromptDuration} (default off): a hard limit on one turn. The SDK answers
 *   {@code -32800} with message {@code "Prompt exceeded maxPromptDuration of <d>"} and data
 *   {@code {"maxPromptDuration": "<ISO-8601>"}}.</li>
 * </ul>
 * {@code Duration.ZERO} turns either off; exactly one answer is sent per prompt. This demo
 * uses 1 s and 3 s so that it runs quickly. Both are also on
 * {@code AcpAgentSupport.Builder} for annotated agents.
 *
 * <p><b>{@code $/cancel_request}</b> needs no code in the agent: when the client cancels the
 * {@code session/prompt} request itself, the SDK cancels the handler (interrupting a sync
 * handler) and answers {@code -32800}.
 */
public final class CancellationAgent {

    static final Duration CANCEL_GRACE_PERIOD = Duration.ofSeconds(1);
    static final Duration MAX_PROMPT_DURATION = Duration.ofSeconds(3);

    /** Sessions with a session/cancel pending. Thread-safe: the cancel handler and prompts run on different threads. */
    private static final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        AcpSyncAgent agent = AcpAgent.sync(new StdioAcpAgentTransport())
                .cancelGracePeriod(CANCEL_GRACE_PERIOD)
                .maxPromptDuration(MAX_PROMPT_DURATION)
                .initializeHandler(req -> AcpSchema.InitializeResponse.ok())
                .newSessionHandler(req -> new AcpSchema.NewSessionResponse(UUID.randomUUID().toString(), null, null))
                .cancelHandler(notification -> {
                    System.err.println("[CancellationAgent] session/cancel for " + notification.sessionId());
                    cancelled.add(notification.sessionId());
                })
                .promptHandler((req, ctx) -> {
                    cancelled.remove(ctx.getSessionId());
                    String text = req.text();
                    try {
                        if (text.contains("#slow")) {
                            return slow(ctx);
                        }
                        if (text.contains("#stubborn")) {
                            return stubborn(ctx);
                        }
                        if (text.contains("#hang")) {
                            Thread.sleep(Duration.ofMinutes(5).toMillis());
                        }
                        ctx.sendMessage("pong");
                        return AcpSchema.PromptResponse.endTurn();
                    }
                    catch (InterruptedException e) {
                        // The SDK interrupted this handler: $/cancel_request, the grace period or
                        // maxPromptDuration. It has already answered the prompt; this return is dropped.
                        System.err.println("[CancellationAgent] handler interrupted by the SDK");
                        Thread.currentThread().interrupt();
                        return new AcpSchema.PromptResponse(AcpSchema.StopReason.CANCELLED);
                    }
                })
                .build();
        System.err.println("[CancellationAgent] Ready");
        agent.run();
    }

    /** A cooperative handler: stops when its session is cancelled, and says so. */
    private static AcpSchema.PromptResponse slow(SyncPromptContext ctx) throws InterruptedException {
        for (int tick = 1; tick <= 10; tick++) {
            if (cancelled.remove(ctx.getSessionId())) {
                ctx.sendMessage("cancelled after tick " + (tick - 1) + "; wrapping up");
                Thread.sleep(500); // still the active turn: a prompt sent now is rejected
                return new AcpSchema.PromptResponse(AcpSchema.StopReason.CANCELLED);
            }
            ctx.sendMessage("tick " + tick);
            Thread.sleep(200);
        }
        return AcpSchema.PromptResponse.endTurn();
    }

    /** A handler that ignores session/cancel: the SDK answers after the grace period. */
    private static AcpSchema.PromptResponse stubborn(SyncPromptContext ctx) throws InterruptedException {
        for (int tick = 1; tick <= 50; tick++) {
            if (tick <= 3) {
                ctx.sendMessage("stubborn tick " + tick);
            }
            Thread.sleep(200);
        }
        return AcpSchema.PromptResponse.endTurn();
    }
}
