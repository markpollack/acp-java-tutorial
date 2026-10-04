/*
 * Module 36: Terminal Auth and Logout - the demo client
 *
 * Build & run:
 *   ./mvnw package -pl module-36-terminal-auth-logout -q
 *   ./mvnw exec:java -pl module-36-terminal-auth-logout
 */
package com.acptutorial.module36;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.agentclientprotocol.sdk.spec.AcpError;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * A client that signs in to {@link AuthAgent} with a terminal login, logs out, and signs in
 * again with the agent auth method.
 *
 * <ol>
 *   <li>Advertise terminal auth on the builder: {@code ClientCapabilities.builder().auth(new
 *   AuthCapabilities(true))}. The agent then offers both methods.</li>
 *   <li>{@code session/new} before signing in fails with {@code -32000}
 *   ({@code AUTHENTICATION_REQUIRED}).</li>
 *   <li>For an {@code AuthMethodTerminal} the client runs the agent program again, as its
 *   own process, with the method's {@code args} and {@code env} added. A real client opens a
 *   terminal for the user; this demo types the user name into the process's input. It does
 *   not call {@code authenticate} for a terminal method.</li>
 *   <li>After the login, {@code session/new} works.</li>
 *   <li>{@code getAgentCapabilities().supportsLogout()} says whether {@code logout} is
 *   available; after it, {@code session/new} fails again.</li>
 *   <li>For an {@code AuthMethodAgent}, {@code authenticate(new AuthenticateRequest(id))}.</li>
 *   <li>A second client that does not advertise terminal auth is offered only the agent
 *   method.</li>
 * </ol>
 *
 * <p>{@code AuthMethod} is an open union: dispatch with {@code instanceof}.
 */
public final class AuthDemo {

    private static final String MODULE_NAME = "module-36-terminal-auth-logout";
    private static final String JAR_NAME = "auth-agent.jar";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Module 36: Terminal Auth and Logout ===\n");
        String jar = findAgentJar();
        Path credentials = Files.createTempDirectory("acp-tutorial-36").resolve("credentials");

        AgentParameters agent = AgentParameters.builder("java").arg("-jar").arg(jar)
                .addEnvVar("ACP_TUTORIAL_CREDENTIALS", credentials.toString())
                .build();

        System.out.println("--- client advertising auth.terminal ---");
        var terminalCapable = AcpSchema.ClientCapabilities.builder()
                .auth(new AcpSchema.AuthCapabilities(true))
                .build();
        try (AcpSyncClient client = AcpClient.sync(new StdioAcpClientTransport(agent))
                .clientCapabilities(terminalCapable)
                .sessionUpdateConsumer(AuthDemo::printUpdate)
                .build()) {

            AcpSchema.InitializeResponse init = client.initialize();
            AcpSchema.AuthMethodTerminal terminal = null;
            System.out.println("1. initialize: agentInfo " + init.agentInfo().name() + " " + init.agentInfo().version()
                    + " (from @AcpAgent); the agent offers");
            for (AcpSchema.AuthMethod method : init.authMethods()) {
                if (method instanceof AcpSchema.AuthMethodTerminal t) {
                    terminal = t;
                    System.out.println("   terminal method '" + t.id() + "': run the agent with args " + t.args()
                            + " env " + t.env());
                }
                else if (method instanceof AcpSchema.AuthMethodAgent a) {
                    System.out.println("   agent method '" + a.id() + "': call authenticate");
                }
            }
            NegotiatedCapabilities agentCaps = client.getAgentCapabilities();
            System.out.println("   agent supports logout: " + agentCaps.supportsLogout());

            System.out.println("2. session/new before signing in");
            tryNewSession(client);

            System.out.println("3. terminal login: the client runs the agent program as a separate process");
            runTerminalLogin(jar, terminal, credentials, "ada");

            System.out.println("4. session/new after the terminal login");
            String sid = tryNewSession(client);
            prompt(client, sid);

            System.out.println("5. logout");
            if (agentCaps.supportsLogout()) {
                client.logout(new AcpSchema.LogoutRequest());
                System.out.println("   logged out");
            }
            tryNewSession(client);

            System.out.println("6. authenticate with the agent method");
            client.authenticate(new AcpSchema.AuthenticateRequest("api-key"));
            System.out.println("   authenticated");
            prompt(client, tryNewSession(client));
        }

        System.out.println("\n--- client without auth.terminal ---");
        try (AcpSyncClient client = AcpClient.sync(new StdioAcpClientTransport(agent))
                .clientCapabilities(new AcpSchema.ClientCapabilities())
                .build()) {
            AcpSchema.InitializeResponse init = client.initialize();
            List<String> ids = new ArrayList<>();
            init.authMethods().forEach(m -> ids.add(m.id() + (m instanceof AcpSchema.AuthMethodTerminal ? " (terminal)" : " (agent)")));
            System.out.println("7. the agent offers only: " + ids);
        }

        Files.deleteIfExists(credentials);
        System.out.println("\n=== Demo Complete ===");
    }

    private static String tryNewSession(AcpSyncClient client) {
        try {
            String sid = client.newSession(new AcpSchema.NewSessionRequest(System.getProperty("user.dir"), List.of()))
                    .sessionId();
            System.out.println("   session/new: OK");
            return sid;
        }
        catch (AcpError e) {
            System.out.println("   session/new: AcpError " + e.getCode()
                    + (e.getCode() == AcpErrorCodes.AUTHENTICATION_REQUIRED ? " (AUTHENTICATION_REQUIRED)" : "")
                    + ": " + e.getError().message());
            return null;
        }
    }

    private static void prompt(AcpSyncClient client, String sid) {
        var response = client.prompt(new AcpSchema.PromptRequest(sid, List.of(new AcpSchema.TextContent("hi"))));
        System.out.println("   stop reason: " + response.stopReason());
    }

    /** What a client does for an AuthMethodTerminal: run the agent's command with the method's args and env. */
    private static void runTerminalLogin(String jar, AcpSchema.AuthMethodTerminal method, Path credentials,
            String typedUser) throws Exception {
        List<String> command = new ArrayList<>(List.of("java", "-jar", jar));
        command.addAll(method.args());
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.environment().putAll(method.env());
        pb.environment().put("ACP_TUTORIAL_CREDENTIALS", credentials.toString()); // as the agent was launched
        Process login = pb.start();
        try (OutputStream in = login.getOutputStream()) {
            in.write((typedUser + "\n").getBytes(StandardCharsets.UTF_8)); // the user types their name
        }
        try (var out = new BufferedReader(new InputStreamReader(login.getInputStream(), StandardCharsets.UTF_8))) {
            out.lines().forEach(line -> System.out.println("   [terminal] " + line));
        }
        login.waitFor(30, TimeUnit.SECONDS);
        System.out.println("   terminal login exited " + login.exitValue());
    }

    private static void printUpdate(AcpSchema.SessionNotification notification) {
        if (notification.update() instanceof AcpSchema.AgentMessageChunk msg
                && msg.content() instanceof AcpSchema.TextContent text) {
            System.out.println("   agent: " + text.text());
        }
    }

    private static String findAgentJar() {
        Path fromModule = Path.of("target/" + JAR_NAME);
        if (Files.exists(fromModule)) {
            return fromModule.toString();
        }
        Path fromRoot = Path.of(MODULE_NAME + "/target/" + JAR_NAME);
        if (Files.exists(fromRoot)) {
            return fromRoot.toString();
        }
        throw new RuntimeException(
            "Agent JAR not found. Run: ./mvnw package -pl " + MODULE_NAME + " -q");
    }
}
