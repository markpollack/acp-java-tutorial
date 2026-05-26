/*
 * Module 31: Elicitation Agent
 *
 * An agent that demonstrates elicitation — requesting structured user
 * input from the client during prompt processing.
 *
 * Elicitation is an agent-to-client request. The agent sends a form
 * schema describing what input it needs, and the client presents it
 * to the user. The user can accept (with values), decline, or cancel.
 *
 * This demo exercises all form field types:
 * - Text input (StringPropertySchema)
 * - Single-select dropdown (StringPropertySchema + oneOf)
 * - Multi-select checkboxes (MultiSelectPropertySchema)
 * - Boolean toggle (BooleanPropertySchema)
 * - Number input (IntegerPropertySchema)
 *
 * Build & run:
 *   ./mvnw package -pl module-31-elicitation -q
 *   ./mvnw exec:java -pl module-31-elicitation
 */
package com.acptutorial.module31;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpAsyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.BooleanPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.CreateElicitationRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ElicitationAction;
import com.agentclientprotocol.sdk.spec.AcpSchema.ElicitationPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.ElicitationSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.EnumOption;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.IntegerPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.MultiSelectPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.StringPropertySchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.UntitledMultiSelectItems;

import reactor.core.publisher.Mono;

public class ElicitationAgent {

    public static void main(String[] args) {
        System.err.println("[ElicitationAgent] Starting...");
        var transport = new StdioAcpAgentTransport();
        AtomicReference<AcpAsyncAgent> agentRef = new AtomicReference<>();

        AcpAsyncAgent agent = AcpAgent.async(transport)
            .initializeHandler(req -> {
                System.err.println("[ElicitationAgent] Initialize");
                return Mono.just(InitializeResponse.ok());
            })
            .newSessionHandler(req -> {
                String sessionId = UUID.randomUUID().toString();
                System.err.println("[ElicitationAgent] New session: " + sessionId);
                return Mono.just(new NewSessionResponse(sessionId, null, null));
            })
            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();
                String text = req.text();
                System.err.println("[ElicitationAgent] Prompt: " + text);

                if (text.contains("project")) {
                    // Demo 1: Project setup form with multiple field types
                    Map<String, ElicitationPropertySchema> fields = Map.of(
                        "name", StringPropertySchema.text("Project Name"),
                        "template", StringPropertySchema.singleSelect("Template",
                            List.of(new EnumOption("web", "Web Application"),
                                    new EnumOption("cli", "CLI Tool"),
                                    new EnumOption("lib", "Library"))),
                        "features", new MultiSelectPropertySchema("array", "Features",
                            null, null,
                            new UntitledMultiSelectItems("string",
                                List.of("testing", "docker", "ci", "docs")),
                            null, null),
                        "javaVersion", new IntegerPropertySchema("integer",
                            "Java Version", null, 17L, 11L, 21L),
                        "gitInit", new BooleanPropertySchema("boolean",
                            "Initialize Git?", null, true)
                    );

                    var schema = new ElicitationSchema(fields,
                        List.of("name", "template"));

                    return agentRef.get()
                        .createElicitation(CreateElicitationRequest.form(
                            sessionId, "Configure your new project:", schema))
                        .flatMap(response -> {
                            if (response.action() == ElicitationAction.ACCEPT) {
                                var content = response.content();
                                StringBuilder msg = new StringBuilder();
                                msg.append("Project configured!\n");
                                msg.append("  Name: ").append(content.get("name")).append("\n");
                                msg.append("  Template: ").append(content.get("template")).append("\n");
                                msg.append("  Features: ").append(content.get("features")).append("\n");
                                msg.append("  Java: ").append(content.get("javaVersion")).append("\n");
                                msg.append("  Git: ").append(content.get("gitInit")).append("\n");
                                return context.sendMessage(msg.toString())
                                    .then(Mono.just(PromptResponse.endTurn()));
                            } else {
                                return context.sendMessage(
                                    "User " + response.action().name().toLowerCase() + "d the form.\n")
                                    .then(Mono.just(new PromptResponse(StopReason.END_TURN)));
                            }
                        });

                } else if (text.contains("simple")) {
                    // Demo 2: Simple single-field form
                    var schema = new ElicitationSchema(
                        Map.of("color", StringPropertySchema.singleSelect("Favorite color",
                            List.of(new EnumOption("red", "Red"),
                                    new EnumOption("blue", "Blue"),
                                    new EnumOption("green", "Green")))),
                        List.of("color"));

                    return agentRef.get()
                        .createElicitation(CreateElicitationRequest.form(
                            sessionId, "Quick question:", schema))
                        .flatMap(response -> {
                            String msg = response.action() == ElicitationAction.ACCEPT
                                ? "You chose: " + response.content().get("color") + "\n"
                                : "No selection made.\n";
                            return context.sendMessage(msg)
                                .then(Mono.just(PromptResponse.endTurn()));
                        });

                } else {
                    return context.sendMessage("Say 'project' for a full form or 'simple' for a quick select.\n")
                        .then(Mono.just(PromptResponse.endTurn()));
                }
            })
            .build();

        agentRef.set(agent);
        System.err.println("[ElicitationAgent] Ready");
        agent.start().then(agent.awaitTermination()).block();
    }

}
