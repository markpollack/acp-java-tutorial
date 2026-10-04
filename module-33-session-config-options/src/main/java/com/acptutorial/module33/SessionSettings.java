/*
 * Module 33: Session Config Options - the agent's settings
 *
 * Shared by ConfigAgent (builder API) and AnnotatedConfigAgent (annotations), so the two
 * agents differ only in how they are wired, not in what they offer.
 */
package com.acptutorial.module33;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.agentclientprotocol.sdk.error.AcpProtocolException;
import com.agentclientprotocol.sdk.spec.AcpSchema;

/**
 * The settings one agent keeps for each ACP session, and the config options that describe
 * them.
 *
 * <p>Three options are offered:
 * <ul>
 *   <li>{@code model}: a select with category {@code "model"}, built with
 *   {@link AcpSchema.SessionConfigSelect#model}. Clients recognise the category and show a
 *   model picker. The choices are grouped ("Hosted", "Local").</li>
 *   <li>{@code mode}: a select with category {@code "mode"}, built with
 *   {@link AcpSchema.SessionConfigSelect#builder()}. The same values are also offered as
 *   legacy session modes ({@link #modes}): during the transition the spec asks agents with
 *   mode-like settings to send both, and clients that understand config options to use them
 *   and ignore {@code modes}. Both write the same state here.</li>
 *   <li>{@code verbose}: a {@link AcpSchema.SessionConfigBoolean}, offered <em>only</em> to a
 *   client that advertised {@code session.configOptions.boolean}. The SDK does not filter
 *   boolean options for you; the agent decides, from the client's capabilities, at
 *   {@code session/new}.</li>
 * </ul>
 *
 * <p>Every method returns the <em>full</em> option list: both the
 * {@code session/set_config_option} response and a {@code config_option_update} carry the
 * complete current state, never a delta.
 *
 * <p>The SDK validates nothing about a {@code set_config_option} request: the id, the value
 * and its type reach the handler as sent ({@code value()} is an {@code Object}: a
 * {@code String} for a select, a {@code Boolean} for a boolean). The spec defines no error
 * for a bad request, so this agent answers {@code -32602} (invalid params), which the client
 * receives as an {@code AcpError}.
 *
 * <p>State is in a concurrent map keyed by session id: the annotated agent's bean is shared
 * by every connection a factory serves, so it must be thread-safe.
 */
final class SessionSettings {

    static final String MODEL = "model";
    static final String MODE = "mode";
    static final String VERBOSE = "verbose";

    /** A preview model that is "rate limited": prompting with it makes the agent fall back. */
    static final String PREVIEW_MODEL = "orbit-2-preview";
    static final String DEFAULT_MODEL = "orbit-1-mini";
    static final String FALLBACK_MODEL = "orbit-1";

    private static final List<String> MODELS = List.of("orbit-1-mini", "orbit-1", PREVIEW_MODEL, "local-7b");
    private static final List<String> MODES = List.of("ask", "code");

    /** Per-session state. Whether the client takes boolean options is decided once, at session/new. */
    static final class State {
        volatile String model = DEFAULT_MODEL;
        volatile String mode = "ask";
        volatile boolean verbose = false;
        final boolean booleansSupported;

        State(boolean booleansSupported) {
            this.booleansSupported = booleansSupported;
        }
    }

    private final Map<String, State> sessions = new ConcurrentHashMap<>();

    /** Creates the state for a new session and returns the options to advertise. */
    List<AcpSchema.SessionConfigOption> open(String sessionId, boolean clientSupportsBooleans) {
        State state = new State(clientSupportsBooleans);
        sessions.put(sessionId, state);
        return options(state);
    }

    /** The legacy session modes, kept in step with the {@code mode} config option. */
    AcpSchema.SessionModeState modes(String sessionId) {
        return new AcpSchema.SessionModeState(state(sessionId).mode, List.of(
                new AcpSchema.SessionMode("ask", "Ask", "Answer questions, change nothing"),
                new AcpSchema.SessionMode("code", "Code", "Edit files")));
    }

    State state(String sessionId) {
        State state = sessions.get(sessionId);
        if (state == null) {
            throw new AcpProtocolException(AcpErrorCodes.RESOURCE_NOT_FOUND,
                    "Unknown session: " + sessionId, null);
        }
        return state;
    }

