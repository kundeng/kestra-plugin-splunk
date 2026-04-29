# Kestra Splunk Plugin

Native Kestra plugin for Splunk integration — search queries via REST API and event ingestion via HTTP Event Collector (HEC).

## Tasks

### `io.kestra.plugin.splunk.SplunkSearch`

Execute a Splunk search query. Creates a search job, polls until complete, and returns paginated results.

```yaml
- id: search
  type: io.kestra.plugin.splunk.SplunkSearch
  host: "michmed.splunkcloud.com"
  port: "8089"
  username: "{{ secret('SPLUNK_USER') }}"
  password: "{{ secret('SPLUNK_PASS') }}"
  query: '| savedsearch "My Saved Search"'
  insecureSkipVerify: true
```

**Outputs:** `uri` (results file in internal storage), `resultCount`, `sid`

### `io.kestra.plugin.splunk.SplunkHecSend`

Send events to Splunk HTTP Event Collector.

```yaml
- id: send
  type: io.kestra.plugin.splunk.SplunkHecSend
  host: "hec-host.example.com"
  port: "8088"
  token: "{{ secret('SPLUNK_HEC_TOKEN') }}"
  eventData: '{"key": "value"}'
  sourcetype: "myapp:events"
  index: "main"
```

**Outputs:** `statusCode`, `statusMessage`

## Performance

Tested against Splunk Cloud with production data:

| Query | Records | Duration |
|-------|---------|----------|
| NAC Deny saved search | 80,913 | ~15s |
| NAC Connections | 125,700 | ~30s |
| SNOW CI | 60,748 | ~20s |

Runs natively in Kestra's JVM — no Docker container overhead.

## Installation

### Docker volume mount
```yaml
volumes:
  - ./plugin-splunk.jar:/app/plugins/plugin-splunk.jar:ro
```

### Build from source
```bash
docker run --rm --network host \
  -v $(pwd):/project -w /project \
  gradle:8.12-jdk21 gradle shadowJar --no-daemon
# Output: build/libs/plugin-splunk-*.jar
```

## Development

```
src/main/java/io/kestra/plugin/splunk/
├── SplunkConnection.java    # Shared connection interface
├── SplunkSearch.java         # Search task
├── SplunkHecSend.java        # HEC send task
└── package-info.java         # Plugin metadata
```

### Requirements
- Java 21+
- Gradle 8.12+
- Kestra 0.21+ (compile-only dependency)

## License

Apache License 2.0
