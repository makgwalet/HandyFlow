-- Training Provider: read-only audit for the proposed V911/V912 CHECK hardening.
-- This script only SELECTs. It changes nothing and is NOT a Flyway migration (it lives outside db/migration on purpose).
-- Run it against a copy or the live database as a read-only user. Each row says which proposed constraint it is for, the table, and how many existing rows
-- would break it. A violations value of 0 means the constraint can be added as it is. Anything above 0 must be reviewed by a person before that constraint is added.
-- Sample ids are for finding the rows. Nothing is rewritten, defaulted or deleted by this script.

WITH checks AS (
    -- C1: retired or withdrawn records must carry a reason of at least 10 characters (the existing CHECKs let a missing reason through)
    SELECT 'C1' AS check_id, 'trainprov_authorities' AS tbl, 'RETIRED without a reason of 10+ characters' AS rule, id
      FROM trainprov_authorities WHERE status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) < 10
    UNION ALL SELECT 'C1', 'trainprov_delivery_sites', 'RETIRED without a reason of 10+ characters', id
      FROM trainprov_delivery_sites WHERE status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) < 10
    UNION ALL SELECT 'C1', 'trainprov_qualifications', 'RETIRED without a reason of 10+ characters', id
      FROM trainprov_qualifications WHERE status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) < 10
    UNION ALL SELECT 'C1', 'trainprov_programmes', 'RETIRED without a reason of 10+ characters', id
      FROM trainprov_programmes WHERE status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) < 10
    UNION ALL SELECT 'C1', 'trainprov_accreditation_scopes', 'WITHDRAWN without a reason of 10+ characters', id
      FROM trainprov_accreditation_scopes WHERE status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdrawn_reason)), 0) < 10
    UNION ALL SELECT 'C1', 'trainprov_programme_offerings', 'WITHDRAWN without a reason of 10+ characters', id
      FROM trainprov_programme_offerings WHERE status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdrawn_reason)), 0) < 10
    -- C2: a conditional approval must have its conditions written down
    UNION ALL SELECT 'C2', 'trainprov_accreditations', 'CONDITIONAL_APPROVAL without conditions of 10+ characters', id
      FROM trainprov_accreditations WHERE status = 'CONDITIONAL_APPROVAL' AND COALESCE(char_length(btrim(conditions)), 0) < 10
    -- C3: a recorded verification must name its source (5+ characters)
    UNION ALL SELECT 'C3', 'trainprov_accreditations', 'verified date or person without a source of 5+ characters', id
      FROM trainprov_accreditations
      WHERE (last_verified_on IS NOT NULL OR verified_by IS NOT NULL OR verification_source IS NOT NULL)
        AND (last_verified_on IS NULL OR verified_by IS NULL OR COALESCE(char_length(btrim(verification_source)), 0) < 5)
    -- C4: an approval must have been verified (the service always records this when it records an approval)
    UNION ALL SELECT 'C4', 'trainprov_accreditations', 'APPROVED or CONDITIONAL_APPROVAL without a recorded verification', id
      FROM trainprov_accreditations WHERE status IN ('APPROVED', 'CONDITIONAL_APPROVAL') AND (last_verified_on IS NULL OR verified_by IS NULL)
    -- C5: a qualification version that is not a draft must name its source (the existing CHECK lets a missing source through)
    UNION ALL SELECT 'C5', 'trainprov_qualification_versions', 'CURRENT or SUPERSEDED without a source note of 5+ characters', id
      FROM trainprov_qualification_versions WHERE status <> 'DRAFT' AND COALESCE(char_length(btrim(source_note)), 0) < 5
    -- C6: names, titles and codes must not be blank
    UNION ALL SELECT 'C6', 'trainprov_authorities', 'name shorter than 2 characters', id FROM trainprov_authorities WHERE COALESCE(char_length(btrim(name)), 0) < 2
    UNION ALL SELECT 'C6', 'trainprov_delivery_sites', 'name shorter than 2 characters', id FROM trainprov_delivery_sites WHERE COALESCE(char_length(btrim(name)), 0) < 2
    UNION ALL SELECT 'C6', 'trainprov_accreditations', 'title shorter than 2 characters', id FROM trainprov_accreditations WHERE COALESCE(char_length(btrim(title)), 0) < 2
    UNION ALL SELECT 'C6', 'trainprov_accreditation_scopes', 'programme_title shorter than 2 characters', id FROM trainprov_accreditation_scopes WHERE COALESCE(char_length(btrim(programme_title)), 0) < 2
    UNION ALL SELECT 'C6', 'trainprov_qualifications', 'title shorter than 2 characters', id FROM trainprov_qualifications WHERE COALESCE(char_length(btrim(title)), 0) < 2
    UNION ALL SELECT 'C6', 'trainprov_programmes', 'code or title shorter than 2 characters', id FROM trainprov_programmes WHERE COALESCE(char_length(btrim(code)), 0) < 2 OR COALESCE(char_length(btrim(title)), 0) < 2
    UNION ALL SELECT 'C6', 'trainprov_programme_modules', 'title shorter than 2 characters', id FROM trainprov_programme_modules WHERE COALESCE(char_length(btrim(title)), 0) < 2
    -- C7: optional reference fields must be NULL or non-blank (a blank string also blocks the unique indexes)
    UNION ALL SELECT 'C7', 'trainprov_accreditations', 'accreditation_number is blank', id FROM trainprov_accreditations WHERE accreditation_number IS NOT NULL AND char_length(btrim(accreditation_number)) = 0
    UNION ALL SELECT 'C7', 'trainprov_qualifications', 'official_ref or saqa_id is blank', id FROM trainprov_qualifications
      WHERE (official_ref IS NOT NULL AND char_length(btrim(official_ref)) = 0) OR (saqa_id IS NOT NULL AND char_length(btrim(saqa_id)) = 0)
    -- C8: an override reason, when given, must be a real reason (10+ characters)
    UNION ALL SELECT 'C8', 'trainprov_programme_versions', 'approval_override_reason shorter than 10 characters', id
      FROM trainprov_programme_versions WHERE approval_override_reason IS NOT NULL AND char_length(btrim(approval_override_reason)) < 10
    UNION ALL SELECT 'C8', 'trainprov_accreditation_events', 'override_reason shorter than 10 characters', id
      FROM trainprov_accreditation_events WHERE override_reason IS NOT NULL AND char_length(btrim(override_reason)) < 10
    -- C9: dates must be in order
    UNION ALL SELECT 'C9', 'trainprov_accreditations', 'submitted_on is after approval_date', id
      FROM trainprov_accreditations WHERE submitted_on IS NOT NULL AND approval_date IS NOT NULL AND submitted_on > approval_date
    UNION ALL SELECT 'C9', 'trainprov_programme_offerings', 'withdrawn before it was offered from', id
      FROM trainprov_programme_offerings WHERE withdrawn_at IS NOT NULL AND withdrawn_at::date < offered_from
    -- C10: states that need a stated reason (the service requires one on each of these moves)
    UNION ALL SELECT 'C10', 'trainprov_accreditations', 'REJECTED, SUSPENDED, WITHDRAWN, MORE_INFORMATION_REQUIRED or EXPIRED without a reason of 10+ characters', id
      FROM trainprov_accreditations
      WHERE status IN ('REJECTED', 'SUSPENDED', 'WITHDRAWN', 'MORE_INFORMATION_REQUIRED', 'EXPIRED') AND COALESCE(char_length(btrim(status_reason)), 0) < 10
    -- C11: references that cross tenants (no CHECK can prevent this; it needs composite foreign keys, which are a decision for a person, see the report)
    UNION ALL SELECT 'C11', 'trainprov_accreditations', 'authority belongs to another tenant', a.id
      FROM trainprov_accreditations a JOIN trainprov_authorities x ON x.id = a.authority_id WHERE x.tenant_id <> a.tenant_id
    UNION ALL SELECT 'C11', 'trainprov_accreditation_scopes', 'accreditation, site, course or programme belongs to another tenant', s.id
      FROM trainprov_accreditation_scopes s
      LEFT JOIN trainprov_accreditations a ON a.id = s.accreditation_id LEFT JOIN trainprov_delivery_sites d ON d.id = s.site_id
      LEFT JOIN trainprov_courses c ON c.id = s.course_id LEFT JOIN trainprov_programmes p ON p.id = s.programme_id
      WHERE a.tenant_id <> s.tenant_id OR d.tenant_id <> s.tenant_id OR c.tenant_id <> s.tenant_id OR p.tenant_id <> s.tenant_id
    UNION ALL SELECT 'C11', 'trainprov_qualifications', 'authority belongs to another tenant', q.id
      FROM trainprov_qualifications q JOIN trainprov_authorities x ON x.id = q.authority_id WHERE x.tenant_id <> q.tenant_id
    UNION ALL SELECT 'C11', 'trainprov_qualification_versions', 'qualification belongs to another tenant', v.id
      FROM trainprov_qualification_versions v JOIN trainprov_qualifications q ON q.id = v.qualification_id WHERE q.tenant_id <> v.tenant_id
    UNION ALL SELECT 'C11', 'trainprov_programmes', 'qualification or course belongs to another tenant', p.id
      FROM trainprov_programmes p LEFT JOIN trainprov_qualifications q ON q.id = p.qualification_id LEFT JOIN trainprov_courses c ON c.id = p.course_id
      WHERE q.tenant_id <> p.tenant_id OR c.tenant_id <> p.tenant_id
    UNION ALL SELECT 'C11', 'trainprov_programme_versions', 'programme or qualification version belongs to another tenant', v.id
      FROM trainprov_programme_versions v JOIN trainprov_programmes p ON p.id = v.programme_id LEFT JOIN trainprov_qualification_versions qv ON qv.id = v.qualification_version_id
      WHERE p.tenant_id <> v.tenant_id OR qv.tenant_id <> v.tenant_id
    UNION ALL SELECT 'C11', 'trainprov_programme_modules', 'programme version belongs to another tenant', m.id
      FROM trainprov_programme_modules m JOIN trainprov_programme_versions v ON v.id = m.programme_version_id WHERE v.tenant_id <> m.tenant_id
    UNION ALL SELECT 'C11', 'trainprov_programme_offerings', 'programme or site belongs to another tenant', o.id
      FROM trainprov_programme_offerings o JOIN trainprov_programmes p ON p.id = o.programme_id JOIN trainprov_delivery_sites d ON d.id = o.site_id
      WHERE p.tenant_id <> o.tenant_id OR d.tenant_id <> o.tenant_id
    UNION ALL SELECT 'C11', 'trainprov_accreditation_events', 'accreditation belongs to another tenant', e.id
      FROM trainprov_accreditation_events e JOIN trainprov_accreditations a ON a.id = e.accreditation_id WHERE a.tenant_id <> e.tenant_id
    -- C12: an approved programme version should say when it takes effect (decision for a person, see the report)
    UNION ALL SELECT 'C12', 'trainprov_programme_versions', 'APPROVED without effective_from', id
      FROM trainprov_programme_versions WHERE status = 'APPROVED' AND effective_from IS NULL
)
SELECT check_id, tbl AS table_name, rule, count(*) AS violations, (array_agg(id ORDER BY id))[1:5] AS sample_ids
FROM checks
GROUP BY check_id, tbl, rule
ORDER BY check_id, tbl, rule;
