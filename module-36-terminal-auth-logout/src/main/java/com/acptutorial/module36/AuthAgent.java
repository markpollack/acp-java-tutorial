/*
 * Module 36: Terminal Auth and Logout - the agent
 *
 * Build & run:
 *   ./mvnw package -pl module-36-terminal-auth-logout -q
 *   ./mvnw exec:java -pl module-36-terminal-auth-logout
 */
package com.acptutorial.module36;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.support.AcpAgentSupport;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.Authenticate;
import com.agentclientprotocol.sdk.annotation.Initialize;
import com.agentclientprotocol.sdk.annotation.Logout;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.agentclientprotocol.sdk.error.AcpProtocolException;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * An agent that requires sign-in, offers two ways to do it, and supports logout, written
 * with annotations.
 *
 * <p><b>Auth methods.</b> The agent lists them in its {@code initialize} answer
 * ({@code authMethods}). {@code AuthMethod} is an open union of two kinds:
 * <ul>
 *   <li>{@link AcpSchema.AuthMethodAgent}: the agent does the work in {@code authenticate}.
 *   The client calls {@code authenticate(new AuthenticateRequest(id))}; the agent's
 *   {@link Authenticate @Authenticate} method accepts, or rejects by throwing
 *   {@code AcpProtocolException}.</li>
 *   <li>{@link AcpSchema.AuthMethodTerminal}: an interactive login (a TUI). The client runs
 *   the <em>agent program</em> again as a separate process in a terminal, adding the
 *   method's {@code args} and {@code env}, and does <em>not</em> pass this method to
 *   {@code authenticate}. The login stores credentials where the running agent can find
 *   them. Offer it only to a client that advertised {@code auth.terminal}
 *   ({@code NegotiatedCapabilities.supportsTerminalAuth()}).</li>
 * </ul>
 * An unknown auth method type from a newer agent reads as {@code AuthMethodAgent}.
 *
 * <p><b>Requiring auth.</b> Until the user is signed in, {@code session/new} is rejected with
 * {@code AcpErrorCodes.AUTHENTICATION_REQUIRED} ({@code -32000}).
 *
 * <p><b>Logout.</b> The agent advertises {@code AgentCapabilities.auth =
 * AgentAuthCapabilities.withLogout()}; its {@link Logout @Logout} method clears the stored
 * credentials. Clients check {@code supportsLogout()} before calling {@code logout(..)}.
 *
 * <p>{@link Initialize @Initialize} takes the connection's {@link NegotiatedCapabilities}
 * (the client's, already recorded when the handler runs), so the agent can decide which
 * methods to offer.
 *
 * <p>Run with {@code --login} this same program is the terminal login: it asks for a user
 * name on the terminal and writes the credentials file. In this demo the credentials file
 * is named by the {@code ACP_TUTORIAL_CREDENTIALS} environment variable.
 */
@AcpAgent(name = "auth-agent", version = "1.0.0")
public class AuthAgent {

    static final String API_KEY_METHOD = "api-key";
    static final String TERMINAL_METHOD = "terminal-login";
    static final String LOGIN_ARG = "--login";

    private final Path credentials = credentialsFile();

    /** Set by the agent auth method; lives only as long as this agent process. */
    private volatile String apiKeyUser;

    @Initialize
    AcpSchema.InitializeResponse initialize(AcpSchema.InitializeRequest req, NegotiatedCapabilities client) {
        List<AcpSchema.AuthMethod> methods = new ArrayList<>();
        methods.add(new AcpSchema.AuthMethodAgent(API_KEY_METHOD, "API key", "Use the key configured for this machine"));
        if (client.supportsTerminalAuth()) {
            methods.add(new AcpSchema.AuthMethodTerminal(TERMINAL_METHOD, "Log in in a terminal",
                    List.of(LOGIN_ARG), Map.of("AUTH_AGENT_LOGIN_STYLE", "plain")));
        }
        var capabilities = AcpSchema.AgentCapabilities.builder()
                .auth(AcpSchema.AgentAuthCapabilities.withLogout())
                .build();
        return new AcpSchema.InitializeResponse(1, capabilities, methods);
    }

    @Authenticate
    AcpSchema.AuthenticateResponse authenticate(AcpSchema.AuthenticateRequest req) {
        if (API_KEY_METHOD.equals(req.methodId())) {
            apiKeyUser = "api-key user";
            return new AcpSchema.AuthenticateResponse();
        }
        if (TERMINAL_METHOD.equals(req.methodId())) {
            throw new AcpProtocolException(AcpErrorCodes.INVALID_PARAMS,
                    "Terminal login is run by the client, not passed to authenticate", null);
        }
        throw new AcpProtocolException(AcpErrorCodes.INVALID_PARAMS, "Unknown auth method " + req.methodId(), null);
    }

    @Logout
    AcpSchema.LogoutResponse logout(AcpSchema.LogoutRequest req) {
        apiKeyUser = null;
        try {
            Files.deleteIfExists(credentials);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new AcpSchema.LogoutResponse();
    }

    @NewSession
    AcpSchema.NewSessionResponse newSession(AcpSchema.NewSessionRequest req) {
        if (signedInAs() == null) {
            throw new AcpProtocolException(AcpErrorCodes.AUTHENTICATION_REQUIRED, "Authentication required", null);
        }
        return new AcpSchema.NewSessionResponse(UUID.randomUUID().toString(), null, null);
    }

    @Prompt
    AcpSchema.PromptResponse prompt(AcpSchema.PromptRequest req, SyncPromptContext ctx) {
        ctx.sendMessage("Hello, " + signedInAs() + ".");
        return AcpSchema.PromptResponse.endTurn();
    }

    /** Who is signed in: the terminal login's stored credentials, or the agent method, or nobody. */
    private String signedInAs() {
        try {
            if (Files.exists(credentials)) {
                return Files.readString(credentials).trim() + " (terminal login)";
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return apiKeyUser;
    }

    static Path credentialsFile() {
        String configured = System.getenv("ACP_TUTORIAL_CREDENTIALS");
        return configured != null ? Path.of(configured)
                : Path.of(System.getProperty("java.io.tmpdir"), "acp-tutorial-36", "credentials");
    }

    /** The terminal login: an interactive program, not ACP. Reads a user name, stores it. */
    static void terminalLogin() throws IOException {
        System.out.println("Auth Agent login (style: " + System.getenv().getOrDefault("AUTH_AGENT_LOGIN_STYLE", "fancy") + ")");
        System.out.print("user name: ");
        System.out.flush();
        String user = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (user == null || user.isBlank()) {
            System.out.println("no user name; not logged in");
            System.exit(1);
        }
        Path file = credentialsFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, user.trim());
        System.out.println("logged in as " + user.trim());
    }

    public static void main(String[] args) throws IOException {
        if (List.of(args).contains(LOGIN_ARG)) {
            terminalLogin();
            return;
        }
        System.err.println("[AuthAgent] Ready");
        AcpAgentSupport.create(new AuthAgent())
                .transport(new StdioAcpAgentTransport())
                .build()
                .run();
    }
}
