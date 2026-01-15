# Cyclop Optimization Plan

This document is the living plan to harden and optimize the system. It is organized in stages so each step is shippable and traceable.

## Stage 1 — Stabilize Runtime (Reactive Correctness & Dependencies)
- Make reactive paths non-blocking:
  - Remove `.block()` in reactive flows (Mongo saves, WebClient calls, request logging). If a call must block (e.g., Apache HTTP), wrap with `publishOn(boundedElastic)` and clear boundaries.
  - Keep Reactor chains reactive end-to-end (backpressure-aware).
- Align dependency baselines:
  - Pick one stack (e.g., Spring Boot 3.2.x + Spring Cloud 2023.x + Java 21).
  - Drop old BOMs/versions (Boot 2.2.x, Hoxton) and ensure compiler plugins match Java 21.
- Consumer identity:
  - Use stable Kafka consumer group ids unless explicit replay is desired. Set offset/reset policy per intent (e.g., `latest` with stable group for steady processing).
- Timeouts everywhere:
  - WebClient/HTTP timeouts, Kafka producer delivery timeout, and reactor operators with sensible defaults to avoid hangs.

## Stage 2 — Backpressure, Retries, and Resource Tuning
- Kafka producer:
  - Tune `maxInFlight` (start 64–256), delivery timeout, batch/linger to meet latency budget.
  - Monitor send latency and error rates; adjust partitions vs throughput needs.
- Kafka consumer/streams:
  - Match consumer concurrency to partitions; set `max.poll.records` and `max.poll.interval.ms` to keep poll loop healthy.
- Websocket resilience:
  - Replace bare `.retry()` with bounded backoff + jitter; add caps or circuit breakers for sustained failures.
- Logging volume:
  - Reduce hot-path INFO to DEBUG or sample logs; avoid full payload logging per message in production.

## Stage 3 — State & Persistence Efficiency
- Market state:
  - Replace coarse `synchronized` with per-key atomic updates (`compute` on `ConcurrentHashMap`) to cut contention in hot paths.
- Strategy access:
  - Cache active strategies by symbol/interval with periodic refresh; avoid per-candle Mongo fetches when configs are static.
- Persistence:
  - Batch/coalesce Mongo writes for request/error logs where possible.
  - Ensure indexes on query fields (e.g., `symbolString`, `candleStick`, `status`, `botStatus`).

## Stage 4 — Observability & Safety Rails
- Metrics:
  - Publish lag (producer and consumer), in-flight counts, websocket reconnects, HTTP latency/error rates, Mongo latency, order success/fail, TP/SL adjustments.
- Health:
  - Readiness on Kafka connectivity and websocket status; liveness on event loop health.
- Guardrails:
  - Max daily loss, per-symbol exposure caps, circuit breaker on error spikes, heartbeat/last-published watchdog to auto-stop or alert.

## Stage 5 — Testing & Validation
- Integration:
  - Publisher → embedded Kafka → consumer flow tests with sample klines; assert expected orders/events.
- Adapter correctness:
  - Simulated exchange responses for submit/sync/cancel/TP-reduce; property tests for TP/SL math.
- Load/soak:
  - Websocket ingest + Kafka produce at target TPS; consumer under many strategies; track CPU/GC/lag.

## Stage 6 — Operational Hygiene
- Configuration:
  - Externalize secrets; use profiles for local/stage/prod; validate required env vars at startup.
- Containers/Orchestration:
  - Set resource limits/requests, liveness/readiness probes, pin image versions; align Kafka partitions with consumer parallelism.
- CI/CD:
  - Build/test/lint gates; dependency vulnerability scans; publish SBOM; enforce code style.

## Stage 7 — Performance Tuning Loop
- Measure under load (JFR/async-profiler) to find real hotspots (GC, logging, HTTP, serialization).
- Tune one variable at a time (producer in-flight, batch size, retry backoff) and re-measure lag/throughput/latency.
- Keep a changelog of tuning adjustments and their measured impact.

## Working Agreements
- Keep stages incremental and shippable; no big-bang rewrites.
- Add observability alongside changes to validate improvements.
- Favor configuration-first tweaks before deep refactors; when refactoring, add tests to lock behavior.
