/*
 * Module 34: Extension Methods - the annotated agent
 *
 * Started by the demo with:  java -cp target/extension-agent.jar com.acptutorial.module34.AnnotatedExtensionAgent
 */
package com.acptutorial.module34;

import java.util.Map;
import java.util.UUID;

import com.acptutorial.module34.Extensions.LogEvent;
import com.acptutorial.module34.Extensions.Selection;
import com.acptutorial.module34.Extensions.SelectionQuery;
import com.acptutorial.module34.Extensions.WordCountParams;
import com.acptutorial.module34.Extensions.WordCountResult;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.support.AcpAgentSupport;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.ExtNotification;
import com.agentclientprotocol.sdk.annotation.ExtRequest;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.json.TypeRef;
import com.agentclientprotocol.sdk.spec.AcpError;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * The same extension methods as {@link ExtensionAgent}, served with
 * {@link ExtRequest @ExtRequest} and {@link ExtNotification @ExtNotification}.
 *
 * <p>The annotation names the method (it must start with {@code _}; discovery fails
 * otherwise). The handler takes <em>at most one</em> params parameter, read as that
 * parameter's type: a record for typed params, {@code Map<String, Object>} for the raw
 * object. Besides it, the method may take the connection's {@link AcpSyncAgent} (or
 * {@code AcpAsyncAgent}) and {@code NegotiatedCapabilities}, which is how
 * {@code @ExtNotification} answers with a notification of its own here. An
 * {@code @ExtRequest}'s return value is the result; returning {@code null} answers
 * {@code -32603}. An {@code @ExtNotification} gets no answer, so it must return {@code void}:
 * building the agent rejects one that returns a value.
 */
@AcpAgent(name = "extension-agent-annotated", version = "1.0.0")
public class AnnotatedExtensionAgent {

    @NewSession
    AcpSchema.NewSessionResponse newSession(AcpSchema.NewSessionRequest req) {
        return new AcpSchema.NewSessionResponse(UUID.randomUUID().toString(), null, null);
    }

    @ExtRequest(Extensions.WORD_COUNT)
    WordCountResult wordCount(WordCountParams params) {
        return new WordCountResult(params.text().split("\\s+").length, params.text().length());
    }

    @ExtRequest(Extensions.ECHO)
    Map<String, Object> echo(Map<String, Object> params) {
        return Extensions.ordered("echoed", params, "agentSawJavaType", params.getClass().getSimpleName());
    }

    @ExtNotification(Extensions.LOG)
    void log(LogEvent event, AcpSyncAgent agent) {
        agent.sendExtNotification(Extensions.ACK,
                Extensions.ordered("received", event.level() + ": " + event.message(), "by", "annotated agent"));
    }

    @Prompt
    AcpSchema.PromptResponse prompt(AcpSchema.PromptRequest req, SyncPromptContext ctx, AcpSyncAgent agent) {
        Selection selection = agent.sendExtRequest(Extensions.SELECTION, new SelectionQuery("src/Main.java"),
                new TypeRef<Selection>() {
                });
        agent.sendExtNotification(Extensions.STATUS, Extensions.ordered("state", "reviewing", "file", selection.file()));
        try {
            agent.sendExtRequest(Extensions.NOT_SERVED, Map.of());
        }
        catch (AcpError e) {
            ctx.sendThought("the client does not serve " + Extensions.NOT_SERVED + " (code " + e.getCode() + ")");
        }
        ctx.sendMessage("Reviewed " + selection.file() + " lines " + selection.startLine() + "-"
                + selection.endLine() + ": '" + selection.text() + "' looks fine (annotated agent).");
        return AcpSchema.PromptResponse.endTurn();
    }

    public static void main(String[] args) {
        System.err.println("[AnnotatedExtensionAgent] Ready");
        AcpAgentSupport.create(new AnnotatedExtensionAgent())
                .transport(new StdioAcpAgentTransport())
                .build()
                .run();
    }
}
