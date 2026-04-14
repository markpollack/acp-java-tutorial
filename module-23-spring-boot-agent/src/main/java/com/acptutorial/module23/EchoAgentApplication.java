/*
 * Module 23: Spring Boot Agent - Application Entry Point
 *
 * A standard Spring Boot application. The autoconfiguration does the rest:
 * 1. Discovers the @AcpAgent bean (EchoAgentBean)
 * 2. Creates a StdioAcpAgentTransport (default for agents)
 * 3. Wires the agent through AcpAgentSupport
 * 4. Starts the agent via SmartLifecycle after context refresh
 *
 * Build & run:
 *   ./mvnw package -pl module-23-spring-boot-agent -q
 *   java -jar module-23-spring-boot-agent/target/module-23-spring-boot-agent-1.0.0-SNAPSHOT.jar
 */
package com.acptutorial.module23;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EchoAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EchoAgentApplication.class, args);
    }

}
