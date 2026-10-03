# Module 33: Session Config Options

Expose per-session settings (a model picker, a mode, a capability-gated boolean) as
session config options, change them with `session/set_config_option`, and push agent-side
changes with `config_option_update`. One agent, written twice: builder API and annotations.
No API key required.

## Documentation

Full tutorial: https://lab.pollack.ai/docs/acp-java-sdk/tutorial/33-session-config-options

## Running

```bash
./mvnw -Psdk-candidate package -pl module-33-session-config-options -q
./mvnw -Psdk-candidate exec:java -pl module-33-session-config-options
```
