# ELK Analytics for Data Services

## Why this package exists

Synapse's statistics framework emits analytics events for APIs, Proxy Services,
Sequences, Endpoints, and Inbound Endpoints; the `ElasticStatisticsPublisher`
consumes those events and writes a JSON line to `synapse-analytics.log`, which
Filebeat ships to Elasticsearch and Kibana renders.

Data Services bypass Synapse entirely — they run on the Axis2 layer via
`DBInOutMessageReceiver` / `DBInOnlyMessageReceiver` — so the existing publisher
never sees DS traffic. This package adds the missing emit path without
duplicating the appender or the Filebeat pipeline.

## Design constraint: the Maven cycle

The obvious approach — make this package depend on
`org.wso2.micro.integrator.analytics.messageflow.data.publisher` and call
`ElasticStatisticsPublisher` directly — creates a Maven cycle:

```
analytics.messageflow.data.publisher
  -> initializer
    -> dataservices.core
      -> analytics.messageflow.data.publisher   ← cycle
```

To avoid the cycle, `DataServicesAnalyticsPublisher` acquires a Log4j logger
**named after** `ElasticStatisticsPublisher` (a plain string, no compile-time
reference) and logs through it. The MI `log4j2.properties` already routes that
logger name to the `ELK_ANALYTICS_APPENDER`, so DS events land in the same log
file (synapse-analytics.log) as API/Proxy events with no additional configuration.

## Class responsibilities

| Class | Responsibility |
|---|---|
| `DataServicesAnalyticsCollector` | Public API called from the four DS entry points (`DBInOutMessageReceiver`, `DBInOnlyMessageReceiver`, `ODataPassThroughHandler`, `DataServiceCallMediator`). Reads message-context state, builds a `DataServiceAnalyticsEvent`, enforces idempotency (one event per entry), detects fault payloads, and delegates publishing to the publisher. |
| `DataServicesAnalyticsPublisher` | Serializes an event into the canonical envelope JSON (`serverInfo`, `timestamp`, `schemaVersion`, `payload`) and writes it through the bridge logger. Never throws into the caller's flow. |
| `DataServiceAnalyticsEvent` | Plain data holder for one event. Internal representation; not serialized field-for-field. |
| `DataServicesAnalyticsConstants` | Every property key, JSON field name, sub-type sentinel, and default value used by the package, in one place. |
| `AnalyticsConfig` | Reads `analytics.enabled`, `analytics.data_service_analytics.enabled`, and `analytics.prefix` from `${carbon.home}/conf/synapse.properties` — the same source the Synapse-side publisher reads. JVM system properties override the file. Cached for JVM lifetime — restart MI to pick up changes. |
| `ServerInfoProvider` | Computes the `serverInfo` block (hostname, ipAddress, serverName, id) without taking a dependency on `ServerConfigurationInformation`. Cached for JVM lifetime. |

## Sub-types

Every event carries one of three sub-types under
`payload.dataServiceDetails.subType`:

- **`DATA_SERVICE`** — direct SOAP/REST invocation reaching
  `DBInOutMessageReceiver` or `DBInOnlyMessageReceiver`.
- **`ODATA`** — invocation via `ODataPassThroughHandler` (URL pattern `/odata/...`).
- **`DSS_CALL_MEDIATOR`** — invocation via the `<dataservicecall>` Synapse
  mediator (typically from inside an API or sequence).

## How to enable

Two toggles, both in `deployment.toml`'s `[analytics]` section (which renders to
`synapse.properties`):

- **`enabled = true`** — global master switch. Same toggle that controls
  API/Proxy/Sequence/Endpoint/Inbound analytics; turning it off silences every
  entity type at once.
- **`data_service_analytics.enabled = true`** — per-entity-type opt-out,
  mirroring `api_analytics.enabled` / `proxy_service_analytics.enabled` /
  etc. Defaults to `true`. Set to `false` to silence DS events while keeping
  the other entity types' analytics flowing.

For local testing without editing `deployment.toml`, pass
`-Danalytics.enabled=true` on the MI startup command line.

### Effective behavior

| `analytics.enabled` | `data_service_analytics.enabled` | DS events emitted? |
|---|---|---|
| `false` | (any) | No — master switch off |
| `true` | `true` (or absent) | Yes |
| `true` | `false` | No — DS specifically silenced |

## How to extend

Adding a new DS entry point (e.g. an inbound endpoint that pulls from a queue
and dispatches to a DS) follows the existing pattern:

1. Call `DataServicesAnalyticsCollector.reportEntryEvent(msgCtx)` at the start
   of the dispatch.
2. Call `closeEntryEventWithSubType(msgCtx, result, "NEW_SUBTYPE")` on the
   success path, or `closeFlowForcefullyWithSubType(msgCtx, e, "NEW_SUBTYPE")`
   on the failure path.
3. Add the new sub-type constant to `DataServicesAnalyticsConstants`.
4. If the message context doesn't expose the right service or operation name
   (e.g. the entry point runs under Synapse where `axisOperation` is always
   `"mediate"`), stamp explicit overrides before `reportEntryEvent`:
   ```java
   msgCtx.setProperty(DS_ANALYTICS_SERVICE_NAME_OVERRIDE, "MyService");
   msgCtx.setProperty(DS_ANALYTICS_OPERATION_OVERRIDE,    "myOperation");
   ```

## Emitted JSON shape

Matches what `ElasticDataSchema` emits for API events, so existing Kibana
dashboards can consume DS events with only field renames:

```json
{
  "serverInfo": {
    "hostname": "...",
    "serverName": "...",
    "ipAddress": "...",
    "id": "..."
  },
  "timestamp": "2026-05-19T04:29:33.905Z",
  "schemaVersion": 1,
  "payload": {
    "metadata": {
      "errorCode": "...",
      "errorMessage": "...",
      "faultDetail": "..."
    },
    "entityType": "DataService",
    "entityClassName": "org.wso2.micro.integrator.dataservices.core.DataService",
    "failure": false,
    "faultResponse": false,
    "latency": 42,
    "messageId": "urn:uuid:...",
    "correlation_id": "...",
    "dataServiceDetails": {
      "name": "EmployeeDataService",
      "operation": "_getemployee_employeenumber",
      "subType": "DATA_SERVICE"
    },
    "httpMethod": "GET",
    "httpUrl": "/services/...",
    "remoteHost": "10.0.0.5"
  }
}
```

The `metadata` bag is always present but empty on success. On failure it
carries `errorCode`, `errorMessage`, and (when the source fault element
included one) `faultDetail`.

## Known limitations

- **Transport-layer faults are not captured.** JSON parse failures, type
  coercion in Axis2 handlers, "EPR not found", and similar errors happen
  before the receiver runs. These appear in `wso2carbon.log` only.
- **Boxcarring / request-box / batch invocations report one event per outer
  HTTP request**, not per inner sub-operation.
- **Config changes require an MI restart.** Properties are cached for the JVM
  lifetime to match the behavior of the Synapse-side publisher.
