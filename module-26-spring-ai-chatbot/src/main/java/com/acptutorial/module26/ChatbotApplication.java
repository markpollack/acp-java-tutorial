/*
 * Module 26: Spring AI Chatbot Agent - Application Entry Point
 *
 * The portability flavor of module 25. Same ACP agent, but the prompt handler
 * talks to the model through Spring AI's ChatClient instead of the Anthropic SDK
 * directly. Swap the spring-ai-starter-model-* dependency (or a property) to
 * change provider - the ACP agent never changes.
 *
 * Spring Boot autoconfiguration discovers the @AcpAgent bean and manages its
 * lifecycle, exactly like module 23.
 *
 * Build & run (requires ANTHROPIC_API_KEY):
 *   ./mvnw package -pl module-26-spring-ai-chatbot -q
 *   ./mvnw exec:java -pl module-26-spring-ai-chatbot
 */
package com.acptutorial.module26;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }

}
