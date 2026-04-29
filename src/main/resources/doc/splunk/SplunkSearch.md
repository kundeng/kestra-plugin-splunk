# SplunkSearch

Execute a Splunk search query via the REST API.

## Usage

```yaml
- id: search
  type: io.kestra.plugin.splunk.SplunkSearch
  host: "michmed.splunkcloud.com"
  port: "8089"
  username: "{{ secret('SC_USER') }}"
  password: "{{ secret('SC_PASSWORD') }}"
  query: '| loadjob savedsearch="my:saved:search"'
  insecureSkipVerify: true
```

## How it works

1. Creates a search job via `POST /services/search/jobs`
2. Polls job status until `isDone: true`
3. Fetches results with pagination (default batch: 10,000)
4. Stores results as JSON in Kestra internal storage

## Properties

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| host | String | Yes | — | Splunk host |
| port | String | No | 8089 | REST API port |
| scheme | String | No | https | http or https |
| username | String | No | — | Basic auth user |
| password | String | No | — | Basic auth password |
| token | String | No | — | Bearer token (alternative to user/pass) |
| query | String | Yes | — | SPL search query |
| outputMode | String | No | json | json or csv |
| batchSize | Integer | No | 10000 | Results per fetch |
| pollIntervalSeconds | Integer | No | 2 | Seconds between status checks |
| maxWaitSeconds | Integer | No | 3600 | Max wait for job completion |
| insecureSkipVerify | Boolean | No | true | Skip SSL verification |

## Outputs

| Output | Type | Description |
|--------|------|-------------|
| uri | URI | Internal storage URI for results file |
| resultCount | Integer | Number of results |
| sid | String | Splunk search job SID |

## Performance

Tested with production data:
- 80,913 records: ~15 seconds (query + poll + fetch)
- 125,700 records: ~30 seconds
- Runs in Kestra JVM — no container overhead
