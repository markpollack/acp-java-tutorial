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
 * application against it three times.
 *
 * <ol>
 *   <li>Agent: {@code HttpAgentApplication} with
 *   {@code spring.acp.agent.transport.http.listener.port=0}; the bound port comes from the
 *   {@code StreamableHttpAcpAgentTransport} bean's {@code getPort()}.</li>
 *   <li>Client over Streamable HTTP
 *   ({@code --spring.acp.client.transport.http.uri=http://localhost:<port>/acp}): the agent
 *   reads {@code NOTES.md} through the client's file handler.</li>
 *   <li>Client over WebSocket, to the same listener and path
 *   ({@code --spring.acp.client.transport.websocket.uri=ws://localhost:<port>/acp}).</li>
 *   <li>Client over Streamable HTTP with
 *   {@code --spring.acp.client.capabilities.read-text-file=false}: the handler is still
 *   registered but not advertised, so the agent does not ask for the file, and the client
 *   logs one WARN naming the handler it will not be asked to run.</li>
 * </ol>
 *
 * <p>Only a property changes between the three runs: the client code is the same.
 *
 * <p>Both applications live in this module, in separate packages so that component scanning
 * keeps the {@code @AcpAgent} bean out of the client. In production they are separate
 * deployments.
 */
public final class SpringBootHttpDemo {

    public static void main(String[] args) {
        System.out.println("=== Module 38: Spring Boot over HTTP ===\n");

        ConfigurableApplicationContext agent = HttpAgentApplication.start(
                "--spring.acp.agent.transport.http.listener.port=0", "--spring.main.keep-alive=false");
        try {
            int port = agent.getBean(StreamableHttpAcpAgentTransport.class).getPort();
            System.out.println("agent application listening (port 0 -> a free port): " + (port > 0));
            String http = "http://localhost:" + port + "/acp";
            String ws = "ws://localhost:" + port + "/acp";

            System.out.println("\n--- client over Streamable HTTP ---");
            HttpClientApplication.run("--spring.acp.client.transport.http.uri=" + http);

            System.out.println("\n--- client over WebSocket ---");
            HttpClientApplication.run("--spring.acp.client.transport.websocket.uri=" + ws);

            System.out.println("\n--- client over Streamable HTTP, read-text-file=false ---");
            HttpClientApplication.run("--spring.acp.client.transport.http.uri=" + http,
                    "--spring.acp.client.capabilities.read-text-file=false");
        }
        finally {
            agent.close();
            System.out.println("\nagent application closed");
        }
        System.out.println("\n=== Demo Complete ===");
    }
}
