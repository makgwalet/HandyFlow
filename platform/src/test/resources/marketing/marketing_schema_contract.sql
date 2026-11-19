-- Marketing schema contract test.
--
-- Verifies, against a database migrated with ALL Flyway scripts, the schema behaviours the Marketing code
-- depends on: unique-active suppression, append-only evidence, idempotent provider events, idempotent
-- conversions, one active journey enrolment per contact, the journey claim query, and topic-name uniqueness.
--
-- Run:  psql -v ON_ERROR_STOP=1 -f marketing_schema_contract.sql <db>
-- Everything happens in one transaction that is rolled back, so it leaves no data behind.
-- Any failed assertion raises an exception and psql exits non-zero.

\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE _t(id int, name text, ok boolean);

CREATE FUNCTION pg_temp.assert(n int, name text, cond boolean) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO _t VALUES (n, name, coalesce(cond, false));
    IF NOT coalesce(cond, false) THEN RAISE EXCEPTION 'FAILED #% %', n, name; END IF;
END $$;

DO $$
DECLARE
    t1 uuid := gen_random_uuid();  t2 uuid := gen_random_uuid();
    n int; j uuid := gen_random_uuid(); p1 uuid := gen_random_uuid(); p2 uuid := gen_random_uuid();
    claimed int;
