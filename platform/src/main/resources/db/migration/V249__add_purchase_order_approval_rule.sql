-- NUMBERING NOTE: same placeholder caution as every migration this
-- session — confirm the real next version number before running.

-- backlog 1.1 — Purchase Order migration onto the shared approval engine.
-- No threshold/condition — every submitted PO always needs exactly one
-- SCM_ADMIN approval, matching the current, unconditional
-- ScPurchaseOrder.approve() gate exactly.
INSERT INTO approval_rules (id, tenant_id, module, entity_type, name, active, priority,
                            conditions, approval_mode, approver_chain, is_platform_default,
                            created_at, updated_at)
VALUES (
    gen_random_uuid(), NULL, 'supplychain', 'PURCHASE_ORDER', 'Purchase Order approval (platform default)', true, 100,
    NULL,
    'SEQUENTIAL',
    '[{"type":"ROLE","value":"SCM_ADMIN"}]',
    true, now(), now()
);