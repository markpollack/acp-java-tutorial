/*
 * Module 34: Extension Methods - the extension contract
 *
 * The method names and the params/result types both sides agree on. Put these in a shared
 * library when the agent and the client are separate projects.
 */
package com.acptutorial.module34;

/**
 * The custom methods this tutorial's agent and client add to ACP.
 *
 * <p>ACP reserves every method name that does not start with an underscore for the
 * protocol. A custom method must start with {@code _} (the SDK throws
 * {@code IllegalArgumentException} otherwise, when you register a handler or send), so no
 * extension can ever replace or shadow a protocol method. Namespacing the rest of the name
 * with a domain you control ({@code _acptutorial/...}) keeps it from colliding with someone
 * else's extension.
 *
 * <p>The params and results are plain records: the SDK reads params as the type given by a
 * {@code TypeRef} (or as the raw JSON value, a {@code Map}, {@code List}, {@code String},
 * {@code Number} or {@code Boolean}) and writes results with the JSON mapper.
 */
public final class Extensions {

    // client -> agent
    public static final String WORD_COUNT = "_acptutorial/word_count";   // request, typed
    public static final String ECHO = "_acptutorial/echo";               // request, raw
    public static final String LOG = "_acptutorial/log";                 // notification, typed

    // agent -> client
    public static final String SELECTION = "_acptutorial/editor/selection"; // request, typed
    public static final String STATUS = "_acptutorial/status";              // notification, raw
    public static final String ACK = "_acptutorial/ack";                    // notification, raw

    /** A method nobody serves: requests to it are answered -32601 (method not found). */
    public static final String NOT_SERVED = "_acptutorial/not_served";

    public record WordCountParams(String text) {
    }

    public record WordCountResult(int words, int characters) {
    }

    public record LogEvent(String level, String message) {
    }

    public record SelectionQuery(String file) {
    }

    public record Selection(String file, int startLine, int endLine, String text) {
    }

    /** An insertion-ordered map, so the JSON (and this demo's output) keeps a stable field order. */
    public static java.util.Map<String, Object> ordered(Object... keysAndValues) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private Extensions() {
    }
}
