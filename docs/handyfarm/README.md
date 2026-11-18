# HandyFarm documentation

HandyFarm is an independent agricultural business management application within the HandyFlow ecosystem. It connects land and farm management, crop production, livestock and animal health, poultry and aquaculture, irrigation and water, workers and daily activities, machinery and equipment, agricultural inventory, harvest and storage, sales and traceability, production costing and gross margins, compliance and biosecurity, and weather and agricultural intelligence.

Written 2026-11-18. Product name HandyFarm; backend module Agriculture.

## Documents

| File | What it holds |
|---|---|
| `HANDYFARM_PRD.md` | Approved decisions, functional scope, personas, first-release definition, acceptance scenarios |
| `HANDYFARM_ARCHITECTURE.md` | Engines, hierarchy, ownership matrix, offline design, security and permissions, API and technology direction |
| `HANDYFARM_CURRENT_STATE_AND_GAPS.md` | What exists in the repo today, requirement-to-code traceability, gap register, decisions needed |
| `HANDYFARM_ROADMAP_AND_TEST_PLAN.md` | Phases 0 to 9, release gates, automated scenarios, documents still to write |
| `../mobile/SUPPLY_CHAIN_MOBILE_PLAN.md` | The sibling Supply Chain mobile plan |
| `../mobile/MOBILE_SHARED_FOUNDATION_REUSE_MATRIX.md` | First-cut inventory of the shared mobile foundation |

Related existing records: `ADR-001-agriculture-financial-integration.md`, `ADR-002-agriculture-weather-and-satellite.md` (repo root), `docs/mobile/MOBILE_*` (12 Nov), and `HandyFlow-Apps/handyfarm-offline-increment/handyfarm/docs/agriculture/` (app overview, API map, API gaps, offline architecture).

## Sources and how they were reconciled

1. **Approved Product Requirements and Implementation Plan.** Records the product owner's eight decisions. Treated as authoritative.
2. **HandyFlow Agri deep product, business and technical planning.** A broader, earlier paper. Used for detail (engines, workflows, security, reports). Where it differs from the approved plan, the approved plan wins:

| Topic | Deep plan | Approved plan (used) |
|---|---|---|
| Product name | "HandyFlow Agri", then corrects to HandyFarm | HandyFarm |
| Offline | Phase 4 | Phase 1 foundation first; Field Worker Mode in Phase 4 |
| First release | Farm, crops, livestock, health, tasks, evidence, offline, basic inputs and reports | Adds mixed-farm setup, basic purchasing, enterprise gross margin, tablets |
| Poultry and aquaculture | Phase 7 specialised | Can follow the first release |
| Stock | Warns HandyFarm-only tenants still need stock | Independent basic purchasing and inputs; packaging still to decide |

3. **HandyFlow Supply Chain Mobile plan.** Documented separately as a sibling app. Its eight closing decisions were recommended defaults and are not recorded as approved. It also conflicts with the 12 Nov assessment, which folded Supply Chain into the employee and warehousing apps.

Gaps in the source papers: the deep plan ends at "31. Decisions to formalise before implementation. My recommendations are below." with no content, so no decisions were taken from it. The Supply Chain paper's ten-item brief for Claude (repository audit first) is reflected in the reuse matrix and roadmap, not as a separate task list.

## Status of this work
Documentation and a read-only repo inspection only. No code, migrations, APIs or screens were changed. Nothing was compiled or run. Rows marked `not checked` in the current-state document need a code read before anyone relies on them.

## Next steps the plan calls for
Screen specification, offline and sync design, API and database change register, permission matrix, prioritised tickets, test plan. First decisions to take are listed at the end of `HANDYFARM_CURRENT_STATE_AND_GAPS.md`.
