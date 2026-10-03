/*
 * Module 38: Spring Boot over HTTP - the client application
 *
 * Run on its own against an agent on port 8080:
 *   ./mvnw exec:java -pl module-38-spring-boot-http \
 *       -Dexec.mainClass=com.acptutorial.module38.client.HttpClientApplication \
 *       -Dexec.args=--demo.agent.url=http://localhost:8080/acp
 */
package com.acptutorial.module38.client;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.agentclientprotocol.autoconfigure.client.AcpClientCustomizer;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.StreamableHttpAcpClientTransport;
import com.agentclientprotocol.sdk.json.AcpJsonMapper;
import com.agentclientprotocol.sdk.spec.AcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

import reactor.core.publisher.Mono;

/**
 * A Spring Boot application that talks to a remote ACP agent through the autoconfigured
 * {@link AcpSyncClient}.
 *
 * <p><b>The transport.</b> acp-autoconfig creates the client from whichever
 * {@link AcpClientTransport} bean exists, and backs off its own when the application
 * defines one. Its properties cover stdio and WebSocket
 * ({@code spring.acp.client.transport.websocket.uri}); for Streamable HTTP this application
 * defines the transport bean itself, when {@code demo.agent.url} is set.
 *
 * <p><b>{@link AcpClientCustomizer}</b> is how an application adds to the autoconfigured
 * client builder (an {@code AcpClient.AsyncSpec}) before it is built. Every customizer bean
 * is applied, in order, to the one builder behind both {@code AcpAsyncClient} and
 * {@code AcpSyncClient}. This one registers:
 * <ul>
 *   <li>a session-update consumer that prints the agent's messages. Consumers add up: the
 *   autoconfiguration's own debug-logging consumer stays;</li>
 *   <li>a handler for {@code fs/read_text_file}, serving files from an in-memory
 *   workspace.</li>
 * </ul>
 * Registering a handler does not advertise it: the client's file capabilities come from
 * {@code spring.acp.client.capabilities.read-text-file} and {@code write-text-file}, which
 * default to {@code false} in acp-autoconfig 0.13.0. So a handler and its capability property
 * go together ({@code client.properties} turns {@code read-text-file} on).
 *
 * <p>{@code main} closes the context when the runner finishes, so the client beans shut
 * down (and close the connection) while the application is still running, as in module 24.
 */
@SpringBootApplication
public class HttpClientApplication {

    /** The client's "workspace": what fs/read_text_file serves. */
    static final Map<String, String> WORKSPACE = Map.of("NOTES.md", """
            # Notes
            - [x] write module 37
            - [ ] write module 38
            - [ ] run the integration tests
            """);

    public static void run(String... args) {
        new SpringApplicationBuilder(HttpClientApplication.class)
                .properties("spring.config.name=client")
                .run(args)
                .close();
    }

    public static void main(String[] args) {
        run(args);
    }

    @Bean
    @ConditionalOnProperty("demo.agent.url")
    AcpClientTransport streamableHttpTransport(@Value("${demo.agent.url}") URI url) {
        return new StreamableHttpAcpClientTransport(url, AcpJsonMapper.createDefault());
    }

    @Bean
    AcpClientCustomizer printAndServeFiles() {
        return spec -> spec
                .sessionUpdateConsumer(notification -> {
                    if (notification.update() instanceof AcpSchema.AgentMessageChunk msg
                            && msg.content() instanceof AcpSchema.TextContent text) {
                        System.out.println("   agent: " + text.text());
                    }
                    return Mono.empty();
                })
                .readTextFileHandler(req -> {
                    System.out.println("   client: agent asked to read " + req.path());
                    String content = WORKSPACE.get(req.path());
                    if (content == null) {
                        return Mono.error(new IllegalArgumentException("No such file: " + req.path()));
                    }
                    return Mono.just(new AcpSchema.ReadTextFileResponse(content));
                });
    }

    @Bean
    CommandLineRunner demo(AcpSyncClient client) {
        return args -> {
            var init = client.initialize();
            System.out.println("   connected; protocol version " + init.protocolVersion());
            var session = client.newSession(new AcpSchema.NewSessionRequest("/workspace", List.of()));
            var response = client.prompt(new AcpSchema.PromptRequest(session.sessionId(),
                    List.of(new AcpSchema.TextContent("How are my notes?"))));
            System.out.println("   stop reason: " + response.stopReason());
        };
    }
}
