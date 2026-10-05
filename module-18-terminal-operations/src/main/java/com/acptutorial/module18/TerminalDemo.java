/*
 * Module 18: Terminal Operations Demo
 *
 * Demonstrates terminal operations between agent and client.
 *
 * Key APIs:
 * - createTerminalHandler() - handle terminal creation, spawn process
 * - terminalOutputHandler() - capture process output
 * - waitForTerminalExitHandler() - wait for process to finish
 * - killTerminalHandler() - stop the process, keeping the terminal for its output
 * - releaseTerminalHandler() - clean up process resources
 *
 * The client advertises the terminal capability once all five handlers are registered;
 * with only some of them it logs a warning naming the missing ones and advertises none.
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
import com.agentclientprotocol.sdk.spec.AcpSchema.CreateTerminalResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.KillTerminalCommandResponse;
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

    // One terminal: its process, the thread that drains the process's output into
    // `output` as it arrives, and the exit code once known. `output` is shared with the
    // reader thread, so it is read and written under its own lock.
    record TerminalState(Process process, Thread outputReader, StringBuilder output, Integer exitCode) {
        TerminalState withExitCode(Integer code) {
            return new TerminalState(process, outputReader, output, code);
        }
        String outputSoFar() {
            synchronized (output) {
                return output.toString();
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Module 18: Terminal Operations ===\n");

        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg(findAgentJar())
            .build();

        var transport = new StdioAcpClientTransport(params);

        // The client advertises the terminal capability once all five terminal handlers below
        // are registered (create, output, wait for exit, kill, release); with only some of them
        // it logs a warning naming the missing ones and advertises no terminal
        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateHandler(notification -> {
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
                    System.out.println("[Client] Command: " + req.command() +
                        (req.args() != null ? " " + String.join(" ", req.args()) : ""));

                    try {
                        // Build command list from command + args
                        List<String> commandList = new java.util.ArrayList<>();
                        commandList.add(req.command());
                        if (req.args() != null) {
                            commandList.addAll(req.args());
                        }

                        // Start the process
                        ProcessBuilder pb = new ProcessBuilder(commandList);
                        pb.redirectErrorStream(true);
                        Process process = pb.start();

                        // Drain the output as it arrives, so terminal/output can return what
                        // the command has printed so far
                        StringBuilder output = new StringBuilder();
                        Thread outputReader = new Thread(() -> {
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(process.getInputStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    synchronized (output) {
                                        output.append(line).append("\n");
                                    }
                                }
                            } catch (Exception e) {
                                System.err.println("[Client] Output reader error: " + e.getMessage());
                            }
                        });
                        terminals.put(terminalId, new TerminalState(process, outputReader, output, null));
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
                        // The process has exited; wait for the reader to drain the last of its
                        // output, so a terminal/output that follows sees all of it
                        state.outputReader().join();
                        terminals.computeIfPresent(terminalId, (id, s) -> s.withExitCode(exitCode));
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

                    return new TerminalOutputResponse(state.outputSoFar(), false, null);
                })
                // Handler: Kill the command. The terminal stays valid: its output can still be
                // read, and the agent releases it afterwards.
                .killTerminalHandler(req -> {
                    String terminalId = req.terminalId();
                    System.out.println("[Client] Killing command in terminal: " + terminalId);

                    TerminalState state = terminals.get(terminalId);
                    if (state != null && state.process() != null) {
                        state.process().destroyForcibly();
                    }

                    return new KillTerminalCommandResponse();
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

            // Initialize: advertises the terminal capability set on the builder
            client.initialize();
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
