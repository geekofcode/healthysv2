# Audit, data access and platform administration

The `audit` Modulith module provides the platform-wide audit contract and the read model used by
the administration UI. `audit.audit_log` and `audit.data_access_log` are append-only: Flyway V15
installs database triggers that reject updates and deletes.

## Captured context

`AuditTrail` stores structured before/after values or access context together with the actor,
organization, patient/resource identifiers, correlation ID, request IP, user agent and timestamp.
Denied patient-access decisions are written in an independent transaction so they remain visible
even when the protected operation is rejected.

## Administration API

All endpoints require `PLATFORM_ADMIN`:

- `GET /api/v1/admin/overview`
- `GET /api/v1/admin/audit-dashboard?days=7`
- `GET /api/v1/admin/audit-logs`
- `GET /api/v1/admin/data-access-logs`
- `GET /api/v1/admin/authentication-logs`
- `GET /api/v1/admin/security-events`
- `POST /api/v1/admin/security-events/{id}/resolve`

Search results are paginated and can be filtered by actor, organization, patient, module, action,
resource and time range. Authentication rows are intended to be populated by a Keycloak event
listener or a trusted ingestion process; the dashboard already exposes them once available.

The React administration space is available at `/admin`; its audit and security dashboard is at
`/admin/audit-security` and is only shown to platform administrators.
