/*
 * Module 35: Cancellation and Timeouts - the demo client
 *
 * Build & run:
 *   ./mvnw package -pl module-35-cancellation-timeouts -q
 *   ./mvnw exec:java -pl module-35-cancellation-timeouts
 */
package com.acptutorial.module35;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import com.agentclientprotocol.sdk.client.AcpAsyncClient;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.CancellationSignal;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.agentclientprotocol.sdk.spec.AcpError;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.RequestCancellation;

import reactor.core.publisher.Mono;

/**
 * The two ways to cancel in ACP, and the Java SDK's prompt timeouts, against
 * {@link CancellationAgent}.
 *
 * <ol>
 *   <li><b>{@code session/cancel}</b> asks the agent to end the turn. Send the prompt with a
 *   {@link CancellationSignal}, {@code client.prompt(request, stop)}, and call
 *   {@code stop.cancel()} from any thread: the client sends {@code session/cancel} for the
 *   prompt's session, once, and the prompt still returns the agent's answer. The turn ends when
 *   the cancelled prompt answers {@code cancelled}; a prompt sent in between is rejected with
 *   {@code -32600} ({@code AcpErrorCodes.INVALID_REQUEST}). The raw notification,
 *   {@code client.cancel(new CancelNotification(sid))}, does the same for whatever turn the
 *   session is running (scenario 3).</li>
 *   <li><b>{@code $/cancel_request}</b> cancels any one request, in either direction. With
 *   {@code contextWrite(RequestCancellation.cancelWhen(trigger))} it is <em>graceful</em>:
 *   the SDK sends {@code $/cancel_request} when {@code trigger} emits or completes and keeps
 *   waiting, so the caller still gets the peer's answer (here {@code AcpError -32800},
 *   {@code REQUEST_CANCELLED}). Disposing the request's {@code Mono} instead (directly, with
 *   {@code timeout(..)}, or by the SDK request timeout) also sends {@code $/cancel_request},
 *   but the caller's {@code Mono} ends at once and the late answer is dropped.</li>
 *   <li><b>{@code cancelGracePeriod}</b> (Java SDK policy): a handler that ignores
 *   {@code session/cancel} is answered {@code cancelled} by the SDK after the grace
 *   period (1 s in this demo, 60 s by default).</li>
 *   <li><b>{@code maxPromptDuration}</b> (Java SDK policy): a turn that runs too long is
 *   answered {@code -32800} (3 s in this demo, off by default).</li>
 * </ol>
 *
 * <p>The client is the async {@link AcpAsyncClient}: {@code cancelWhen} is a Reactor context
 * entry, and starting a prompt without blocking is what lets the demo cancel it. Each
 * scenario uses a fresh session so that one scenario's turn cannot overlap the next.
 *
 * <p>Note two behaviour changes from 0.18.0: a client-side request timeout now sends
 * {@code $/cancel_request}, so it cancels the work at a Java agent; and a prompt is no longer
 * bounded by the client's {@code requestTimeout} at all, since its answer comes only at the end
 * of the turn. To bound a turn on the client, set {@code promptTimeout(Duration)} on the client
 * builder (none by default), or apply {@code timeout(..)} to the prompt's {@code Mono}, as
 * scenario 2 does.
 */
public final class CancellationDemo {

    private static final String MODULE_NAME = "module-35-cancellation-timeouts";
    private static final String JAR_NAME = "cancellation-agent.jar";

    /** Counts down on each agent message, so the demo can cancel once the turn is under way. */
    private static final AtomicReference<CountDownLatch> MESSAGES = new AtomicReference<>(new CountDownLatch(0));

    public static void main(String[] args) throws Exception {
        var params = AgentParameters.builder("java").arg("-jar").arg(findAgentJar()).build();

        AcpAsyncClient client = AcpClient.async(new StdioAcpClientTransport(params))
                .sessionUpdateHandler(notification -> {
                    if (notification.update() instanceof AcpSchema.AgentMessageChunk msg
                            && msg.content() instanceof AcpSchema.TextContent text) {
                        System.out.println("  agent: " + text.text());
                        MESSAGES.get().countDown();
                    }
                    return Mono.empty();
                })
                .build();
        try {
            System.out.println("=== Module 35: Cancellation and Timeouts ===\n");
            client.initialize().block();

            sessionCancel(client);
            cancelRequest(client);
            gracePeriod(client);
            maxPromptDuration(client);

            System.out.println("=== Demo Complete ===");
        }
        finally {
            client.closeGracefully().block(Duration.ofSeconds(10));
        }
    }

