-- V90 — Project Management QA test data (full — covers every tab)
-- docker cp V90__pm_test_data.sql handyflow-db:/tmp/V90.sql
-- docker exec -i handyflow-db psql -U handyflow -d handyflow -f /tmp/V90.sql

DO $$
DECLARE
  v_tenant UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';
  v_user   UUID := '3a41cfaf-333a-4b6f-ad76-b282bcb0e701';
  p1 UUID; p2 UUID; p3 UUID; p4 UUID; p5 UUID;
  ph1 UUID; ph2 UUID; ph3 UUID;
  t1 UUID; t2 UUID; t3 UUID; t4 UUID; t5 UUID; t6 UUID; t7 UUID;
BEGIN

-- ── PROJECTS ────────────────────────────────────────────────────────────────

p1 := gen_random_uuid();
p2 := gen_random_uuid();
p3 := gen_random_uuid();
p4 := gen_random_uuid();
p5 := gen_random_uuid();

-- P1: N2 Bridge — ACTIVE, RED (over budget, late, full data on every tab)
INSERT INTO projects VALUES (p1,v_tenant,'PRJ-0001','N2 Bridge Extension — Midrand',
  'Reinforced concrete bridge extension over the Jukskei River on the N2 freeway.',
  'CONSTRUCTION','ACTIVE','RED','SANRAL (South African National Roads Agency)',
  'N2 Freeway KM 14.2, Midrand, Gauteng',
  CURRENT_DATE-120,CURRENT_DATE+60,CURRENT_DATE-120,CURRENT_DATE+30,
  8500000,6200000,1800000,9200000,'SANRAL-2026-N2-EXT-001','FIXED_PRICE',5.00,
  '7CE','NHBRC-2026-44521',v_user,'Thabo Molefe',
  replace(gen_random_uuid()::text,'-',''),null,v_user,
  NOW()-INTERVAL '120 days',NOW(),null,null,null,null);

-- P2: Midrand Logistics — ACTIVE, AMBER
INSERT INTO projects VALUES (p2,v_tenant,'PRJ-0002','Midrand Logistics Park — Site Preparation',
  'Bulk earthworks, drainage and access roads for 45 ha logistics park.',
  'EARTHMOVING','ACTIVE','AMBER','Midrand Logistics Developers (Pty) Ltd',
  'Erf 445 Halfway House, Midrand',
  CURRENT_DATE-45,CURRENT_DATE+90,CURRENT_DATE-45,CURRENT_DATE+90,
  3200000,980000,420000,3500000,'MLD-2026-SITE-001','TIME_AND_MATERIAL',0.00,
  null,null,v_user,'Thabo Molefe',
  replace(gen_random_uuid()::text,'-',''),null,v_user,
  NOW()-INTERVAL '45 days',NOW(),null,null,null,null);

-- P3: Security Upgrade — ACTIVE, GREEN
INSERT INTO projects VALUES (p3,v_tenant,'PRJ-0003','Germiston Industrial Park — Security Upgrade',
  'CCTV, access control and perimeter fencing for 12-unit industrial park.',
  'SECURITY','ACTIVE','GREEN','Germiston Industrial Holdings',
  '14 Industry Road, Germiston',
  CURRENT_DATE-20,CURRENT_DATE+40,CURRENT_DATE-20,CURRENT_DATE+40,
  480000,95000,120000,520000,'GIH-SEC-2026-003','FIXED_PRICE',0.00,
  null,null,v_user,'Thabo Molefe',
  replace(gen_random_uuid()::text,'-',''),null,v_user,
  NOW()-INTERVAL '20 days',NOW(),null,null,null,null);

-- P4: Planning
INSERT INTO projects VALUES (p4,v_tenant,'PRJ-0004','Sandton Corporate HQ Fitout',
  'Full interior fitout of 4 floors — boardrooms, open-plan, server room.',
  'GENERAL','PLANNING','GREEN','Apex Holdings (Pty) Ltd',null,
  CURRENT_DATE+30,CURRENT_DATE+150,CURRENT_DATE+30,CURRENT_DATE+150,
  2100000,0,0,null,null,'FIXED_PRICE',0.00,
  null,null,v_user,'Thabo Molefe',
  replace(gen_random_uuid()::text,'-',''),null,v_user,
  NOW()-INTERVAL '5 days',NOW(),null,null,null,null);

