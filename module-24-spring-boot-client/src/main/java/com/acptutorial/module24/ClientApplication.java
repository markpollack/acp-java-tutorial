/*
 * Module 24: Spring Boot Client
 *
 * Demonstrates using the autoconfigured AcpSyncClient in a Spring Boot app.
 * The client is configured entirely through application.properties:
 *   spring.acp.client.transport.stdio.command=java
 *   spring.acp.client.transport.stdio.args=-jar,...
 *
 * The autoconfiguration:
 * 1. Detects the stdio properties and creates a StdioAcpClientTransport
 * 2. Creates AcpSyncClient and AcpAsyncClient beans
 * 3. Manages graceful shutdown via DisposableBean
 *
 * Build & run (requires module-23 agent JAR):
 *   ./mvnw package -pl module-23-spring-boot-agent,module-24-spring-boot-client -q
 *   ./mvnw spring-boot:run -pl module-24-spring-boot-client
 */
package com.acptutorial.module24;

import java.util.List;

import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(AcpSyncClient client) {
        return args -> {
            System.out.println("=== Module 24: Spring Boot Client Demo ===\n");

            // Initialize the connection
            System.out.println("Sending initialize...");
            client.initialize();
            System.out.println("Connected!\n");

            // Create a session
            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            // Send a prompt
            String message = "Hello from Spring Boot client!";
            System.out.println("Sending: " + message);

            var response = client.prompt(new PromptRequest(
                session.sessionId(),
                List.of(new TextContent(message))));

            System.out.println("Stop reason: " + response.stopReason());
        };
    }

}
