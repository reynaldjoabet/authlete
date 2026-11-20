-- V20260112__seed_oauth_scopes.sql
-- Creates auth_scopes table and upserts a canonical scope list (idempotent).
-- Compatible with PostgreSQL 12..19
--
-- Runs unchanged under Flyway (which wraps each migration in its own transaction) and under psql.
-- Transaction control is deliberately left to the runner: an explicit BEGIN/COMMIT here would end
-- Flyway's transaction early. From psql, run it atomically and stop on the first error:
--
--   psql -v ON_ERROR_STOP=1 --single-transaction -f scope_seed.sql
--
-- Re-running is cheap by design. Only rows whose content actually changed are rewritten, so a
-- no-op run creates no dead tuples, writes no row WAL, and leaves updated_at alone -- the table
-- does not slowly bloat from deploys that seed the same list every time.

-- Fail fast instead of queueing: DDL behind a long-running transaction holds a lock queue that
-- blocks every reader of the table until it is granted. Retry the run rather than stall traffic.
-- SET LOCAL lasts only until the end of the transaction, so the session's settings are untouched.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE IF NOT EXISTS auth_scopes (
  -- Identity rather than BIGSERIAL: the sequence is owned by the column, and GENERATED ALWAYS
  -- rejects hand-written ids that would later collide with generated ones.
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- Scope names are ASCII protocol tokens compared byte for byte (RFC 6749 section 3.3), so the
  -- "C" collation matches their semantics and makes the unique index cheaper to compare and
  -- immune to OS collation-library upgrades.
  name TEXT COLLATE "C" NOT NULL UNIQUE
    CONSTRAINT auth_scopes_name_format CHECK (name ~ '^[a-z][a-z0-9_]*(\.[a-z0-9_]+)*$'),
  description TEXT,
  default_entry BOOLEAN NOT NULL DEFAULT FALSE,
  attributes JSONB NOT NULL DEFAULT '{}'::jsonb
    CONSTRAINT auth_scopes_attributes_object CHECK (jsonb_typeof(attributes) = 'object'),
  descriptions JSONB NOT NULL DEFAULT '[]'::jsonb
    CONSTRAINT auth_scopes_descriptions_array CHECK (jsonb_typeof(descriptions) = 'array'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE auth_scopes IS
  'OAuth scopes this authorization server offers. Seeded by scope_seed.sql; name is the protocol token.';
COMMENT ON COLUMN auth_scopes.default_entry IS
  'Granted when a client requests no scope explicitly.';
COMMENT ON COLUMN auth_scopes.descriptions IS
  'Localized descriptions as a JSON array of {"tag": <BCP 47>, "value": <text>}.';

-- The UNIQUE constraint on name already creates a b-tree index on it. Earlier versions of this
-- script added a second, identical one, which only doubled the write cost of every insert and
-- update. Drop it where it exists.
DROP INDEX IF EXISTS idx_auth_scopes_name;

-- Insert / upsert scopes. Add or remove rows as needed.
-- Only name and description vary per scope; the remaining columns take the table defaults below.
-- A scope removed from this list is NOT deleted from the table: tokens and client registrations
-- may still reference it, so retiring a scope is a deliberate, separate change.
INSERT INTO auth_scopes (name, description, default_entry, attributes, descriptions)
SELECT seed.name, seed.description, FALSE, '{}'::jsonb, '[]'::jsonb
FROM (VALUES
  ('billing.invoices.read','Read invoices, including list, details, and downloadable representations'),
  ('billing.invoices.create','Create new invoices or invoice drafts'),
  ('billing.invoices.update','Update non-financial invoice metadata such as due date or billing address'),
  ('billing.invoices.adjust','Apply financial adjustments to invoices including credits and debits'),
  ('billing.invoices.cancel','Cancel or void an invoice'),
  ('billing.invoices.reopen','Reopen a previously closed or cancelled invoice'),
  ('billing.invoices.manage','Administrative invoice management (superset of update, adjust, cancel, reopen)'),
  ('billing.audit.read','Read billing audit logs and invoice change history'),
  ('billing.audit.export','Export billing audit data for compliance or reporting'),

  ('payments.transactions.read','Read payment transaction records'),
  ('payments.transactions.create','Initiate payment transactions'),
  ('payments.transactions.capture','Capture previously authorized payments'),
  ('payments.transactions.void','Void an uncaptured payment transaction'),
  ('payments.transactions.refund','Refund a settled payment transaction'),

  ('payments.authorizations.read','Read payment authorization status'),
  ('payments.authorizations.create','Create payment authorizations'),

  ('payments.methods.read','Read stored payment methods'),
  ('payments.methods.create','Add a new payment method'),
  ('payments.methods.update','Update payment method metadata'),
  ('payments.methods.delete','Delete a stored payment method'),
  ('payments.methods.manage','Administrative management of payment methods'),

  ('payments.settlements.read','Read settlement batches and summaries'),
  ('payments.settlements.close','Close settlement batches'),

  ('payments.disputes.read','Read payment disputes and chargebacks'),
  ('payments.disputes.respond','Respond to payment disputes with evidence'),

  ('accounts.read','Read account list and basic account metadata'),
  ('accounts.balances.read','Read current and available account balances'),
  ('accounts.transactions.read','Read account transaction history'),
  ('accounts.transactions.export','Export account transaction history'),

  ('payments.initiations.create','Initiate a bank payment on behalf of the user'),
  ('payments.initiations.status.read','Read the status of initiated bank payments'),
  ('payments.initiations.cancel','Cancel a pending bank payment initiation'),

  ('beneficiaries.read','Read beneficiaries or payees'),
  ('beneficiaries.create','Create a new beneficiary or payee'),
  ('beneficiaries.delete','Delete an existing beneficiary or payee'),

  ('customers.profile.read','Read customer profile information'),
  ('customers.profile.update','Update non-sensitive customer profile information'),
  ('customers.identity.read','Read verified customer identity attributes'),
  ('customers.identity.verify','Perform customer identity verification'),
  ('customers.contacts.read','Read customer contact details'),
  ('customers.contacts.update','Update customer contact details'),

  ('audit.events.read','Read security and compliance audit events'),
  ('audit.events.export','Export audit events for compliance review'),
  ('risk.scores.read','Read fraud and risk assessment scores'),
  ('risk.rules.read','Read fraud detection rules'),
  ('risk.rules.manage','Manage fraud detection rules'),
  ('limits.read','Read transaction and account limits'),
  ('limits.update','Update transaction and account limits'),

  ('ledger.entries.read','Read financial ledger entries'),
  ('ledger.entries.create','Create new ledger entries'),
  ('ledger.entries.adjust','Apply financial corrections to ledger entries'),
  ('treasury.balances.read','Read treasury account balances'),
  ('treasury.transfers.create','Initiate internal treasury transfers'),

  ('reports.financial.read','Read financial reports'),
  ('reports.regulatory.read','Read regulatory compliance reports'),
  ('reports.exports.create','Generate report exports'),
  ('reports.exports.read','Download generated report exports')
) AS seed (name, description)
ON CONFLICT (name) DO UPDATE
  SET description = EXCLUDED.description,
      default_entry = EXCLUDED.default_entry,
      attributes = EXCLUDED.attributes,
      descriptions = EXCLUDED.descriptions,
      updated_at = now()
  -- Skip rows that already match. Without this, every run rewrites all of them: one dead tuple
  -- per row per deploy, plus WAL, plus an updated_at that no longer means "content changed".
  WHERE (auth_scopes.description, auth_scopes.default_entry, auth_scopes.attributes, auth_scopes.descriptions)
        IS DISTINCT FROM
        (EXCLUDED.description, EXCLUDED.default_entry, EXCLUDED.attributes, EXCLUDED.descriptions);