-- P5: Completed
INSERT INTO projects VALUES (p5,v_tenant,'PRJ-0005','Boksburg Warehouse Extension',
  'Steel structure warehouse extension 1200m².',
  'CONSTRUCTION','COMPLETED','GREEN','Boksburg Logistics (Pty) Ltd',null,
  CURRENT_DATE-180,CURRENT_DATE-30,CURRENT_DATE-180,CURRENT_DATE-30,
  1850000,1820000,0,null,null,'FIXED_PRICE',5.00,
  null,null,v_user,'Thabo Molefe',
  replace(gen_random_uuid()::text,'-',''),null,v_user,
  NOW()-INTERVAL '180 days',NOW()-INTERVAL '30 days',
  NOW()-INTERVAL '30 days',null,null,null);

-- ── PHASES for P1 ────────────────────────────────────────────────────────────

ph1 := gen_random_uuid(); ph2 := gen_random_uuid(); ph3 := gen_random_uuid();

INSERT INTO project_phases VALUES
  (ph1,v_tenant,p1,'Phase 1 — Demolition & Excavation','Remove existing structure and excavate for footings',1,'COMPLETED',   CURRENT_DATE-120,CURRENT_DATE-80, NOW()-INTERVAL '120 days'),
  (ph2,v_tenant,p1,'Phase 2 — Foundations & Piling',   'Pile driving and reinforced concrete footings',       2,'IN_PROGRESS', CURRENT_DATE-80, CURRENT_DATE+10, NOW()-INTERVAL '80 days'),
  (ph3,v_tenant,p1,'Phase 3 — Superstructure',         'Formwork, rebar, concrete pour and curing',           3,'NOT_STARTED', CURRENT_DATE+10, CURRENT_DATE+60, NOW()-INTERVAL '80 days');

-- ── TASKS for P1 (7 tasks across all statuses + phases) ──────────────────────

t1:=gen_random_uuid(); t2:=gen_random_uuid(); t3:=gen_random_uuid(); t4:=gen_random_uuid();
t5:=gen_random_uuid(); t6:=gen_random_uuid(); t7:=gen_random_uuid();

INSERT INTO project_tasks VALUES
  (t1,v_tenant,p1,ph1,null,'T-001','Site Clearance & Demolition','Milestone','COMPLETED','HIGH',v_user,'Sipho Nkosi',CURRENT_DATE-120,CURRENT_DATE-110,CURRENT_DATE-120,CURRENT_DATE-108,100,80,88,true,true,180000,192000,true,null,1,v_user,NOW()-INTERVAL '120 days',NOW()),
  (t2,v_tenant,p1,ph1,null,'T-002','Bulk Excavation to Formation Level','TASK','COMPLETED','HIGH',v_user,'Pieter van Zyl',CURRENT_DATE-110,CURRENT_DATE-85,CURRENT_DATE-110,CURRENT_DATE-83,100,240,262,true,false,380000,408000,false,null,2,v_user,NOW()-INTERVAL '110 days',NOW()),
  (t3,v_tenant,p1,ph2,null,'T-003','Pile Driving — 24×600mm Piles','TASK','IN_PROGRESS','CRITICAL',v_user,'Hendrik Botha',CURRENT_DATE-80,CURRENT_DATE-20,CURRENT_DATE-80,null,65,320,210,true,false,680000,442000,true,null,3,v_user,NOW()-INTERVAL '80 days',NOW()),
  (t4,v_tenant,p1,ph2,null,'T-004','Pile Caps and Ground Beams','TASK','IN_PROGRESS','HIGH',v_user,'Zanele Dlamini',CURRENT_DATE-30,CURRENT_DATE+10,CURRENT_DATE-28,null,40,160,65,false,false,290000,116000,false,null,4,v_user,NOW()-INTERVAL '30 days',NOW()),
  (t5,v_tenant,p1,ph2,null,'T-005','Phase 2 Inspection Sign-off','MILESTONE','NOT_STARTED','CRITICAL',null,null,CURRENT_DATE+10,CURRENT_DATE+10,null,null,0,4,0,true,true,0,0,true,null,5,v_user,NOW()-INTERVAL '30 days',NOW()),
  (t6,v_tenant,p1,ph3,null,'T-006','Formwork and Falsework Erection','TASK','NOT_STARTED','HIGH',null,null,CURRENT_DATE+11,CURRENT_DATE+30,null,null,0,180,0,true,false,320000,0,false,null,6,v_user,NOW()-INTERVAL '10 days',NOW()),
  (t7,v_tenant,p1,ph2,null,'T-007','Reinforcement — Main Deck Rebar','TASK','BLOCKED','CRITICAL',v_user,'Francois Du Plessis',CURRENT_DATE-10,CURRENT_DATE+5,CURRENT_DATE-10,null,20,200,40,true,false,580000,116000,false,null,7,v_user,NOW()-INTERVAL '10 days',NOW());

