/*
 * Module 41: Quarkus - the interceptor behind @Audited
 */
package com.acptutorial.module41;

import java.util.concurrent.atomic.AtomicInteger;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Counts the calls of every {@link Audited @Audited} method and, when the method is an ACP
 * prompt handler, tells the client about it with a thought.
 *
 * <p>An interceptor binding makes Quarkus ArC generate a subclass of the agent bean at build
 * time. The SDK finds the handlers through the {@code @AcpAgent} class that subclass extends and
 * invokes them on the bean ArC hands it, so this interceptor runs around the handler. The SDK's
 * own {@code AcpInterceptor} beans are the ACP-aware alternative, applied to every handler.
 */
@Audited
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuditInterceptor {

    private final AtomicInteger calls = new AtomicInteger();

    @AroundInvoke
    Object audit(InvocationContext invocation) throws Exception {
        int call = calls.incrementAndGet();
        for (Object argument : invocation.getParameters()) {
            if (argument instanceof SyncPromptContext prompt) {
                prompt.sendThought("[audit] " + invocation.getMethod().getName() + " call #" + call
                        + ", intercepted by a CDI interceptor");
            }
        }
        return invocation.proceed();
    }
}
