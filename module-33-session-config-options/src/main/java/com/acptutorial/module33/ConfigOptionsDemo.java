/*
 * Module 33: Session Config Options - the demo client
 *
 * Build & run:
 *   ./mvnw package -pl module-33-session-config-options -q
 *   ./mvnw exec:java -pl module-33-session-config-options
 */
package com.acptutorial.module33;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpError;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * A client that reads an agent's session config options, changes them, and sees the agent
 * change one itself.
 *
 * <p>It runs three passes over stdio:
 * <ol>
 *   <li>the builder agent ({@link ConfigAgent}), advertising boolean config options;</li>
 *   <li>the annotated agent ({@link AnnotatedConfigAgent}), the same;</li>
 *   <li>the builder agent again, <em>without</em> boolean support: the agent leaves the
 *   {@code verbose} option out, and setting it anyway is rejected with {@code -32602}. This
 *   pass also calls the legacy {@code session/set_mode}, as a client that predates config
 *   options would, and the next answer shows the agent kept both in step.</li>
 * </ol>
 *
 * <p>Key points:
 * <ul>
 *   <li><b>Capabilities go on the builder.</b> {@code ClientCapabilities.builder().session(
 *   ClientSessionCapabilities.withBooleanConfigOptions())} is set with
 *   {@code .clientCapabilities(...)}; {@code initialize()} sends it.</li>
 *   <li><b>{@code setSessionConfigOption} returns the full list</b>: print it, replace your
 *   copy. {@code SetSessionConfigOptionRequest.select(..)} sends a {@code String},
 *   {@code .bool(..)} a {@code Boolean} with {@code type: "boolean"}.</li>
 *   <li><b>{@code config_option_update}</b> arrives through the session-update consumer
 *   when the agent changes a value itself; it also carries the full list.</li>
 *   <li><b>Updates are handled before {@code prompt()} returns.</b> The SDK completes a
 *   response only after every notification received before it has been handled by the
 *   consumer, so the client prints the stop reason right after {@code prompt()} with no
 *   extra synchronization. (A Java SDK guarantee; do not assume it of other ACP SDKs.)</li>
 *   <li><b>Option types are an open union.</b> Dispatch with {@code instanceof} and keep a
 *   branch for {@code UnknownSessionConfigOption}, a type from a newer agent.</li>
 * </ul>
 */
public final class ConfigOptionsDemo {

    private static final String MODULE_NAME = "module-33-session-config-options";
    private static final String JAR_NAME = "config-agent.jar";

    public static void main(String[] args) {
        System.out.println("=== Module 33: Session Config Options ===\n");
        String jar = findAgentJar();
        run("builder", AgentParameters.builder("java").arg("-jar").arg(jar).build(), true);
        run("annotated", AgentParameters.builder("java").arg("-cp").arg(jar)
                .arg(AnnotatedConfigAgent.class.getName()).build(), true);
        runWithoutBooleans(AgentParameters.builder("java").arg("-jar").arg(jar).build());
        System.out.println("=== Demo Complete ===");
    }

    private static void run(String variant, AgentParameters params, boolean advertiseBooleans) {
        System.out.println("--- " + variant + " agent, boolean options advertised: " + advertiseBooleans + " ---");

        var capabilities = advertiseBooleans
                ? AcpSchema.ClientCapabilities.builder()
                        .session(AcpSchema.ClientSessionCapabilities.withBooleanConfigOptions())
                        .build()
                : new AcpSchema.ClientCapabilities();

        try (AcpSyncClient client = AcpClient.sync(new StdioAcpClientTransport(params))
                .clientCapabilities(capabilities)
                .sessionUpdateConsumer(ConfigOptionsDemo::printUpdate)
                .build()) {

            client.initialize();
            var session = client.newSession(new AcpSchema.NewSessionRequest(
                    System.getProperty("user.dir"), List.of()));
            String sid = session.sessionId();
            System.out.println("session/new offered:");
            printOptions(session.configOptions());
            if (session.modes() != null) {
                System.out.println("  (legacy modes also sent: current=" + session.modes().currentModeId()
                        + "; a config-options client ignores them)");
            }

            System.out.println("set model -> orbit-1");
            printOptions(client.setSessionConfigOption(
                    AcpSchema.SetSessionConfigOptionRequest.select(sid, SessionSettings.MODEL, "orbit-1"))
                    .configOptions());

            System.out.println("set mode -> code");
            printOptions(client.setSessionConfigOption(
                    AcpSchema.SetSessionConfigOptionRequest.select(sid, SessionSettings.MODE, "code"))
                    .configOptions());

            System.out.println("set verbose -> true");
            try {
                printOptions(client.setSessionConfigOption(
                        AcpSchema.SetSessionConfigOptionRequest.bool(sid, SessionSettings.VERBOSE, true))
                        .configOptions());
            }
            catch (AcpError e) {
                System.out.println("  rejected: code " + e.getCode() + ": " + e.getMessage());
            }

            System.out.println("set model -> gpt-99 (not offered)");
            try {
                client.setSessionConfigOption(
                        AcpSchema.SetSessionConfigOptionRequest.select(sid, SessionSettings.MODEL, "gpt-99"));
                System.out.println("  unexpectedly accepted");
            }
            catch (AcpError e) {
                System.out.println("  rejected: code " + e.getCode() + ": " + e.getMessage());
            }

            System.out.println("prompt");
            var answer = client.prompt(new AcpSchema.PromptRequest(sid,
                    List.of(new AcpSchema.TextContent("Say hello"))));
            System.out.println("  stop reason: " + answer.stopReason());

            System.out.println("set model -> " + SessionSettings.PREVIEW_MODEL + ", then prompt");
            client.setSessionConfigOption(AcpSchema.SetSessionConfigOptionRequest.select(
                    sid, SessionSettings.MODEL, SessionSettings.PREVIEW_MODEL));
            answer = client.prompt(new AcpSchema.PromptRequest(sid,
                    List.of(new AcpSchema.TextContent("Say hello again"))));
            System.out.println("  stop reason: " + answer.stopReason());
        }
        System.out.println();
    }

