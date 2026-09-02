/**
 * Collections Agency — outsourced-provider module for a registered
 * third-party debt collector managing debtor portfolios on behalf of
 * external creditor clients. This is the provider-side sibling of the
 * `debtcollection` (internal) module, built as a genuinely separate
 * module rather than a mode on it — the same reasoning already
 * established for accountant/accounting, payrollbureau/hr, and
 * recruiter/recruitmentagency: this agency's clients aren't necessarily
 * HandyFlow tenants themselves, and the agency needs its own
 * client-portfolio/billing/trust-accounting layer that has no equivalent
 * in `debtcollection`. `debtcollection` stays untouched and isolated;
 * this module does NOT depend on it and does NOT import any of its
 * classes.
 * <p>
 * REAL DIFFERENCES FROM `debtcollection`, NOT JUST A RELABEL (per the
 * user's own domain analysis that scoped this module):
 * <ul>
 *   <li>The agency does not own the debts it works — it collects on
 *   behalf of a creditor client (CollAgencyClient), and the money
 *   collected belongs to that client, not the agency.</li>
 *   <li>Revenue is commission (a percentage of what's recovered), billed
 *   to the creditor client — not a debt the agency is owed itself.</li>
 *   <li>Money collected from debtors sits in a TRUST relationship until
 *   remitted to the client, minus commission. This is the first
 *   trust-accounting pattern in this codebase (confirmed by search — no
 *   precedent existed before this module) and is deliberately a
 *   **self-contained module trust ledger**: CollAgencyTrustTransaction
 *   tracks money received from debtors and remitted to clients entirely
 *   within this module's own tables. It NEVER posts to the tenant's real
 *   chart of accounts — that money was never the tenant's own revenue or
 *   asset. Only the agency's own commission (CollAgencyCommissionInvoice)
 *   posts to the real GL via AccountingFacade, the same
 *   createJournalEntry()/postJournalEntry() pattern
 *   RecruitmentAgencyService/PayrollBureauService already use for their
 *   own fee/placement invoices. This was a confirmed design decision
 *   (not guessed) — see the Collections Agency status doc for the
 *   trade-offs discussed before building.</li>
 *   <li>Registered debt collectors face real regulatory obligations an
 *   original creditor does not: mandatory firm registration (tracked on
 *   CollAgencyProfile) AND mandatory individual collector registration
 *   (CollAgencyCollector) under the Debt Collectors Act, both with
 *   renewal tracking, and stricter National Credit Act communication
 *   rules — every contact must disclose third-party-collector status,
 *   name the original creditor, and state the debtor's rights
 *   (enforced, not just tracked, by CollAgencyContactLog itself — see
 *   that entity's own Javadoc).</li>
 *   <li>The core operational loop is placement/handover, not case
 *   escalation: a creditor client places a batch of debtor accounts
 *   (CollAgencyPlacementBatch -> CollAgencyDebtorAccount), the agency
 *   works them, and reports progress/recovery back to the client.</li>
 * </ul>
 * <p>
 * ENTITY STYLE NOTE: this module follows the plain-entity, raw-UUID
 * provider-module convention this specific family already established
 * (RecAgencyClient/RecAgencyInvoice, PayClient/PayFeeNote — @Id UUID
 * assigned in the field initializer, a raw UUID tenant_id column, manual
 * createdAt/updatedAt, String status fields rather than @Enumerated Java
 * enums), NOT the AggregateRoot/TenantId-embeddable convention used by
 * `legalcompliance`/`debtcollection` (internal-department modules). This
 * is a deliberate choice to match this module's actual family, confirmed
 * by reading RecAgencyClient/RecAgencyInvoice/PayPortalAccessGrant
 * directly — not an inconsistency.
 * <p>
 * allowedDependencies:
 *   shared        — TenantId/TenantSequenceService/etc.
 *   billing       — FeatureGuard.requireModule(), same as every other
 *                   separately-subscribable module.
 *   accounting    — AccountingFacade, for posting commission revenue only
 *                   (createJournalEntry/postJournalEntry/getAccounts —
 *                   same three methods RecruitmentAgencyService/
 *                   PayrollBureauService already use for this exact
 *                   purpose). NOTE: recruitmentagency's own package-info
 *                   is missing "accounting" from its allowedDependencies
 *                   despite RecruitmentAgencyService injecting and
 *                   calling AccountingFacade — a real pre-existing
 *                   inconsistency discovered while researching this
 *                   module, flagged here rather than silently copied;
 *                   out of scope to fix in this delivery.
 *   evidence      — EvidenceFacade, for demand letters, AODs, and other
 *                   debtor/client correspondence.
 *   notifications — registration-expiry (firm and individual collector)
 *                   and contact/payment-plan reminders via
 *                   NotificationService, same pattern as every other
 *                   compliance scheduler in this codebase.
 * <p>
 * Deliberately NOT a dependency: `debtcollection` (see above — no
 * dependency in either direction) or `identity` (payrollbureau/
 * recruitmentagency include it only for TenantFacade-based branded
 * emails; this module's own CollAgencyProfile already carries the
 * agency's display name, so that dependency wasn't needed here).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "billing", "accounting", "evidence", "notifications"}
)
package za.co.handyflow.platform.collectionsagency;