-- Tasks for P2
INSERT INTO project_tasks VALUES
  (gen_random_uuid(),v_tenant,p2,null,null,'T-001','Strip Topsoil and Stockpile','TASK','COMPLETED','HIGH',v_user,'Riaan Pretorius',CURRENT_DATE-45,CURRENT_DATE-30,CURRENT_DATE-45,CURRENT_DATE-28,100,120,128,false,false,180000,192000,false,null,1,v_user,NOW()-INTERVAL '45 days',NOW()),
  (gen_random_uuid(),v_tenant,p2,null,null,'T-002','Bulk Cut and Fill to Design Levels','TASK','IN_PROGRESS','HIGH',v_user,'Sipho Dlamini',CURRENT_DATE-30,CURRENT_DATE+30,CURRENT_DATE-29,null,45,360,162,true,false,920000,414000,false,null,2,v_user,NOW()-INTERVAL '30 days',NOW()),
  (gen_random_uuid(),v_tenant,p2,null,null,'T-003','Stormwater Drainage Installation','TASK','NOT_STARTED','MEDIUM',null,null,CURRENT_DATE+30,CURRENT_DATE+75,null,null,0,200,0,false,false,480000,0,false,null,3,v_user,NOW()-INTERVAL '5 days',NOW());

-- Tasks for P3
INSERT INTO project_tasks VALUES
  (gen_random_uuid(),v_tenant,p3,null,null,'T-001','Cable Routing and Conduit','TASK','IN_PROGRESS','HIGH',v_user,'Themba Khumalo',CURRENT_DATE-20,CURRENT_DATE+5,CURRENT_DATE-20,null,60,80,48,true,false,95000,57000,false,null,1,v_user,NOW()-INTERVAL '20 days',NOW()),
  (gen_random_uuid(),v_tenant,p3,null,null,'T-002','CCTV Camera Installation (48 cameras)','TASK','IN_PROGRESS','HIGH',v_user,'Rudi Grobler',CURRENT_DATE-10,CURRENT_DATE+15,CURRENT_DATE-10,null,30,120,36,false,false,180000,54000,false,null,2,v_user,NOW()-INTERVAL '10 days',NOW()),
  (gen_random_uuid(),v_tenant,p3,null,null,'T-003','Access Control — Biometric Readers','TASK','NOT_STARTED','MEDIUM',null,null,CURRENT_DATE+15,CURRENT_DATE+30,null,null,0,60,0,false,false,120000,0,false,null,3,v_user,NOW()-INTERVAL '5 days',NOW()),
  (gen_random_uuid(),v_tenant,p3,null,null,'T-004','System Commissioning and Handover','MILESTONE','NOT_STARTED','HIGH',null,null,CURRENT_DATE+38,CURRENT_DATE+40,null,null,0,8,0,true,true,0,0,true,null,4,v_user,NOW()-INTERVAL '5 days',NOW());

-- ── RESOURCES for P1 ─────────────────────────────────────────────────────────

