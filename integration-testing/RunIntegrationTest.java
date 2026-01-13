///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.zeroturnaround:zt-exec:1.12
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.1
//DEPS org.springaicommunity:claude-code-sdk:1.0.0-SNAPSHOT
//REPOS mavenlocal,mavencentral,central-snapshots=https://central.sonatype.com/repository/maven-snapshots/
//JAVA 21
//SOURCES jbang-lib/IntegrationTestUtils.java
//SOURCES jbang-lib/AIValidator.java

/**
 * Single entry point for running ACP Java Tutorial integration tests.
 *
 * Usage:
 *   cd integration-testing
 *   jbang RunIntegrationTest.java module-01-first-contact
 *
 * Or run all tests:
 *   ./scripts/run-integration-tests.sh
 */
public class RunIntegrationTest {

    public static void main(String... args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String moduleId = args[0];

        if ("--help".equals(moduleId) || "-h".equals(moduleId)) {
            printUsage();
            System.exit(0);
        }

        if ("--list".equals(moduleId)) {
            listAvailableConfigs();
            System.exit(0);
        }

        IntegrationTestUtils.runIntegrationTest(moduleId);
    }

    private static void printUsage() {
        System.out.println("""
            Integration Test Runner for ACP Java Tutorial

            Usage: jbang RunIntegrationTest.java <module-id>

            Examples:
              jbang RunIntegrationTest.java module-01-first-contact
              jbang RunIntegrationTest.java module-12-echo-agent

            Options:
              --list    List all available module configs
              --help    Show this help message

            To run all tests:
              ./scripts/run-integration-tests.sh

            Module Categories:
              Gemini Client (01-08): Require GEMINI_API_KEY
              Local Agent (12-15):  Run agent as subprocess
              In-Memory (16):       No external processes
            """);
    }

    private static void listAvailableConfigs() {
        java.nio.file.Path configDir = java.nio.file.Path.of("configs");
        if (!java.nio.file.Files.exists(configDir)) {
            System.err.println("No configs directory found. Are you in the integration-testing directory?");
            System.exit(1);
        }

        System.out.println("Available module configurations:");
        try (var stream = java.nio.file.Files.list(configDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .sorted()
                  .forEach(p -> {
                      String name = p.getFileName().toString();
                      String moduleId = name.substring(0, name.length() - 5); // Remove .json
                      System.out.println("  " + moduleId);
                  });
        } catch (Exception e) {
            System.err.println("Error listing configs: " + e.getMessage());
        }
    }
}
