CREATE SCHEMA licensing;
CREATE TABLE licensing.plan (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(150) NOT NULL,
 amount_minor BIGINT NOT NULL CHECK(amount_minor>=0), currency VARCHAR(3) NOT NULL,
 billing_interval VARCHAR(10) NOT NULL CHECK(billing_interval IN ('MONTH','YEAR')),
 doctor_quota INTEGER NOT NULL CHECK(doctor_quota>=0), professional_quota INTEGER NOT NULL CHECK(professional_quota>=0),
 cashier_quota INTEGER NOT NULL CHECK(cashier_quota>=0), accountant_quota INTEGER NOT NULL CHECK(accountant_quota>=0),
 ai_assistant_enabled BOOLEAN NOT NULL DEFAULT false, stripe_price_id VARCHAR(255) NOT NULL UNIQUE,
 active BOOLEAN NOT NULL DEFAULT true, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE licensing.subscription (
 organization_id UUID PRIMARY KEY REFERENCES organization.organization(id), plan_id UUID REFERENCES licensing.plan(id),
 status VARCHAR(40) NOT NULL DEFAULT 'INACTIVE', stripe_customer_id VARCHAR(255), stripe_subscription_id VARCHAR(255) UNIQUE,
 period_start TIMESTAMPTZ, period_end TIMESTAMPTZ, cancel_at_period_end BOOLEAN NOT NULL DEFAULT false,
 doctor_quota INTEGER NOT NULL DEFAULT 0, professional_quota INTEGER NOT NULL DEFAULT 0,
 cashier_quota INTEGER NOT NULL DEFAULT 0, accountant_quota INTEGER NOT NULL DEFAULT 0,
 ai_assistant_enabled BOOLEAN NOT NULL DEFAULT false, amount_minor BIGINT, currency VARCHAR(3), billing_interval VARCHAR(10),
 pending_plan_snapshot JSONB, pending_checkout_url TEXT, pending_checkout_plan_id UUID REFERENCES licensing.plan(id), pending_checkout_expires_at TIMESTAMPTZ,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE licensing.member (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organization.organization(id),
 person_id UUID REFERENCES identity.person(id), email VARCHAR(254), role VARCHAR(30) NOT NULL CHECK(role IN ('HOSPITAL_ADMIN','DOCTOR','NURSE','HOSPITAL_AGENT','CASHIER','ACCOUNTANT')),
 status VARCHAR(20) NOT NULL CHECK(status IN ('INVITED','ACTIVE','INACTIVE')), invited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 expires_at TIMESTAMPTZ, updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), CHECK(person_id IS NOT NULL OR email IS NOT NULL));
CREATE UNIQUE INDEX uq_license_member_person ON licensing.member(organization_id,person_id) WHERE person_id IS NOT NULL AND status<>'INACTIVE';
CREATE UNIQUE INDEX uq_license_member_email ON licensing.member(organization_id,lower(email)) WHERE email IS NOT NULL AND status<>'INACTIVE';
CREATE INDEX idx_license_member_org ON licensing.member(organization_id,status);
CREATE TABLE licensing.stripe_event (id VARCHAR(255) PRIMARY KEY, type VARCHAR(150) NOT NULL, processed_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE licensing.subscription_invoice (id VARCHAR(255) PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization.organization(id), status VARCHAR(40), amount_paid BIGINT, currency VARCHAR(3), hosted_invoice_url TEXT, invoice_pdf TEXT, created_at TIMESTAMPTZ NOT NULL);

CREATE TABLE licensing.audit_entry (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID REFERENCES organization.organization(id), actor_person_id UUID REFERENCES identity.person(id), action VARCHAR(50) NOT NULL, target_id UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
