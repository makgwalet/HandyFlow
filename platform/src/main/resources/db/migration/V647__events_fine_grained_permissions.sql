-- Events: register the fine-grained permissions the controllers already check (EventsPermissions).
-- Until now only EVENTS_READ and EVENTS_MANAGE existed in the catalogue, so a role could not be given just
-- "check-in" or "refund". Every write check still also accepts EVENTS_MANAGE, so nothing changes for existing roles.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'EVENTS_WRITE', 'Create, edit, postpone and duplicate events'),
    (gen_random_uuid(), 'EVENTS_APPROVE', 'Publish an event, take it live and complete it'),
    (gen_random_uuid(), 'EVENTS_CANCEL', 'Cancel an entire event'),
    (gen_random_uuid(), 'EVENTS_REGISTRATION_MANAGE', 'Register, cancel and transfer guests, import guest lists and manage the waitlist'),
    (gen_random_uuid(), 'EVENTS_TICKETING_MANAGE', 'Create and change ticket tiers, pricing and capacity'),
    (gen_random_uuid(), 'EVENTS_CHECKIN', 'Scan tickets and check guests in at the door'),
    (gen_random_uuid(), 'EVENTS_SUPPLIER_MANAGE', 'Manage event suppliers, quotes and commitments'),
    (gen_random_uuid(), 'EVENTS_AUDIT_READ', 'Read the change history of an event'),
    (gen_random_uuid(), 'EVENTS_BUDGET_READ', 'See event budgets, commitments and supplier balances'),
    (gen_random_uuid(), 'EVENTS_BUDGET_MANAGE', 'Edit event budgets and record supplier invoices and payments'),
    (gen_random_uuid(), 'EVENTS_BUDGET_APPROVE', 'Approve or reject an event budget and supplier commitments'),
    (gen_random_uuid(), 'EVENTS_REPORTS_READ', 'See event dashboards and reports'),
    (gen_random_uuid(), 'EVENTS_EXPORT', 'Download guest and attendance data that contains personal information'),
    (gen_random_uuid(), 'EVENTS_PAYMENTS_READ', 'See ticket payments, refunds and profitability'),
    (gen_random_uuid(), 'EVENTS_PAYMENTS_MANAGE', 'Confirm payments received and cancel open checkouts'),
    (gen_random_uuid(), 'EVENTS_REFUND', 'Raise, complete or fail ticket refunds'),
    (gen_random_uuid(), 'EVENTS_OPS_READ', 'See the event-day roster, incidents and emergency contacts'),
    (gen_random_uuid(), 'EVENTS_OPS_MANAGE', 'Edit the run sheet, roster, contacts and checklists; handle incidents'),
    (gen_random_uuid(), 'EVENTS_INCIDENT_REPORT', 'Report an event incident or add a note to one')
ON CONFLICT (name) DO NOTHING;

-- module_key / resource / verb / sensitivity / grantable_by metadata (IAM Phase 1)
UPDATE permissions p
   SET module_key = 'events', resource = v.resource, verb = v.verb, sensitivity = v.sensitivity,
       requires_second_person = false, grantable_by = v.grantable_by
  FROM (VALUES
    ('EVENTS_WRITE', 'events', 'WRITE', 'NORMAL', 'ADMIN'),
    ('EVENTS_APPROVE', 'events', 'APPROVE', 'SENSITIVE', 'ADMIN'),
    ('EVENTS_CANCEL', 'events', 'CANCEL', 'SENSITIVE', 'ADMIN'),
    ('EVENTS_REGISTRATION_MANAGE', 'registration', 'MANAGE', 'NORMAL', 'ADMIN'),
    ('EVENTS_TICKETING_MANAGE', 'ticketing', 'MANAGE', 'NORMAL', 'ADMIN'),
    ('EVENTS_CHECKIN', 'checkin', 'USE', 'NORMAL', 'ADMIN'),
    ('EVENTS_SUPPLIER_MANAGE', 'supplier', 'MANAGE', 'SENSITIVE', 'ADMIN'),
    ('EVENTS_AUDIT_READ', 'audit', 'READ', 'SENSITIVE', 'ADMIN'),
    ('EVENTS_BUDGET_READ', 'budget', 'READ', 'FINANCIAL', 'ADMIN'),
    ('EVENTS_BUDGET_MANAGE', 'budget', 'MANAGE', 'FINANCIAL', 'ADMIN'),
    ('EVENTS_BUDGET_APPROVE', 'budget', 'APPROVE', 'FINANCIAL', 'MODULE_ADMIN'),
    ('EVENTS_REPORTS_READ', 'report', 'READ', 'NORMAL', 'ADMIN'),
    ('EVENTS_EXPORT', 'guest_data', 'EXPORT', 'SENSITIVE', 'MODULE_ADMIN'),
    ('EVENTS_PAYMENTS_READ', 'payment', 'READ', 'FINANCIAL', 'ADMIN'),
    ('EVENTS_PAYMENTS_MANAGE', 'payment', 'MANAGE', 'FINANCIAL', 'ADMIN'),
    ('EVENTS_REFUND', 'refund', 'MANAGE', 'FINANCIAL', 'MODULE_ADMIN'),
    ('EVENTS_OPS_READ', 'operations', 'READ', 'SENSITIVE', 'ADMIN'),
    ('EVENTS_OPS_MANAGE', 'operations', 'MANAGE', 'NORMAL', 'ADMIN'),
    ('EVENTS_INCIDENT_REPORT', 'incident', 'REPORT', 'NORMAL', 'ADMIN')
  ) AS v(name, resource, verb, sensitivity, grantable_by)
 WHERE p.name = v.name;

-- Roles that can already manage events keep seeing the new permissions as granted, so the role screens match what they can do.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions mp ON mp.id = rp.permission_id AND mp.name = 'EVENTS_MANAGE'
  CROSS JOIN permissions np
 WHERE left(np.name, 7) = 'EVENTS_'
ON CONFLICT DO NOTHING;
