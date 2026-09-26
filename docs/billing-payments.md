# Billing and payments

The `billing` Modulith module owns invoices, invoice lines, payments and payment transactions.
Flyway migration `V14__billing_payments.sql` strengthens the tables introduced by V7 without
rewriting an already applied migration.

## Workflow

1. Create a `DRAFT` invoice with one or more server-calculated lines.
2. Issue it (`ISSUED`) once its content is final.
3. Record one or more successful payments. The invoice becomes `PARTIALLY_PAID` and then `PAID`.
4. A draft or unpaid issued invoice may be cancelled.

Amounts, balance, status transitions and currency consistency are enforced by the domain model.
Supported payment methods are `CASH`, `CARD`, `MOBILE_MONEY`, `INSURANCE`, `MUTUAL` and
`THIRD_PARTY`. Provider and external transaction references are retained for reconciliation.

## API

- `POST /api/v1/invoices`
- `GET /api/v1/invoices`
- `GET /api/v1/invoices/{id}`
- `POST /api/v1/invoices/{id}/issue`
- `POST /api/v1/invoices/{id}/cancel`
- `POST /api/v1/invoices/{id}/payments`
- `GET /api/v1/invoices/{id}/payments`

`PLATFORM_ADMIN`, `HOSPITAL_ADMIN`, `HOSPITAL_AGENT`, `CASHIER` and `ACCOUNTANT` may manage
billing. Except for platform administrators, the JWT `healthys_organization_id` claim limits them
to their organization. Patients have read-only access to their own invoices.

The realm blueprint maps the Keycloak user attribute `healthys_organization_id` into web and
mobile access tokens. The same roles and mapper must be added manually to an already deployed
realm.
