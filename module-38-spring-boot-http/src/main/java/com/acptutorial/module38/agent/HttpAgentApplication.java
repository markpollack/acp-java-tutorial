/*
 * Module 38: Spring Boot over HTTP - the agent application
 *
 * Run on its own (port 8080, from agent.properties; Ctrl+C to stop):
 *   ./mvnw exec:java -pl module-38-spring-boot-http -Dexec.mainClass=com.acptutorial.module38.agent.HttpAgentApplication
 */
package com.acptutorial.module38.agent;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A Spring Boot application that serves its {@code @AcpAgent} bean over the network.
 *
 * <p>Compared with module 23 (stdio), one property changes:
 * {@code spring.acp.agent.transport.type=http}. acp-autoconfig then builds an
 * {@code AcpAgentFactory} from the bean ({@code AcpAgentSupport...buildFactory()}: one agent
 * runtime per connection, all dispatching to the one bean) and, because this is not a web
 * application, runs the SDK's {@code StreamableHttpAcpAgentTransport} listener as a bean:
 * Jetty with HTTP/1.1, h2c and the WebSocket upgrade, on
 * {@code spring.acp.agent.transport.http.port} and {@code .path}. In a servlet web
 * application it would mount {@code StreamableHttpAcpServlet} on the application's own server
 * instead (HTTP/SSE only, no WebSocket).
 *
 * <p>Port {@code 0} picks a free port; the bound port is
 * {@code context.getBean(StreamableHttpAcpAgentTransport.class).getPort()}.
 *
 * <p>The application reads {@code agent.properties} ({@code spring.config.name=agent}) so it
 * can share this module with the client application, which reads {@code client.properties}.
 */
@SpringBootApplication
public class HttpAgentApplication {

    public static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(HttpAgentApplication.class)
                .properties("spring.config.name=agent")
                .run(args);
    }

    public static void main(String[] args) {
        start(args);
    }
}
