/*
 * Module 38: Spring Boot over HTTP - the demo
 *
 * Build & run:
 *   ./mvnw compile -pl module-38-spring-boot-http -q
 *   ./mvnw exec:java -pl module-38-spring-boot-http
 */
package com.acptutorial.module38;

import com.acptutorial.module38.agent.HttpAgentApplication;
import com.acptutorial.module38.client.HttpClientApplication;
import com.agentclientprotocol.sdk.agent.transport.StreamableHttpAcpAgentTransport;

import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the Spring Boot agent application on a free port, then runs the Spring Boot client
 * application against it twice.
 *
 * <ol>
 *   <li>Agent: {@code HttpAgentApplication} with
 *   {@code spring.acp.agent.transport.http.port=0}; the bound port comes from the
 *   {@code StreamableHttpAcpAgentTransport} bean's {@code getPort()}.</li>
 *   <li>Client over Streamable HTTP ({@code --demo.agent.url=http://localhost:<port>/acp}):
 *   the agent reads {@code NOTES.md} through the client's file handler.</li>
 *   <li>Client over Streamable HTTP with
 *   {@code --spring.acp.client.capabilities.read-text-file=false}: the handler is still
 *   registered but not advertised, so the agent does not ask for the file.</li>
 * </ol>
 *
 * <p>The listener also accepts WebSocket on the same path, and acp-autoconfig can create a
 * WebSocket client from {@code spring.acp.client.transport.websocket.uri=ws://host:port/acp}.
 * This demo does not run it: with SDK 0.80.0 candidate 20261003.012658 a WebSocket client
 * logs errors when the Spring context closes it (the client is closed more than once, and
 * the WebSocket transport's close is not idempotent). Module 37 shows WebSocket without
 * Spring.
 *
 * <p>Both applications live in this module, in separate packages so that component scanning
 * keeps the {@code @AcpAgent} bean out of the client. In production they are separate
 * deployments.
 */
public final class SpringBootHttpDemo {

    public static void main(String[] args) {
        System.out.println("=== Module 38: Spring Boot over HTTP ===\n");

        ConfigurableApplicationContext agent = HttpAgentApplication.start(
                "--spring.acp.agent.transport.http.port=0", "--spring.main.keep-alive=false");
        try {
            int port = agent.getBean(StreamableHttpAcpAgentTransport.class).getPort();
            System.out.println("agent application listening (port 0 -> a free port): " + (port > 0));
            String http = "http://localhost:" + port + "/acp";

            System.out.println("\n--- client over Streamable HTTP ---");
            HttpClientApplication.run("--demo.agent.url=" + http);

            System.out.println("\n--- client over Streamable HTTP, read-text-file=false ---");
            HttpClientApplication.run("--demo.agent.url=" + http,
                    "--spring.acp.client.capabilities.read-text-file=false");
        }
        finally {
            agent.close();
            System.out.println("\nagent application closed");
        }
        System.out.println("\n=== Demo Complete ===");
    }
}
