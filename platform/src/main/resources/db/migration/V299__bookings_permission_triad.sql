-- src/main/resources/db/migration/V299__bookings_permission_triad.sql
--
-- FIX: a real security gap found while reviewing V287's read-only
-- permission backfill against production data. BookingsController never
-- had its own permission triad — every one of its 24 endpoints, reads
-- and writes alike, was gated by USER_READ alone. Because USER_READ is
-- correctly marked is_read_only = TRUE (it genuinely is read-only
-- everywhere else it's used), and AdminAuthService.generateImpersonationToken()
-- grants an impersonation session every permission where is_read_only =
-- TRUE, this meant a support engineer impersonating ANY user with
-- USER_READ could create, modify, confirm, cancel, and delete bookings —
-- directly contradicting impersonation's own explicit read-only
-- guarantee (the token even carries a readOnly: true claim asserting
-- this can't happen).
--
-- This migration adds the missing triad; BookingsController itself is
-- updated in the same commit to actually use it, split by HTTP method
-- the same way every other module's controller in this codebase already
-- does: GET -> READ, POST/PUT -> MANAGE, DELETE -> ADMIN.
--
-- NOT fixed here, flagged separately: GET /api/v1/bookings/available-slots
-- has no @PreAuthorize at all. Left alone deliberately — it may be an
-- intentional public "check availability before booking" endpoint for a
-- customer-facing widget, and adding a permission check without knowing
-- that would risk breaking it. A decision for whoever owns the bookings
-- module's actual product requirements, not something to guess here.

INSERT INTO permissions (id, name, description, is_read_only)
VALUES
    (gen_random_uuid(), 'BOOKINGS_READ',   'View booking services, staff, availability and bookings', TRUE),
    (gen_random_uuid(), 'BOOKINGS_MANAGE', 'Create and update booking services, staff, availability and bookings', FALSE),
    (gen_random_uuid(), 'BOOKINGS_ADMIN',  'Delete booking services and staff', FALSE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.name IN ('BOOKINGS_READ', 'BOOKINGS_MANAGE', 'BOOKINGS_ADMIN')
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- Anyone who previously relied on USER_READ alone to use bookings loses
-- that access the moment BookingsController's code changes to require
-- BOOKINGS_READ/MANAGE/ADMIN instead — a real behaviour change, not just
-- an additive one. Whoever deploys this needs to grant BOOKINGS_READ (at
-- minimum) to every role that currently needs bookings access, or staff
-- who could see/manage bookings yesterday will be locked out today. This
-- migration deliberately does NOT attempt to guess who that should be —
-- see the review note in PLATFORM-ENGINES-PROGRESS.md / the strategic
-- roadmap backlog for the full reasoning and a suggested grant query.
