/*
 * AI Validation using Claude Agent SDK Java.
 * Validates tutorial output by asking Claude to analyze if it demonstrates expected behavior.
 */

import org.springaicommunity.claude.agent.sdk.Query;
import org.springaicommunity.claude.agent.sdk.QueryOptions;
import com.fasterxml.jackson.databind.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static java.lang.System.*;

public class AIValidator {

    // Haiku model for fast, cost-effective validation (~$0.001 per validation)
    private static final String VALIDATION_MODEL = "claude-haiku-4-5-20251001";
    private static final Duration VALIDATION_TIMEOUT = Duration.ofSeconds(60);

    // Validation result record
    public record ValidationResult(
        boolean success,
        double confidence,
        String reasoning,
        List<String> issues
    ) {}

    /**
     * Validate tutorial output using Claude.
     * Returns structured validation result with success/failure, confidence, and reasoning.
     */
    public static ValidationResult validate(String logOutput, String expectedBehavior, String moduleName) {
        String prompt = buildValidationPrompt(logOutput, expectedBehavior, moduleName);

        try {
            QueryOptions options = QueryOptions.builder()
                .model(VALIDATION_MODEL)
                .timeout(VALIDATION_TIMEOUT)
                .maxTurns(1)  // No tool use needed for validation
                .disallowedTools(List.of("Bash", "Write", "Edit", "Read", "Glob", "Grep"))
                .appendSystemPrompt("You are a tutorial validator. Analyze the output and respond ONLY with valid JSON.")
                .build();

            String response = Query.text(prompt, options);
            return parseResponse(response);

        } catch (Exception e) {
            err.println("⚠️  AI validation error: " + e.getMessage());
            // Return failure with error details
            return new ValidationResult(
                false,
                0.0,
                "AI validation failed: " + e.getMessage(),
                List.of("Exception during validation: " + e.getClass().getSimpleName())
            );
        }
    }

    private static String buildValidationPrompt(String logOutput, String expectedBehavior, String moduleName) {
        // Truncate very long outputs to avoid token limits
        String truncatedOutput = logOutput;
        if (logOutput.length() > 10000) {
            truncatedOutput = logOutput.substring(0, 5000) +
                "\n... [" + (logOutput.length() - 10000) + " chars truncated] ...\n" +
                logOutput.substring(logOutput.length() - 5000);
        }

        return """
            You are a tutorial validator for the ACP Java Tutorial (Agent Client Protocol).

            ## Module: %s

            ## Expected Behavior:
            %s

            ## Actual Output:
            ```
            %s
            ```

            ## Validation Task:
            Analyze if this tutorial output demonstrates the expected behavior.

            Look for:
            1. Module header present (=== Module NN: ===)
            2. Expected functionality demonstrated based on the expected behavior description
            3. For Gemini modules: responses received from the agent
            4. For local agent modules: agent started and responded correctly
            5. Stop reason displayed (END_TURN, etc.)
            6. No fatal errors, stack traces, or exceptions

            Be lenient - the exact wording doesn't matter, only that the functionality was demonstrated.
            If the output shows the expected behavior, mark it as success.

            Respond ONLY with valid JSON (no markdown, no explanation):
            {
              "success": true or false,
              "confidence": 0.0 to 1.0,
              "reasoning": "one sentence explanation",
              "issues": ["list", "of", "problems"] or []
            }
            """.formatted(moduleName, expectedBehavior, truncatedOutput);
    }

    private static ValidationResult parseResponse(String response) {
        try {
            // Extract JSON from response (Claude might include extra text)
            String jsonStr = extractJson(response);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> result = mapper.readValue(jsonStr, Map.class);

            boolean success = Boolean.TRUE.equals(result.get("success"));
            double confidence = result.get("confidence") instanceof Number n ? n.doubleValue() : 0.5;
            String reasoning = result.get("reasoning") != null ? result.get("reasoning").toString() : "No reasoning provided";

            @SuppressWarnings("unchecked")
            List<String> issues = result.get("issues") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of();

            return new ValidationResult(success, confidence, reasoning, issues);

        } catch (Exception e) {
            // If we can't parse the response, try to infer from text content
            boolean looksSuccessful = response.toLowerCase().contains("\"success\": true") ||
                                     response.toLowerCase().contains("\"success\":true");
            return new ValidationResult(
                looksSuccessful,
                0.5,
                "Could not parse structured response: " + e.getMessage(),
                List.of("Response parsing failed")
            );
        }
    }

    private static String extractJson(String response) {
        // Try to find JSON object in response
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");

        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        // If no braces found, return original (will fail parsing)
        return response;
    }
}
