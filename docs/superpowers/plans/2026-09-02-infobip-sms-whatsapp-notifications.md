# Infobip SMS and WhatsApp Notifications Implementation Plan

**Goal:** Replace the upstream Twilio notification scaffold with durable, consented, event-driven Infobip SMS and WhatsApp delivery, then prove both channels against a real handset.

**Design:** `docs/superpowers/specs/2026-09-02-infobip-sms-whatsapp-notifications-design.md`

**Baseline:** `origin/main` at `4dd292c`; the local design commit is rebased on top. Upstream now contains both the minimal mobile scaffold and the `PaymentEvent.Released` event.

**Stack:** Java 21, Spring Boot 4.1, Spring MVC/Security/JDBC, PostgreSQL/Flyway, transactional outbox, Infobip Messages API, Maven/JUnit/AssertJ/Mockito, React-generated OpenAPI client, Docker Compose on AWS EC2.

## Delivery discipline

- Implement each task test-first: write the focused failing test, run it to establish the failure, add the minimum production code, and rerun it.
- Keep provider I/O out of domain transactions.
- Never put a real API key, sender, destination, template name, or callback secret in source or fixtures.
- Use synthetic E.164 numbers in automated tests.
- Commit after each independently green task.
- The final live smoke test is manual and opt-in; it must never be part of CI.

## Task 1: Synchronize the design with the new upstream baseline

**Modify**

- `docs/superpowers/specs/2026-09-02-infobip-sms-whatsapp-notifications-design.md`

**Steps**

1. Record that upstream added a Twilio scaffold and real escrow events during design review.
2. Clarify that the scaffold is migrated rather than duplicated.
3. Replace the future escrow seam with consumption of `PaymentEvent.Released`.
4. Correct webhook architecture to use HMAC-authenticated Infobip subscriptions, because per-request callbacks do not provide subscription authentication.
5. Preserve the existing direct delivery-confirmation use case through a narrow, template-only compatibility command.
6. Run `git diff --check` and commit the revised design plus this plan.

## Task 2: Add the mobile notification schema

**Create**

- `apps/backend/src/main/resources/db/migration/V20260903200000__notification_add_mobile_delivery.sql`
- `apps/backend/src/test/java/za/co/trademesh/integration/MobileNotificationMigrationUpgradeTest.java`

**Modify**

- `apps/backend/src/test/java/za/co/trademesh/support/MigrationNamingTest.java` only if its assertions require the new latest migration.

**Schema**

1. Add `sms_enabled BOOLEAN NOT NULL DEFAULT FALSE` and `whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE` to `notification_preference`.
2. Create `notification_contact_point` with user ownership, encrypted phone, keyed fingerprint, masked suffix, independent consent timestamps, and audit timestamps.
3. Create `mobile_notification` with unique idempotency key, request fingerprint, user/direct recipient metadata, channel, category, template identity, encrypted recipient, status, provider IDs, and lifecycle timestamps.
4. Create `mobile_notification_template_data`; every value is encrypted before insert.
5. Create `mobile_delivery_attempt` with a unique notification/outbox/attempt constraint and explicit started, accepted, failed, or unknown outcomes.
6. Create `mobile_status_observation` with a unique callback fingerprint and no raw callback body.
7. Add database checks for E.164 mask shape, known channels/categories/statuses, positive versions/attempts, and timestamp/state consistency.
8. Prove both a clean migration and an upgrade from the previous notification schema using Testcontainers.

**Verify**

```bash
cd apps/backend
./mvnw -Dtest=MobileNotificationMigrationUpgradeTest,MigrationNamingTest test
```

## Task 3: Build encrypted contact management and channel preferences

**Create**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/domain/NotificationContactPoint.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/PhoneNumbers.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/NotificationContactService.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/api/NotificationContactContracts.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/api/NotificationContactController.java`
- `apps/backend/src/test/java/za/co/trademesh/modules/notification/api/NotificationContactIntegrationTest.java`

**Modify**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/NotificationDataProtector.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/infrastructure/SensitiveNotificationDataProtector.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/domain/NotificationPreference.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/domain/NotificationRepository.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/infrastructure/JdbcNotificationRepository.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/NotificationPreferenceService.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/api/NotificationPreferenceContracts.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/api/NotificationPreferenceController.java`
- `apps/backend/src/test/java/za/co/trademesh/modules/notification/api/NotificationDeliveryIntegrationTest.java`

**Behavior**