INSERT INTO project_resources VALUES
  (gen_random_uuid(),v_tenant,p1,t3,'HUMAN','Hendrik Botha','Pile Rig Operator',100,CURRENT_DATE-80,CURRENT_DATE-20,null,1800,320,210,NOW()-INTERVAL '80 days'),
  (gen_random_uuid(),v_tenant,p1,t4,'HUMAN','Zanele Dlamini','Concrete Foreman',100,CURRENT_DATE-30,CURRENT_DATE+10,null,1400,160,65,NOW()-INTERVAL '30 days'),
  (gen_random_uuid(),v_tenant,p1,t7,'HUMAN','Francois Du Plessis','Rebar Fixer',100,CURRENT_DATE-10,CURRENT_DATE+5,null,1200,200,40,NOW()-INTERVAL '10 days'),
  (gen_random_uuid(),v_tenant,p1,null,'HUMAN','Sipho Nkosi','Site Agent',100,CURRENT_DATE-120,CURRENT_DATE+60,null,2500,960,620,NOW()-INTERVAL '120 days'),
  (gen_random_uuid(),v_tenant,p1,null,'EQUIPMENT','Liebherr LB 16 Pile Rig','',100,CURRENT_DATE-80,CURRENT_DATE-20,8500,null,320,195,NOW()-INTERVAL '80 days'),
  (gen_random_uuid(),v_tenant,p1,null,'EQUIPMENT','Concrete Pump Truck 32m','',50,CURRENT_DATE-30,CURRENT_DATE+30,3200,null,160,40,NOW()-INTERVAL '30 days'),
  (gen_random_uuid(),v_tenant,p1,null,'VEHICLE','DAF CF 450 Tipper 6x4','Spoil removal',100,CURRENT_DATE-80,CURRENT_DATE-60,2100,null,200,200,NOW()-INTERVAL '80 days'),
  (gen_random_uuid(),v_tenant,p1,null,'SUBCONTRACTOR','Cape Piling CC','Specialist piling',100,CURRENT_DATE-80,CURRENT_DATE-20,null,null,320,210,NOW()-INTERVAL '80 days');

-- Resources for P2
INSERT INTO project_resources VALUES
  (gen_random_uuid(),v_tenant,p2,null,'HUMAN','Riaan Pretorius','Site Foreman',100,CURRENT_DATE-45,CURRENT_DATE+90,null,1600,540,195,NOW()-INTERVAL '45 days'),
  (gen_random_uuid(),v_tenant,p2,null,'EQUIPMENT','CAT 374 Excavator','Primary earthworks',100,CURRENT_DATE-45,CURRENT_DATE+60,12000,null,720,290,NOW()-INTERVAL '45 days'),
  (gen_random_uuid(),v_tenant,p2,null,'EQUIPMENT','Volvo A40G Articulated Dump Truck','',100,CURRENT_DATE-45,CURRENT_DATE+60,8500,null,720,295,NOW()-INTERVAL '45 days');

-- ── BUDGET LINES for P1 ───────────────────────────────────────────────────────

INSERT INTO project_budget_lines VALUES
  (gen_random_uuid(),v_tenant,p1,ph1,'LABOUR',  'Phase 1 Labour — Demolition & Excavation',  850000,0,      842000,false,false,1,NOW()-INTERVAL '120 days'),
  (gen_random_uuid(),v_tenant,p1,ph1,'EQUIPMENT','Plant Hire — Demolition Phase',             620000,0,      598000,false,false,2,NOW()-INTERVAL '120 days'),
  (gen_random_uuid(),v_tenant,p1,ph2,'SUBCONTRACT','Cape Piling CC — Specialist Contract',   1400000,1400000,1155000,false,true,3,NOW()-INTERVAL '80 days'),
  (gen_random_uuid(),v_tenant,p1,ph2,'MATERIALS','Reinforcement Steel — 48 tons',              980000,980000, 680000,false,false,4,NOW()-INTERVAL '80 days'),
  (gen_random_uuid(),v_tenant,p1,ph2,'LABOUR',  'Phase 2 Labour — Piling & Ground Beams',    720000,0,      395000,false,false,5,NOW()-INTERVAL '80 days'),
  (gen_random_uuid(),v_tenant,p1,ph3,'MATERIALS','Formwork Hire — Deck and Piers',            480000,420000,  0,false,false,6,NOW()-INTERVAL '10 days'),
  (gen_random_uuid(),v_tenant,p1,ph3,'LABOUR',  'Phase 3 Labour — Superstructure',           820000,0,       0,false,false,7,NOW()-INTERVAL '10 days'),
  (gen_random_uuid(),v_tenant,p1,ph3,'MATERIALS','Ready-mix Concrete — 480m³',               580000,0,       0,false,false,8,NOW()-INTERVAL '10 days'),
  (gen_random_uuid(),v_tenant,p1,null,'CONTINGENCY','Contingency — 5% contract value (PS)',  460000,0,       0,true,false,9,NOW()-INTERVAL '120 days'),
  (gen_random_uuid(),v_tenant,p1,null,'OVERHEAD','Site overhead — supervision and P&G',      590000,0,      530000,false,false,10,NOW()-INTERVAL '120 days');

