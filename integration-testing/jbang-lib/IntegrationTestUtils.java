/*
 * Centralized utilities for JBang integration tests.
 * Adapted from claude-agent-sdk-java-tutorial integration testing framework.
 *
 * Key features:
 * - Two-layer validation: deterministic requiredOutput check + AI behavioral validation
 * - Uses mvn exec:java to run modules
 * - Supports both Gemini CLI modules and local agent modules
 */

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.zeroturnaround.exec.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import static java.lang.System.*;

public class IntegrationTestUtils {

    // Record for test configuration
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExampleInfo(
        String moduleId,
        String displayName,
        int timeoutSec,
        String[] requiredEnv,
        String expectedBehavior,
        boolean requiresPackage,  // true for agent modules that need JAR built
        String[] requiredOutput   // exact substrings that MUST appear in stdout
    ) {
        // Provide default for requiresPackage
        public boolean requiresPackage() {
            return requiresPackage;
        }
        // Provide default for requiredOutput
        public String[] requiredOutput() {
            return requiredOutput != null ? requiredOutput : new String[0];
        }
    }

    // Load configuration from configs/<moduleId>.json
    public static ExampleInfo loadConfig(String moduleId) throws Exception {
        Path configPath = Path.of("configs", moduleId + ".json");
        if (!Files.exists(configPath)) {
            throw new RuntimeException("Config not found: " + configPath);
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(configPath.toFile(), ExampleInfo.class);
    }

    // Verify required environment variables
    public static void verifyEnvironment(ExampleInfo cfg) {
        if (cfg.requiredEnv() != null) {
            for (String envVar : cfg.requiredEnv()) {
                if (getenv(envVar) == null) {
                    err.println("❌ Missing required environment variable: " + envVar);
                    exit(1);
                }
            }
        }
    }

    // Find repository root (contains pom.xml with modules)
    public static Path findRepoRoot() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        // If we're in integration-testing, go up one level
        if (current.endsWith("integration-testing")) {
            return current.getParent();
        }
        // Otherwise traverse up looking for parent pom.xml
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.exists(pom)) {
                try {
                    String content = Files.readString(pom);
                    if (content.contains("<modules>")) {
                        return current;
                    }
                } catch (Exception e) {
                    // Continue searching
                }
            }
            current = current.getParent();
        }
        throw new RuntimeException("Could not find repository root with parent pom.xml");
    }

    // Build the module (compile or package depending on config)
    public static void buildModule(ExampleInfo cfg) throws Exception {
        Path repoRoot = findRepoRoot();
        String goal = cfg.requiresPackage() ? "package" : "compile";
        out.println("🏗️  Building " + cfg.moduleId() + " (" + goal + ")...");

        ProcessResult result = new ProcessExecutor()
            .command("./mvnw", goal, "-DskipTests", "-pl", cfg.moduleId(), "-q")
            .directory(repoRoot.toFile())
            .timeout(300, TimeUnit.SECONDS)
            .redirectOutput(System.out)
            .redirectError(System.err)
            .execute();
        if (result.getExitValue() != 0) {
            throw new RuntimeException("Build failed for " + cfg.moduleId());
        }
    }

    // Create log file path
    public static Path createLogFile(String moduleId) throws Exception {
        Path logDir = Path.of("logs");
        Files.createDirectories(logDir);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        return logDir.resolve(moduleId + "-" + timestamp + ".log");
    }

    // Run the module using mvn exec:java
    public static ProcessResult runModule(ExampleInfo cfg, Path logFile) throws Exception {
        Path repoRoot = findRepoRoot();
        return new ProcessExecutor()
            .command("./mvnw", "exec:java", "-pl", cfg.moduleId(), "-q")
            .directory(repoRoot.toFile())
            .timeout(cfg.timeoutSec(), TimeUnit.SECONDS)
            .redirectOutput(Files.newOutputStream(logFile))
            .redirectErrorStream(true)
            .execute();
    }

    // Display output preview
    public static void displayOutputPreview(String output) {
        out.println("📋 Output Preview (first 50 lines):");
        out.println("---");
        String[] lines = output.split("\n");
        for (int i = 0; i < Math.min(50, lines.length); i++) {
            out.println(lines[i]);
        }
        if (lines.length > 50) {
            out.println("... (" + (lines.length - 50) + " more lines)");
        }
        out.println("---");
    }

    // Check that all required output substrings appear in the actual output
    public static List<String> checkRequiredOutput(String output, String[] requiredOutput) {
        List<String> missing = new java.util.ArrayList<>();
        for (String required : requiredOutput) {
            if (!output.contains(required)) {
                missing.add(required);
            }
        }
        return missing;
    }

    // Main test execution flow
    public static void runIntegrationTest(String moduleId) throws Exception {
        out.println("🧪 Integration Test: " + moduleId);
        out.println("═".repeat(60));

        // Load configuration
        ExampleInfo cfg = loadConfig(moduleId);
        out.println("📝 " + cfg.displayName());
        out.println("⏱️  Timeout: " + cfg.timeoutSec() + "s");

        // Verify environment
        out.println("\n🔍 Verifying environment...");
        verifyEnvironment(cfg);
        out.println("✅ Environment OK");

        // Build module
        out.println("\n🏗️  Building module...");
        buildModule(cfg);
        out.println("✅ Build complete");

        // Create log file
        Path logFile = createLogFile(moduleId);

        // Run module
        out.println("\n🚀 Running " + moduleId + "...");
        ProcessResult result = runModule(cfg, logFile);
        int exitCode = result.getExitValue();

        // Read output
        String output = Files.readString(logFile);

        // Display output preview
        out.println("\n📋 Module Output:");
        displayOutputPreview(output);
        out.println("📁 Full log: " + logFile.toAbsolutePath());

        // Check exit code
        if (exitCode != 0) {
            err.println("\n❌ Module exited with code: " + exitCode);
            exit(exitCode);
        }

        // Deterministic output check (hard gate before AI validation)
        List<String> missingOutput = checkRequiredOutput(output, cfg.requiredOutput());
        if (!missingOutput.isEmpty()) {
            err.println("\n❌ FAILED: Required output missing from " + cfg.displayName());
            err.println("  The following required strings were NOT found in the output:");
            for (String missing : missingOutput) {
                err.println("    ✗ \"" + missing + "\"");
            }
            err.println("\n  This is a deterministic check — these strings must appear in stdout.");
            err.println("  Fix the module code to produce the expected output, or update");
            err.println("  requiredOutput in configs/" + cfg.moduleId() + ".json if the output changed.");
            exit(1);
        }
        if (cfg.requiredOutput().length > 0) {
            out.println("\n✅ All " + cfg.requiredOutput().length + " required output strings found");
        }

        // AI Validation
        out.println("\n🤖 Running AI validation...");
        AIValidator.ValidationResult validation = AIValidator.validate(
            output,
            cfg.expectedBehavior(),
            cfg.displayName()
        );

        // Display validation results
        out.println("  Success: " + validation.success());
        out.println("  Confidence: " + String.format("%.2f", validation.confidence()));
        out.println("  Reasoning: " + validation.reasoning());

        if (validation.issues() != null && !validation.issues().isEmpty()) {
            out.println("  Issues:");
            for (String issue : validation.issues()) {
                out.println("    - " + issue);
            }
        }

        // Final result
        out.println("\n" + "═".repeat(60));
        if (validation.success()) {
            out.println("🎉 PASSED: " + cfg.displayName());
        } else {
            err.println("❌ FAILED: " + cfg.displayName());
            exit(1);
        }
    }
}