    /** Applies a client's change and returns the full list. Invalid input is answered -32602. */
    List<AcpSchema.SessionConfigOption> apply(AcpSchema.SetSessionConfigOptionRequest req) {
        return apply(req.sessionId(), req.configId(), req.value());
    }

    List<AcpSchema.SessionConfigOption> apply(String sessionId, String configId, Object value) {
        State state = state(sessionId);
        switch (configId) {
            case MODEL -> {
                if (!(value instanceof String model) || !MODELS.contains(model)) {
                    throw invalid("Unknown model '" + value + "'; expected one of " + MODELS);
                }
                state.model = model;
            }
            case MODE -> setMode(state, value);
            case VERBOSE -> {
                if (!state.booleansSupported) {
                    // We never offered it to this client, so for this session it does not exist.
                    throw invalid("Unknown config option '" + VERBOSE + "'");
                }
                if (!(value instanceof Boolean on)) {
                    throw invalid("Option 'verbose' takes a boolean, got '" + value + "'");
                }
                state.verbose = on;
            }
            default -> throw invalid("Unknown config option '" + configId + "'");
        }
        return options(state);
    }

    /** A legacy session/set_mode: writes the same state as the {@code mode} config option. */
    void applyMode(AcpSchema.SetSessionModeRequest req) {
        setMode(state(req.sessionId()), req.modeId());
    }

    private static void setMode(State state, Object value) {
        if (!(value instanceof String mode) || !MODES.contains(mode)) {
            throw invalid("Unknown mode '" + value + "'; expected one of " + MODES);
        }
        state.mode = mode;
    }

    /**
     * The agent changing a setting itself: the preview model is "rate limited", so fall back.
     * Returns the full list when something changed, or null when nothing did.
     */
    List<AcpSchema.SessionConfigOption> fallBackIfRateLimited(String sessionId) {
        State state = state(sessionId);
        if (!PREVIEW_MODEL.equals(state.model)) {
            return null;
        }
        state.model = FALLBACK_MODEL;
        return options(state);
    }

    /** The answer text for one turn, showing which settings are in effect. */
    String answer(String sessionId, String agentName) {
        State state = state(sessionId);
        String answer = "[" + state.model + ", mode=" + state.mode + "] Hello from the " + agentName + ".";
        if (state.verbose) {
            answer += " (verbose: I read your prompt, picked a model, and wrote this line.)";
        }
        return answer;
    }

    List<AcpSchema.SessionConfigOption> options(State state) {
        // A grouped select with category "model": clients recognise it as the model picker.
        var model = AcpSchema.SessionConfigSelect.model(MODEL, "Model", state.model,
                new AcpSchema.GroupedSelectOptions(List.of(
                        new AcpSchema.SessionConfigSelectGroup("hosted", "Hosted", List.of(
                                new AcpSchema.SessionConfigSelectOption("orbit-1-mini", "Orbit 1 Mini"),
                                new AcpSchema.SessionConfigSelectOption("orbit-1", "Orbit 1"),
                                new AcpSchema.SessionConfigSelectOption(PREVIEW_MODEL, "Orbit 2 (preview)"))),
                        new AcpSchema.SessionConfigSelectGroup("local", "Local", List.of(
                                new AcpSchema.SessionConfigSelectOption("local-7b", "Local 7B"))))));
        // Any other categorized select: the builder. Category "mode" marks the successor of session modes.
        var mode = AcpSchema.SessionConfigSelect.builder()
                .id(MODE)
                .name("Mode")
                .category(AcpSchema.SessionConfigOptionCategory.MODE)
                .currentValue(state.mode)
                .options(List.of(
                        new AcpSchema.SessionConfigSelectOption("ask", "Ask"),
                        new AcpSchema.SessionConfigSelectOption("code", "Code")))
                .build();
        List<AcpSchema.SessionConfigOption> all = new ArrayList<>(List.of(model, mode));
        if (state.booleansSupported) {
            // Boolean options go only to clients that advertised session.configOptions.boolean.
            all.add(AcpSchema.SessionConfigBoolean.builder()
                    .id(VERBOSE)
                    .name("Verbose answers")
                    .description("Explain each step in the answer")
                    .currentValue(state.verbose)
                    .build());
        }
        return all;
    }

    void close(String sessionId) {
        sessions.remove(sessionId);
    }

    private static AcpProtocolException invalid(String message) {
        return new AcpProtocolException(AcpErrorCodes.INVALID_PARAMS, message, null);
    }
}
