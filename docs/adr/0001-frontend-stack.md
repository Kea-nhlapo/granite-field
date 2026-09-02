# ADR 0001: Frontend foundation

## Status

Accepted

## Decision

The frontend is a React single-page application built with Vite and TypeScript.
Node 24 LTS and npm 11 are the pinned runtime and package-manager lines.
Vitest and Testing Library cover automated tests. Oxlint performs static linting,
and Prettier owns formatting.

## Why

The frontend needs interactive maps, live shipment updates, multi-step forms,
and role-specific dashboards. It does not currently need server rendering or a
second server framework. Vite keeps the application small and gives contributors
a fast local workflow while the Spring backend remains independently deployable.

## Consequences

- Frontend code stays inside apps/frontend.
- Backend code stays inside apps/backend.
- Shared HTTP schemas live in packages/api-contracts.
- The product UI lives only in apps/frontend. There is no separate marketing
  landing or login SPA.
- Routing, data fetching, and design components will be added only when their
  feature tickets require them.
- If server rendering becomes a real requirement, the team will write a new ADR
  before changing frameworks.