    /** 1. session/cancel: the turn ends with the cancelled answer; a prompt in between gets -32600. */
    private static void sessionCancel(AcpAsyncClient client) throws Exception {
        System.out.println("--- 1. session/cancel ---");
        String sid = newSession(client);
        MESSAGES.set(new CountDownLatch(3));
        CancellationSignal stop = new CancellationSignal();
        CompletableFuture<AcpSchema.PromptResponse> slow = client.prompt(prompt(sid, "#slow"), stop).toFuture();
        MESSAGES.get().await(5, TimeUnit.SECONDS);

        stop.cancel();
        System.out.println("client: sent session/cancel (stop.cancel())");

        try {
            client.prompt(prompt(sid, "ping")).block();
            System.out.println("client: second prompt unexpectedly accepted");
        }
        catch (AcpError e) {
            System.out.println("client: prompt sent before the cancelled turn answered -> AcpError " + e.getCode()
                    + (e.getCode() == AcpErrorCodes.INVALID_REQUEST ? " (INVALID_REQUEST)" : "")
                    + ", data " + e.getData());
        }

        AcpSchema.PromptResponse answer = slow.get(10, TimeUnit.SECONDS);
        // StopReason is an open value: compare with equals, never ==.
        System.out.println("client: cancelled prompt answered, stop reason " + answer.stopReason()
                + " (CANCELLED: " + AcpSchema.StopReason.CANCELLED.equals(answer.stopReason()) + ")");

        AcpSchema.PromptResponse next = client.prompt(prompt(sid, "ping")).block();
        System.out.println("client: the turn is over, the next prompt works: stop reason " + next.stopReason());
        System.out.println();
    }

    /** 2. $/cancel_request: graceful with RequestCancellation.cancelWhen, and by disposing the Mono. */
    private static void cancelRequest(AcpAsyncClient client) throws Exception {
        System.out.println("--- 2. $/cancel_request ---");
        String sid = newSession(client);
        MESSAGES.set(new CountDownLatch(2));
        // The trigger: completes once two ticks have arrived.
        Mono<Void> twoTicks = Mono.fromFuture(CompletableFuture.runAsync(() -> awaitQuietly(MESSAGES.get())));
        try {
            client.prompt(prompt(sid, "#slow"))
                    .contextWrite(RequestCancellation.cancelWhen(twoTicks))
                    .block(Duration.ofSeconds(10));
            System.out.println("client: unexpectedly completed");
        }
        catch (AcpError e) {
            System.out.println("client: graceful cancel (cancelWhen) still received the agent's answer -> AcpError "
                    + e.getCode() + (e.getCode() == AcpErrorCodes.REQUEST_CANCELLED ? " (REQUEST_CANCELLED)" : ""));
        }

        String sid2 = newSession(client);
        try {
            client.prompt(prompt(sid2, "#slow")).timeout(Duration.ofMillis(300)).block();
            System.out.println("client: unexpectedly completed");
        }
        catch (RuntimeException e) {
            boolean timedOut = e.getCause() instanceof TimeoutException || e instanceof IllegalStateException;
            System.out.println("client: disposing the Mono (timeout 300 ms) also sends $/cancel_request, but returns at once: "
                    + (timedOut ? "TimeoutException" : e.getClass().getSimpleName()) + "; the late answer is dropped");
        }
        System.out.println();
    }

    /** 3. cancelGracePeriod: the SDK answers cancelled for a handler that ignores session/cancel. */
    private static void gracePeriod(AcpAsyncClient client) throws Exception {
        System.out.println("--- 3. cancelGracePeriod (Java SDK policy, " + CancellationAgent.CANCEL_GRACE_PERIOD.toMillis()
                + " ms here) ---");
        String sid = newSession(client);
        MESSAGES.set(new CountDownLatch(2));
        CompletableFuture<AcpSchema.PromptResponse> stubborn = client.prompt(prompt(sid, "#stubborn")).toFuture();
        MESSAGES.get().await(5, TimeUnit.SECONDS);

        long start = System.nanoTime();
        client.cancel(new AcpSchema.CancelNotification(sid)).block();
        System.out.println("client: sent session/cancel; the handler ignores it");
        AcpSchema.PromptResponse answer = stubborn.get(10, TimeUnit.SECONDS);
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        System.out.println("client: the SDK answered for the handler: stop reason " + answer.stopReason()
                + ", after the grace period: " + (millis >= 800 && millis < 5000));
        System.out.println();
    }

    /** 4. maxPromptDuration: a turn that never ends is answered -32800. */
    private static void maxPromptDuration(AcpAsyncClient client) {
        System.out.println("--- 4. maxPromptDuration (Java SDK policy, " + CancellationAgent.MAX_PROMPT_DURATION.toMillis()
                + " ms here) ---");
        String sid = newSession(client);
        long start = System.nanoTime();
        try {
            client.prompt(prompt(sid, "#hang")).block(Duration.ofSeconds(15));
            System.out.println("client: unexpectedly completed");
        }
        catch (AcpError e) {
            long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
            System.out.println("client: AcpError " + e.getCode() + ": " + e.getError().message());
            System.out.println("client: data " + e.getData() + ", after the limit: " + (millis >= 2500 && millis < 10000));
        }
        System.out.println();
    }

    private static String newSession(AcpAsyncClient client) {
        return client.newSession(new AcpSchema.NewSessionRequest(System.getProperty("user.dir"), List.of()))
                .block().sessionId();
    }

    private static AcpSchema.PromptRequest prompt(String sessionId, String text) {
        return new AcpSchema.PromptRequest(sessionId, List.of(new AcpSchema.TextContent(text)));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String findAgentJar() {
        Path fromModule = Path.of("target/" + JAR_NAME);
        if (Files.exists(fromModule)) {
            return fromModule.toString();
        }
        Path fromRoot = Path.of(MODULE_NAME + "/target/" + JAR_NAME);
        if (Files.exists(fromRoot)) {
            return fromRoot.toString();
        }
        throw new RuntimeException(
            "Agent JAR not found. Run: ./mvnw package -pl " + MODULE_NAME + " -q");
    }
}
