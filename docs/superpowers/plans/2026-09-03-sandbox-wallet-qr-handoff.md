# Sandbox wallet and QR handoff implementation plan

1. Add the sandbox-wallet schema, repository, service, authenticated read API,
   local universal-supplier directory, and local-only idempotent Lungile seed.
2. Initialize ordinary wallets at ZAR 50.00 and connect successful escrow lock
   and release events to idempotent wallet ledger entries.
3. Carry the supplier profile through the release event, resolve the claimed
   supplier user, and send amount/balance template data only to that supplier.
4. Regenerate the OpenAPI client and add the Supplier registration option,
   account-owned wallet strip/history, universal supplier selection, QR render,
   supplier scan/paste confirmation route, two-signature status, and protected
   release action.
5. Add focused backend and frontend tests, run formatting/static checks/builds,
   then exercise the two-account local flow without committing or pushing.
