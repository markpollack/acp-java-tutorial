/*
 * Module 40: Micronaut - the interceptor behind @Audited
 */
package com.acptutorial.module40;

import java.util.concurrent.atomic.AtomicInteger;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;

/**
 * Counts the calls of every {@link Audited @Audited} method and, when the method is an ACP
 * prompt handler, tells the client about it with a thought.
 *
 * <p>Applying advice makes Micronaut generate a subclass of the agent class at compile time
 * (an "intercepted" bean). That subclass carries no {@code @AcpAgent} annotation; the SDK finds
 * the handlers through the class it extends and invokes them on the generated subclass, so this
 * interceptor runs around the handler. The SDK's own {@code AcpInterceptor} beans are the
 * ACP-aware alternative, applied to every handler.
 */
@Singleton
@InterceptorBean(Audited.class)
public class AuditInterceptor implements MethodInterceptor<Object, Object> {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        int call = calls.incrementAndGet();
        for (Object argument : context.getParameterValues()) {
            if (argument instanceof SyncPromptContext prompt) {
                prompt.sendThought("[audit] " + context.getMethodName() + " call #" + call
                        + ", intercepted by a Micronaut @Around interceptor");
            }
        }
        return context.proceed();
    }
}
