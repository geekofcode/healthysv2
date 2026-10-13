# Consultation core refactor

## First implementation increment

The platform is moving to organization subscriptions with staff quotas, one global patient identity, care episodes containing initial and follow-up visits, cash-only patient billing, dated doctor queues and subscription-gated OpenAI assistance.

Implemented in this increment:
- CASH-only new patient payments, preserving historical electronic payment reads.
- Cashier and hospital-agent Keycloak aliases and frontend roles; nurse patient creation/editing.
- Additive V26 care episode/visit tables and domain foundations with fixed follow-up duration snapshots, first actual encounter start, same-organization coverage, postponement and reassignment guards.
- Domain, billing, frontend and migration regression tests.

The care module is not yet exposed through application services or controllers. The legacy consultation flow remains active until orchestration, secure access and migration are complete. Do not interpret these foundations as a completed clinical journey.

## Configuration

`HEALTHYS_CARE_FOLLOW_UP_DURATION` maps to `healthys.care.follow-up-duration` (ISO-8601 Duration). The provisional development default is PT168H (7 days), pending product confirmation.
`HEALTHYS_CARE_RETENTION_YEARS` maps to `healthys.care.retention-years` (default 30).

## Next increments

1. Organization plans, subscriptions, quota accounting and entitlements, with Stripe subscription webhooks.
2. Secure cash settlement to episode readiness, and visit scheduling/reassignment with queue history and access grants.
3. Doctor day calendar and persistent reorderable queue; visit lifecycle linked to existing clinical reports and prescriptions.
4. Same-organization free follow-up, OpenAI Python jobs gated by subscription, retention/deletion worker.
5. Dry-run legacy migration, end-to-end validation and removal of obsolete entry points.

## Validation

Frontend: 24 suites / 88 tests pass; production build passes.
Backend: tests added, execution blocked before compilation by unavailable Maven parent dependency and Java 17 runtime (project requires 21). V26 has not been executed against a database in this environment.
