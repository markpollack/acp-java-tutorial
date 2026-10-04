/*
 * Module 40: Micronaut - the demo
 *
 * Build & run:
 *   ./mvnw -Psdk-candidate package -pl module-40-micronaut -q
 *   ./mvnw -Psdk-candidate exec:java -pl module-40-micronaut
 */
package com.acptutorial.module40;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.json.AcpJsonMapper;
import com.agentclientprotocol.sdk.micronaut.agent.AcpAgentRuntime;
import com.agentclientprotocol.sdk.spec.AcpSchema;

import io.micronaut.context.ApplicationContext;

/**
 * Runs {@link GreetingAgent}, a Micronaut bean, two ways, and talks to it with ACP clients
 * that Micronaut builds from configuration.
 *
 * <ol>
 *   <li><b>stdio.</b> A Micronaut client application with
 *   {@code acp.client.transport.stdio.command} and {@code .args} launches the packaged agent
 *   application ({@code java -jar target/micronaut-agent.jar}) as a process. The client prints
 *   the {@code initialize} answer it receives: {@code agentInfo} and the capabilities, all
 *   derived from the agent's annotations. Then a prompt (through the {@code @Around}
 *   interceptor), {@code session/list} and {@code session/close}.</li>
 *   <li><b>HTTP.</b> The same application started in this JVM with
 *   {@code acp.agent.transport.type=http} and port 0; {@code AcpAgentRuntime.port()} is the
 *   bound port. One client connects with {@code acp.client.transport.http.uri} (Streamable
 *   HTTP), another with {@code acp.client.transport.websocket.uri}, to the same path. The
 *   interceptor's call count goes on across the two connections: one agent bean serves every
 *   connection.</li>
 * </ol>
 *
 * <p>Each client application sets {@code acp.agent.enabled=false}: it shares this module's
 * classpath, and so the agent's bean definition, with the agent application. In production they
 * are separate applications.
 */
public final class MicronautDemo {

    private static final String MODULE_NAME = "module-40-micronaut";
    private static final String JAR_NAME = "micronaut-agent.jar";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Module 40: Micronaut ===\n");
        String jar = findAgentJar();
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();

        System.out.println("--- 1. stdio: a Micronaut client launches the Micronaut agent application ---");
        try (ApplicationContext client = context(Map.of(
                "acp.agent.enabled", false,
                "acp.client.transport.stdio.command", java,
                "acp.client.transport.stdio.args", List.of("-jar", jar)))) {
            AcpSyncClient acp = client.getBean(AcpSyncClient.class);
            printInitialize(acp.initialize());
            String sessionId = acp.newSession(new AcpSchema.NewSessionRequest("/workspace", List.of())).sessionId();
            prompt(acp, sessionId, "hi over stdio");
            System.out.println("   session/list: " + acp.listSessions(new AcpSchema.ListSessionsRequest(null)).sessions().size()
                    + " session(s)");
            acp.closeSession(new AcpSchema.CloseSessionRequest(sessionId));
            System.out.println("   session/close; session/list: "
                    + acp.listSessions(new AcpSchema.ListSessionsRequest(null)).sessions().size() + " session(s)");
        }
        System.out.println("   client closed; the agent process exited when its input ended");

        System.out.println("\n--- 2. HTTP: the same agent application on the SDK's listener ---");
        try (ApplicationContext agent = context(Map.of(
                "acp.agent.transport.type", "http",
                "acp.agent.transport.http.port", 0))) {
            int port = agent.getBean(AcpAgentRuntime.class).port().orElseThrow();
            System.out.println("agent application listening (port 0 -> a free port): " + (port > 0));

            System.out.println("\n   Streamable HTTP client (acp.client.transport.http.uri)");
            try (ApplicationContext client = context(Map.of(
                    "acp.agent.enabled", false,
                    "acp.client.transport.http.uri", "http://localhost:" + port + "/acp"))) {
                converse(client.getBean(AcpSyncClient.class), "hi over Streamable HTTP");
            }

            System.out.println("\n   WebSocket client (acp.client.transport.websocket.uri)");
            try (ApplicationContext client = context(Map.of(
                    "acp.agent.enabled", false,
                    "acp.client.transport.websocket.uri", "ws://localhost:" + port + "/acp"))) {
                converse(client.getBean(AcpSyncClient.class), "hi over WebSocket");
            }
        }
        System.out.println("\nagent application closed");
        System.out.println("\n=== Demo Complete ===");
    }

    /** A Micronaut application context with this module's configuration plus the given properties. */
    private static ApplicationContext context(Map<String, Object> properties) {
        return ApplicationContext.builder()
                .deduceEnvironment(false)
                .properties(properties)
                .start();
    }

    private static void converse(AcpSyncClient acp, String text) {
        var init = acp.initialize();
        System.out.println("   connected: " + init.agentInfo().name() + ", protocol version " + init.protocolVersion());
        String sessionId = acp.newSession(new AcpSchema.NewSessionRequest("/workspace", List.of())).sessionId();
        prompt(acp, sessionId, text);
    }

    private static void prompt(AcpSyncClient acp, String sessionId, String text) {
        var response = acp.prompt(new AcpSchema.PromptRequest(sessionId, List.of(new AcpSchema.TextContent(text))));
        System.out.println("   stop reason: " + response.stopReason());
    }

    /** What the client received for initialize: the JSON, then the fields the annotations produced. */
    private static void printInitialize(AcpSchema.InitializeResponse init) throws Exception {
        System.out.println("   initialize response the client received:");
        System.out.println("   " + AcpJsonMapper.createDefault().writeValueAsString(init));
        var info = init.agentInfo();
        System.out.println("   agentInfo (from @AcpAgent): " + info.name() + " " + info.version() + ", title '" + info.title() + "'");
        var caps = init.agentCapabilities();
        var session = caps.sessionCapabilities();
        System.out.println("   sessionCapabilities.list (from @ListSessions): " + (session != null && session.list() != null));
        System.out.println("   sessionCapabilities.close (from @CloseSession): " + (session != null && session.close() != null));
        System.out.println("   promptCapabilities.embeddedContext (from @Prompt(embeddedContext = true)): "
                + caps.promptCapabilities().embeddedContext());
        System.out.println("   loadSession (no @LoadSession): " + caps.loadSession());
    }

    private static String findAgentJar() {
        Path fromModule = Path.of("target/" + JAR_NAME);
        if (Files.exists(fromModule)) {
            return fromModule.toAbsolutePath().toString();
        }
        Path fromRoot = Path.of(MODULE_NAME + "/target/" + JAR_NAME);
        if (Files.exists(fromRoot)) {
            return fromRoot.toAbsolutePath().toString();
        }
        throw new RuntimeException("Agent JAR not found. Run: ./mvnw -Psdk-candidate package -pl " + MODULE_NAME + " -q");
    }
}
