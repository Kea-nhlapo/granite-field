# API contracts

The OpenAPI document in `openapi/openapi.yaml` is the source of truth for
Granite Field HTTP operations. Frontend types and `OPERATION_IDS` live in
`apps/frontend/src/shared/api/generated.ts`. `scripts/check-client.mjs` fails CI
when those IDs drift.

Ordinary-user schemas omit precise live location and internal-risk-only fields.
Authentication uses the `granite.sid` session cookie.

Example payloads live in `examples/`. Backend services should serve this same
contract; see `trademesh.openapi.contract` in
`apps/backend/src/main/resources/application.yml`.
