# Module 34: Extension Methods

Add your own `_`-prefixed requests and notifications to ACP, in both directions, typed and
raw: builder handlers (`extRequestHandler`, `extNotificationHandler`), annotations
(`@ExtRequest`, `@ExtNotification`) and the senders (`sendExtRequest`, `sendExtNotification`).
No API key required.

## Documentation

Full tutorial: https://lab.pollack.ai/docs/acp-java-sdk/tutorial/34-extension-methods

## Running

```bash
./mvnw package -pl module-34-extension-methods -q
./mvnw exec:java -pl module-34-extension-methods
```