-- Budget for P2
INSERT INTO project_budget_lines VALUES
  (gen_random_uuid(),v_tenant,p2,null,'LABOUR',  'Earthworks labour',     520000,0, 180000,false,false,1,NOW()-INTERVAL '45 days'),
  (gen_random_uuid(),v_tenant,p2,null,'EQUIPMENT','Plant hire',          1400000,0, 480000,false,false,2,NOW()-INTERVAL '45 days'),
  (gen_random_uuid(),v_tenant,p2,null,'MATERIALS','Drainage materials',   480000,420000,0,false,false,3,NOW()-INTERVAL '10 days'),
  (gen_random_uuid(),v_tenant,p2,null,'OVERHEAD', 'Site management',      180000,0,  92000,false,false,4,NOW()-INTERVAL '45 days');

-- ── CHANGE ORDERS for P1 ─────────────────────────────────────────────────────

INSERT INTO change_orders VALUES
  (gen_random_uuid(),v_tenant,p1,'CO-001','Deck width increase 14m → 16m',
   'SANRAL requested deck widened 2m to accommodate future HOV lane.',
   'Client-initiated scope change per JBCC Clause 12.1',
   'SUBMITTED',680000,21,v_user,NOW()-INTERVAL '5 days',null,null,null,NOW()-INTERVAL '5 days',NOW()),
  (gen_random_uuid(),v_tenant,p1,'CO-002','Additional dewatering — groundwater',
   'Unforeseen groundwater required 6 additional pump weeks.',
   'Latent conditions not apparent from geotech report',
   'APPROVED',145000,10,v_user,NOW()-INTERVAL '45 days',v_user,'Thabo Molefe',NOW()-INTERVAL '40 days',null,NOW()-INTERVAL '45 days',NOW());

-- ── RISKS for P1 ─────────────────────────────────────────────────────────────

INSERT INTO project_risks VALUES
  (gen_random_uuid(),v_tenant,p1,'R-001','Rebar supply shortage — steel price spike',
   'Main contractor experiencing supply delays from Macsteel. 3-week lead time on 32mm bar.',
   'FINANCIAL',4,5,'RED','OPEN',
   'Pre-ordered 20 tons from Scaw Metals at current spot price.',
   v_user,'Thabo Molefe',CURRENT_DATE+7,false,NOW()-INTERVAL '15 days',NOW()),
  (gen_random_uuid(),v_tenant,p1,'R-002','OHSA non-compliance — fall protection on pier edges',
   'Inspected by DoL on 15 June. 3 penalty notices issued for inadequate fall arrest systems.',
   'SAFETY',4,5,'RED','OPEN',
   'All work halted on exposed edges. Scaffolding contractor engaged.',
   v_user,'Sipho Nkosi',CURRENT_DATE+3,true,NOW()-INTERVAL '10 days',NOW()),
  (gen_random_uuid(),v_tenant,p1,'R-003','Ground water ingress into pile excavations',
   'Water table higher than expected from geotech report. Dewatering pumps deployed.',
   'TECHNICAL',3,4,'AMBER','OPEN',
   'Additional dewatering pump ordered. Excavation sequence revised.',
   v_user,'Pieter van Zyl',CURRENT_DATE+14,false,NOW()-INTERVAL '20 days',NOW()),
  (gen_random_uuid(),v_tenant,p1,'R-004','Client variation — widen deck from 14m to 16m',
   'SANRAL requested 2m additional width. Change order CO-001 submitted.',
   'FINANCIAL',2,3,'AMBER','OPEN',
   'Change order submitted. Contract allows for variations under Clause 12.',
   v_user,'Thabo Molefe',CURRENT_DATE+21,false,NOW()-INTERVAL '5 days',NOW()),
  (gen_random_uuid(),v_tenant,p1,'R-005','Heavy rainfall — 2-week construction delay',
   'Unusually high January rainfall caused 12 working-day stoppage.',
   'SCHEDULE',5,3,'AMBER','MITIGATED',
   'Extension of time approved. Programme compressed with night shifts.',
   v_user,'Thabo Molefe',null,false,NOW()-INTERVAL '60 days',NOW()),
  (gen_random_uuid(),v_tenant,p1,'R-006','Utility strike during excavation',
   'Uncharted gas main discovered at -2.8m depth. Area evacuated 6 hours.',
   'SAFETY',2,5,'AMBER','CLOSED',
   'Utility detection completed for full site.',
   v_user,'Sipho Nkosi',null,true,NOW()-INTERVAL '45 days',NOW());

