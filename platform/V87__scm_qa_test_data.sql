-- V87 — Supply Chain QA test data (fixed: inventory uses real catalogue_item IDs)
-- RUN:
--   docker cp V87__scm_qa_test_data.sql handyflow-db:/tmp/V87.sql
--   docker exec -i handyflow-db psql -U handyflow -d handyflow -f /tmp/V87.sql

\set tenant '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f'
\set user   '3a41cfaf-333a-4b6f-ad76-b282bcb0e701'

-- Remove Swagger test supplier
DELETE FROM sc_suppliers WHERE tenant_id = :'tenant' AND name = 'string';

DO $$
DECLARE
    v_tenant  UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';
    v_user    UUID := '3a41cfaf-333a-4b6f-ad76-b282bcb0e701';
    v_wh      UUID := 'f6c797f6-c9d0-4686-8558-8ec915a488dc';
    v_site    UUID := '782da420-004d-485b-a951-32b7d3901f4a';
    v_van     UUID := '40564842-543e-4fe3-b7a5-3de19319956f';

    v_bosch   UUID; v_mac UUID; v_engen UUID; v_vaal UUID;
    v_safpro  UUID := gen_random_uuid();
    v_voltex  UUID := gen_random_uuid();
    v_arrow   UUID := gen_random_uuid();

    v_po_draft    UUID := gen_random_uuid();
    v_po_pending  UUID := gen_random_uuid();
    v_po_approved UUID := gen_random_uuid();
    v_po_sent     UUID := gen_random_uuid();
    v_po_partial  UUID := gen_random_uuid();
    v_po_full     UUID := gen_random_uuid();

    v_gr_draft  UUID := gen_random_uuid();
    v_gr_posted UUID := gen_random_uuid();

    v_inv_received UUID := gen_random_uuid();
    v_inv_review   UUID := gen_random_uuid();
    v_inv_approved UUID := gen_random_uuid();
    v_inv_paid     UUID := gen_random_uuid();
    v_inv_disputed UUID := gen_random_uuid();

    -- Real catalogue item IDs — picked from whatever exists in this tenant's catalogue
    v_cat1 UUID; v_cat2 UUID; v_cat3 UUID; v_cat4 UUID; v_cat5 UUID;
    v_cat_count INT;

