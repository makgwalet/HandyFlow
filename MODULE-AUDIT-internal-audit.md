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

## 2. Dashboard trends and recent activity (Slice 2, V549)

**Trend arrows.** The headline figures (engagements, open findings, overdue actions, high-risk areas) are now saved once a day, at the first look at the dashboard (`audit_dashboard_snapshots`, V549). `GET /internal-audit/dashboard/trends` counts them, saves today's, and compares with the saved day nearest to 90 days ago that is at least 7 days old. Fewer open findings, overdue actions or high-risk areas shows green; more shows red; engagements are neutral. With nothing old enough saved, there is no arrow and the screen says so.

**Recent activity.** `GET /internal-audit/dashboard/activity` reads the audit trail (`audit_events`) for the action codes internal audit writes (findings, workpapers, reports, follow-ups, programme, quality reviews, review notes, action plans) and names the finding, engagement or workpaper it happened to. New method `AuditTrailFacade.recent`. Both endpoints need `AUDIT_READ`; the dashboard shows neither if its call fails.

**Limits**
- Nothing is back-filled, and a day no one opened the dashboard has no row, so the first arrows appear a week after install and compare with whatever the oldest suitable day is, not always a full quarter. The text says how many days ago.
- High-risk areas counts universe entries whose latest assessment's final risk is HIGH or CRITICAL.
- Activity covers only actions that already write to the audit trail; plan approvals, universe edits and engagement creation do not, so they do not appear.
- Not compiled or run: the Spring classes (`AuditSnapshotStore`, `InternalAuditTrendService`, `AuditActivityService`, the controller, `AuditTrailService.recent`) and the SQL, including `LIKE ANY (?)` with an array parameter. Run: `DashboardTrendsTest` (6, offline) and `auditUi.test.ts` (18, under Node). Frontend dashboard cases written, not run.

## 3. Findings & Remediation across engagements (Slice 3)

New section **Findings & Remediation** in the Internal Audit menu (needs `AUDIT_READ`), backed by `GET /internal-audit/remediation`. Until now findings could only be seen one engagement at a time.

- **Summary:** open findings (with critical and high counts), past due, open with no action agreed, actions overdue (and waiting for validation), and the share of actions validated.
- **Findings tab:** every finding, worst first (open before settled, then severity, then earliest due date), with engagement, area, owner, due date, a "Repeat" mark and a remediation bar. Filters: text, severity and state (open and in progress by default; past due; open with no action; resolved or closed). "View" opens a panel with what was found, the recommendation, management's response and the finding's actions.
- **Actions tab:** every action plan, overdue first (longest late first), then those still to do, then settled ones.
- **Remediation meaning:** an action counts as done once the auditor has *validated* it; one that is only completed is "awaiting validation"; cancelled actions are left out. A finding's percentage is validated over live actions, and is 0 with no actions.

**Limits**
- The whole list is loaded and filtered in the browser; fine for hundreds of findings, but a company with many thousands would need server paging.
- Actions can be completed, validated or returned only from the existing finding screens; this screen is read-only.
- The panel shows what the list carries (description, recommendation, management response); the root cause, criteria, condition and consequence are still only in the engagement's own finding view.
- Not compiled or run: `AuditRemediationService`, the controller and the new `findAllForTenant` query (Spring and PostgreSQL). Run: `RemediationBoardTest` (8, offline), `remediation.test.ts` (4, under Node); the new screen was server-rendered once with data. `Remediation.test.tsx` is written but not run (vitest is not installed).

## 4. Risk Heat Map (Slice 4)

New section **Risk Heat Map** (needs `AUDIT_READ`), backed by `GET /internal-audit/heat-map`. It reads each active universe area's latest risk assessment.

- **Inherent and control risk:** a 5 by 5 grid, inherent risk down the side (highest at the top) and control risk across (higher is weaker control). Each square shows how many areas sit there; pick one to list them. Squares are coloured by the product of the two scores: up to 4 low, up to 9 medium, up to 15 high, above that critical.
- **By process area:** for each process area, how many areas are rated low, medium, high and critical (final audit risk, including any override), and how many are not assessed. Worst process area first.
- Tiles: areas, assessed, not assessed (links to the Audit Universe), and the share rated high or critical.

