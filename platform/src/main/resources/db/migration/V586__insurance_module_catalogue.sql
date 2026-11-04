-- Patch 0574: the two Insurance modules never had a module_catalogue row (V267 and V268 left it as a comment), so no tenant could
-- be given either one from the catalogue. Their permissions were seeded by V269 and are not repeated here.
--
-- PRICE IS NOT DECIDED. Both rows go in at R0.00 and INACTIVE (is_active = false, active = false) so neither can be sold or
-- shown as a purchasable module until the names and prices are confirmed. To launch: UPDATE module_catalogue SET monthly_price = ...,
-- name = ..., is_active = true, active = true WHERE key IN ('insurance', 'insurancebrokerage'). Tenants that already hold either
-- module through their plan or tenant_modules are not affected by the catalogue row.

INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order, is_active, active)
VALUES
    ('insurance', 'Insurance', 'Your own insurance policies on business assets, with renewal tracking',
     0.00, 'ShieldCheck', 'Professional Services', 710, false, false),
    ('insurancebrokerage', 'Insurance Brokerage', 'Clients, insurers, policy placement and commission for insurance brokers',
     0.00, 'Handshake', 'Professional Services', 720, false, false)
ON CONFLICT (key) DO NOTHING;