BEGIN
    -- Resolve existing suppliers
    SELECT id INTO v_bosch FROM sc_suppliers WHERE tenant_id = v_tenant AND name ILIKE '%Bosch%'    LIMIT 1;
    SELECT id INTO v_mac   FROM sc_suppliers WHERE tenant_id = v_tenant AND name ILIKE '%Macsteel%' LIMIT 1;
    SELECT id INTO v_engen FROM sc_suppliers WHERE tenant_id = v_tenant AND name ILIKE '%Engen%'    LIMIT 1;
    SELECT id INTO v_vaal  FROM sc_suppliers WHERE tenant_id = v_tenant AND name ILIKE '%Vaal%'     LIMIT 1;

    IF v_bosch IS NULL THEN
        RAISE NOTICE 'Bosch supplier not found — seed suppliers first via the UI or V86';
        RETURN;
    END IF;

    -- ── New suppliers ─────────────────────────────────────────────────────────
    INSERT INTO sc_suppliers (id, tenant_id, name, registration_number, vat_number,
        bbbee_level, contact_name, contact_email, contact_phone, city, province,
        bank_name, bank_account, bank_branch_code, payment_terms_days, currency,
        total_orders, on_time_deliveries, late_deliveries, status, notes,
        created_by, created_at, updated_at)
    VALUES
    (v_safpro, v_tenant, 'SafPro Industrial Supplies', '2010/044321/07', '4890012345',
        1, 'Nomvula Sithole', 'nomvula@safpro.co.za', '+27 11 765 4321', 'Johannesburg', 'Gauteng',
        'Standard Bank', '011234567890', '051001', 30, 'ZAR', 24, 22, 2,
        'ACTIVE', 'PPE and safety equipment', v_user, NOW(), NOW()),
    (v_voltex, v_tenant, 'Voltex Electrical', '1998/002341/07', '4100098765',
        3, 'Rudi Grobler', 'rudi.grobler@voltex.co.za', '+27 11 490 6000', 'Johannesburg', 'Gauteng',
        null, null, null, 60, 'ZAR', 8, 5, 3,
        'INACTIVE', 'Contract ended June 2026 — do not order', v_user, NOW(), NOW()),
    (v_arrow, v_tenant, 'Arrow Fasteners & Hardware', '2018/109876/07',
        null, null, 'Calvin Pretorius', 'calvin@arrowfast.co.za', '+27 12 345 6789', 'Pretoria', 'Gauteng',
        null, null, null, 14, 'ZAR', 15, 8, 7,
        'ACTIVE', 'Bolts, nuts, anchors — 14-day terms', v_user, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

    -- ── Purchase Orders ───────────────────────────────────────────────────────
    -- DRAFT
    INSERT INTO sc_purchase_orders (id, tenant_id, order_number, supplier_id, supplier_name,
        deliver_to_location, order_date, required_by_date, currency, exchange_rate,
        subtotal, vat_amount, total_amount, status, notes,
        created_by, created_by_name, created_at, updated_at)
    VALUES (v_po_draft, v_tenant, 'PO-00010', v_engen, 'Engen Petroleum',
        v_wh, CURRENT_DATE, CURRENT_DATE+14, 'ZAR', 1.000000,
        0.00, 0.00, 0.00, 'DRAFT', 'Monthly diesel replenishment',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '2 hours', NOW())
    ON CONFLICT (id) DO NOTHING;

    -- PENDING_APPROVAL (with lines)
    INSERT INTO sc_purchase_orders (id, tenant_id, order_number, supplier_id, supplier_name,
        deliver_to_location, order_date, required_by_date, currency, exchange_rate,
        subtotal, vat_amount, total_amount, status, project_ref, notes,
        created_by, created_by_name, created_at, updated_at)
    VALUES (v_po_pending, v_tenant, 'PO-00011', v_mac, 'Macsteel Service Centres',
        v_site, CURRENT_DATE-1, CURRENT_DATE+21, 'ZAR', 1.000000,
        45600.00, 6840.00, 52440.00, 'PENDING_APPROVAL', 'BRIDGE-EXT-2026',
        'Steel for Midrand bridge extension',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '1 day', NOW())
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO sc_po_lines (id, purchase_order_id, tenant_id, item_name, supplier_sku,
        qty_ordered, qty_received, qty_invoiced, unit_cost, vat_rate, vat_amount,
        line_total, line_total_incl, is_fully_received)
    VALUES
    (gen_random_uuid(), v_po_pending, v_tenant, '76x76x6mm Angle Iron 6m', 'MS-AI-766',
        20.000, 0.000, 0.000, 1140.00, 15.00, 3420.00, 22800.00, 26220.00, false),
    (gen_random_uuid(), v_po_pending, v_tenant, '219x8mm CHS Round Tube 6m', 'MS-CHS-219',
        10.000, 0.000, 0.000, 2280.00, 15.00, 3420.00, 22800.00, 26220.00, false)
    ON CONFLICT DO NOTHING;

    -- APPROVED (with lines)
    INSERT INTO sc_purchase_orders (id, tenant_id, order_number, supplier_id, supplier_name,
        deliver_to_location, order_date, required_by_date, currency, exchange_rate,
        subtotal, vat_amount, total_amount, status, project_ref,
        approved_by, approved_by_name, approved_at,
        created_by, created_by_name, created_at, updated_at)
    VALUES (v_po_approved, v_tenant, 'PO-00012', v_bosch, 'Bosch Power Tools SA',
        v_wh, CURRENT_DATE-3, CURRENT_DATE+10, 'ZAR', 1.000000,
        26100.00, 3915.00, 30015.00, 'APPROVED', 'WORKSHOP-RESTOCK-Q3',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '4 hours',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '3 days', NOW())
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO sc_po_lines (id, purchase_order_id, tenant_id, item_name, supplier_sku,
        qty_ordered, qty_received, qty_invoiced, unit_cost, vat_rate, vat_amount,
        line_total, line_total_incl, is_fully_received)
    VALUES
    (gen_random_uuid(), v_po_approved, v_tenant, 'Bosch GSB 18V-55 Combi Drill', 'BOS-GSB18V55',
        10.000, 0.000, 0.000, 1850.00, 15.00, 2775.00, 18500.00, 21275.00, false),
    (gen_random_uuid(), v_po_approved, v_tenant, 'Bosch GWS 9-115 Angle Grinder 5-pack', 'BOS-GWS9115',
        5.000, 0.000, 0.000, 1320.00, 15.00, 990.00, 6600.00, 7590.00, false),
    (gen_random_uuid(), v_po_approved, v_tenant, 'Bosch 125mm Diamond Blade (10-pack)', 'BOS-DB125-10',
        5.000, 0.000, 0.000, 200.00, 15.00, 150.00, 1000.00, 1150.00, false)
    ON CONFLICT DO NOTHING;

    -- SENT (with lines)
    INSERT INTO sc_purchase_orders (id, tenant_id, order_number, supplier_id, supplier_name,
        deliver_to_location, order_date, required_by_date, currency, exchange_rate,
        subtotal, vat_amount, total_amount, status, project_ref, notes,
        approved_by, approved_by_name, approved_at, sent_at,
        created_by, created_by_name, created_at, updated_at)
    VALUES (v_po_sent, v_tenant, 'PO-00013', v_vaal, 'Vaal Rubber and Industrial',
        v_wh, CURRENT_DATE-7, CURRENT_DATE+7, 'ZAR', 1.000000,
        8700.00, 1305.00, 10005.00, 'SENT', 'PLANT-MAINTENANCE-JUN',
        'Conveyor belt replacement — urgent',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '6 days', NOW()-INTERVAL '5 days',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '7 days', NOW())
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO sc_po_lines (id, purchase_order_id, tenant_id, item_name, supplier_sku,
        qty_ordered, qty_received, qty_invoiced, unit_cost, vat_rate, vat_amount,
        line_total, line_total_incl, is_fully_received)
    VALUES
    (gen_random_uuid(), v_po_sent, v_tenant, 'Conveyor Belt 3m x 500mm', 'VRI-CB-3X500',
        3.000, 0.000, 0.000, 1800.00, 15.00, 810.00, 5400.00, 6210.00, false),
    (gen_random_uuid(), v_po_sent, v_tenant, 'O-Ring Kit 200-piece assorted', 'VRI-OR-200',
        5.000, 0.000, 0.000, 540.00, 15.00, 405.00, 2700.00, 3105.00, false),
    (gen_random_uuid(), v_po_sent, v_tenant, 'Hydraulic Hose 10m DN12', 'VRI-HH-10M',
        3.000, 0.000, 0.000, 200.00, 15.00, 90.00, 600.00, 690.00, false)
    ON CONFLICT DO NOTHING;

    -- PARTIALLY_RECEIVED
    INSERT INTO sc_purchase_orders (id, tenant_id, order_number, supplier_id, supplier_name,
        deliver_to_location, order_date, required_by_date, currency, exchange_rate,
        subtotal, vat_amount, total_amount, status, project_ref,
        approved_by, approved_by_name, approved_at, sent_at,
        created_by, created_by_name, created_at, updated_at)
    VALUES (v_po_partial, v_tenant, 'PO-00014', v_safpro, 'SafPro Industrial Supplies',
        v_wh, CURRENT_DATE-14, CURRENT_DATE-2, 'ZAR', 1.000000,
        12450.00, 1867.50, 14317.50, 'PARTIALLY_RECEIVED', 'SITE-SAFETY-JUN',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '13 days', NOW()-INTERVAL '12 days',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '14 days', NOW())
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO sc_po_lines (id, purchase_order_id, tenant_id, item_name, supplier_sku,
        qty_ordered, qty_received, qty_invoiced, unit_cost, vat_rate, vat_amount,
        line_total, line_total_incl, is_fully_received)
    VALUES
    (gen_random_uuid(), v_po_partial, v_tenant, 'Hard Hat SABS Approved (dozen)', 'SAF-HH-12',
        10.000, 10.000, 10.000, 450.00, 15.00, 675.00, 4500.00, 5175.00, true),
    (gen_random_uuid(), v_po_partial, v_tenant, 'Safety Harness Class A', 'SAF-HA-A',
        20.000, 20.000, 0.000, 295.00, 15.00, 885.00, 5900.00, 6785.00, true),
    (gen_random_uuid(), v_po_partial, v_tenant, 'Steel-Toe Boots Size 8-12 assorted', 'SAF-STB-ASS',
        15.000, 0.000, 0.000, 136.67, 15.00, 307.50, 2050.00, 2357.50, false)
    ON CONFLICT DO NOTHING;

    -- FULLY_RECEIVED
    INSERT INTO sc_purchase_orders (id, tenant_id, order_number, supplier_id, supplier_name,
        deliver_to_location, order_date, currency, exchange_rate,
        subtotal, vat_amount, total_amount, status, project_ref,
        approved_by, approved_by_name, approved_at, sent_at,
        created_by, created_by_name, created_at, updated_at)
    VALUES (v_po_full, v_tenant, 'PO-00015', v_bosch, 'Bosch Power Tools SA',
        v_wh, CURRENT_DATE-30, 'ZAR', 1.000000,
        26100.00, 3915.00, 30015.00, 'FULLY_RECEIVED', 'WORKSHOP-Q2',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '29 days', NOW()-INTERVAL '28 days',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '30 days', NOW())
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO sc_po_lines (id, purchase_order_id, tenant_id, item_name, supplier_sku,
        qty_ordered, qty_received, qty_invoiced, unit_cost, vat_rate, vat_amount,
        line_total, line_total_incl, is_fully_received)
    VALUES
    (gen_random_uuid(), v_po_full, v_tenant, 'Bosch GSB 18V-55 Combi Drill', 'BOS-GSB18V55',
        10.000, 10.000, 10.000, 1850.00, 15.00, 2775.00, 18500.00, 21275.00, true),
    (gen_random_uuid(), v_po_full, v_tenant, 'Bosch GWS 9-115 Angle Grinder 5-pack', 'BOS-GWS9115',
        5.000, 5.000, 5.000, 1320.00, 15.00, 990.00, 6600.00, 7590.00, true),
    (gen_random_uuid(), v_po_full, v_tenant, 'Bosch 125mm Diamond Blade (10-pack)', 'BOS-DB125-10',
        5.000, 5.000, 5.000, 200.00, 15.00, 150.00, 1000.00, 1150.00, true)
    ON CONFLICT DO NOTHING;

    -- ── Goods Receipts ────────────────────────────────────────────────────────
    INSERT INTO sc_goods_receipts (id, tenant_id, receipt_number, purchase_order_id,
        supplier_id, received_to, delivery_note_ref,
        received_by, received_by_name, received_date, status, created_at, updated_at)
    VALUES (v_gr_draft, v_tenant, 'GR-00003', v_po_sent,
        v_vaal, v_wh, 'VRI-DN-7734',
        v_user, 'Thabo Molefe', CURRENT_DATE, 'DRAFT', NOW()-INTERVAL '30 minutes', NOW())
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO sc_goods_receipts (id, tenant_id, receipt_number, purchase_order_id,
        supplier_id, received_to, delivery_note_ref,
        received_by, received_by_name, received_date, status, posted_at, created_at, updated_at)
    VALUES (v_gr_posted, v_tenant, 'GR-00004', v_po_full,
        v_bosch, v_wh, 'BOSCH-DN-9981',
        v_user, 'Thabo Molefe', CURRENT_DATE-25, 'POSTED',
        NOW()-INTERVAL '25 days', NOW()-INTERVAL '25 days', NOW())
    ON CONFLICT (id) DO NOTHING;

    -- ── Inventory — use REAL catalogue_item IDs from this tenant ─────────────
    -- Pick up to 5 real items; fall back gracefully if fewer exist
    SELECT COUNT(*) INTO v_cat_count
    FROM catalogue_items
    WHERE (tenant_id = v_tenant OR tenant_id IS NULL);

    IF v_cat_count = 0 THEN
        RAISE NOTICE 'No catalogue items found — skipping inventory inserts. Add items via Catalogue module first.';
    ELSE
        -- Pick real IDs in order
        SELECT id INTO v_cat1 FROM catalogue_items WHERE (tenant_id = v_tenant OR tenant_id IS NULL) ORDER BY name LIMIT 1 OFFSET 0;
        SELECT id INTO v_cat2 FROM catalogue_items WHERE (tenant_id = v_tenant OR tenant_id IS NULL) ORDER BY name LIMIT 1 OFFSET 1;
        SELECT id INTO v_cat3 FROM catalogue_items WHERE (tenant_id = v_tenant OR tenant_id IS NULL) ORDER BY name LIMIT 1 OFFSET 2;
        SELECT id INTO v_cat4 FROM catalogue_items WHERE (tenant_id = v_tenant OR tenant_id IS NULL) ORDER BY name LIMIT 1 OFFSET 3;
        SELECT id INTO v_cat5 FROM catalogue_items WHERE (tenant_id = v_tenant OR tenant_id IS NULL) ORDER BY name LIMIT 1 OFFSET 4;

        -- Use v_cat1 as fallback for any nulls (when fewer than 5 items exist)
        v_cat2 := COALESCE(v_cat2, v_cat1);
        v_cat3 := COALESCE(v_cat3, v_cat1);
        v_cat4 := COALESCE(v_cat4, v_cat1);
        v_cat5 := COALESCE(v_cat5, v_cat1);

        -- Item 1: Warehouse, adequate stock (qty > reorder_point)
        INSERT INTO sc_inventory (id, tenant_id, location_id, catalogue_item_id,
            qty_on_hand, qty_reserved, qty_in_transit, reorder_point, reorder_qty,
            avg_cost, last_cost, bin_location, expiry_tracking, lot_tracking, created_at, updated_at)
        VALUES (gen_random_uuid(), v_tenant, v_wh, v_cat1,
            12.000, 2.000, 0.000, 5.000, 10.000,
            1850.00, 1850.00, 'A1-S1', false, false, NOW()-INTERVAL '25 days', NOW())
        ON CONFLICT DO NOTHING;

        -- Item 2: Site, LOW STOCK (qty_on_hand=3 <= reorder_point=5)
        INSERT INTO sc_inventory (id, tenant_id, location_id, catalogue_item_id,
            qty_on_hand, qty_reserved, qty_in_transit, reorder_point, reorder_qty,
            avg_cost, last_cost, bin_location, expiry_tracking, lot_tracking, created_at, updated_at)
        VALUES (gen_random_uuid(), v_tenant, v_site, v_cat2,
            3.000, 0.000, 20.000, 5.000, 20.000,
            1140.00, 1140.00, 'SITE-YARD', false, false, NOW()-INTERVAL '10 days', NOW())
        ON CONFLICT DO NOTHING;

        -- Item 3: Warehouse, adequate
        INSERT INTO sc_inventory (id, tenant_id, location_id, catalogue_item_id,
            qty_on_hand, qty_reserved, qty_in_transit, reorder_point, reorder_qty,
            avg_cost, last_cost, bin_location, expiry_tracking, lot_tracking, created_at, updated_at)
        VALUES (gen_random_uuid(), v_tenant, v_wh, v_cat3,
            350.000, 0.000, 0.000, 100.000, 500.000,
            22.50, 22.50, 'FUEL-TANK-1', false, false, NOW()-INTERVAL '5 days', NOW())
        ON CONFLICT DO NOTHING;

        -- Item 4: Warehouse, LOW STOCK (qty=4 <= reorder_point=10, all reserved)
        INSERT INTO sc_inventory (id, tenant_id, location_id, catalogue_item_id,
            qty_on_hand, qty_reserved, qty_in_transit, reorder_point, reorder_qty,
            avg_cost, last_cost, bin_location, expiry_tracking, lot_tracking, created_at, updated_at)
        VALUES (gen_random_uuid(), v_tenant, v_wh, v_cat4,
            4.000, 4.000, 0.000, 10.000, 20.000,
            295.00, 295.00, 'B2-S3', false, false, NOW()-INTERVAL '12 days', NOW())
        ON CONFLICT DO NOTHING;

        -- Item 5: Service Van, low qty but above reorder
        INSERT INTO sc_inventory (id, tenant_id, location_id, catalogue_item_id,
            qty_on_hand, qty_reserved, qty_in_transit, reorder_point, reorder_qty,
            avg_cost, last_cost, bin_location, expiry_tracking, lot_tracking, created_at, updated_at)
        VALUES (gen_random_uuid(), v_tenant, v_van, v_cat5,
            2.000, 0.000, 5.000, 1.000, 5.000,
            540.00, 540.00, 'VAN-REAR', false, false, NOW()-INTERVAL '2 days', NOW())
        ON CONFLICT DO NOTHING;

        RAISE NOTICE 'Inventory: 5 items inserted using catalogue IDs: %, %, %, %, %',
            v_cat1, v_cat2, v_cat3, v_cat4, v_cat5;
    END IF;

    -- ── Supplier Invoices — all statuses + all 3-way match states ─────────────

    -- RECEIVED + NO_PO
    INSERT INTO sc_supplier_invoices (id, tenant_id, invoice_number, supplier_invoice_ref,
        supplier_id, purchase_order_id, goods_receipt_id,
        invoice_date, due_date, received_date, currency,
        subtotal, vat_amount, total_amount, match_status, match_notes, status, notes,
        created_at, updated_at)
    VALUES (v_inv_received, v_tenant, 'SINV-00010', 'ARROW-INV-2026-441',
        v_arrow, null, null,
        CURRENT_DATE-2, CURRENT_DATE+12, CURRENT_DATE-2, 'ZAR',
        4347.83, 652.17, 5000.00,
        'PENDING', 'No linked purchase order — verify before approving', 'RECEIVED',
        'Arrow Fasteners — bolts and anchor sets', NOW()-INTERVAL '2 days', NOW())
    ON CONFLICT (id) DO NOTHING;

    -- UNDER_REVIEW + PARTIAL_MATCH
    INSERT INTO sc_supplier_invoices (id, tenant_id, invoice_number, supplier_invoice_ref,
        supplier_id, purchase_order_id, goods_receipt_id,
        invoice_date, due_date, received_date, currency,
        subtotal, vat_amount, total_amount, match_status, match_notes, status, notes,
        created_at, updated_at)
    VALUES (v_inv_review, v_tenant, 'SINV-00011', 'VRI-INV-2026-1103',
        v_vaal, v_po_sent, null,
        CURRENT_DATE-1, CURRENT_DATE+59, CURRENT_DATE-1, 'ZAR',
        8700.00, 1305.00, 10005.00,
        'PENDING', 'PO found but GR not yet posted — hold until goods received',
        'UNDER_REVIEW', 'Vaal Rubber — awaiting GR confirmation',
        NOW()-INTERVAL '1 day', NOW())
    ON CONFLICT (id) DO NOTHING;

    -- APPROVED + PO_MATCHED
    INSERT INTO sc_supplier_invoices (id, tenant_id, invoice_number, supplier_invoice_ref,
        supplier_id, purchase_order_id, goods_receipt_id,
        invoice_date, due_date, received_date, currency,
        subtotal, vat_amount, total_amount, match_status, status,
        approved_by, approved_by_name, approved_at, notes,
        created_at, updated_at)
    VALUES (v_inv_approved, v_tenant, 'SINV-00012', 'SAF-INV-2026-887',
        v_safpro, v_po_partial, null,
        CURRENT_DATE-5, CURRENT_DATE+25, CURRENT_DATE-5, 'ZAR',
        4782.61, 717.39, 5500.00,
        'PENDING', 'APPROVED',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '2 days',
        'SafPro partial delivery — hard hats and harnesses only',
        NOW()-INTERVAL '5 days', NOW())
    ON CONFLICT (id) DO NOTHING;

    -- PAID + MATCHED (full 3-way)
    INSERT INTO sc_supplier_invoices (id, tenant_id, invoice_number, supplier_invoice_ref,
        supplier_id, purchase_order_id, goods_receipt_id,
        invoice_date, due_date, received_date, currency,
        subtotal, vat_amount, total_amount, match_status, status,
        approved_by, approved_by_name, approved_at,
        paid_at, payment_reference, notes,
        created_at, updated_at)
    VALUES (v_inv_paid, v_tenant, 'SINV-00013', 'BOSCH-INV-2026-4521',
        v_bosch, v_po_full, v_gr_posted,
        CURRENT_DATE-22, CURRENT_DATE-7, CURRENT_DATE-22, 'ZAR',
        22695.65, 3404.35, 26100.00,
        'MATCHED', 'PAID',
        v_user, 'Thabo Molefe', NOW()-INTERVAL '20 days',
        NOW()-INTERVAL '7 days', 'EFT-2026-06-16-001',
        'Bosch Q2 workshop restock — fully matched',
        NOW()-INTERVAL '22 days', NOW())
    ON CONFLICT (id) DO NOTHING;

    -- DISPUTED
    INSERT INTO sc_supplier_invoices (id, tenant_id, invoice_number, supplier_invoice_ref,
        supplier_id, purchase_order_id, goods_receipt_id,
        invoice_date, due_date, received_date, currency,
        subtotal, vat_amount, total_amount, match_status, match_notes, status, notes,
        created_at, updated_at)
    VALUES (v_inv_disputed, v_tenant, 'SINV-00014', 'MAC-INV-78432',
        v_mac, v_po_pending, null,
        CURRENT_DATE-3, CURRENT_DATE+27, CURRENT_DATE-3, 'ZAR',
        48260.87, 7239.13, 55500.00,
        'PENDING', 'Invoice R55 500 exceeds approved PO R52 440 — queried with supplier',
        'DISPUTED',
        'Macsteel price discrepancy — awaiting credit note',
        NOW()-INTERVAL '3 days', NOW())
    ON CONFLICT (id) DO NOTHING;

    -- Update supplier stats
    UPDATE sc_suppliers SET total_orders = 1, on_time_deliveries = 1 WHERE id = v_bosch;
    UPDATE sc_suppliers SET total_orders = 1 WHERE id = v_mac;

    RAISE NOTICE '✓ V87 complete';
    RAISE NOTICE '  Suppliers: Bosch, Macsteel, Engen, Vaal + SafPro, Voltex(INACTIVE), Arrow';
    RAISE NOTICE '  POs: DRAFT(no lines), PENDING, APPROVED, SENT, PARTIALLY_RECEIVED, FULLY_RECEIVED';
    RAISE NOTICE '  Invoices: RECEIVED/NO_PO, UNDER_REVIEW/PARTIAL, APPROVED/PO_MATCHED, PAID/MATCHED, DISPUTED';

END $$;