**Limits**
- The mockup's Inherent / Residual / Control-effectiveness tabs are not built: the assessment stores inherent risk, control risk and a final level, but no residual score or control-effectiveness rating, so those views would be invented.
- The square colours are a standard 5 by 5 convention, not the assessment's own formula, which adds five inputs (inherent, control, past findings, time since last audit, business impact) and can put an area in a different level than its square. The screen says so.
- Process areas are grouped by the universe entry's free-text process area, case-insensitively; spellings that differ other than in case are separate rows.
- Not compiled or run: `AuditHeatMapService`, the controller (Spring). Run: `RiskHeatMapTest` (7, offline), `heatMap.test.ts` (4, under Node); the screen was server-rendered once with data. `HeatMap.test.tsx` is written but not run.

## 5. Engagement detail page and workpapers split view (patch 0515)

Each row in the Engagements list now has a **Details** link to `/internal-audit/engagements/{id}`, a page with three tabs. It is backed by `GET /internal-audit/engagements/{id}/summary` (needs `AUDIT_READ`).

- **Overview:** overall progress, signed-off workpapers, open findings, overdue actions, team size; progress by phase (Planning, Fieldwork, Testing, Reporting); and milestones (done, in progress, pending, late).
- **Workpapers:** the current version of every workpaper on the left (search, status and section filters), the chosen one on the right with who prepared, reviewed and signed it off, its review comments (the existing panel) and a **Download** button. Download is new: `GET /internal-audit/workpaper-files/{id}/content` (needs `AUDIT_READ`), and each download is recorded in the audit trail as `AUDIT_WORKPAPER_DOWNLOADED`.
- **Findings & actions:** the Findings & Remediation screen limited to this engagement.

**How progress is worked out (not stored):** Planning is five items set (objectives, scope, criteria and the materiality figures); Fieldwork is signed-off workpapers over all workpapers; Testing is programme steps done, or sampled items tested when there is no programme; Reporting follows the report stage (25, 50, 75, 100). Overall is the plain average of the four.

**Limits**
- Planning, sampling and testing, team changes and uploading workpapers are still done in the Engagements list; the detail page links back to it.
- Workpapers are loaded one folder at a time (existing endpoints), so an engagement with many folders makes one call per folder.
- Not compiled or run: `AuditEngagementSummaryService`, `AuditWorkpaperDownloadService` and both controllers (Spring). Run: `EngagementSummaryBuilderTest` (10) and `DownloadNamesTest` (3) offline, and `engagementDetail.test.ts` (6, under Node). tsc and eslint are clean on the new files. Nothing was viewed in a browser, and no component test was written for the two new screens.

## 6. My Tasks, Audit Settings and the External Audit Portal restyle (patch 0516)

**My Tasks** (new section, `AUDIT_READ`, backed by `GET /internal-audit/my-tasks`): the signed-in person's own open work, most urgent first, grouped as overdue, due in 7 days, later and no date. It lists actions they own that are still open; findings they own that are open or in progress; review notes waiting for their answer (they prepared the workpaper); and answered notes waiting for them to clear (they raised it). Each row opens its engagement. It is a plain card list, so it reads on a phone.

**Audit Settings** (new section under Setup, `AUDIT_READ`, backed by `GET /internal-audit/settings`): the risk score bands, reminder timing and engagement roles, read only. The band limits and reminder days are now named constants (`RiskAssessment.LOW_MAX`/`MEDIUM_MAX`/`HIGH_MAX`, `ReminderRules`) that the code itself uses, so the screen cannot disagree with it. No behaviour changed.

**External Audit Portal:** the home and business pages share one frame (`PortalShell`) with page gutters that shrink on a phone, scrollable tabs, a tile row (open exceptions, resolved, evidence files) and rounded cards. Wording, tabs and data are unchanged.

**Limits**
- Audit Settings cannot be edited: there is no settings storage, and inventing one was out of scope. If the firm wants its own score bands or reminder days, that needs a settings table and a change to the scoring code.
- My Tasks does not include evidence requests or audit queries (they belong to the other modules' screens), and it does not list actions waiting for the person to validate, because who may validate is decided when they try (never the owner or the person who completed it).
- My Tasks reads every action, finding and open note for the company and filters in memory, and looks up one workpaper per open note. Fine at this size; it should be queried by owner if volumes grow.
- Not compiled or run: `AuditMyTasksService`, `AuditSettingsController`, `AuditMyTasksController` (Spring), and the edit to `RiskAssessment`. Run offline: `MyTasksTest` (5, one mutation caught), `AuditSettingsTest` (3, against a stub of `RiskAssessment` holding the same three numbers) and the existing `ReminderRulesTest` (3) after the constants change; `myTasks.test.ts` (3, under Node). tsc is clean; the three new screens and the engagement page were server-rendered once with sample data. No component tests written, nothing viewed in a browser, and the portal was not rendered.