1. Normalize spaces and hyphens, then require canonical E.164 (`+` plus 8–15 digits).
2. Extend the data protector with a keyed HMAC-SHA-256 fingerprint using the existing 32-byte notification key.
3. Store only AES-GCM ciphertext, HMAC fingerprint, and last four digits.
4. Implement authenticated self-only `GET`, `PUT`, and `DELETE /api/notification-contacts/phone` endpoints.
5. Require explicit SMS and WhatsApp consent booleans; false maps to a null consent timestamp.
6. Return only a mask such as `*******4567`.
7. Extend preference reads with both channels, defaulting them to false.
8. Give the existing preference `PUT` partial-update semantics and reject an empty update.
9. Reject enabling a channel unless its consented contact exists; always permit disabling it.
10. Verify authorization, masking, encryption at rest, consent revocation, deletion, backward-compatible email updates, and default-off behavior.

**Verify**

```bash
cd apps/backend
./mvnw -Dtest=NotificationContactIntegrationTest,NotificationDeliveryIntegrationTest test
```

## Task 4: Replace arbitrary message strings with versioned mobile templates

**Create**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileTemplateCatalog.java`
- `apps/backend/src/main/resources/notification-templates/mobile/capacity-match-found.v1.properties`
- `apps/backend/src/main/resources/notification-templates/mobile/handover-confirmation-accepted.v1.properties`
- `apps/backend/src/main/resources/notification-templates/mobile/handover-finalized-clean.v1.properties`
- `apps/backend/src/main/resources/notification-templates/mobile/handover-finalized-disputed.v1.properties`
- `apps/backend/src/main/resources/notification-templates/mobile/escrow-released.v1.properties`
- `apps/backend/src/main/resources/notification-templates/mobile/delivery-confirmation.v1.properties`
- `apps/backend/src/test/java/za/co/trademesh/modules/notification/application/MobileTemplateCatalogTest.java`

**Modify**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/NotificationTemplates.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileNotificationRequests.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/delivery/application/DeliveryProposalService.java`
- `apps/backend/src/test/java/za/co/trademesh/modules/delivery/application/DeliveryProposalServiceTest.java`

**Behavior**

1. Discover template property files from the classpath and index them by key/version.
2. Keep SMS text, allowed variable names, WhatsApp parameter order, and language in versioned resources—not Java body literals.
3. Reject duplicate template identities, unknown variables, missing variables, invalid links, and rendered SMS bodies over the configured limit.
4. Change the mobile request boundary to expose:
   - a user request containing recipient user ID, category, event identity, and template data; and
   - a package-level/direct compatibility request containing a destination, selected channel, category, and template data for delivery confirmation only.
5. Remove the hardcoded delivery-confirmation body from `DeliveryProposalService`; keep its token only in encrypted template data.
6. Assert exact safe renderings and prove no QR nonce, payment amount, full shipment ID, or arbitrary caller body can enter a template.

**Verify**

```bash
cd apps/backend
./mvnw -Dtest=MobileTemplateCatalogTest,DeliveryProposalServiceTest test
```

## Task 5: Persist and enqueue consent-aware mobile notifications

**Create**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/domain/MobileChannel.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/domain/MobileNotification.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/domain/MobileNotificationStatus.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/domain/MobileDeliveryAttempt.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/domain/MobileDeliveryAttemptStatus.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/domain/MobileNotificationRepository.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/infrastructure/JdbcMobileNotificationRepository.java`
- `apps/backend/src/test/java/za/co/trademesh/modules/notification/application/MobileNotificationServiceIntegrationTest.java`

**Modify**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileNotificationService.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileDeliveryRequested.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/LocalMobileCapture.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/infrastructure/LocalCaptureMobileProvider.java`

**Behavior**

1. A user request fans out only to channels that have both consent and an enabled category preference.
2. Missing contact, consent, or preference creates a `SUPPRESSED` audit record and no outbox message for that channel.
3. Snapshot recipient and template variables encrypted in PostgreSQL.
4. Build deterministic keys from event type, event ID, recipient, channel, template key, and version.
5. Store and compare a request fingerprint so key reuse with different content fails loudly.
6. Enqueue only the opaque notification UUID; do not put phone or content into the outbox payload.
7. Migrate the direct delivery-confirmation request into this durable path.
8. Verify replay idempotency, fan-out, suppression, ciphertext at rest, and local capture after worker polling.

**Verify**

```bash
cd apps/backend
./mvnw -Dtest=MobileNotificationServiceIntegrationTest,NotificationDeliveryIntegrationTest test
```

