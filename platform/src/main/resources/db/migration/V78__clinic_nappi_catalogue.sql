-- WHY a separate catalogue table?
-- medication_name VARCHAR(200) on clinic_prescriptions stores free-text.
-- Medical aids don't accept free-text — every medicine needs a 9-digit NAPPI code.
-- This catalogue is the lookup that links free-text search → NAPPI code → billing line.
-- Pre-seeded with ~40 high-frequency SA medicines. Practice can add custom entries.

CREATE TABLE clinic_medication_catalogue (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID REFERENCES tenants(id),     -- NULL = global/system entry
    nappi_code      VARCHAR(20) NOT NULL,
    generic_name    VARCHAR(200) NOT NULL,
    brand_name      VARCHAR(200),
    dosage_form     VARCHAR(50),                     -- TABLET, CAPSULE, SYRUP, INJECTION, etc.
    strength        VARCHAR(50),                     -- e.g. "500mg", "10mg/5ml"
    schedule        INT CHECK (schedule BETWEEN 0 AND 8), -- SA medicine schedule 0-8
    single_exit_price NUMERIC(10,2),                 -- SEP in ZAR (medicine price regulations)
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clinic_med_catalogue_nappi    ON clinic_medication_catalogue(nappi_code);
CREATE INDEX idx_clinic_med_catalogue_generic  ON clinic_medication_catalogue(LOWER(generic_name));
CREATE INDEX idx_clinic_med_catalogue_tenant   ON clinic_medication_catalogue(tenant_id) WHERE tenant_id IS NOT NULL;

-- Add nappi_code and schedule to prescriptions for billing compliance
ALTER TABLE clinic_prescriptions
    ADD COLUMN nappi_code VARCHAR(20),
    ADD COLUMN schedule   INT CHECK (schedule BETWEEN 0 AND 8);

-- Seed common SA medicines (system-wide, tenant_id = NULL)
INSERT INTO clinic_medication_catalogue (nappi_code, generic_name, brand_name, dosage_form, strength, schedule, single_exit_price) VALUES
-- Antibiotics (Schedule 4)
('701408001', 'Amoxicillin',                'Amoxil',          'CAPSULE',   '500mg',      4,  42.50),
('701408002', 'Amoxicillin + Clavulanate',  'Augmentin',       'TABLET',    '875/125mg',  4, 215.00),
('701408003', 'Azithromycin',               'Zithromax',       'TABLET',    '500mg',      4, 180.00),
('701408004', 'Ciprofloxacin',              'Ciprobay',        'TABLET',    '500mg',      4,  95.00),
('701408005', 'Doxycycline',                'Doximal',         'CAPSULE',   '100mg',      4,  38.00),
('701408006', 'Metronidazole',              'Flagyl',          'TABLET',    '400mg',      4,  28.00),
('701408007', 'Trimethoprim + Sulfa',       'Bactrim',         'TABLET',    '160/800mg',  4,  22.00),
-- Analgesics / Anti-inflammatory (Schedule 0-3)
('702100001', 'Paracetamol',                'Panado',          'TABLET',    '500mg',      0,  15.00),
('702100002', 'Ibuprofen',                  'Brufen',          'TABLET',    '400mg',      2,  28.00),
('702100003', 'Diclofenac',                 'Voltaren',        'TABLET',    '50mg',       3,  42.00),
('702100004', 'Tramadol',                   'Tramahexal',      'CAPSULE',   '50mg',       4,  85.00),
('702100005', 'Codeine + Paracetamol',      'Adco-Dol',        'TABLET',    '8/500mg',    2,  32.00),
-- Antihypertensives (Schedule 4)
('703200001', 'Amlodipine',                 'Norvasc',         'TABLET',    '5mg',        4,  68.00),
('703200002', 'Enalapril',                  'Renitec',         'TABLET',    '10mg',       4,  55.00),
('703200003', 'Losartan',                   'Cozaar',          'TABLET',    '50mg',       4, 125.00),
('703200004', 'Hydrochlorothiazide',        'Ridaq',           'TABLET',    '12.5mg',     4,  18.00),
('703200005', 'Bisoprolol',                 'Concor',          'TABLET',    '5mg',        4,  95.00),
-- Diabetes (Schedule 4)
('704100001', 'Metformin',                  'Glucophage',      'TABLET',    '500mg',      4,  38.00),
('704100002', 'Glibenclamide',              'Daonil',          'TABLET',    '5mg',        4,  22.00),
('704100003', 'Sitagliptin',                'Januvia',         'TABLET',    '100mg',      4, 420.00),
-- Respiratory (Schedule 3-4)
('705100001', 'Salbutamol inhaler',         'Ventolin',        'INHALER',   '100mcg',     3,  95.00),
('705100002', 'Beclomethasone inhaler',     'Becloforte',      'INHALER',   '250mcg',     4, 185.00),
('705100003', 'Montelukast',                'Singulair',       'TABLET',    '10mg',       4, 220.00),
('705100004', 'Prednisone',                 'Meticorten',      'TABLET',    '5mg',        4,  18.00),
-- Gastro (Schedule 2-4)
('706100001', 'Omeprazole',                 'Losec',           'CAPSULE',   '20mg',       3,  48.00),
('706100002', 'Pantoprazole',               'Pantoloc',        'TABLET',    '40mg',       4,  88.00),
('706100003', 'Domperidone',                'Motilium',        'TABLET',    '10mg',       2,  32.00),
('706100004', 'Loperamide',                 'Imodium',         'CAPSULE',   '2mg',        2,  28.00),
-- Mental health (Schedule 5-6)
('707100001', 'Sertraline',                 'Zoloft',          'TABLET',    '50mg',       5, 145.00),
('707100002', 'Escitalopram',               'Cipralex',        'TABLET',    '10mg',       5, 185.00),
('707100003', 'Amitriptyline',              'Trepiline',       'TABLET',    '25mg',       5,  28.00),
('707100004', 'Haloperidol',                'Serenase',        'TABLET',    '5mg',        6,  35.00),
-- Cholesterol (Schedule 4)
('708100001', 'Atorvastatin',               'Lipitor',         'TABLET',    '20mg',       4, 125.00),
('708100002', 'Simvastatin',                'Zocor',           'TABLET',    '20mg',       4,  68.00),
-- Topical (Schedule 2-4)
('709100001', 'Betamethasone cream',        'Diprosone',       'CREAM',     '0.05%',      4,  65.00),
('709100002', 'Clotrimazole cream',         'Canesten',        'CREAM',     '1%',         2,  48.00),
('709100003', 'Mupirocin ointment',         'Bactroban',       'OINTMENT',  '2%',         4,  85.00);
