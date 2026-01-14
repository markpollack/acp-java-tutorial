/*
 * Module 18: Terminal Operations Demo
 *
 * Demonstrates terminal operations between agent and client.
 *
 * Key APIs:
 * - createTerminalHandler() - handle terminal creation, spawn process
 * - terminalOutputHandler() - capture process output
 * - waitForTerminalExitHandler() - wait for process to finish
 * - releaseTerminalHandler() - clean up process resources
 *
 * Build & run:
 *   ./mvnw package -pl module-18-terminal-operations -q
 *   ./mvnw exec:java -pl module-18-terminal-operations
 */
package com.acptutorial.module18;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.ClientCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.CreateTerminalResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.FileSystemCapability;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReleaseTerminalResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.TerminalOutputResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.WaitForTerminalExitResponse;

public class TerminalDemo {

    private static final String MODULE_NAME = "module-18-terminal-operations";
    private static final String JAR_NAME = "terminal-agent.jar";

    // Track terminal processes
    private static final Map<String, TerminalState> terminals = new ConcurrentHashMap<>();

    record TerminalState(Process process, StringBuilder output, Integer exitCode) {
        TerminalState withOutput(StringBuilder newOutput) {
            return new TerminalState(process, newOutput, exitCode);
        }
        TerminalState withExitCode(Integer code) {
            return new TerminalState(process, output, code);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Module 18: Terminal Operations ===\n");

        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg(findAgentJar())
            .build();

        var transport = new StdioAcpClientTransport(params);

        // Configure client with terminal capability
        var clientCaps = new ClientCapabilities(
            new FileSystemCapability(false, false),  // no file access
            true  // terminal enabled
        );

        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    if (update instanceof AgentMessageChunk msg) {
                        String text = ((TextContent) msg.content()).text();
                        System.out.print(text);
                    }
                })
                // Handler: Create terminal - spawn a process
                .createTerminalHandler(req -> {
                    String terminalId = UUID.randomUUID().toString();
                    System.out.println("\n[Client] Creating terminal: " + terminalId);
                    System.out.println("[Client] Command: " + req.command());

                    try {
                        // Start the process
                        ProcessBuilder pb = new ProcessBuilder("sh", "-c", req.command());
                        pb.redirectErrorStream(true);
                        Process process = pb.start();

                        terminals.put(terminalId, new TerminalState(process, new StringBuilder(), null));

                        // Start a thread to capture output
                        Thread outputReader = new Thread(() -> {
                            try {
                                BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(process.getInputStream()));
                                String line;
                                StringBuilder output = new StringBuilder();
                                while ((line = reader.readLine()) != null) {
                                    output.append(line).append("\n");
                                }
                                TerminalState state = terminals.get(terminalId);
                                if (state != null) {
                                    terminals.put(terminalId, state.withOutput(output));
                                }
                            } catch (Exception e) {
                                System.err.println("[Client] Output reader error: " + e.getMessage());
                            }
                        });
                        outputReader.start();

                        return new CreateTerminalResponse(terminalId);
                    } catch (Exception e) {
                        System.err.println("[Client] Failed to create process: " + e.getMessage());
                        throw new RuntimeException("Failed to create terminal: " + e.getMessage());
                    }
                })
                // Handler: Wait for terminal exit
                .waitForTerminalExitHandler(req -> {
                    String terminalId = req.terminalId();
                    System.out.println("[Client] Waiting for terminal exit: " + terminalId);

                    TerminalState state = terminals.get(terminalId);
                    if (state == null || state.process() == null) {
                        return new WaitForTerminalExitResponse(-1, null);
                    }

                    try {
                        int exitCode = state.process().waitFor();
                        terminals.put(terminalId, state.withExitCode(exitCode));
                        System.out.println("[Client] Process exited with code: " + exitCode);
                        return new WaitForTerminalExitResponse(exitCode, null);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new WaitForTerminalExitResponse(-1, null);
                    }
                })
                // Handler: Get terminal output
                .terminalOutputHandler(req -> {
                    String terminalId = req.terminalId();
                    System.out.println("[Client] Getting output for: " + terminalId);

                    TerminalState state = terminals.get(terminalId);
                    if (state == null) {
                        return new TerminalOutputResponse("", false, null);
                    }

                    String output = state.output() != null ? state.output().toString() : "";
                    return new TerminalOutputResponse(output, false, null);
                })
                // Handler: Release terminal
                .releaseTerminalHandler(req -> {
                    String terminalId = req.terminalId();
                    System.out.println("[Client] Releasing terminal: " + terminalId);

                    TerminalState state = terminals.remove(terminalId);
                    if (state != null && state.process() != null) {
                        state.process().destroyForcibly();
                    }

                    return new ReleaseTerminalResponse();
                })
                .build()) {

            // Initialize with terminal capability
            client.initialize(new InitializeRequest(1, clientCaps));
            System.out.println("Connected to TerminalAgent\n");

            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            String sessionId = session.sessionId();

            // Test 1: Simple command
            System.out.println("--- Test 1: Run 'echo Hello World' ---");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("run echo Hello World"))));
            System.out.println();

            // Test 2: Command with output
            System.out.println("\n--- Test 2: Run 'ls -la' ---");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("run ls -la"))));
            System.out.println();

            // Test 3: Multi-line output
            System.out.println("\n--- Test 3: Run 'cat /etc/os-release' ---");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("run cat /etc/os-release"))));
            System.out.println();

            System.out.println("\n=== Demo Complete ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
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
            "Agent JAR not found. Run: ./mvnw package -pl " + MODULE_NAME);
    }
}
