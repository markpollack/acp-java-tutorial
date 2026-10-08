# Module 39: Forward Compatibility and _meta

A newer peer's session updates, content blocks and stop reasons are kept as `Unknown*`
records and open values instead of failing; dispatch with `instanceof` and a default branch,
compare open values with `equals`, and carry your own data in `_meta`. Runs in memory with
`acp-test`. No API key required.

## Documentation

Full tutorial: https://lab.pollack.ai/docs/acp-java-sdk/tutorial/39-forward-compatibility

## Running

```bash
./mvnw compile -pl module-39-forward-compatibility -q
./mvnw exec:java -pl module-39-forward-compatibility
```
