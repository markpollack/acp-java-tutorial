# Module 35: Cancellation and Timeouts

The two ways to cancel in ACP (`session/cancel` for a prompt turn, `$/cancel_request` for
any request, graceful with `RequestCancellation.cancelWhen`) and the Java SDK's prompt
timeouts, `cancelGracePeriod` and `maxPromptDuration`, which are SDK policy rather than
protocol. Uses short timeouts so it runs in a few seconds. No API key required.

## Documentation

Full tutorial: https://lab.pollack.ai/docs/acp-java-sdk/tutorial/35-cancellation-timeouts

## Running

```bash
./mvnw package -pl module-35-cancellation-timeouts -q
./mvnw exec:java -pl module-35-cancellation-timeouts
```
