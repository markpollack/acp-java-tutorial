/*
 * Module 31: Elicitation Demo
 *
 * Demonstrates the elicitation protocol — the agent asks the client
 * for structured user input via form schemas.
 *
 * Covers:
 * - Simple single-select (accept)
 * - Full multi-field form (accept with all field types)
 * - User declining the form
 * - User cancelling the form
 *
 * Build & run:
 *   ./mvnw package -pl module-31-elicitation -q
 *   ./mvnw exec:java -pl module-31-elicitation
 */
package com.acptutorial.module31;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.BooleanPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.ClientCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.CreateElicitationResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.ElicitationCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.ElicitationPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.ElicitationSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.FileSystemCapability;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.IntegerPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.MultiSelectPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NumberPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.StringPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.UntitledMultiSelectItems;

public class ElicitationDemo {

    private static final String MODULE_NAME = "module-31-elicitation";
    private static final String JAR_NAME = "elicitation-agent.jar";

    // Controls what the client does with the next elicitation
    private static final AtomicReference<String> nextAction = new AtomicReference<>("accept");

    public static void main(String[] args) {
        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg(findAgentJar())
            .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    if (notification.update() instanceof AgentMessageChunk msg) {
                        System.out.print(((TextContent) msg.content()).text());
                    }
                })
                .createElicitationHandler(req -> handleElicitation(req))
                .build()) {

            System.out.println("=== Module 31: Elicitation ===\n");

            var caps = new ClientCapabilities(
                new FileSystemCapability(), false,
                new ElicitationCapabilities(), null);
            client.initialize(new InitializeRequest(1, caps));
            System.out.println("Connected to ElicitationAgent\n");

            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            String sessionId = session.sessionId();

            // Demo 1: Simple single-select — user accepts
            System.out.println("--- Demo 1: Simple Select (accept) ---");
            nextAction.set("accept");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("simple"))));
            System.out.println();

            // Demo 2: Full project form — user accepts all fields
            System.out.println("--- Demo 2: Project Setup (accept) ---");
            nextAction.set("accept");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("project"))));
            System.out.println();

            // Demo 3: User declines the form
            System.out.println("--- Demo 3: User Declines ---");
            nextAction.set("decline");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("simple"))));
            System.out.println();

            // Demo 4: User cancels the form
            System.out.println("--- Demo 4: User Cancels ---");
            nextAction.set("cancel");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("simple"))));

            System.out.println("\n=== Demo Complete ===");
            System.out.println("Elicitation lets agents request structured input from users.");
            System.out.println("The agent handles accept, decline, and cancel responses.");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static CreateElicitationResponse handleElicitation(
            AcpSchema.CreateElicitationRequest req) {

        System.out.println("  [Elicitation] Agent asks: " + req.message());
        String action = nextAction.get();

        if ("decline".equals(action)) {
            System.out.println("  [Elicitation] User declines\n");
            return CreateElicitationResponse.decline();
        }
        if ("cancel".equals(action)) {
            System.out.println("  [Elicitation] User cancels\n");
            return CreateElicitationResponse.cancel();
        }

        ElicitationSchema schema = req.requestedSchema();
        if (schema == null || schema.properties() == null) {
            System.out.println("  [Elicitation] No schema — declining\n");
            return CreateElicitationResponse.decline();
        }

        Map<String, Object> values = new HashMap<>();
        for (var entry : schema.properties().entrySet()) {
            Object value = autoFill(entry.getKey(), entry.getValue());
            values.put(entry.getKey(), value);
            System.out.println("  [Elicitation]   " + entry.getKey() + " = " + value);
        }

        System.out.println("  [Elicitation] Accepting with " + values.size() + " fields\n");
        return CreateElicitationResponse.accept(values);
    }

    private static Object autoFill(String name, ElicitationPropertySchema field) {
        if (field instanceof StringPropertySchema str) {
            if (str.oneOf() != null && !str.oneOf().isEmpty()) {
                return str.oneOf().get(0).constValue();
            }
            if (str.enumValues() != null && !str.enumValues().isEmpty()) {
                return str.enumValues().get(0);
            }
            return str.defaultValue() != null ? str.defaultValue() : "my-" + name;
        } else if (field instanceof IntegerPropertySchema p) {
            return p.defaultValue() != null ? p.defaultValue() : 17;
        } else if (field instanceof NumberPropertySchema p) {
            return p.defaultValue() != null ? p.defaultValue() : 0.5;
        } else if (field instanceof BooleanPropertySchema p) {
            return p.defaultValue() != null ? p.defaultValue() : true;
        } else if (field instanceof MultiSelectPropertySchema multi) {
            if (multi.items() instanceof UntitledMultiSelectItems u) {
                var items = u.enumValues();
                return items.size() > 1 ? List.of(items.get(0), items.get(1)) : items;
            }
            return List.of();
        }
        return "unknown";
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