## Task 6: Implement durable delivery attempts and Infobip submission

**Create**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileDeliveryCoordinator.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileDeliveryTransactions.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileProviderException.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/InfobipNotificationProperties.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/infrastructure/InfobipMobileDeliveryProvider.java`
- `apps/backend/src/test/java/za/co/trademesh/modules/notification/application/MobileDeliveryRetryIntegrationTest.java`
- `apps/backend/src/test/java/za/co/trademesh/modules/notification/infrastructure/InfobipMobileDeliveryProviderTest.java`

**Modify**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileDeliveryProvider.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileDeliveryHandler.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileNotificationProperties.java`
- `apps/backend/src/main/resources/application.yml`
- `apps/backend/src/main/resources/application-sandbox.yml`
- `apps/backend/src/main/resources/application-production.yml`

**Delete**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/infrastructure/TwilioMobileDeliveryProvider.java`

**Behavior**

1. Submit `POST /messages-api/1/messages` with `Authorization: App <API key>` and JSON content.
2. Use configured Infobip SMS/WhatsApp senders and strip the leading `+` only at the provider boundary.
3. Send SMS as `TEXT`; send WhatsApp through the configured approved template name/language and ordered parameters.
4. Send the notification UUID as both custom message ID where supported and opaque `callbackData`.
5. Fail startup in Infobip mode when the base URL is non-HTTPS or any key, sender, HMAC secret, or active template mapping is absent.
6. Persist a started attempt before I/O and the provider ID/status afterward.
7. Retry confirmed transient 429/5xx failures with the outbox policy; mark deterministic 4xx failures permanent.
8. Treat connection loss/timeouts as `SUBMISSION_UNKNOWN`, never as permission to blindly submit the message again.
9. Sanitize stored provider errors and never log request bodies, API keys, recipient numbers, or rendered text.
10. Prove exact HTTP contracts with Spring's mock server and prove bounded attempt behavior against PostgreSQL.

**Verify**

```bash
cd apps/backend
./mvnw -Dtest=InfobipMobileDeliveryProviderTest,MobileDeliveryRetryIntegrationTest test
```

## Task 7: Reconcile ambiguous submissions and authenticate callbacks

**Create**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileReconciliationRequested.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileReconciliationHandler.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/InfobipStatusService.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/api/InfobipWebhookController.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/infrastructure/InfobipWebhookVerifier.java`
- `apps/backend/src/test/java/za/co/trademesh/modules/notification/api/InfobipWebhookIntegrationTest.java`
- `apps/backend/src/test/java/za/co/trademesh/modules/notification/application/MobileReconciliationIntegrationTest.java`

**Modify**

- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileDeliveryProvider.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/infrastructure/InfobipMobileDeliveryProvider.java`
- `apps/backend/src/main/java/za/co/trademesh/shared/security/SecurityConfiguration.java`

**Behavior**

1. On `SUBMISSION_UNKNOWN`, atomically enqueue reconciliation instead of another send.
2. Query `GET /messages-api/1/reports?messageID=<notification UUID>` with bounded outbox retries.
3. Expose dedicated public POST endpoints for Infobip delivery and seen subscription events.
4. Verify `X-Hub-Signature` as HMAC-SHA-256 over the untouched request bytes before parsing; compare in constant time.
5. Resolve notifications through custom message ID, provider ID, or opaque callback data without trusting recipient fields.
6. Map Infobip groups to accepted/queued/sent/delivered/read/failed/rejected/expired states.
7. Persist a unique observation fingerprint, return success for callback replay, and prevent late callbacks from regressing state.
8. Reject bad signatures, oversized bodies, malformed JSON, unknown channels, and unknown statuses without logging raw bodies.
9. Prove callback replay, out-of-order status handling, bad signatures, unknown notifications, and reconciliation outcomes.

**Verify**

```bash
cd apps/backend
./mvnw -Dtest=InfobipWebhookIntegrationTest,MobileReconciliationIntegrationTest test
```

## Task 8: Connect capacity, handover, and escrow events

**Create**

- `apps/backend/src/main/java/za/co/trademesh/modules/access/application/BusinessNotificationRecipients.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/access/infrastructure/JdbcBusinessNotificationRecipients.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/handover/application/HandoverNotificationRecipients.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/handover/infrastructure/JdbcHandoverNotificationRecipients.java`
- `apps/backend/src/main/java/za/co/trademesh/modules/notification/application/MobileNotificationEventListener.java`
- `apps/backend/src/test/java/za/co/trademesh/modules/notification/application/MobileNotificationEventListenerIntegrationTest.java`

**Behavior**

1. `CapacityMatchCompleted`: when count is positive, parse the authenticated actor as the requesting user and enqueue `capacity-match-found.v1`.
2. `ConfirmationAccepted`: query challenge participants and notify only the non-actor with `handover-confirmation-accepted.v1`.
3. `HandoverFinalized`: notify both participants using the clean or disputed template.
4. `PaymentEvent.Released`: query active business members and notify each through `escrow-released.v1` without amount or payment data.
5. Use after-commit listeners and new transactions, matching the established risk-listener pattern.
6. Prove correct recipients, zero-match suppression, disabled-channel suppression, two-channel fan-out, and event replay idempotency.
7. Run ArchUnit to prove cross-module dependencies use application boundaries only.

**Verify**

```bash
cd apps/backend
./mvnw -Dtest=MobileNotificationEventListenerIntegrationTest,ModuleBoundaryTest test
```

## Task 9: Wire deployment configuration and operator documentation

**Modify**

- `apps/backend/.env.example`
- `infra/containers/.env.aws.example`
- `infra/containers/docker-compose.yml`
- `infra/containers/docker-compose.aws.yml`
- `docs/deployment/aws-ec2.md`

**Create**

- `docs/operations/infobip-notifications.md`

**Behavior**

1. Replace mobile-notification Twilio settings with blank Infobip placeholders.
2. Keep Twilio Verify settings clearly scoped to phone authentication; notification delivery no longer consumes them.
3. Pass Infobip settings through both Compose stacks without committing values.
4. Select `infobip` in deployed profiles and keep `local` for the local profile/tests.
5. Document account setup, least-privilege API scope, South African SMS sender provisioning, WhatsApp sender/template approval, subscription endpoints, HMAC_SHA_256, AWS secret injection, rotation, dashboards, failure triage, and rollback to local/off.
6. Add startup-configuration tests proving local mode needs no live secrets and live mode fails closed.

## Task 10: Publish API contract and generated client

**Modify (generated)**

- `packages/api-contracts/openapi/trademesh-v1.json`
- `apps/frontend/src/shared/api/generated/**`

**Steps**

1. Generate the OpenAPI document from the running backend after contact/preference APIs are final.
2. Run `npm run generate:api` in `apps/frontend`.
3. Confirm the generated client has typed contact and multi-channel preference methods.
4. Run the contract consistency and generated-client checks.

**Verify**

```bash
cd apps/backend
./mvnw -Dtest=OpenApiContractIntegrationTest test
cd ../frontend
npm run check:api
npm run typecheck
```

## Task 11: Full automated verification

**Run**

```bash
cd apps/backend
./mvnw test
cd ../frontend
npm run verify
cd ../..
docker compose --env-file infra/containers/.env.example -f infra/containers/docker-compose.yml config
docker compose --env-file infra/containers/.env.aws.example -f infra/containers/docker-compose.aws.yml config
git diff --check
git status --short
```

Inspect the resulting diffs for secrets, real phone numbers, Twilio notification references, arbitrary message bodies, unsigned webhook paths, and API drift.

## Task 12: Live Infobip smoke test and completion audit

**Manual prerequisites**

- Infobip account/base URL and least-privilege API key.
- Provisioned South African SMS sender.
- Registered WhatsApp sender and approved templates.
- Public AWS HTTPS callback endpoints attached to HMAC-authenticated Infobip subscriptions.
- A user-owned South African handset entered only through the authenticated contact API, with both consents and preferences enabled.

**Steps**

1. Deploy the tested image and inject secrets through the AWS environment/secret store.
2. Register the test handset through the authenticated API; do not add a recipient environment fallback.
3. Trigger an actual supported event or use the explicit, excluded-by-default smoke-test runner.
4. Confirm one SMS and one WhatsApp message arrive on the handset.
5. Confirm database records contain provider IDs and reach `DELIVERED`; confirm WhatsApp reaches `READ` when opened and the provider reports it.
6. Replay a signed callback and confirm no duplicate observation or state regression.
7. Inspect logs to confirm that API keys, full numbers, and rendered bodies are absent.
8. Complete a requirement-by-requirement audit against the approved design before marking the goal complete.

The goal remains active until this live evidence exists. Automated tests and a deployable adapter alone are insufficient proof of live delivery.
