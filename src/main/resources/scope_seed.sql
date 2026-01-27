-- V20260112__seed_oauth_scopes.sql
-- Creates auth_scopes table and upserts a canonical scope list (idempotent).
-- Compatible with PostgreSQL 12..18

BEGIN;

CREATE TABLE IF NOT EXISTS auth_scopes (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  description TEXT,
  default_entry BOOLEAN NOT NULL DEFAULT FALSE,
  attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
  descriptions JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Insert / upsert scopes. Add or remove rows as needed.
-- All rows use ON CONFLICT to update description / flags if they already exist.
INSERT INTO auth_scopes (name, description, default_entry, attributes, descriptions)
VALUES
  ('billing.invoices.read','Read invoices, including list, details, and downloadable representations', false, '{}'::jsonb, '[]'::jsonb),
  ('billing.invoices.create','Create new invoices or invoice drafts', false, '{}'::jsonb, '[]'::jsonb),
  ('billing.invoices.update','Update non-financial invoice metadata such as due date or billing address', false, '{}'::jsonb, '[]'::jsonb),
  ('billing.invoices.adjust','Apply financial adjustments to invoices including credits and debits', false, '{}'::jsonb, '[]'::jsonb),
  ('billing.invoices.cancel','Cancel or void an invoice', false, '{}'::jsonb, '[]'::jsonb),
  ('billing.invoices.reopen','Reopen a previously closed or cancelled invoice', false, '{}'::jsonb, '[]'::jsonb),
  ('billing.invoices.manage','Administrative invoice management (superset of update, adjust, cancel, reopen)', false, '{}'::jsonb, '[]'::jsonb),
  ('billing.audit.read','Read billing audit logs and invoice change history', false, '{}'::jsonb, '[]'::jsonb),
  ('billing.audit.export','Export billing audit data for compliance or reporting', false, '{}'::jsonb, '[]'::jsonb),

  ('payments.transactions.read','Read payment transaction records', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.transactions.create','Initiate payment transactions', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.transactions.capture','Capture previously authorized payments', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.transactions.void','Void an uncaptured payment transaction', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.transactions.refund','Refund a settled payment transaction', false, '{}'::jsonb, '[]'::jsonb),

  ('payments.authorizations.read','Read payment authorization status', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.authorizations.create','Create payment authorizations', false, '{}'::jsonb, '[]'::jsonb),

  ('payments.methods.read','Read stored payment methods', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.methods.create','Add a new payment method', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.methods.update','Update payment method metadata', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.methods.delete','Delete a stored payment method', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.methods.manage','Administrative management of payment methods', false, '{}'::jsonb, '[]'::jsonb),

  ('payments.settlements.read','Read settlement batches and summaries', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.settlements.close','Close settlement batches', false, '{}'::jsonb, '[]'::jsonb),

  ('payments.disputes.read','Read payment disputes and chargebacks', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.disputes.respond','Respond to payment disputes with evidence', false, '{}'::jsonb, '[]'::jsonb),

  ('accounts.read','Read account list and basic account metadata', false, '{}'::jsonb, '[]'::jsonb),
  ('accounts.balances.read','Read current and available account balances', false, '{}'::jsonb, '[]'::jsonb),
  ('accounts.transactions.read','Read account transaction history', false, '{}'::jsonb, '[]'::jsonb),
  ('accounts.transactions.export','Export account transaction history', false, '{}'::jsonb, '[]'::jsonb),

  ('payments.initiations.create','Initiate a bank payment on behalf of the user', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.initiations.status.read','Read the status of initiated bank payments', false, '{}'::jsonb, '[]'::jsonb),
  ('payments.initiations.cancel','Cancel a pending bank payment initiation', false, '{}'::jsonb, '[]'::jsonb),

  ('beneficiaries.read','Read beneficiaries or payees', false, '{}'::jsonb, '[]'::jsonb),
  ('beneficiaries.create','Create a new beneficiary or payee', false, '{}'::jsonb, '[]'::jsonb),
  ('beneficiaries.delete','Delete an existing beneficiary or payee', false, '{}'::jsonb, '[]'::jsonb),

  ('customers.profile.read','Read customer profile information', false, '{}'::jsonb, '[]'::jsonb),
  ('customers.profile.update','Update non-sensitive customer profile information', false, '{}'::jsonb, '[]'::jsonb),
  ('customers.identity.read','Read verified customer identity attributes', false, '{}'::jsonb, '[]'::jsonb),
  ('customers.identity.verify','Perform customer identity verification', false, '{}'::jsonb, '[]'::jsonb),
  ('customers.contacts.read','Read customer contact details', false, '{}'::jsonb, '[]'::jsonb),
  ('customers.contacts.update','Update customer contact details', false, '{}'::jsonb, '[]'::jsonb),

  ('audit.events.read','Read security and compliance audit events', false, '{}'::jsonb, '[]'::jsonb),
  ('audit.events.export','Export audit events for compliance review', false, '{}'::jsonb, '[]'::jsonb),
  ('risk.scores.read','Read fraud and risk assessment scores', false, '{}'::jsonb, '[]'::jsonb),
  ('risk.rules.read','Read fraud detection rules', false, '{}'::jsonb, '[]'::jsonb),
  ('risk.rules.manage','Manage fraud detection rules', false, '{}'::jsonb, '[]'::jsonb),
  ('limits.read','Read transaction and account limits', false, '{}'::jsonb, '[]'::jsonb),
  ('limits.update','Update transaction and account limits', false, '{}'::jsonb, '[]'::jsonb),

  ('ledger.entries.read','Read financial ledger entries', false, '{}'::jsonb, '[]'::jsonb),
  ('ledger.entries.create','Create new ledger entries', false, '{}'::jsonb, '[]'::jsonb),
  ('ledger.entries.adjust','Apply financial corrections to ledger entries', false, '{}'::jsonb, '[]'::jsonb),
  ('treasury.balances.read','Read treasury account balances', false, '{}'::jsonb, '[]'::jsonb),
  ('treasury.transfers.create','Initiate internal treasury transfers', false, '{}'::jsonb, '[]'::jsonb),

  ('reports.financial.read','Read financial reports', false, '{}'::jsonb, '[]'::jsonb),
  ('reports.regulatory.read','Read regulatory compliance reports', false, '{}'::jsonb, '[]'::jsonb),
  ('reports.exports.create','Generate report exports', false, '{}'::jsonb, '[]'::jsonb),
  ('reports.exports.read','Download generated report exports', false, '{}'::jsonb, '[]'::jsonb)
ON CONFLICT (name) DO UPDATE
  SET description = EXCLUDED.description,
      default_entry = EXCLUDED.default_entry,
      attributes = EXCLUDED.attributes,
      descriptions = EXCLUDED.descriptions,
      updated_at = now();

-- Optionally: create an index useful for lookups by attribute keys (example)
CREATE INDEX IF NOT EXISTS idx_auth_scopes_name ON auth_scopes (name);

COMMIT;
