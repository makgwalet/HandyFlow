# Internal Audit module audit

## 1. Interface refresh (Slice 1)

The screens already covered the whole audit cycle; this slice changes how they read, to match the design mockup, using only data the server already returns.

**Shared look** (`auditUi.tsx`, `auditUi.styles.ts`, `auditUi.logic.ts`): stat tiles, a progress ring, meters, status pills, a filter bar and a pager. The sums behind them (progress by stage, ring arcs, severity bar widths, key dates, filtering, paging) are plain functions with tests.

**Dashboard.** Four tiles (audits in the plan, open findings, overdue actions, repeat findings); an Audit progress ring; Findings by severity as bars (critical to low, always all four); coverage of the audit universe; the existing overdue actions, repeat findings and areas-not-planned lists; Upcoming key dates (engagement starts and ends in the next 90 days, and open engagements past their end date); and links to the main sections.

**Annual Plans.** Each plan opens to a summary (counts, percentage completed, entries per quarter) and a table of entries with search, risk and status filters and paging.

**Engagements.** A count by stage above the list, search and a status filter, the dates and a coloured status on each row.

**Stage names.** Engagement statuses map PLANNING to Planned, FIELDWORK and REPORTING to In progress, CLOSED to Completed. Deferred plan entries are shown but left out of the percentage, as on the server.

**Not done, and why**
- *Trend arrows ("vs last quarter").* The server keeps no history of these figures; showing one would be invented.
- *Recent activity.* No endpoint lists audit events across the module.
- *Cross-engagement Findings & Remediation table, Risk Heat Map, Audit Settings, External Audit Portal restyle, mobile task list.* Findings are loaded per engagement only and the universe list carries one risk level, not the inherent and control scores a heat map needs; these need new endpoints or a restyle of screens outside this slice.
- *Engagement detail page* (info card, progress bars, milestones). The existing expandable engagement and its tabs are unchanged.
- *Workpapers split view.* The workpaper list inside an engagement is unchanged.

**Tests.** Run under Node: `auditUi.test.ts` (13 cases). The new components were also server-rendered once as a smoke check. Written but not run (vitest is not installed here): `PlanEntries.test.tsx` and three new cases in `AuditDashboards.test.tsx`. `tsc` is clean; the new files lint clean and `EngagementsSection.tsx` keeps its 16 existing lint errors. Nothing was viewed in a browser, so spacing and dark mode are unchecked.
