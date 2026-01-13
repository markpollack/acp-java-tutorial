/*
 * SimpleInMemoryTransportPair - A minimal in-memory transport for testing.
 *
 * This is a simplified version of the SDK's InMemoryTransportPair,
 * demonstrating the core concepts without the full production features.
 *
 * For production testing, use the SDK's InMemoryTransportPair from
 * the test-jar or copy it into your test sources.
 */
package com.acptutorial.module16;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.agentclientprotocol.sdk.spec.AcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.TypeRef;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Creates connected client and agent transports that communicate in-memory.
 */
public class SimpleInMemoryTransportPair {

    private final SimpleClientTransport clientTransport;
    private final SimpleAgentTransport agentTransport;

    private SimpleInMemoryTransportPair() {
        // Bidirectional communication channels
        Sinks.Many<AcpSchema.JSONRPCMessage> clientToAgent = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<AcpSchema.JSONRPCMessage> agentToClient = Sinks.many().unicast().onBackpressureBuffer();

        this.clientTransport = new SimpleClientTransport(clientToAgent, agentToClient);
        this.agentTransport = new SimpleAgentTransport(agentToClient, clientToAgent);
    }

    public static SimpleInMemoryTransportPair create() {
        return new SimpleInMemoryTransportPair();
    }

    public AcpClientTransport clientTransport() {
        return clientTransport;
    }

    public AcpAgentTransport agentTransport() {
        return agentTransport;
    }

    public Mono<Void> closeGracefully() {
        return Mono.when(clientTransport.closeGracefully(), agentTransport.closeGracefully());
    }

    // Simple client transport
    private static class SimpleClientTransport implements AcpClientTransport {
        private final Sinks.Many<AcpSchema.JSONRPCMessage> outbound;
        private final Sinks.Many<AcpSchema.JSONRPCMessage> inbound;
        private final ObjectMapper mapper = new ObjectMapper();
        private Consumer<Throwable> exceptionHandler = t -> {};

        SimpleClientTransport(Sinks.Many<AcpSchema.JSONRPCMessage> outbound,
                             Sinks.Many<AcpSchema.JSONRPCMessage> inbound) {
            this.outbound = outbound;
            this.inbound = inbound;
        }

        @Override
        public List<Integer> protocolVersions() {
            return List.of(AcpSchema.LATEST_PROTOCOL_VERSION);
        }

        @Override
        public Mono<Void> connect(Function<Mono<AcpSchema.JSONRPCMessage>, Mono<AcpSchema.JSONRPCMessage>> handler) {
            return inbound.asFlux()
                .flatMap(msg -> Mono.just(msg).transform(handler))
                .doOnError(exceptionHandler::accept)
                .then();
        }

        @Override
        public Mono<Void> sendMessage(AcpSchema.JSONRPCMessage message) {
            outbound.tryEmitNext(message);
            return Mono.empty();
        }

        @Override
        public Mono<Void> closeGracefully() {
            outbound.tryEmitComplete();
            return Mono.empty();
        }

        @Override
        public void setExceptionHandler(Consumer<Throwable> handler) {
            this.exceptionHandler = handler;
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return mapper.convertValue(data, mapper.constructType(typeRef.getType()));
        }
    }

    // Simple agent transport
    private static class SimpleAgentTransport implements AcpAgentTransport {
        private final Sinks.Many<AcpSchema.JSONRPCMessage> outbound;
        private final Sinks.Many<AcpSchema.JSONRPCMessage> inbound;
        private final Sinks.One<Void> terminationSink = Sinks.one();
        private final ObjectMapper mapper = new ObjectMapper();
        private Consumer<Throwable> exceptionHandler = t -> {};

        SimpleAgentTransport(Sinks.Many<AcpSchema.JSONRPCMessage> outbound,
                            Sinks.Many<AcpSchema.JSONRPCMessage> inbound) {
            this.outbound = outbound;
            this.inbound = inbound;
        }

        @Override
        public List<Integer> protocolVersions() {
            return List.of(AcpSchema.LATEST_PROTOCOL_VERSION);
        }

        @Override
        public Mono<Void> start(Function<Mono<AcpSchema.JSONRPCMessage>, Mono<AcpSchema.JSONRPCMessage>> handler) {
            return inbound.asFlux()
                .flatMap(msg -> Mono.just(msg)
                    .transform(handler)
                    .flatMap(response -> {
                        outbound.tryEmitNext(response);
                        return Mono.empty();
                    }))
                .doOnError(exceptionHandler::accept)
                .doFinally(s -> terminationSink.tryEmitValue(null))
                .then();
        }

        @Override
        public Mono<Void> sendMessage(AcpSchema.JSONRPCMessage message) {
            outbound.tryEmitNext(message);
            return Mono.empty();
        }

        @Override
        public Mono<Void> closeGracefully() {
            outbound.tryEmitComplete();
            terminationSink.tryEmitValue(null);
            return Mono.empty();
        }

        @Override
        public Mono<Void> awaitTermination() {
            return terminationSink.asMono();
        }

        @Override
        public void setExceptionHandler(Consumer<Throwable> handler) {
            this.exceptionHandler = handler;
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return mapper.convertValue(data, mapper.constructType(typeRef.getType()));
        }
    }
}