-- Risks for P2
INSERT INTO project_risks VALUES
  (gen_random_uuid(),v_tenant,p2,'R-001','Unexpected rock — excavation cost overrun',
   'Rock encountered at -1.2m in NW corner. Requires blasting.',
   'FINANCIAL',3,4,'AMBER','OPEN',
   'Blasting contractor engaged. Variation order being prepared.',
   v_user,'Thabo Molefe',CURRENT_DATE+10,false,NOW()-INTERVAL '5 days',NOW());

-- ── DOCUMENTS for P1 ─────────────────────────────────────────────────────────

INSERT INTO project_documents VALUES
  (gen_random_uuid(),v_tenant,p1,'DRAWING','Foundation Layout Plan','Rev C','https://example.com/docs/N2-STR-001-RevC.pdf','N2-STR-001-RevC.pdf',2840,'CURRENT','Pile positions and pile cap layout',v_user,'Thabo Molefe',NOW()-INTERVAL '5 days'),
  (gen_random_uuid(),v_tenant,p1,'DRAWING','Foundation Layout Plan','Rev B','https://example.com/docs/N2-STR-001-RevB.pdf','N2-STR-001-RevB.pdf',2720,'SUPERSEDED','Superseded by Rev C',v_user,'Thabo Molefe',NOW()-INTERVAL '30 days'),
  (gen_random_uuid(),v_tenant,p1,'DRAWING','Reinforcement — Pile Cap Type A','Rev A','https://example.com/docs/N2-STR-002-RevA.pdf','N2-STR-002-RevA.pdf',1680,'APPROVED','600mm dia pile cap rebar schedule',v_user,'Thabo Molefe',NOW()-INTERVAL '60 days'),
  (gen_random_uuid(),v_tenant,p1,'RFI','RFI-001: Pile toe levels — north pier','Rev 1','https://example.com/docs/RFI-001.pdf','RFI-001.pdf',320,'APPROVED','Response received from structural engineer',v_user,'Sipho Nkosi',NOW()-INTERVAL '35 days'),
  (gen_random_uuid(),v_tenant,p1,'RFI','RFI-002: Deck reinforcement cover increase',null,'https://example.com/docs/RFI-002.pdf','RFI-002.pdf',280,'FOR_REVIEW','Request to increase cover from 40mm to 55mm',v_user,'Sipho Nkosi',NOW()-INTERVAL '3 days'),
  (gen_random_uuid(),v_tenant,p1,'CONTRACT','SANRAL JBCC Principal Building Agreement',null,'https://example.com/docs/SANRAL-Contract-2026.pdf','SANRAL-Contract-2026.pdf',15200,'CURRENT','Signed contract — JBCC Minor Works Edition 6.2',v_user,'Thabo Molefe',NOW()-INTERVAL '125 days'),
  (gen_random_uuid(),v_tenant,p1,'REPORT','Monthly Progress Report — June 2026',null,'https://example.com/docs/Progress-June-2026.pdf','Progress-June-2026.pdf',4200,'CURRENT','June 2026 EVM report — CPI 0.87, SPI 0.74',v_user,'Thabo Molefe',NOW()-INTERVAL '1 day'),
  (gen_random_uuid(),v_tenant,p1,'SUBMITTAL','Ready-Mix Concrete Mix Design — 40MPa','Rev A','https://example.com/docs/Concrete-Mix-Design.pdf','Concrete-Mix-Design.pdf',890,'APPROVED','Approved mix design from AfriSam for deck pour',v_user,'Thabo Molefe',NOW()-INTERVAL '25 days');

-- ── SITE DIARIES for P1 ──────────────────────────────────────────────────────

