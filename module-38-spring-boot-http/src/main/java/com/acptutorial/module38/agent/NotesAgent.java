/*
 * Module 38: Spring Boot over HTTP - the @AcpAgent bean
 */
package com.acptutorial.module38.agent;

import java.util.UUID;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema;

import org.springframework.stereotype.Component;

/**
 * An agent that reads a file from the client's workspace when the client says it can serve
 * one.
 *
 * <p>Over a listener transport this one bean serves every connection, so it keeps no
 * per-connection state. The {@link NegotiatedCapabilities} parameter is the capabilities of
 * the connection the prompt arrived on: the agent asks the client for {@code fs/read_text_file}
 * only if that client advertised it.
 */
@Component
@AcpAgent(name = "notes-agent", version = "1.0.0")
public class NotesAgent {

    @NewSession
    AcpSchema.NewSessionResponse newSession(AcpSchema.NewSessionRequest req) {
        return new AcpSchema.NewSessionResponse(UUID.randomUUID().toString(), null, null);
    }

    @Prompt
    AcpSchema.PromptResponse prompt(AcpSchema.PromptRequest req, SyncPromptContext ctx, NegotiatedCapabilities client) {
        if (!client.supportsReadTextFile()) {
            ctx.sendMessage("This client did not advertise fs.readTextFile, so I will not ask it for NOTES.md.");
            return AcpSchema.PromptResponse.endTurn();
        }
        String notes = ctx.readFile("NOTES.md");
        long todos = notes.lines().filter(line -> line.startsWith("- [ ]")).count();
        ctx.sendMessage("NOTES.md has " + notes.lines().count() + " lines and " + todos + " open to-dos.");
        return AcpSchema.PromptResponse.endTurn();
    }
}
