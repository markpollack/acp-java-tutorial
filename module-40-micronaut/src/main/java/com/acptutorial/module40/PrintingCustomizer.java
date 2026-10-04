/*
 * Module 40: Micronaut - customizing the configured ACP client
 */
package com.acptutorial.module40;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.integration.AcpClientCustomizer;
import com.agentclientprotocol.sdk.spec.AcpSchema;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Adds a session-update consumer to the client acp-micronaut builds from
 * {@code acp.client.*}. Every {@link AcpClientCustomizer} bean is applied, in order, to the one
 * builder behind the {@code AcpAsyncClient} and {@code AcpSyncClient} beans. Only present when
 * a client is configured, so the agent application never has it.
 */
@Singleton
@Requires(property = "acp.client.transport")
public class PrintingCustomizer implements AcpClientCustomizer {

    @Override
    public void customize(AcpClient.AsyncSpec spec) {
        spec.sessionUpdateConsumer(notification -> {
            if (notification.update() instanceof AcpSchema.AgentThoughtChunk thought
                    && thought.content() instanceof AcpSchema.TextContent text) {
                System.out.println("   thought: " + text.text());
            }
            else if (notification.update() instanceof AcpSchema.AgentMessageChunk msg
                    && msg.content() instanceof AcpSchema.TextContent text) {
                System.out.println("   agent: " + text.text());
            }
            return Mono.empty();
        });
    }
}