INSERT INTO site_diaries VALUES
  (gen_random_uuid(),v_tenant,p1,CURRENT_DATE,'CLEAR',18.0,
   24,26,'Continued pile driving — completed piles 14–17. Pile cap shuttering for PC-02 and PC-03.',
   'Programme 3 days behind. Targeting catch-up next week.',
   'Rebar delivery delayed — only 4 of 8 tons received. PC-04 on hold.',
   'Working at heights — fall arrest and harness inspection',
   null,v_user,'Sipho Nkosi',NOW(),NOW()),
  (gen_random_uuid(),v_tenant,p1,CURRENT_DATE-1,'CLOUDY',16.0,
   22,26,'Pile driving piles 11–13. Concrete pour pile cap PC-01 (18m³ ready-mix).',
   'PC-01 pour completed. Quality cube samples taken.',
   'Wind gusts delayed crane lifts by 2 hours in afternoon.',
   'Manual handling — correct lifting technique for rebar bundles',
   null,v_user,'Sipho Nkosi',NOW()-INTERVAL '1 day',NOW()-INTERVAL '1 day'),
  (gen_random_uuid(),v_tenant,p1,CURRENT_DATE-2,'RAIN',12.5,
   8,26,'Rain stopped all concrete work. Crew undertook workshop maintenance and tool inspection.',
   'Lost production day — programme impact to be assessed.',
   'Heavy rain — site flooded in excavation areas.',
   'Electrical safety — portable tools in wet conditions',
   'Near miss: worker slipped on muddy ramp — no injury. Documented.',
   v_user,'Sipho Nkosi',NOW()-INTERVAL '2 days',NOW()-INTERVAL '2 days'),
  (gen_random_uuid(),v_tenant,p1,CURRENT_DATE-3,'CLEAR',20.0,
   26,26,'Piles 8–10 completed. Cage installation for piles 11–13 commenced.',
   'Ahead of daily target by 15%. Rebar cage fabrication on track.',
   null,'OHSA Act 85 — section 8 and 9 general duties of employers',
   null,v_user,'Sipho Nkosi',NOW()-INTERVAL '3 days',NOW()-INTERVAL '3 days'),
  (gen_random_uuid(),v_tenant,p1,CURRENT_DATE-4,'CLOUDY',17.0,
   24,26,'Rebar cage fabrication — 6 cages completed. Pilot hole reaming.',
   'Programme on track for this week.',
   null,'Struck-by hazards — exclusion zones around crane operations',
   null,v_user,'Sipho Nkosi',NOW()-INTERVAL '4 days',NOW()-INTERVAL '4 days');

-- ── SNAG ITEMS for P1 ────────────────────────────────────────────────────────

INSERT INTO snag_items VALUES
  (gen_random_uuid(),v_tenant,p1,null,'SN0001','Concrete honeycombing on pile cap PC-03',
   'Visible voids on south face. Area approx 150mm×80mm×30mm deep.',
   'Pier 3 — Pile Cap PC-03, South Face','HIGH','OPEN',
   v_user,'Hendrik Botha',CURRENT_DATE+5,null,v_user,NOW()-INTERVAL '3 days',NOW()),
  (gen_random_uuid(),v_tenant,p1,null,'SN0002','OHSA: Missing guardrail on pier east scaffold',
   'Guardrail gap on east scaffold at +4.5m level. Non-compliance.',
   'Pier 2 East Scaffold Level 2','CRITICAL','IN_PROGRESS',
   v_user,'Safety Officer',CURRENT_DATE+1,null,v_user,NOW()-INTERVAL '2 days',NOW()),
  (gen_random_uuid(),v_tenant,p1,null,'SN0003','Rebar cover insufficient at pile head',
   'Cover measured at 28mm — spec requires 40mm min. Affects 4 pile heads.',
   'North Pier — Piles 7, 8, 9, 10','HIGH','OPEN',
   null,null,CURRENT_DATE+7,null,v_user,NOW()-INTERVAL '5 days',NOW()),
  (gen_random_uuid(),v_tenant,p1,null,'SN0004','Site hoarding panel damaged — security breach',
   '6m section of hoarding displaced by vehicle.',
   'Site entrance — North hoarding','MEDIUM','RESOLVED',
   v_user,'Site Crew',null,null,v_user,NOW()-INTERVAL '10 days',NOW()-INTERVAL '8 days');

RAISE NOTICE '✓ V90 PM test data seeded — 5 projects, full P1 data across all 8 tabs';
END $$;
