-- NUMBERING NOTE: same placeholder caution as every migration this
-- session — confirm the real next version number before running.

-- backlog 1.1 — Change Order migration onto the shared approval engine.
-- No threshold/condition — every submitted CO always needs exactly one
-- PM_APPROVE approval, matching the current, unconditional
-- ChangeOrder.approve() gate exactly.
INSERT INTO approval_rules (id, tenant_id, module, entity_type, name, active, priority,
                            conditions, approval_mode, approver_chain, is_platform_default,
                            created_at, updated_at)
VALUES (
    gen_random_uuid(), NULL, 'projects', 'CHANGE_ORDER', 'Change Order approval (platform default)', true, 100,
    NULL,
    'SEQUENTIAL',
    '[{"type":"ROLE","value":"PM_APPROVE"}]',
    true, now(), now()
);