    private static void runWithoutBooleans(AgentParameters params) {
        System.out.println("--- builder agent, boolean options advertised: false ---");
        try (AcpSyncClient client = AcpClient.sync(new StdioAcpClientTransport(params))
                .clientCapabilities(new AcpSchema.ClientCapabilities())
                .sessionUpdateConsumer(ConfigOptionsDemo::printUpdate)
                .build()) {
            client.initialize();
            var session = client.newSession(new AcpSchema.NewSessionRequest(
                    System.getProperty("user.dir"), List.of()));
            String sid = session.sessionId();
            System.out.println("session/new offered (no verbose option for this client):");
            printOptions(session.configOptions());

            System.out.println("set verbose -> true (never offered)");
            try {
                client.setSessionConfigOption(
                        AcpSchema.SetSessionConfigOptionRequest.bool(sid, SessionSettings.VERBOSE, true));
                System.out.println("  unexpectedly accepted");
            }
            catch (AcpError e) {
                System.out.println("  rejected: code " + e.getCode() + ": " + e.getMessage());
            }

            System.out.println("legacy session/set_mode -> code, then prompt");
            client.setSessionMode(new AcpSchema.SetSessionModeRequest(sid, "code"));
            var answer = client.prompt(new AcpSchema.PromptRequest(sid,
                    List.of(new AcpSchema.TextContent("Say hello"))));
            System.out.println("  stop reason: " + answer.stopReason());
        }
        System.out.println();
    }

    private static void printUpdate(AcpSchema.SessionNotification notification) {
        AcpSchema.SessionUpdate update = notification.update();
        if (update instanceof AcpSchema.AgentMessageChunk chunk) {
            System.out.println("  agent: " + text(chunk.content()));
        }
        else if (update instanceof AcpSchema.AgentThoughtChunk thought) {
            System.out.println("  thought: " + text(thought.content()));
        }
        else if (update instanceof AcpSchema.ConfigOptionUpdate config) {
            System.out.println("  config_option_update from the agent:");
            printOptions(config.configOptions());
        }
        else {
            System.out.println("  update: " + update);
        }
    }

    private static String text(AcpSchema.ContentBlock block) {
        return block instanceof AcpSchema.TextContent t ? t.text() : block.toString();
    }

    static void printOptions(List<AcpSchema.SessionConfigOption> options) {
        if (options == null) {
            System.out.println("  (no config options)");
            return;
        }
        for (AcpSchema.SessionConfigOption o : options) {
            if (o instanceof AcpSchema.SessionConfigSelect select) {
                String marker = AcpSchema.SessionConfigOptionCategory.MODEL.equals(select.category())
                        ? "   <- model picker"
                        : AcpSchema.SessionConfigOptionCategory.MODE.equals(select.category()) ? "   <- mode" : "";
                System.out.println("  select  " + select.id() + " = " + select.currentValue() + marker);
                if (select.options() instanceof AcpSchema.GroupedSelectOptions grouped) {
                    for (AcpSchema.SessionConfigSelectGroup group : grouped.groups()) {
                        StringBuilder line = new StringBuilder("    " + group.name() + ":");
                        group.options().forEach(option -> line.append(' ').append(option.value()));
                        System.out.println(line);
                    }
                }
                else {
                    StringBuilder line = new StringBuilder("    options:");
                    select.options().allOptions().forEach(option -> line.append(' ').append(option.value()));
                    System.out.println(line);
                }
            }
            else if (o instanceof AcpSchema.SessionConfigBoolean bool) {
                System.out.println("  boolean " + bool.id() + " = " + bool.currentValue());
            }
            else if (o instanceof AcpSchema.UnknownSessionConfigOption unknown) {
                System.out.println("  (unknown option type " + unknown.type() + ", ignored)");
            }
            else {
                System.out.println("  (unrecognised option, ignored)");
            }
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
