/*
 * Module 31: Elicitation Demo
 *
 * Demonstrates the elicitation protocol — the agent asks the client
 * for structured user input via form schemas.
 *
 * The client handles elicitation/create requests by rendering each
 * form field to the console and collecting responses. This is a
 * minimal demonstration — real clients (IDEs) would render proper
 * form widgets.
 *
 * Key APIs:
 *   Agent side:  context.createElicitation(request)
 *   Client side: .createElicitationHandler(handler)
 *
 * Build & run:
 *   ./mvnw package -pl module-31-elicitation -q
 *   ./mvnw exec:java -pl module-31-elicitation
 */
package com.acptutorial.module31;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                // Register elicitation handler — renders form fields to console
                .createElicitationHandler(req -> handleElicitation(req))
                .build()) {

            System.out.println("=== Module 31: Elicitation ===\n");

            // Advertise elicitation capability
            var caps = new ClientCapabilities(
                new FileSystemCapability(), false,
                new ElicitationCapabilities(), null);
            client.initialize(new InitializeRequest(1, caps));
            System.out.println("Connected to ElicitationAgent\n");

            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            String sessionId = session.sessionId();

            // Demo 1: Simple single-select
            System.out.println("--- Demo 1: Simple Select ---");
            System.out.println("Sending 'simple' prompt...\n");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("simple"))));
            System.out.println();

            // Demo 2: Full project setup form
            System.out.println("--- Demo 2: Project Setup Form ---");
            System.out.println("Sending 'project' prompt...\n");
            client.prompt(new PromptRequest(sessionId,
                List.of(new TextContent("project"))));

            System.out.println("\n=== Demo Complete ===");
            System.out.println("Elicitation lets agents request structured input from users.");
            System.out.println("Field types: text, select, multi-select, boolean, integer");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles an elicitation request by auto-filling form fields.
     * A real client would render UI widgets; this demo just picks
     * sensible defaults and prints what it chose.
     */
    private static CreateElicitationResponse handleElicitation(
            AcpSchema.CreateElicitationRequest req) {

        System.out.println("  [Elicitation] Agent asks: " + req.message());

        ElicitationSchema schema = req.requestedSchema();
        if (schema == null || schema.properties() == null) {
            System.out.println("  [Elicitation] No form schema — declining");
            return CreateElicitationResponse.decline();
        }

        Map<String, Object> values = new HashMap<>();

        for (var entry : schema.properties().entrySet()) {
            String fieldName = entry.getKey();
            ElicitationPropertySchema field = entry.getValue();

            Object value = autoFillField(fieldName, field);
            values.put(fieldName, value);
            System.out.println("  [Elicitation]   " + fieldName + " = " + value);
        }

        System.out.println("  [Elicitation] Accepting with " + values.size() + " fields\n");
        return CreateElicitationResponse.accept(values);
    }

    /**
     * Auto-fills a form field with a sensible value for demonstration.
     */
    private static Object autoFillField(String name, ElicitationPropertySchema field) {
        if (field instanceof StringPropertySchema str) {
            // Single-select: pick the first option
            if (str.oneOf() != null && !str.oneOf().isEmpty()) {
                return str.oneOf().get(0).constValue();
            }
            if (str.enumValues() != null && !str.enumValues().isEmpty()) {
                return str.enumValues().get(0);
            }
            // Text: use default or generate
            return str.defaultValue() != null ? str.defaultValue() : "my-" + name;

        } else if (field instanceof IntegerPropertySchema intProp) {
            return intProp.defaultValue() != null ? intProp.defaultValue() : 17;

        } else if (field instanceof NumberPropertySchema numProp) {
            return numProp.defaultValue() != null ? numProp.defaultValue() : 0.5;

        } else if (field instanceof BooleanPropertySchema boolProp) {
            return boolProp.defaultValue() != null ? boolProp.defaultValue() : true;

        } else if (field instanceof MultiSelectPropertySchema multi) {
            // Pick first two items if available
            if (multi.items() instanceof UntitledMultiSelectItems untitled) {
                var items = untitled.enumValues();
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
