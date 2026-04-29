# SplunkHecSend

Send events to Splunk HTTP Event Collector (HEC).

## Usage

```yaml
- id: send
  type: io.kestra.plugin.splunk.SplunkHecSend
  host: "splunk-test"
  port: "8088"
  token: "{{ secret('SC_HEC') }}"
  eventData: '{"key": "value"}'
  sourcetype: "myapp:events"
  index: "main"
  insecureSkipVerify: true
```

## Properties

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| host | String | Yes | — | HEC host |
| port | String | No | 443 | HEC port |
| scheme | String | No | https | http or https |
| token | String | Yes | — | HEC token |
| eventData | String | No | — | Raw event data string |
| inputFile | String | No | — | Internal storage URI (alternative to eventData) |
| index | String | No | — | Splunk index |
| sourcetype | String | No | — | Splunk sourcetype |
| source | String | No | — | Splunk source |
| hostField | String | No | — | Splunk host metadata |
| insecureSkipVerify | Boolean | No | true | Skip SSL verification |

## Outputs

| Output | Type | Description |
|--------|------|-------------|
| statusCode | Integer | HTTP response code |
| statusMessage | String | HTTP response message |

## Limitations

- Maximum payload ~5MB per request (Splunk HEC limit)
- For larger payloads, use the chunked Python approach in `infoblox-to-splunk` subflow
