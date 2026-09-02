# Backend operations

TradeMesh emits JSON logs to standard output. Each HTTP response returns an
`X-Request-ID`, and the same value appears as `request_id` in logs and as the
correlation ID on events created during that request.

The request completion log deliberately includes only the HTTP method, status,
duration, and request ID. Do not add URLs, query strings, headers, bodies,
emails, identity values, document fields, invitation tokens, or coordinates.

The unauthenticated health endpoints are:

- `/actuator/health/liveness` — whether the process should be restarted.
- `/actuator/health/readiness` — disk and PostGIS/database readiness.

`/actuator/metrics` is available only to an administrator. Useful starting
meters are `http.server.requests`, `trademesh.http.request.duration`, and
`trademesh.http.rate_limit.rejections`.

This directory intentionally contains no monitoring platform. A deployment can
later collect standard output and scrape metrics using the platform it already
operates.
