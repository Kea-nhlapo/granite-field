# ADR 0005: Production keys need versions and overlap

## Status

Accepted as a production requirement; deliberately incomplete for the hackathon

## Decision

Secrets come from a deployment secret manager, never Git.

- Database, object-storage, email, and external-provider credentials rotate in
  the provider first. Deployments switch to the new credential, verify it, and
  only then revoke the old one.
- JWT signing keys need a key identifier and an overlap window: issue with the
  new key while accepting both old and new keys until existing access tokens
  expire.
- Encryption keys need a version beside every ciphertext. New data uses the
  current version; old versions remain decrypt-only while a background job
  re-encrypts retained records.
- Suspected exposure skips the normal overlap. Revoke immediately, rotate, and
  invalidate affected sessions or invitation links.

## Why

Replacing a single unversioned key can either cause downtime or make old data
unreadable. A short, explicit overlap allows safe rotation without accepting an
old key forever.

## Consequences

The current JWT and notification encryption configuration each accept one key.
Changing the JWT secret forces users to sign in again; changing the notification
key can make queued encrypted delivery data unreadable. That is acceptable for
the hackathon, but production is blocked until versioned key rings and a tested
rotation runbook exist.
