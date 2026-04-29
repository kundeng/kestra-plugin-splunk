# Contributing to kestra-plugin-splunk

## Development Setup

1. Clone the repo
2. Build: `./gradlew shadowJar`
3. Test in Kestra: mount the JAR at `/app/plugins/`

## Adding a New Task

1. Create a class extending `Task` implementing `RunnableTask<YourOutput>`
2. Add `@Plugin`, `@Schema`, `@SuperBuilder`, `@Getter`, `@NoArgsConstructor` annotations
3. Implement `run(RunContext runContext)` method
4. Create an `Output` inner class implementing `io.kestra.core.models.tasks.Output`
5. Register in `META-INF/services/io.kestra.core.models.Plugin`
6. Add documentation in `src/main/resources/doc/splunk/`

## Property Guidelines

- Use `@PluginProperty(dynamic = true)` for String properties that accept Pebble templates
- Do NOT use `dynamic = true` on Integer/Boolean — Kestra validates at import time
- Always call `runContext.render(property)` before using dynamic properties
- Check for null/empty after rendering (Pebble returns "" for undefined variables)

## Testing

Build and mount:
```bash
./gradlew shadowJar
# Copy build/libs/plugin-splunk-*.jar to Kestra's /app/plugins/
```

## Code Style

- Lombok for boilerplate (@Getter, @SuperBuilder, @NoArgsConstructor)
- OkHttp for HTTP calls
- Jackson for JSON
- SLF4J via `runContext.logger()` for logging
