# Module 36: Terminal Auth and Logout

An agent that requires sign-in and offers two auth methods, `AuthMethodAgent` (handled in
`@Authenticate`) and `AuthMethodTerminal` (an interactive login the client runs as a
separate process), plus the logout capability and `@Logout`. No API key required.

## Documentation

Full tutorial: https://lab.pollack.ai/docs/acp-java-sdk/tutorial/36-terminal-auth-logout

## Running

```bash
./mvnw -Psdk-candidate package -pl module-36-terminal-auth-logout -q
./mvnw -Psdk-candidate exec:java -pl module-36-terminal-auth-logout
```
