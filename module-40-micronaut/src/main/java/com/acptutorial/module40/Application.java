/*
 * Module 40: Micronaut - the agent application
 *
 * Run on its own over stdio (an editor or client launches it):
 *   ./mvnw package -pl module-40-micronaut -q
 *   java -jar module-40-micronaut/target/micronaut-agent.jar
 * Or over Streamable HTTP and WebSocket on port 8080 (Ctrl+C to stop):
 *   java -jar module-40-micronaut/target/micronaut-agent.jar --acp.agent.transport.type=http
 */
package com.acptutorial.module40;

import io.micronaut.runtime.Micronaut;

/**
 * Starts the Micronaut application that serves {@link GreetingAgent}.
 *
 * <p>There is no ACP code here. acp-micronaut finds the one {@code @AcpAgent} bean from the
 * bean definitions the compiler wrote and serves it when the context starts, over the
 * transport {@code acp.agent.transport.type} names:
 * <ul>
 *   <li>{@code stdio} (the default): standard input and output carry the protocol, so the
 *   banner is off and every log line goes to standard error. When the client closes the
 *   agent's input and every answer is written, the context closes and the process exits 0.</li>
 *   <li>{@code http} (or {@code websocket}, the same listener): the SDK's Jetty listener, on
 *   {@code acp.agent.transport.http.listener.port} (on 127.0.0.1 unless
 *   {@code acp.agent.transport.http.listener.host} says otherwise), serving Streamable HTTP and
 *   WebSocket on one path, {@code acp.agent.transport.http.path}.
 *   It runs on its own port, next to Micronaut's HTTP server if the application has one.</li>
 * </ul>
 */
public final class Application {

    private Application() {
    }

    public static void main(String[] args) {
        Micronaut.build(args).mainClass(Application.class).banner(false).start();
    }
}
