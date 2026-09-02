# ADR 0004: Keep evidence longer than raw operational data

## Status

Accepted as a provisional policy pending legal and partner review

## Decision

Retention is based on purpose, not one blanket period.

- Raw telemetry is downsampled after 7 days and deleted after 90 days. Those
  values are already application settings.
- Security and request logs should be kept for 30 days and must never contain
  bodies, tokens, identity values, document contents, or precise coordinates.
- Expired invitation attempts and other short-lived security state should be
  removed after 30 days.
- Confirmed orders, source documents, handovers, risk indicators, and evidence
  records have a provisional seven-year retention period because they may be
  needed for disputes, insurers, or financial records.
- A legal hold pauses deletion for the affected transaction. A verified privacy
  request removes data only where another obligation does not require it.

## Why

Raw GPS points are expensive and privacy-sensitive but lose operational value
quickly. The much smaller evidence trail remains useful for disputes and partner
reviews. Keeping both forever would increase cost and breach impact.

## Consequences

Only telemetry cleanup is automated in the current application. The remaining
deletion jobs, legal-hold workflow, and final South African retention periods
must be approved before a production launch.