BEGIN
    -- Two real tenants (mkt_contact_preferences has a foreign key to tenants).
    INSERT INTO tenants (id, name, slug, email, tenant_id) VALUES
        (t1, 'Contract T1', 'contract-t1-' || substr(t1::text, 1, 8), 'ct1@x.test', t1),
        (t2, 'Contract T2', 'contract-t2-' || substr(t2::text, 1, 8), 'ct2@x.test', t2);

    -- 1. Suppression: only ONE active row per tenant/channel/address (concurrent unsubscribes are a no-op).
    INSERT INTO mkt_suppression_entries (id, tenant_id, email, channel, reason, source, created_at)
        VALUES (gen_random_uuid(), t1, 'a@x.test', 'EMAIL', 'UNSUBSCRIBED', 'T', now());
    INSERT INTO mkt_suppression_entries (id, tenant_id, email, channel, reason, source, created_at)
        VALUES (gen_random_uuid(), t1, 'a@x.test', 'EMAIL', 'UNSUBSCRIBED', 'T', now()) ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS n = ROW_COUNT;
    PERFORM pg_temp.assert(1, 'second active suppression is a no-op (ON CONFLICT DO NOTHING)', n = 0);
    PERFORM pg_temp.assert(2, 'exactly one active suppression row',
        (SELECT count(*) FROM mkt_suppression_entries WHERE tenant_id = t1 AND email = 'a@x.test' AND lifted_at IS NULL) = 1);

    -- 2. Same address in ANOTHER tenant is independent.
    INSERT INTO mkt_suppression_entries (id, tenant_id, email, channel, reason, source, created_at)
        VALUES (gen_random_uuid(), t2, 'a@x.test', 'EMAIL', 'MANUAL', 'T', now());
    PERFORM pg_temp.assert(3, 'suppression is tenant-scoped',
        (SELECT count(*) FROM mkt_suppression_entries WHERE email = 'a@x.test' AND lifted_at IS NULL AND tenant_id IN (t1, t2)) = 2);

    -- 3. Lifting keeps history and allows a NEW active suppression later.
    UPDATE mkt_suppression_entries SET lifted_at = now(), lift_reason = 'documented reason' WHERE tenant_id = t1 AND email = 'a@x.test';
    INSERT INTO mkt_suppression_entries (id, tenant_id, email, channel, reason, source, created_at)
        VALUES (gen_random_uuid(), t1, 'a@x.test', 'EMAIL', 'COMPLAINT', 'T', now());
    PERFORM pg_temp.assert(4, 'lifted row kept as history + new active row allowed',
        (SELECT count(*) FROM mkt_suppression_entries WHERE tenant_id = t1 AND email = 'a@x.test') = 2
        AND (SELECT count(*) FROM mkt_suppression_entries WHERE tenant_id = t1 AND email = 'a@x.test' AND lifted_at IS NULL) = 1);

    -- 4. The audience SUPPRESSED_SQL fragment (copied from MarketingAudienceService) agrees with the index semantics.
    INSERT INTO mkt_contact_preferences (id, tenant_id, entity_type, email, name, email_opted_in)
        VALUES (p1, t1, 'SUBSCRIBER', 'a@x.test', 'A', true), (p2, t1, 'SUBSCRIBER', 'b@x.test', 'B', true);
    PERFORM pg_temp.assert(5, 'SUPPRESSED_SQL flags the suppressed contact only',
        (SELECT count(*) FROM mkt_contact_preferences p WHERE p.tenant_id = t1 AND p.email_opted_in
            AND EXISTS (SELECT 1 FROM mkt_suppression_entries s WHERE s.tenant_id = p.tenant_id
                        AND s.channel = 'EMAIL' AND s.email = p.email AND s.lifted_at IS NULL)) = 1);

    -- 5. Consent evidence: append-only log accepts every event type the code writes.
    INSERT INTO mkt_consent_evidence (id, tenant_id, preference_id, email, event_type, source, evidence_text, created_at)
        SELECT gen_random_uuid(), t1, p1, 'a@x.test', e, 'T', 'x', now()
        FROM unnest(ARRAY['OPT_IN','OPT_OUT','IMPORT_DECLARATION','SUPPRESSION_ADDED','SUPPRESSION_LIFTED']) e;
    PERFORM pg_temp.assert(6, 'consent evidence stores all five event types',
        (SELECT count(*) FROM mkt_consent_evidence WHERE tenant_id = t1 AND email = 'a@x.test') = 5);

    -- 6. Provider events are idempotent on (provider, provider_event_id).
    INSERT INTO mkt_delivery_events (id, tenant_id, provider, provider_event_id, event_type, occurred_at, outcome)
        VALUES (gen_random_uuid(), t1, 'ses', 'evt-1', 'HARD_BOUNCE', now(), 'APPLIED');
    INSERT INTO mkt_delivery_events (id, tenant_id, provider, provider_event_id, event_type, occurred_at, outcome)
        VALUES (gen_random_uuid(), t1, 'ses', 'evt-1', 'HARD_BOUNCE', now(), 'APPLIED') ON CONFLICT (provider, provider_event_id) DO NOTHING;
    GET DIAGNOSTICS n = ROW_COUNT;
    PERFORM pg_temp.assert(7, 'a retried provider webhook is a no-op', n = 0);

    -- 7. Conversions are idempotent per tenant on external_ref, but another tenant may reuse the ref.
    INSERT INTO mkt_conversions (id, tenant_id, external_ref, kind, email, amount_cents, currency, paid_at, source)
        VALUES (gen_random_uuid(), t1, 'INV-1', 'PAYMENT', 'a@x.test', 10000, 'ZAR', now(), 'INTEGRATION');
    INSERT INTO mkt_conversions (id, tenant_id, external_ref, kind, email, amount_cents, currency, paid_at, source)
        VALUES (gen_random_uuid(), t1, 'INV-1', 'PAYMENT', 'a@x.test', 10000, 'ZAR', now(), 'INTEGRATION') ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS n = ROW_COUNT;
    PERFORM pg_temp.assert(8, 'duplicate sale ref is ignored', n = 0);
    INSERT INTO mkt_conversions (id, tenant_id, external_ref, kind, email, amount_cents, currency, paid_at, source)
        VALUES (gen_random_uuid(), t2, 'INV-1', 'PAYMENT', 'a@x.test', 10000, 'ZAR', now(), 'INTEGRATION');
    PERFORM pg_temp.assert(9, 'same ref in another tenant is allowed',
        (SELECT count(*) FROM mkt_conversions WHERE external_ref = 'INV-1') = 2);

    -- 8. Journeys: one ACTIVE enrolment per (journey, contact); a finished one allows re-entry.
    INSERT INTO mkt_journeys (id, tenant_id, name, trigger_type, status, steps)
        VALUES (j, t1, 'J', 'MANUAL', 'ACTIVE', '[]'::jsonb);
    INSERT INTO mkt_journey_enrollments (id, tenant_id, journey_id, preference_id, email, status, current_step, next_run_at)
        VALUES (gen_random_uuid(), t1, j, p2, 'b@x.test', 'ACTIVE', 0, now() - interval '1 minute');
    INSERT INTO mkt_journey_enrollments (id, tenant_id, journey_id, preference_id, email, status, current_step, next_run_at)
        VALUES (gen_random_uuid(), t1, j, p2, 'b@x.test', 'ACTIVE', 0, now()) ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS n = ROW_COUNT;
    PERFORM pg_temp.assert(10, 'no duplicate ACTIVE enrolment for the same contact', n = 0);

    -- 9. The engine's claim query (verbatim shape): only due, unlocked/stale-locked, ACTIVE rows in ACTIVE journeys.
    INSERT INTO mkt_journey_enrollments (id, tenant_id, journey_id, preference_id, email, status, current_step, next_run_at, locked_at)
        VALUES (gen_random_uuid(), t1, j, p1, 'a@x.test', 'ACTIVE', 0, now() - interval '1 minute', now());   -- freshly locked: must be skipped
    WITH c AS (
        UPDATE mkt_journey_enrollments SET locked_at = now()
        WHERE id IN (SELECT e.id FROM mkt_journey_enrollments e JOIN mkt_journeys jj ON jj.id = e.journey_id
                     WHERE e.status = 'ACTIVE' AND jj.status = 'ACTIVE' AND e.next_run_at <= now()
                       AND (e.locked_at IS NULL OR e.locked_at < now() - interval '10 minutes')
                       AND e.journey_id = j
                     ORDER BY e.next_run_at LIMIT 50 FOR UPDATE OF e SKIP LOCKED)
        RETURNING id)
    SELECT count(*) INTO claimed FROM c;
    PERFORM pg_temp.assert(11, 'claim query takes the due unlocked row and skips the freshly locked one', claimed = 1);

    -- 10. Topic names are unique per tenant, case-insensitively, among non-deleted topics.
    INSERT INTO mkt_topics (id, tenant_id, name) VALUES (gen_random_uuid(), t1, 'Newsletter');
    BEGIN
        INSERT INTO mkt_topics (id, tenant_id, name) VALUES (gen_random_uuid(), t1, 'NEWSLETTER');
        PERFORM pg_temp.assert(12, 'topic names are case-insensitively unique per tenant', false);
    EXCEPTION WHEN unique_violation THEN
        PERFORM pg_temp.assert(12, 'topic names are case-insensitively unique per tenant', true);
    END;

    -- 11. Double opt-in token hashes are unique (a token can only ever resolve to one request).
    INSERT INTO mkt_forms (id, tenant_id, slug, name, title, consent_text)
        VALUES ('00000000-0000-0000-0000-0000000000f1', t1, 'tstform1', 'F', 'F', 'consent');
    INSERT INTO mkt_optin_requests (id, tenant_id, form_id, email, token_hash, consent_text, expires_at)
        VALUES (gen_random_uuid(), t1, '00000000-0000-0000-0000-0000000000f1', 'c@x.test', repeat('a', 64), 'consent', now() + interval '1 day');
    BEGIN
        INSERT INTO mkt_optin_requests (id, tenant_id, form_id, email, token_hash, consent_text, expires_at)
            VALUES (gen_random_uuid(), t1, '00000000-0000-0000-0000-0000000000f1', 'd@x.test', repeat('a', 64), 'consent', now() + interval '1 day');
        PERFORM pg_temp.assert(13, 'opt-in token hash is unique', false);
    EXCEPTION WHEN unique_violation THEN
        PERFORM pg_temp.assert(13, 'opt-in token hash is unique', true);
    END;
END $$;

SELECT id, CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result, name FROM _t ORDER BY id;
ROLLBACK;
