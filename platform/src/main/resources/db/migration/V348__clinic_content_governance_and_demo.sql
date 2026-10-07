-- Clinic question library: content permissions, audit trail, and SYNTHETIC demo groups.
-- Nothing below is clinical guidance. Demo groups are is_demo = TRUE, status DRAFT, and can never be
-- activated (see ContentGovernance); they are served only when handyflow.clinic.question-library.serve-demo=true.

-- ── Permissions: authoring and approval are held by different people, granted explicitly ────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CLINIC_CONTENT_ADMIN',   'Author question groups and send them for clinical review'),
    (gen_random_uuid(), 'CLINIC_CONTENT_APPROVE', 'Review, approve and activate clinical question content (qualified reviewer)')
ON CONFLICT (name) DO NOTHING;
-- Deliberately not granted to any role: the clinic assigns them to named people (DEC-CLINIC-001).

-- ── Audit trail of every status change ───────────────────────────────────────────────────────────────
CREATE TABLE clinic_content_audit (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID        NOT NULL REFERENCES tenants(id),
    group_id       UUID        NOT NULL REFERENCES clinic_question_group(id),
    from_status    VARCHAR(20) NOT NULL,
    to_status      VARCHAR(20) NOT NULL,
    actor_user_id  UUID        REFERENCES users(id),
    note           TEXT,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_content_audit_group ON clinic_content_audit (group_id, created_at);

-- ── Synthetic demo content (platform level) ─────────────────────────────────────────────────────────
INSERT INTO clinic_question_group (id, tenant_id, code, version, name, category, default_enabled, sort_order,
                                   status, clinical_source, is_demo) VALUES
 ('d0000000-0000-0000-0000-000000000001', NULL, 'DEMO_INTAKE',    1, 'DEMO: general intake',        'DEMO', TRUE, 1, 'DRAFT', 'Synthetic demo content, not clinical guidance', TRUE),
 ('d0000000-0000-0000-0000-000000000002', NULL, 'DEMO_FOLLOWUP',  1, 'DEMO: follow-up',             'DEMO', TRUE, 2, 'DRAFT', 'Synthetic demo content, not clinical guidance', TRUE),
 ('d0000000-0000-0000-0000-000000000003', NULL, 'DEMO_ADULT_FEMALE', 1, 'DEMO: sex-specific group', 'DEMO', TRUE, 3, 'DRAFT', 'Synthetic demo content, not clinical guidance', TRUE);
UPDATE clinic_question_group SET applicable_sex = ARRAY['FEMALE'], applicable_min_age_months = 144
 WHERE id = 'd0000000-0000-0000-0000-000000000003';

INSERT INTO clinic_question (id, group_id, code, label, answer_type, options, min_value, max_value, default_visible, default_required, sort_order) VALUES
 ('d1000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'visit_reason', 'DEMO: reason for visit', 'SINGLE_SELECT',
    '[{"value":"ROUTINE","label":"Routine"},{"value":"ILLNESS","label":"Feeling unwell"},{"value":"FOLLOW_UP","label":"Follow-up"}]', NULL, NULL, TRUE, TRUE, 1),
 ('d1000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000001', 'symptom_duration', 'DEMO: how long?', 'DURATION',
    NULL, NULL, NULL, FALSE, FALSE, 2),
 ('d1000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000001', 'discomfort_score', 'DEMO: discomfort (0 to 10)', 'SCALE',
    NULL, 0, 10, FALSE, FALSE, 3),
 ('d1000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000001', 'discomfort_area', 'DEMO: where?', 'BODY',
    NULL, NULL, NULL, FALSE, FALSE, 4),
 ('d1000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000001', 'notes', 'DEMO: notes', 'LONG_TEXT',
    NULL, NULL, NULL, TRUE, FALSE, 5),
 ('d1000000-0000-0000-0000-000000000006', 'd0000000-0000-0000-0000-000000000002', 'improved', 'DEMO: better since last visit?', 'YES_NO',
    NULL, NULL, NULL, TRUE, TRUE, 1),
 ('d1000000-0000-0000-0000-000000000007', 'd0000000-0000-0000-0000-000000000002', 'what_changed', 'DEMO: what changed?', 'TEXT',
    NULL, NULL, NULL, FALSE, FALSE, 2),
 ('d1000000-0000-0000-0000-000000000008', 'd0000000-0000-0000-0000-000000000003', 'demo_checked', 'DEMO: sex-specific item answered?', 'YES_NO',
    NULL, NULL, NULL, TRUE, FALSE, 1);

INSERT INTO clinic_question_rule (question_id, kind, expression, message, target_group_code) VALUES
 ('d1000000-0000-0000-0000-000000000002', 'SHOW_WHEN',     '{"q":"visit_reason","op":"EQ","value":"ILLNESS"}', NULL, NULL),
 ('d1000000-0000-0000-0000-000000000002', 'REQUIRED_WHEN', '{"q":"visit_reason","op":"EQ","value":"ILLNESS"}', NULL, NULL),
 ('d1000000-0000-0000-0000-000000000003', 'SHOW_WHEN',     '{"q":"visit_reason","op":"EQ","value":"ILLNESS"}', NULL, NULL),
 ('d1000000-0000-0000-0000-000000000003', 'WARNING_WHEN',  '{"q":"discomfort_score","op":"GTE","value":8}', 'DEMO ONLY: a high score was entered. Not clinical guidance.', NULL),
 ('d1000000-0000-0000-0000-000000000004', 'SHOW_WHEN',     '{"q":"discomfort_score","op":"GTE","value":1}', NULL, NULL),
 ('d1000000-0000-0000-0000-000000000001', 'TRIGGER_GROUP', '{"q":"visit_reason","op":"EQ","value":"FOLLOW_UP"}', NULL, 'DEMO_FOLLOWUP'),
 ('d1000000-0000-0000-0000-000000000007', 'SHOW_WHEN',     '{"q":"improved","op":"EQ","value":false}', NULL, NULL);

INSERT INTO clinic_red_flag_rule (group_id, code, label, severity, expression, message) VALUES
 ('d0000000-0000-0000-0000-000000000001', 'DEMO_TOP_SCORE', 'DEMO: top score entered', 'URGENT',
    '{"q":"discomfort_score","op":"GTE","value":10}', 'DEMO ONLY: shows how an urgent banner would appear. Not clinical guidance.');

INSERT INTO clinic_visit_type_group (tenant_id, visit_type, group_code, sort_order, required) VALUES
 (NULL, 'CONSULTATION', 'DEMO_INTAKE',       1, TRUE),
 (NULL, 'CONSULTATION', 'DEMO_ADULT_FEMALE', 2, FALSE),
 (NULL, 'FOLLOW_UP',    'DEMO_FOLLOWUP',     1, TRUE);
